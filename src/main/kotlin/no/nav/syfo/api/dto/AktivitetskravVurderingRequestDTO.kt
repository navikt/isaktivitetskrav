package no.nav.syfo.api.dto

import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVurdering
import java.time.LocalDate

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
    val arsaker: List<Arsak> = emptyList(),
    val frist: LocalDate? = null,
    val stansFom: LocalDate? = null,
    val document: List<DocumentComponentDTO>? = emptyList(),
) {
    fun toAktivitetskravVurdering(
        createdByIdent: String,
    ): AktivitetskravVurdering =
        AktivitetskravVurdering.create(
            status = this.status,
            createdBy = createdByIdent,
            beskrivelse = this.beskrivelse,
            arsaker = arsaker,
            stansFom = this.stansFom,
            frist = this.frist,
        )
}

enum class Arsak {
    OPPFOLGINGSPLAN_ARBEIDSGIVER,
    INFORMASJON_BEHANDLER,
    INFORMASJON_SYKMELDT,
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
