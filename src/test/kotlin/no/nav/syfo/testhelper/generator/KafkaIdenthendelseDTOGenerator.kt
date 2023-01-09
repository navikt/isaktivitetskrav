package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.identhendelse.kafka.*

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
