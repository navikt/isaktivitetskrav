package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVurdering
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PAktivitetskravVurdering(
    val id: Int,
    val uuid: UUID,
    val aktivitetskravId: Int,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
    val arsaker: List<PArsak>,
    val stansFom: LocalDate?,
    val frist: LocalDate?,
    val varsel: PAktivitetskravVarsel?,
) {
    fun toAktivitetskravVurdering(): AktivitetskravVurdering =
        when (status) {
            AktivitetskravStatus.AVVENT -> AktivitetskravVurdering.Avvent(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                arsaker = this.arsaker.map { it.toAvventArsak() },
                beskrivelse = this.beskrivelse!!,
                frist = this.frist,
            )
            AktivitetskravStatus.UNNTAK -> AktivitetskravVurdering.Unntak(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                arsaker = this.arsaker.map { it.toUnntakArsak() },
                beskrivelse = this.beskrivelse,
            )
            AktivitetskravStatus.OPPFYLT -> AktivitetskravVurdering.Oppfylt(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                arsaker = this.arsaker.map { it.toOppfyltArsak() },
                beskrivelse = this.beskrivelse,
            )
            AktivitetskravStatus.AUTOMATISK_OPPFYLT -> AktivitetskravVurdering.IkkeOppfylt(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                beskrivelse = this.beskrivelse,
            )
            AktivitetskravStatus.IKKE_OPPFYLT -> AktivitetskravVurdering.IkkeOppfylt(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                beskrivelse = this.beskrivelse,
            )
            AktivitetskravStatus.IKKE_AKTUELL -> AktivitetskravVurdering.IkkeAktuell(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                arsaker = this.arsaker.map { it.toIkkeAktuellArsak() },
                beskrivelse = this.beskrivelse,
            )
            AktivitetskravStatus.INNSTILLING_OM_STANS -> AktivitetskravVurdering.InnstillingOmStans(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                beskrivelse = this.beskrivelse!!,
                stansFom = this.stansFom!!,
                varsel = this.varsel?.toAktivitetkravVarsel(),
            )
            AktivitetskravStatus.FORHANDSVARSEL -> AktivitetskravVurdering.Forhandsvarsel(
                uuid = this.uuid,
                createdAt = this.createdAt,
                createdBy = this.createdBy,
                beskrivelse = this.beskrivelse!!,
                frist = this.frist,
                varsel = this.varsel?.toAktivitetkravVarsel(),
            )
            else -> throw IllegalArgumentException("Ugyldig AktivitetskravStatus: $status som AktivitetskravVurdering")
        }
}

enum class PArsak {
    OPPFOLGINGSPLAN_ARBEIDSGIVER,
    INFORMASJON_BEHANDLER,
    INFORMASJON_SYKMELDT,
    DROFTES_MED_ROL,
    DROFTES_INTERNT,
    MEDISINSKE_GRUNNER,
    TILRETTELEGGING_IKKE_MULIG,
    SJOMENN_UTENRIKS,
    FRISKMELDT,
    GRADERT,
    TILTAK,
    INNVILGET_VTA,
    MOTTAR_AAP,
    ER_DOD,
    ANNET;

    fun toAvventArsak(): AktivitetskravVurdering.Avvent.Arsak =
        when (this) {
            OPPFOLGINGSPLAN_ARBEIDSGIVER -> AktivitetskravVurdering.Avvent.Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER
            INFORMASJON_BEHANDLER -> AktivitetskravVurdering.Avvent.Arsak.INFORMASJON_BEHANDLER
            INFORMASJON_SYKMELDT -> AktivitetskravVurdering.Avvent.Arsak.INFORMASJON_SYKMELDT
            DROFTES_MED_ROL -> AktivitetskravVurdering.Avvent.Arsak.DROFTES_MED_ROL
            DROFTES_INTERNT -> AktivitetskravVurdering.Avvent.Arsak.DROFTES_INTERNT
            ANNET -> AktivitetskravVurdering.Avvent.Arsak.ANNET
            else -> throw IllegalArgumentException("Ugyldig PArsak for Avvent: $this")
        }

    fun toUnntakArsak(): AktivitetskravVurdering.Unntak.Arsak =
        when (this) {
            MEDISINSKE_GRUNNER -> AktivitetskravVurdering.Unntak.Arsak.MEDISINSKE_GRUNNER
            TILRETTELEGGING_IKKE_MULIG -> AktivitetskravVurdering.Unntak.Arsak.TILRETTELEGGING_IKKE_MULIG
            SJOMENN_UTENRIKS -> AktivitetskravVurdering.Unntak.Arsak.SJOMENN_UTENRIKS
            else -> throw IllegalArgumentException("Ugyldig PArsak for Unntak: $this")
        }

    fun toOppfyltArsak(): AktivitetskravVurdering.Oppfylt.Arsak =
        when (this) {
            FRISKMELDT -> AktivitetskravVurdering.Oppfylt.Arsak.FRISKMELDT
            GRADERT -> AktivitetskravVurdering.Oppfylt.Arsak.GRADERT
            TILTAK -> AktivitetskravVurdering.Oppfylt.Arsak.TILTAK
            else -> throw IllegalArgumentException("Ugyldig PArsak for Oppfylt: $this")
        }

    fun toIkkeAktuellArsak(): AktivitetskravVurdering.IkkeAktuell.Arsak =
        when (this) {
            INNVILGET_VTA -> AktivitetskravVurdering.IkkeAktuell.Arsak.INNVILGET_VTA
            MOTTAR_AAP -> AktivitetskravVurdering.IkkeAktuell.Arsak.MOTTAR_AAP
            ER_DOD -> AktivitetskravVurdering.IkkeAktuell.Arsak.ER_DOD
            ANNET -> AktivitetskravVurdering.IkkeAktuell.Arsak.ANNET
            else -> throw IllegalArgumentException("Ugyldig PArsak for IkkeAktuell: $this")
        }
}
