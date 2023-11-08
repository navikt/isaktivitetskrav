package no.nav.syfo.aktivitetskrav.domain

import java.util.EnumSet

enum class AktivitetskravStatus {
    NY,
    AVVENT,
    UNNTAK,
    OPPFYLT,
    AUTOMATISK_OPPFYLT,
    FORHANDSVARSEL,
    STANS,
    IKKE_OPPFYLT,
    IKKE_AKTUELL,
    LUKKET,
}

fun AktivitetskravStatus.isFinal() = this in finalStatuses

private val finalStatuses = EnumSet.of(
    AktivitetskravStatus.LUKKET,
    AktivitetskravStatus.UNNTAK,
    AktivitetskravStatus.OPPFYLT,
    AktivitetskravStatus.IKKE_OPPFYLT,
    AktivitetskravStatus.IKKE_AKTUELL,
    AktivitetskravStatus.AUTOMATISK_OPPFYLT,
)
