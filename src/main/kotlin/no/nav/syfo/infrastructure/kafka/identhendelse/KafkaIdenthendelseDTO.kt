package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.domain.PersonIdent

// Basert på https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/avro/no/nav/person/pdl/aktor/AktorV2.avdl

data class KafkaIdenthendelseDTO(
    val identifikatorer: List<Identifikator>,
) {
    val folkeregisterIdenter = identifikatorer.filter { it.type == IdentType.FOLKEREGISTERIDENT }

    fun getActivePersonident(): PersonIdent? = folkeregisterIdenter
        .find { it.gjeldende }
        ?.let { PersonIdent(it.idnummer) }

    fun getInactivePersonidenter(): List<PersonIdent> = folkeregisterIdenter
        .filter { !it.gjeldende }
        .map { PersonIdent(it.idnummer) }
}

data class Identifikator(
    val idnummer: String,
    val type: IdentType,
    val gjeldende: Boolean,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}
