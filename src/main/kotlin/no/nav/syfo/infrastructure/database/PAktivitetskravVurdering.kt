package no.nav.syfo.infrastructure.database

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.VurderingArsak
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
    val arsaker: List<VurderingArsak>,
    val frist: LocalDate?,
    val varsel: PAktivitetskravVarsel?,
) {
    fun toAktivitetskravVurdering() =
        AktivitetskravVurdering(
            uuid = this.uuid,
            createdAt = this.createdAt,
            createdBy = this.createdBy,
            status = this.status,
            arsaker = this.arsaker,
            beskrivelse = this.beskrivelse,
            frist = this.frist,
            varsel = varsel?.toAktivitetkravVarsel(),
        )
}

fun String.toVurderingArsak(status: AktivitetskravStatus): VurderingArsak =
    when (status) {
        AktivitetskravStatus.AVVENT ->
            when (this) {
                "OPPFOLGINGSPLAN_ARBEIDSGIVER" -> VurderingArsak.Avvent.OppfolgingsplanArbeidsgiver
                "INFORMASJON_BEHANDLER" -> VurderingArsak.Avvent.InformasjonBehandler
                "INFORMASJON_SYKMELDT" -> VurderingArsak.Avvent.InformasjonSykmeldt
                "DROFTES_MED_ROL" -> VurderingArsak.Avvent.DroftesMedROL
                "DROFTES_INTERNT" -> VurderingArsak.Avvent.DroftesInternt
                "ANNET" -> VurderingArsak.Avvent.Annet
                else -> throw IllegalArgumentException("String was $this and status was $status")
            }

        AktivitetskravStatus.UNNTAK ->
            when (this) {
                "MEDISINSKE_GRUNNER" -> VurderingArsak.Unntak.MedisinskeGrunner
                "TILRETTELEGGING_IKKE_MULIG" -> VurderingArsak.Unntak.TilretteleggingIkkeMulig
                "SJOMENN_UTENRIKS" -> VurderingArsak.Unntak.SjomennUtenriks
                else -> throw IllegalArgumentException("String was $this and status was $status")
            }

        AktivitetskravStatus.OPPFYLT ->
            when (this) {
                "FRISKMELDT" -> VurderingArsak.Oppfylt.Friskmeldt
                "GRADERT" -> VurderingArsak.Oppfylt.Gradert
                "TILTAK" -> VurderingArsak.Oppfylt.Tiltak
                else -> throw IllegalArgumentException("String was $this and status was $status")
            }

        AktivitetskravStatus.IKKE_AKTUELL ->
            when (this) {
                "INNVILGET_VTA" -> VurderingArsak.IkkeAktuell.InnvilgetVTA
                "MOTTAR_AAP" -> VurderingArsak.IkkeAktuell.MottarAAP
                "ER_DOD" -> VurderingArsak.IkkeAktuell.ErDod
                "ANNET" -> VurderingArsak.IkkeAktuell.Annet
                else -> throw IllegalArgumentException("String was $this and status was $status")
            }

        else -> throw IllegalArgumentException("String was $this and status was $status")
    }
