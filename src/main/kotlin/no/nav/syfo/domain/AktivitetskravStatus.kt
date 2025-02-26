package no.nav.syfo.domain

import java.util.*

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

fun AktivitetskravStatus.isAllowedChangedVurderingStatus() = this in allowedChangedVurderingStatus

private val allowedChangedVurderingStatus = EnumSet.of(
    AktivitetskravStatus.AVVENT,
    AktivitetskravStatus.UNNTAK,
    AktivitetskravStatus.OPPFYLT,
    AktivitetskravStatus.IKKE_OPPFYLT,
    AktivitetskravStatus.IKKE_AKTUELL,
    AktivitetskravStatus.FORHANDSVARSEL,
    AktivitetskravStatus.INNSTILLING_OM_STANS
)

fun AktivitetskravStatus.requiresVurderingArsak(): Boolean =
    this == AktivitetskravStatus.AVVENT || this == AktivitetskravStatus.UNNTAK || this == AktivitetskravStatus.OPPFYLT || this == AktivitetskravStatus.IKKE_AKTUELL

fun AktivitetskravStatus.toVarselType(): VarselType? = when (this) {
    AktivitetskravStatus.FORHANDSVARSEL -> VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER
    AktivitetskravStatus.UNNTAK -> VarselType.UNNTAK
    AktivitetskravStatus.OPPFYLT -> VarselType.OPPFYLT
    AktivitetskravStatus.IKKE_AKTUELL -> VarselType.IKKE_AKTUELL
    AktivitetskravStatus.INNSTILLING_OM_STANS -> VarselType.INNSTILLING_OM_STANS
    else -> null
}
