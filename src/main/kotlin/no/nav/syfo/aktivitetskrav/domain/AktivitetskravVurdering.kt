package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingResponseDTO
import no.nav.syfo.aktivitetskrav.api.Arsak
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

        override fun toString(): String =
            when (this) {
                OppfolgingsplanArbeidsgiver -> "OPPFOLGINGSPLAN_ARBEIDSGIVER"
                InformasjonBehandler -> "INFORMASJON_BEHANDLER"
                DroftesMedROL -> "DROFTES_MED_ROL"
                DroftesInternt -> "DROFTES_INTERNT"
                Annet -> "ANNET"
            }

        companion object {
            fun fromString(arsak: String): Avvent =
                when (arsak) {
                    "OPPFOLGINGSPLAN_ARBEIDSGIVER" -> OppfolgingsplanArbeidsgiver
                    "INFORMASJON_BEHANDLER" -> InformasjonBehandler
                    "DROFTES_MED_ROL" -> DroftesMedROL
                    "DROFTES_INTERNT" -> DroftesInternt
                    "ANNET" -> Annet
                    else -> throw IllegalArgumentException("arsak: $arsak not supported for status")
                }
        }
    }

    sealed class Unntak : VurderingArsak() {
        object MedisinskeGrunner : Unntak()
        object TilretteleggingIkkeMulig : Unntak()
        object SjomennUtenriks : Unntak()

        override fun toString(): String =
            when (this) {
                MedisinskeGrunner -> "MEDISINSKE_GRUNNER"
                TilretteleggingIkkeMulig -> "TILRETTELEGGING_IKKE_MULIG"
                SjomennUtenriks -> "SJOMENN_UTENRIKS"
            }

        companion object {
            fun fromString(arsak: String): Unntak =
                when (arsak) {
                    "MEDISINSKE_GRUNNER" -> MedisinskeGrunner
                    "TILRETTELEGGING_IKKE_MULIG" -> TilretteleggingIkkeMulig
                    "SJOMENN_UTENRIKS" -> SjomennUtenriks
                    else -> throw IllegalArgumentException("arsak: $arsak not supported for status")
                }
        }
    }

    sealed class Oppfylt : VurderingArsak() {
        object Friskmeldt : Oppfylt()
        object Gradert : Oppfylt()
        object Tiltak : Oppfylt()

        override fun toString(): String =
            when (this) {
                Tiltak -> "TILTAK"
                Gradert -> "GRADERT"
                Friskmeldt -> "FRISKMELDT"
            }

        companion object {
            fun fromString(arsak: String): Oppfylt =
                when (arsak) {
                    "TILTAK" -> Tiltak
                    "GRADERT" -> Gradert
                    "FRISKMELDT" -> Friskmeldt
                    else -> throw IllegalArgumentException("arsak: $arsak not supported for status")
                }
        }
    }

    companion object {
        fun fromString(arsak: String, status: AktivitetskravStatus): VurderingArsak =
            when (status) {
                AktivitetskravStatus.AVVENT -> Avvent.fromString(arsak)
                AktivitetskravStatus.UNNTAK -> Unntak.fromString(arsak)
                AktivitetskravStatus.OPPFYLT -> Oppfylt.fromString(arsak)
                else -> throw IllegalArgumentException("arsak not supported for status")
            }
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
                arsaker = pAktivitetskravVurdering.arsaker.map { VurderingArsak.fromString(it, status) },
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

fun AktivitetskravVurdering.arsakerToString() = this.arsaker.joinToString(",")

fun AktivitetskravVurdering.isFinal() = this.status.isFinal

fun AktivitetskravVurdering.toVurderingResponseDto(varsel: AktivitetskravVarsel?): AktivitetskravVurderingResponseDTO =
    AktivitetskravVurderingResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        createdBy = this.createdBy,
        status = this.status,
        beskrivelse = this.beskrivelse,
        arsaker = this.arsaker.map { Arsak.valueOf(it.toString()) },
        frist = this.frist,
        varsel = varsel?.toVarselResponseDTO()
    )
