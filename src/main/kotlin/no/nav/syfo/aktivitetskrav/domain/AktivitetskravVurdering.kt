package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingResponseDTO
import no.nav.syfo.aktivitetskrav.api.Arsak
import no.nav.syfo.aktivitetskrav.api.toVurderingArsak
import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

sealed class VurderingArsak {

    sealed class Avvent : VurderingArsak() {
        object OppfolgingsplanArbeidsgiver : Avvent()
        object InformasjonBehandler : Avvent()
        object DroftesMedROL : Avvent()
        object DroftesInternt : Avvent()
        object Annet : Avvent()
    }

    sealed class Unntak : VurderingArsak() {
        object MedisinskeGrunner : Unntak()
        object TilretteleggingIkkeMulig : Unntak()
        object SjomennUtenriks : Unntak()
    }

    sealed class Oppfylt : VurderingArsak() {
        object Friskmeldt : Oppfylt()
        object Gradert : Oppfylt()
        object Tiltak : Oppfylt()
    }
}

data class AktivitetskravVurdering private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val status: AktivitetskravStatus,
    val arsaker: List<VurderingArsak>,
    val beskrivelse: String?,
    val frist: LocalDate?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering): AktivitetskravVurdering {
            val status = AktivitetskravStatus.valueOf(pAktivitetskravVurdering.status)
            return AktivitetskravVurdering(
                uuid = pAktivitetskravVurdering.uuid,
                createdAt = pAktivitetskravVurdering.createdAt,
                createdBy = pAktivitetskravVurdering.createdBy,
                status = status,
                arsaker = pAktivitetskravVurdering.arsaker.map { it.toVurderingArsak(status) },
                beskrivelse = pAktivitetskravVurdering.beskrivelse,
                frist = pAktivitetskravVurdering.frist,
            )
        }

        fun create(
            status: AktivitetskravStatus,
            createdBy: String,
            beskrivelse: String?,
            arsaker: List<VurderingArsak>,
            frist: LocalDate? = null,
        ): AktivitetskravVurdering {
            return AktivitetskravVurdering(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                createdBy = createdBy,
                status = status,
                beskrivelse = beskrivelse,
                arsaker = arsaker,
                frist = frist,
            ).also { it.validate() }
        }
    }
}

fun AktivitetskravVurdering.validate() {
    if (!status.isAllowedChangedVurderingStatus()) {
        throw IllegalArgumentException("Can't create vurdering with status $status")
    }
    if (!status.requiresVurderingArsak() && arsaker.isNotEmpty()) {
        throw IllegalArgumentException("$status should not have arsak")
    }
    if (status.requiresVurderingArsak() && arsaker.isEmpty()) {
        throw IllegalArgumentException("Must have arsak for status $status")
    }
}

fun AktivitetskravVurdering.isFinal() = this.status.isFinal

fun AktivitetskravVurdering.toVurderingResponseDto(varsel: AktivitetskravVarsel?): AktivitetskravVurderingResponseDTO =
    AktivitetskravVurderingResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        createdBy = this.createdBy,
        status = this.status,
        beskrivelse = this.beskrivelse,
        arsaker = this.arsaker.map { Arsak.valueOf(it.toDTOString()) },
        frist = this.frist,
        varsel = varsel?.toVarselResponseDTO()
    )

private fun VurderingArsak.toDTOString(): String =
    when (this) {
        VurderingArsak.Avvent.OppfolgingsplanArbeidsgiver -> "OPPFOLGINGSPLAN_ARBEIDSGIVER"
        VurderingArsak.Avvent.InformasjonBehandler -> "INFORMASJON_BEHANDLER"
        VurderingArsak.Avvent.DroftesMedROL -> "DROFTES_MED_ROL"
        VurderingArsak.Avvent.DroftesInternt -> "DROFTES_INTERNT"
        VurderingArsak.Avvent.Annet -> "ANNET"
        VurderingArsak.Unntak.MedisinskeGrunner -> "MEDISINSKE_GRUNNER"
        VurderingArsak.Unntak.TilretteleggingIkkeMulig -> "TILRETTELEGGING_IKKE_MULIG"
        VurderingArsak.Unntak.SjomennUtenriks -> "SJOMENN_UTENRIKS"
        VurderingArsak.Oppfylt.Friskmeldt -> "TILTAK"
        VurderingArsak.Oppfylt.Gradert -> "GRADERT"
        VurderingArsak.Oppfylt.Tiltak -> "FRISKMELDT"
    }