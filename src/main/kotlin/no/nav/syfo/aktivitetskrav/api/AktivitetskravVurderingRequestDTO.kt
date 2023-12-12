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
    arsaker = arsaker.map { VurderingArsak.fromString(it.toString(), this.status) },
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
    TILTAK;
}
