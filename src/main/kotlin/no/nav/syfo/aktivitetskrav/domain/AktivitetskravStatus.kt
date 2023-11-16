package no.nav.syfo.aktivitetskrav.domain

import java.util.*

enum class AktivitetskravStatus(val isFinal: Boolean) {
    NY(false),
    NY_VURDERING(false),
    AVVENT(false),
    UNNTAK(true),
    OPPFYLT(true),
    AUTOMATISK_OPPFYLT(true),
    FORHANDSVARSEL(false),
    STANS(true),
    IKKE_OPPFYLT(true),
    IKKE_AKTUELL(true),
    LUKKET(true),
}

fun AktivitetskravStatus.isAllowedChangedVurderingStatus() = this in allowedChangedVurderingStatus

private val allowedChangedVurderingStatus = EnumSet.of(
    AktivitetskravStatus.AVVENT,
    AktivitetskravStatus.UNNTAK,
    AktivitetskravStatus.OPPFYLT,
    AktivitetskravStatus.IKKE_OPPFYLT,
    AktivitetskravStatus.IKKE_AKTUELL,
    AktivitetskravStatus.FORHANDSVARSEL,
)

fun AktivitetskravStatus.isAllowedStatusBeforeForhandsvarsel() = !this.isFinal

fun AktivitetskravStatus.requiresVurderingArsak(): Boolean =
    this == AktivitetskravStatus.AVVENT || this == AktivitetskravStatus.UNNTAK || this == AktivitetskravStatus.OPPFYLT
