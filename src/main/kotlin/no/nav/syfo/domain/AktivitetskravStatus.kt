package no.nav.syfo.domain

enum class AktivitetskravStatus(val isFinal: Boolean) {
    NY(false),
    NY_VURDERING(false),
    AVVENT(false),
    UNNTAK(true),
    OPPFYLT(true),
    AUTOMATISK_OPPFYLT(true),
    FORHANDSVARSEL(false),
    INNSTILLING_OM_STANS(true),
    IKKE_OPPFYLT(true),
    IKKE_AKTUELL(true),
    LUKKET(true),
}
