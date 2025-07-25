package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.kafka.identhendelse.IdentType
import no.nav.syfo.infrastructure.kafka.identhendelse.Identifikator
import no.nav.syfo.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO

fun generateKafkaIdenthendelseDTO(
    aktivIdent: PersonIdent?,
    inaktiveIdenter: List<PersonIdent>,
): KafkaIdenthendelseDTO {
    val inaktiveIdentifikatorer = inaktiveIdenter.map { inaktivIdent ->
        Identifikator(
            idnummer = inaktivIdent.value,
            type = IdentType.FOLKEREGISTERIDENT,
            gjeldende = false,
        )
    }
    val aktivIdentifikator = aktivIdent?.let {
        Identifikator(
            idnummer = it.value,
            type = IdentType.FOLKEREGISTERIDENT,
            gjeldende = true
        )
    }

    return KafkaIdenthendelseDTO(
        identifikatorer = if (aktivIdentifikator != null) inaktiveIdentifikatorer + aktivIdentifikator else inaktiveIdentifikatorer
    )
}
