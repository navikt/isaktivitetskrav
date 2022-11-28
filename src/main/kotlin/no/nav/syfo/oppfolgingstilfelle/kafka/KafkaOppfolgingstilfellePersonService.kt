package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.oppfolgingstilfelle.domain.*
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration

class KafkaOppfolgingstilfellePersonService(
    private val database: DatabaseInterface,
    private val aktivitetskravService: AktivitetskravService,
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
        if (!latestOppfolgingstilfelle.passererAktivitetskravStoppunkt()) {
            log.info("Skipped processing of record: Oppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} not relevant for aktivitetskrav.")
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_AKTIVITETSKRAV.increment()
            return
        }

        val aktivitetskrav = connection.getAktivitetskrav(
            personIdent = latestOppfolgingstilfelle.personIdent
        ).toAktivitetskravList()

        log.info("Found ${aktivitetskrav.size} aktivitetskrav for person from latestOppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid}")

        val latestAktivitetskravForTilfelle =
            aktivitetskrav.firstOrNull { it gjelder latestOppfolgingstilfelle }

        if (latestAktivitetskravForTilfelle == null) {
            log.info("Found no aktivitetskrav for latestOppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid} - creating aktivitetskrav")
            aktivitetskravService.createAktivitetskrav(
                connection = connection,
                aktivitetskrav = latestOppfolgingstilfelle.toAktivitetskrav(),
            )
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
        } else {
            log.info("Found aktivitetskrav for latestOppfolgingstilfelle with uuid ${latestOppfolgingstilfelle.uuid}")
            when {
                latestAktivitetskravForTilfelle.isNy() -> {
                    log.info("latestAktivitetskravForTilfelle with uuid ${latestAktivitetskravForTilfelle.uuid} is NY: Updating")
                    aktivitetskravService.updateAktivitetskrav(
                        connection = connection,
                        aktivitetskrav = latestAktivitetskravForTilfelle,
                        oppfolgingstilfelle = latestOppfolgingstilfelle,
                    )
                    COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_UPDATED.increment()
                }

                latestAktivitetskravForTilfelle.isAutomatiskOppfylt() -> {
                    log.info("latestAktivitetskravForTilfelle with uuid ${latestAktivitetskravForTilfelle.uuid} is AUTOMATISK_OPPFYLT")
                    if (latestOppfolgingstilfelle.isGradertAtTilfelleEnd()) {
                        log.info("isGradertAtTilfelleEnd: Updating")
                        aktivitetskravService.updateAktivitetskrav(
                            connection = connection,
                            aktivitetskrav = latestAktivitetskravForTilfelle,
                            oppfolgingstilfelle = latestOppfolgingstilfelle
                        )
                        COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_UPDATED.increment()
                    } else {
                        log.info("not isGradertAtTilfelleEnd: Creating")
                        aktivitetskravService.createAktivitetskrav(
                            connection = connection,
                            aktivitetskrav = latestOppfolgingstilfelle.toAktivitetskrav(),
                        )
                        COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_CREATED.increment()
                    }
                }

                else -> {
                    // Hvis latest aktivitetskrav for tilfelle har annen status (UNNTAK/OPPFYLT/AVVENTER): Avhengig av status og tilfellet kan det måtte gjøres ny vurdering senere? Avventer foreløpig.
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
