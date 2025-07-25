package no.nav.syfo.infrastructure.kafka.model

import no.nav.syfo.domain.Aktivitetskrav
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVurderingRecord(
    val uuid: UUID,
    val personIdent: String,
    val createdAt: OffsetDateTime,
    val status: String,
    val isFinal: Boolean,
    val beskrivelse: String?,
    val arsaker: List<String>,
    val stoppunktAt: LocalDate,
    val updatedBy: String?,
    val sisteVurderingUuid: UUID?,
    val sistVurdert: OffsetDateTime?,
    val frist: LocalDate?,
    val previousAktivitetskravUuid: UUID?,
) {
    companion object {
        fun from(
            aktivitetskrav: Aktivitetskrav,
            previousAktivitetskravUuid: UUID? = null,
        ): AktivitetskravVurderingRecord {
            val latestVurdering = aktivitetskrav.vurderinger.firstOrNull()
            return AktivitetskravVurderingRecord(
                uuid = aktivitetskrav.uuid,
                personIdent = aktivitetskrav.personIdent.value,
                createdAt = aktivitetskrav.createdAt,
                status = aktivitetskrav.status.name,
                isFinal = aktivitetskrav.status.isFinal,
                beskrivelse = latestVurdering?.beskrivelse,
                stoppunktAt = aktivitetskrav.stoppunktAt,
                updatedBy = latestVurdering?.createdBy,
                arsaker = latestVurdering?.arsaker() ?: emptyList(),
                sisteVurderingUuid = latestVurdering?.uuid,
                sistVurdert = latestVurdering?.createdAt,
                frist = latestVurdering?.frist(),
                previousAktivitetskravUuid = previousAktivitetskravUuid,
            )
        }
    }
}
