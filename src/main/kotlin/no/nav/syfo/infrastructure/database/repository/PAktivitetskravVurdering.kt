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

enum class PArsak(value: String) {
    OPPFOLGINGSPLAN_ARBEIDSGIVER("OPPFOLGINGSPLAN_ARBEIDSGIVER"),
    INFORMASJON_BEHANDLER("INFORMASJON_BEHANDLER"),
    INFORMASJON_SYKMELDT("INFORMASJON_SYKMELDT"),
    DROFTES_MED_ROL("DROFTES_MED_ROL"),
    DROFTES_INTERNT("DROFTES_INTERNT"),
    MEDISINSKE_GRUNNER("MEDISINSKE_GRUNNER"),
    TILRETTELEGGING_IKKE_MULIG("TILRETTELEGGING_IKKE_MULIG"),
    SJOMENN_UTENRIKS("SJOMENN_UTENRIKS"),
    FRISKMELDT("FRISKMELDT"),
    GRADERT("GRADERT"),
    TILTAK("TILTAK"),
    INNVILGET_VTA("INNVILGET_VTA"),
    MOTTAR_AAP("MOTTAR_AAP"),
    ER_DOD("ER_DOD"),
    ANNET("ANNET");

    fun toAvventArsak(): AktivitetskravVurdering.Avvent.Arsak =
        when (this) {
            OPPFOLGINGSPLAN_ARBEIDSGIVER -> AktivitetskravVurdering.Avvent.Arsak.OppfolgingsplanArbeidsgiver
            INFORMASJON_BEHANDLER -> AktivitetskravVurdering.Avvent.Arsak.InformasjonBehandler
            INFORMASJON_SYKMELDT -> AktivitetskravVurdering.Avvent.Arsak.InformasjonSykmeldt
            DROFTES_MED_ROL -> AktivitetskravVurdering.Avvent.Arsak.DroftesMedROL
            DROFTES_INTERNT -> AktivitetskravVurdering.Avvent.Arsak.DroftesInternt
            ANNET -> AktivitetskravVurdering.Avvent.Arsak.Annet
            else -> throw IllegalArgumentException("Ugyldig PArsak for Avvent: $this")
        }

    fun toUnntakArsak(): AktivitetskravVurdering.Unntak.Arsak =
        when (this) {
            MEDISINSKE_GRUNNER -> AktivitetskravVurdering.Unntak.Arsak.MedisinskeGrunner
            TILRETTELEGGING_IKKE_MULIG -> AktivitetskravVurdering.Unntak.Arsak.TilretteleggingIkkeMulig
            SJOMENN_UTENRIKS -> AktivitetskravVurdering.Unntak.Arsak.SjomennUtenriks
            else -> throw IllegalArgumentException("Ugyldig PArsak for Unntak: $this")
        }

    fun toOppfyltArsak(): AktivitetskravVurdering.Oppfylt.Arsak =
        when (this) {
            FRISKMELDT -> AktivitetskravVurdering.Oppfylt.Arsak.Friskmeldt
            GRADERT -> AktivitetskravVurdering.Oppfylt.Arsak.Gradert
            TILTAK -> AktivitetskravVurdering.Oppfylt.Arsak.Tiltak
            else -> throw IllegalArgumentException("Ugyldig PArsak for Oppfylt: $this")
        }

    fun toIkkeAktuellArsak(): AktivitetskravVurdering.IkkeAktuell.Arsak =
        when (this) {
            INNVILGET_VTA -> AktivitetskravVurdering.IkkeAktuell.Arsak.InnvilgetVTA
            MOTTAR_AAP -> AktivitetskravVurdering.IkkeAktuell.Arsak.MottarAAP
            ER_DOD -> AktivitetskravVurdering.IkkeAktuell.Arsak.ErDod
            ANNET -> AktivitetskravVurdering.IkkeAktuell.Arsak.Annet
            else -> throw IllegalArgumentException("Ugyldig PArsak for IkkeAktuell: $this")
        }
}
