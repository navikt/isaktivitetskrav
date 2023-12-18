package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.client.dokarkiv.domain.BrevkodeType

enum class VarselType {
    FORHANDSVARSEL_STANS_AV_SYKEPENGER,
    UNNTAK,
}

fun VarselType.getDokumentTittel(): String = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> "Forhåndsvarsel om stans av sykepenger"
    VarselType.UNNTAK -> "Vurdering av aktivitetskravet"
}

fun VarselType.getBrevkode(): BrevkodeType = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL
    VarselType.UNNTAK -> BrevkodeType.AKTIVITETSKRAV_VURDERING
}
