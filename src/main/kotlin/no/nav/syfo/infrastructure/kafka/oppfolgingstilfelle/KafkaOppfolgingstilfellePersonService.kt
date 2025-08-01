package no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle

import no.nav.syfo.application.AktivitetskravService
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.gjelder
import no.nav.syfo.domain.isAutomatiskOppfylt
import no.nav.syfo.domain.isNy
import no.nav.syfo.domain.shouldUpdateStoppunkt
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.KafkaConsumerService
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.*

// Oppfølgingstilfeller som begynner før denne datoen antas å ha fullført eventuell aktivitetskrav-vurdering i Arena.
val OLD_TILFELLE_CUTOFF: LocalDate = LocalDate.of(2022, Month.FEBRUARY, 1)

class KafkaOppfolgingstilfellePersonService(
    private val database: DatabaseInterface,
    private val aktivitetskravService: AktivitetskravService,
    private val arenaCutoff: LocalDate,
) : KafkaConsumerService<KafkaOppfolgingstilfellePerson> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaOppfolgingstilfellePerson>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            processRecords(records)
            kafkaConsumer.commitSync()
        }
    }

    private fun processRecords(records: ConsumerRecords<String, KafkaOppfolgingstilfellePerson>) {
        val (tombstoneRecords, validRecords) = records.partition { it.value() == null }
        processTombstoneRecords(tombstoneRecords)
        processValidRecords(validRecords)
    }

    private fun processTombstoneRecords(tombstoneRecords: List<ConsumerRecord<String, KafkaOppfolgingstilfellePerson>>) {
        if (tombstoneRecords.isNotEmpty()) {
            val numberOfTombstones = tombstoneRecords.size
            log.error("Value of $numberOfTombstones ConsumerRecords are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_TOMBSTONE.increment(numberOfTombstones.toDouble())
        }
    }

    private fun processValidRecords(validRecords: List<ConsumerRecord<String, KafkaOppfolgingstilfellePerson>>) {
        database.connection.use { connection ->
            validRecords.forEach { consumerRecord ->
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_READ.increment()
                receiveKafkaOppfolgingstilfellePerson(
                    connection = connection,
                    kafkaOppfolgingstilfellePerson = consumerRecord.value()
                )
            }
            connection.commit()
        }
    }

    private fun receiveKafkaOppfolgingstilfellePerson(
        connection: Connection,
        kafkaOppfolgingstilfellePerson: KafkaOppfolgingstilfellePerson,
    ) {
        val latestOppfolgingstilfelle = kafkaOppfolgingstilfellePerson.toLatestOppfolgingstilfelle()

        if (latestOppfolgingstilfelle == null) {
            log.warn("Skipped processing of record: No Oppfolgingstilfelle found in record.")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NO_TILFELLE.increment()
            return
        }
        if (notRelevantForAktivitetskrav(latestOppfolgingstilfelle)) {
            log.info("Skipped processing of record: Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} not relevant for aktivitetskrav.")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_AKTIVITETSKRAV.increment()
            return
        }

        val aktivitetskravForPerson = aktivitetskravService.getAktivitetskrav(
            connection = connection,
            personIdent = latestOppfolgingstilfelle.personIdent,
        )

        aktivitetskravForPerson.filter { it.isNy() && !(it gjelder latestOppfolgingstilfelle) }
            .forEach { aktivitetskrav ->
                log.info("Found aktivitetskrav NY for tidligere Oppfolgingstilfelle - oppfyll automatisk")
                aktivitetskravService.oppfyllAutomatisk(connection = connection, aktivitetskrav = aktivitetskrav)
            }

        val latestAktivitetskravForTilfelle =
            aktivitetskravForPerson.firstOrNull { it gjelder latestOppfolgingstilfelle }

        if (latestAktivitetskravForTilfelle == null) {
            log.info("Found no aktivitetskrav for Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} - creating aktivitetskrav")
            createAktivitetskrav(connection = connection, oppfolgingstilfelle = latestOppfolgingstilfelle)
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
        } else {
            if (latestAktivitetskravForTilfelle.isAutomatiskOppfylt() && !latestOppfolgingstilfelle.isGradertAtTilfelleEnd()) {
                log.info("Found aktivitetskrav AUTOMATISK_OPPFYLT but Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} not gradert - creating aktivitetskrav")
                createAktivitetskrav(connection = connection, oppfolgingstilfelle = latestOppfolgingstilfelle)
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
            } else if (latestAktivitetskravForTilfelle.shouldUpdateStoppunkt(
                    oppfolgingstilfelle = latestOppfolgingstilfelle,
                    arenaCutoff = arenaCutoff
                )
            ) {
                log.info("Updating stoppunkt for aktivitetskrav for Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid}")
                aktivitetskravService.updateAktivitetskravStoppunkt(
                    connection = connection,
                    aktivitetskrav = latestAktivitetskravForTilfelle,
                    oppfolgingstilfelle = latestOppfolgingstilfelle,
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_UPDATED.increment()
            } else {
                log.info("Found aktivitetskrav for Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} - skipping update")
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_SKIPPED_UPDATE.increment()
            }
        }
    }

    private fun createAktivitetskrav(
        connection: Connection,
        oppfolgingstilfelle: Oppfolgingstilfelle,
    ) {
        val aktivitetskrav = Aktivitetskrav.create(
            personIdent = oppfolgingstilfelle.personIdent,
            oppfolgingstilfelleStart = oppfolgingstilfelle.tilfelleStart,
            isAutomatiskOppfylt = oppfolgingstilfelle.isGradertAtTilfelleEnd(),
        )
        aktivitetskravService.createAktivitetskrav(
            connection = connection,
            aktivitetskrav = aktivitetskrav,
            referanseTilfelleBitUUID = oppfolgingstilfelle.referanseTilfelleBitUuid,
        )
    }

    private fun notRelevantForAktivitetskrav(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
        !oppfolgingstilfelle.passererAktivitetskravStoppunkt() || oppfolgingstilfelle.dodsdato != null || oppfolgingstilfelle.tilfelleStart.isBefore(
            OLD_TILFELLE_CUTOFF
        ) || oppfolgingstilfelle.isInactive()

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
