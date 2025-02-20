package no.nav.syfo.domain

import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostType

enum class VarselType {
    FORHANDSVARSEL_STANS_AV_SYKEPENGER,
    UNNTAK,
    OPPFYLT,
    IKKE_AKTUELL,
    INNSTILLING_OM_STANS,
}

fun VarselType.getDokumentTittel(): String = when (this) {
    VarselType.UNNTAK -> "Vurdering av aktivitetskravet - Unntak"
    VarselType.IKKE_AKTUELL -> "Vurdering av aktivitetskravet - Ikke aktuell"
    VarselType.OPPFYLT -> "Vurdering av aktivitetskravet - Oppfylt"
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> "Vurdering av aktivitetskravet - Forhåndsvarsel om stans"
    VarselType.INNSTILLING_OM_STANS -> "Vurdering av aktivitetskravet - Innstilling om stans"
}

fun VarselType.getBrevkode(): BrevkodeType = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL
    VarselType.UNNTAK, VarselType.OPPFYLT, VarselType.IKKE_AKTUELL -> BrevkodeType.AKTIVITETSKRAV_VURDERING
    VarselType.INNSTILLING_OM_STANS -> BrevkodeType.AKTIVITETSKRAV_STANS
}

fun VarselType.getJournalpostType(): JournalpostType = when (this) {
    VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> JournalpostType.UTGAAENDE
    VarselType.UNNTAK, VarselType.OPPFYLT, VarselType.IKKE_AKTUELL, VarselType.INNSTILLING_OM_STANS -> JournalpostType.NOTAT
}
