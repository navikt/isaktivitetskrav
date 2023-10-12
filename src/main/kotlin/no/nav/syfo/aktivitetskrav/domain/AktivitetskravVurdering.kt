package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingResponseDTO
import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
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
    val frist: LocalDate?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering) = AktivitetskravVurdering(
            uuid = pAktivitetskravVurdering.uuid,
            createdAt = pAktivitetskravVurdering.createdAt,
            createdBy = pAktivitetskravVurdering.createdBy,
            status = AktivitetskravStatus.valueOf(pAktivitetskravVurdering.status),
            arsaker = pAktivitetskravVurdering.arsaker.map { VurderingArsak.valueOf(it) },
            beskrivelse = pAktivitetskravVurdering.beskrivelse,
            frist = pAktivitetskravVurdering.frist,
        )

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
    if (arsaker.any { it.validForStatus != status }) {
        throw IllegalArgumentException("Must have valid arsak for status $status")
    }
}

fun AktivitetskravVurdering.arsakerToString() = this.arsaker.joinToString(",")

fun AktivitetskravVurdering.isFinal() = this.status.isFinal()

fun AktivitetskravVurdering.toVurderingResponseDto(varsel: AktivitetskravVarsel?): AktivitetskravVurderingResponseDTO =
    AktivitetskravVurderingResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        createdBy = this.createdBy,
        status = this.status,
        beskrivelse = this.beskrivelse,
        arsaker = this.arsaker,
        frist = this.frist,
        varsel = varsel?.toVarselResponseDTO()
    )
