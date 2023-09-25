package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.domain.PersonIdent
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class AktivitetskravVarselProducer(private val kafkaProducer: KafkaProducer<String, KafkaAktivitetskravVarsel>) {

    fun sendAktivitetskravVarsel(
        personIdent: PersonIdent,
        aktivitetskravUuid: UUID,
        varsel: AktivitetskravVarsel,
    ) {
        val key = UUID.nameUUIDFromBytes(personIdent.value.toByteArray()).toString()
        val kafkaAktivitetskravVarsel = KafkaAktivitetskravVarsel(
            personIdent = personIdent.value,
            aktivitetskravUuid = aktivitetskravUuid,
            varselUuid = varsel.uuid,
            createdAt = varsel.createdAt,
            journalpostId = varsel.journalpostId!!,
            document = varsel.document
        )
        try {
            kafkaProducer.send(
                ProducerRecord(
                    AKTIVITETSKRAV_VARSEL_TOPIC,
                    key,
                    kafkaAktivitetskravVarsel,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaAktivitetskravVarsel with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        const val AKTIVITETSKRAV_VARSEL_TOPIC = "teamsykefravr.aktivitetskrav-varsel"
        private val log = LoggerFactory.getLogger(AktivitetskravVarselProducer::class.java)
    }
}
