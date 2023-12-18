package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.*
import java.time.LocalDate

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
    val arsaker: List<Arsak>,
    val frist: LocalDate? = null,
)

fun AktivitetskravVurderingRequestDTO.toAktivitetskravVurdering(
    createdByIdent: String,
) = AktivitetskravVurdering.create(
    status = this.status,
    createdBy = createdByIdent,
    beskrivelse = this.beskrivelse,
    arsaker = arsaker.map { it.toVurderingArsak(this.status) },
    frist = this.frist,
)

enum class Arsak {
    OPPFOLGINGSPLAN_ARBEIDSGIVER,
    INFORMASJON_BEHANDLER,
    DROFTES_MED_ROL,
    DROFTES_INTERNT,
    ANNET,
    MEDISINSKE_GRUNNER,
    TILRETTELEGGING_IKKE_MULIG,
    SJOMENN_UTENRIKS,
    FRISKMELDT,
    GRADERT,
    TILTAK,
    INNVILGET_VTA,
    MOTTAR_AAP,
    ER_DOD;
}

fun Arsak.toVurderingArsak(status: AktivitetskravStatus): VurderingArsak =
    when (status) {
        AktivitetskravStatus.AVVENT -> {
            when (this) {
                Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER -> VurderingArsak.Avvent.OppfolgingsplanArbeidsgiver
                Arsak.INFORMASJON_BEHANDLER -> VurderingArsak.Avvent.InformasjonBehandler
                Arsak.DROFTES_MED_ROL -> VurderingArsak.Avvent.DroftesMedROL
                Arsak.DROFTES_INTERNT -> VurderingArsak.Avvent.DroftesInternt
                Arsak.ANNET -> VurderingArsak.Avvent.Annet
                else -> throw IllegalArgumentException("arsak: $this not supported for status: $status")
            }
        }

        AktivitetskravStatus.UNNTAK -> {
            when (this) {
                Arsak.MEDISINSKE_GRUNNER -> VurderingArsak.Unntak.MedisinskeGrunner
                Arsak.TILRETTELEGGING_IKKE_MULIG -> VurderingArsak.Unntak.TilretteleggingIkkeMulig
                Arsak.SJOMENN_UTENRIKS -> VurderingArsak.Unntak.SjomennUtenriks
                else -> throw IllegalArgumentException("arsak: $this not supported for status: $status")
            }
        }

        AktivitetskravStatus.OPPFYLT -> {
            when (this) {
                Arsak.TILTAK -> VurderingArsak.Oppfylt.Tiltak
                Arsak.GRADERT -> VurderingArsak.Oppfylt.Gradert
                Arsak.FRISKMELDT -> VurderingArsak.Oppfylt.Friskmeldt
                else -> throw IllegalArgumentException("arsak: $this not supported for status: $status")
            }
        }

        AktivitetskravStatus.IKKE_AKTUELL -> {
            when (this) {
                Arsak.INNVILGET_VTA -> VurderingArsak.IkkeAktuell.InnvilgetVTA
                Arsak.MOTTAR_AAP -> VurderingArsak.IkkeAktuell.MottarAAP
                Arsak.ER_DOD -> VurderingArsak.IkkeAktuell.ErDod
                Arsak.ANNET -> VurderingArsak.IkkeAktuell.Annet
                else -> throw IllegalArgumentException("arsak: $this not supported for status: $status")
            }
        }

        else -> throw IllegalArgumentException("arsak: $this not supported for status: $status")
    }
