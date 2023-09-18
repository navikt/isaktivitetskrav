package no.nav.syfo.aktivitetskrav.kafka

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class ArbeidstakervarselProducer(private val kafkaArbeidstakervarselProducer: KafkaProducer<String, EsyfovarselHendelse>) {
    fun sendArbeidstakervarsel(varselHendelse: EsyfovarselHendelse) {
        try {
            kafkaArbeidstakervarselProducer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.randomUUID().toString(),
                    varselHendelse,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send hendelse esyfovarsel: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(ArbeidstakervarselProducer::class.java)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed interface EsyfovarselHendelse : Serializable {
    val type: HendelseType
    var data: Any?
}

data class ArbeidstakerHendelse(
    override val type: HendelseType,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?
) : EsyfovarselHendelse

data class VarselData(
    val journalpost: VarselDataJournalpost? = null,
)

data class VarselDataJournalpost(
    val uuid: String,
    val id: String?
)

enum class HendelseType {
    SM_FORHANDSVARSEL_STANS,
}
