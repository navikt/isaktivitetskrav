package no.nav.syfo.infrastructure.kafka.domain

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class KafkaAktivitetskravVurdering(
    val uuid: UUID,
    val personIdent: String,
    val createdAt: OffsetDateTime,
    val status: String,
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
            previousAktivitetskravUuid: UUID? = null
        ): KafkaAktivitetskravVurdering {
            val latestVurdering = aktivitetskrav.vurderinger.firstOrNull()
            return KafkaAktivitetskravVurdering(
                uuid = aktivitetskrav.uuid,
                personIdent = aktivitetskrav.personIdent.value,
                createdAt = aktivitetskrav.createdAt,
                status = aktivitetskrav.status.name,
                beskrivelse = latestVurdering?.beskrivelse,
                stoppunktAt = aktivitetskrav.stoppunktAt,
                updatedBy = latestVurdering?.createdBy,
                arsaker = latestVurdering?.arsaker?.map { it.value } ?: emptyList(),
                sisteVurderingUuid = latestVurdering?.uuid,
                sistVurdert = latestVurdering?.createdAt,
                frist = latestVurdering?.frist,
                previousAktivitetskravUuid = previousAktivitetskravUuid,
            )
        }
    }
}
