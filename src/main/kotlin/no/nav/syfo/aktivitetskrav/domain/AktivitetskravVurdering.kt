package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingResponseDTO
import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

enum class VurderingArsak(val validForStatus: AktivitetskravStatus) {
    OPPFOLGINGSPLAN_ARBEIDSGIVER(AktivitetskravStatus.AVVENT),
    INFORMASJON_BEHANDLER(AktivitetskravStatus.AVVENT),
    ANNET(AktivitetskravStatus.AVVENT),
    MEDISINSKE_GRUNNER(AktivitetskravStatus.UNNTAK),
    TILRETTELEGGING_IKKE_MULIG(AktivitetskravStatus.UNNTAK),
    SJOMENN_UTENRIKS(AktivitetskravStatus.UNNTAK),
    FRISKMELDT(AktivitetskravStatus.OPPFYLT),
    GRADERT(AktivitetskravStatus.OPPFYLT),
    TILTAK(AktivitetskravStatus.OPPFYLT);
}

data class AktivitetskravVurdering private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val status: AktivitetskravStatus,
    val arsaker: List<VurderingArsak>,
    val beskrivelse: String?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering) = AktivitetskravVurdering(
            uuid = pAktivitetskravVurdering.uuid,
            createdAt = pAktivitetskravVurdering.createdAt,
            createdBy = pAktivitetskravVurdering.createdBy,
            status = AktivitetskravStatus.valueOf(pAktivitetskravVurdering.status),
            arsaker = pAktivitetskravVurdering.arsaker.map { VurderingArsak.valueOf(it) },
            beskrivelse = pAktivitetskravVurdering.beskrivelse,
        )

        fun create(
            status: AktivitetskravStatus,
            createdBy: String,
            beskrivelse: String?,
            arsaker: List<VurderingArsak>,
        ): AktivitetskravVurdering {
            if (arsaker.isEmpty() || arsaker.any { it.validForStatus != status }) {
                throw IllegalArgumentException("Must have valid arsak for status $status")
            }

            return AktivitetskravVurdering(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                createdBy = createdBy,
                status = status,
                beskrivelse = beskrivelse,
                arsaker = arsaker,
            )
        }
    }
}

fun AktivitetskravVurdering.arsakerToString() = this.arsaker.joinToString(",")

fun List<AktivitetskravVurdering>.toVurderingResponseDTOs(): List<AktivitetskravVurderingResponseDTO> =
    this.map { it.toVurderingResponseDto() }

fun AktivitetskravVurdering.toVurderingResponseDto(): AktivitetskravVurderingResponseDTO =
    AktivitetskravVurderingResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        createdBy = this.createdBy,
        status = this.status,
        beskrivelse = this.beskrivelse,
        arsaker = this.arsaker
    )
