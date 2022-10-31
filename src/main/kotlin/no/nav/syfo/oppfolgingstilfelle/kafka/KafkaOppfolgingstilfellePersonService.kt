package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.oppfolgingstilfelle.passererAktivitetskravVurderingStoppunkt
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration

class KafkaOppfolgingstilfellePersonService(val database: DatabaseInterface) :
    KafkaConsumerService<KafkaOppfolgingstilfellePerson> {

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

        if (latestOppfolgingstilfelle.passererAktivitetskravVurderingStoppunkt()) {
            /*
            Sjekk grad av aktivitet:
            - Hvis INGEN_AKTIVITET: Aktivitetskrav skal vurderes -> lagre ny vurdering og publisere på kafka (produsere til topic i en consumer?)
            - Hvis GRADERT_AKTIVITET: Aktivitetskrav skal ikke vurderes -> skippe helt eller lagre ny vurdering som oppfylt (eller annen status) og droppe kafka-publisering?
            - Hva med FULL_AKTIVITET eller UKJENT_AKTIVITET (mulige tags)
             */
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_AKTIVITETSKRAV_VURDERING_CREATED.increment()
        } else {
            COUNT_KAFKA_CONSUMER_OPPFOLGINGSTILFELLE_PERSON_SKIPPED_NOT_AKTIVITETSKRAV_VURDERING.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaOppfolgingstilfellePersonService::class.java)
    }
}
