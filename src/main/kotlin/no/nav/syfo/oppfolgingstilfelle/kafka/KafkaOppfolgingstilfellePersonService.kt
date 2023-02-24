package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.oppfolgingstilfelle.domain.*
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

        log.info("TRACE Oppfolgingstilfelle with UUID ${latestOppfolgingstilfelle.uuid} gradertAtTilfelleEnd: ${latestOppfolgingstilfelle.gradertAtTilfelleEnd}")
        if (latestAktivitetskravForTilfelle == null) {
            log.info("Found no aktivitetskrav for Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} - creating aktivitetskrav")
            aktivitetskravService.createAktivitetskrav(
                connection = connection,
                aktivitetskrav = latestOppfolgingstilfelle.toAktivitetskrav(),
            )
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
        } else {
            if (latestAktivitetskravForTilfelle.isAutomatiskOppfylt() && !latestOppfolgingstilfelle.isGradertAtTilfelleEnd()) {
                log.info("Found aktivitetskrav AUTOMATISK_OPPFYLT but Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} not gradert - creating aktivitetskrav")
                aktivitetskravService.createAktivitetskrav(
                    connection = connection,
                    aktivitetskrav = latestOppfolgingstilfelle.toAktivitetskrav(),
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
            } else if (latestAktivitetskravForTilfelle.isVurdert() && latestOppfolgingstilfelle.isGradertAtTilfelleEnd()) {
                log.info("Found vurdert aktivitetskrav and Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} gradert - creating aktivitetskrav")
                aktivitetskravService.createAktivitetskrav(
                    connection = connection,
                    aktivitetskrav = latestOppfolgingstilfelle.toAktivitetskrav(),
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
            } else {
                log.info("Updating aktivitetskrav for Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid}")
                aktivitetskravService.updateAktivitetskravStoppunkt(
                    connection = connection,
                    aktivitetskrav = latestAktivitetskravForTilfelle,
                    oppfolgingstilfelle = latestOppfolgingstilfelle,
                )
                COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_UPDATED.increment()
            }
        }
    }

    private fun notRelevantForAktivitetskrav(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
        !oppfolgingstilfelle.passererAktivitetskravStoppunkt() || oppfolgingstilfelle.dodsdato != null || oppfolgingstilfelle.tilfelleStart.isBefore(
            OLD_TILFELLE_CUTOFF
        ) || oppfolgingstilfelle.tilfelleEnd.isBefore(arenaCutoff)

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
