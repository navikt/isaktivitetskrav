package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostType

enum class VarselType {
    FORHANDSVARSEL_STANS_AV_SYKEPENGER,
    UNNTAK,
    OPPFYLT,
    IKKE_AKTUELL,
}

fun VarselType.getDokumentTittel(): String = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> "Forhåndsvarsel om stans av sykepenger"
    VarselType.UNNTAK, VarselType.OPPFYLT, VarselType.IKKE_AKTUELL -> "Vurdering av aktivitetskravet"
}

fun VarselType.getBrevkode(): BrevkodeType = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL
    VarselType.UNNTAK, VarselType.OPPFYLT, VarselType.IKKE_AKTUELL -> BrevkodeType.AKTIVITETSKRAV_VURDERING
}

fun VarselType.getJournalpostType(): JournalpostType = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> JournalpostType.UTGAAENDE
    VarselType.UNNTAK, VarselType.OPPFYLT, VarselType.IKKE_AKTUELL -> JournalpostType.NOTAT
}
