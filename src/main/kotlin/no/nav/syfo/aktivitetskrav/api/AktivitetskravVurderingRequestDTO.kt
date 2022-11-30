package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.util.nowUTC
import java.util.*

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
)

fun AktivitetskravVurderingRequestDTO.toAktivitetskravVurdering(
    createdByIdent: String,
) = AktivitetskravVurdering(
    uuid = UUID.randomUUID(),
    createdAt = nowUTC(),
    createdBy = createdByIdent,
    status = this.status,
    beskrivelse = this.beskrivelse,
)
