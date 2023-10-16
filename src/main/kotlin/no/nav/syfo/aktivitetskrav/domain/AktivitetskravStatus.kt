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

fun AktivitetskravStatus.isAllowedChangedVurderingStatus() = this in allowedChangedVurderingStatus

private val allowedChangedVurderingStatus = EnumSet.of(
    AktivitetskravStatus.AVVENT,
    AktivitetskravStatus.UNNTAK,
    AktivitetskravStatus.OPPFYLT,
    AktivitetskravStatus.IKKE_OPPFYLT,
    AktivitetskravStatus.IKKE_AKTUELL,
    AktivitetskravStatus.FORHANDSVARSEL,
)

fun AktivitetskravStatus.isAllowedStatusBeforeForhandsvarsel() =
    this in EnumSet.of(AktivitetskravStatus.NY, AktivitetskravStatus.AVVENT)

fun AktivitetskravStatus.requiresVurderingArsak(): Boolean =
    this == AktivitetskravStatus.AVVENT || this == AktivitetskravStatus.UNNTAK || this == AktivitetskravStatus.OPPFYLT

fun AktivitetskravStatus.isFinal() = this in finalStatuses

private val finalStatuses = EnumSet.of(
    AktivitetskravStatus.UNNTAK,
    AktivitetskravStatus.OPPFYLT,
    AktivitetskravStatus.IKKE_OPPFYLT,
    AktivitetskravStatus.IKKE_AKTUELL,
    AktivitetskravStatus.AUTOMATISK_OPPFYLT,
)
