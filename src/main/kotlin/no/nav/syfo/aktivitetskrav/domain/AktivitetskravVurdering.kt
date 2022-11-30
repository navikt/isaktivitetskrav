package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingResponseDTO
import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVurdering(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering) = AktivitetskravVurdering(
            uuid = pAktivitetskravVurdering.uuid,
            createdAt = pAktivitetskravVurdering.createdAt,
            createdBy = pAktivitetskravVurdering.createdBy,
            status = AktivitetskravStatus.valueOf(pAktivitetskravVurdering.status),
            beskrivelse = pAktivitetskravVurdering.beskrivelse,
        )

        fun create(status: AktivitetskravStatus, createdBy: String, beskrivelse: String) = AktivitetskravVurdering(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            createdBy = createdBy,
            status = status,
            beskrivelse = beskrivelse
        )
    }
}

fun List<AktivitetskravVurdering>.toVurderingResponseDTOs(): List<AktivitetskravVurderingResponseDTO> =
    this.map { it.toVurderingResponseDto() }

fun AktivitetskravVurdering.toVurderingResponseDto(): AktivitetskravVurderingResponseDTO =
    AktivitetskravVurderingResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        createdBy = this.createdBy,
        status = this.status,
        beskrivelse = this.beskrivelse,
    )
