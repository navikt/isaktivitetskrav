package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val AKTIVITETSKRAV_VURDERING_STOPPUNKT_WEEKS = 8L

enum class AktivitetskravVurderingStatus {
    NY,
    AVVENT,
    UNNTAK,
    OPPFYLT,
    AUTOMATISK_OPPFYLT,
    STANS
}

data class AktivitetskravVurdering private constructor(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: AktivitetskravVurderingStatus,
    val beskrivelse: String?,
    val stoppunktAt: LocalDate?,
    val updatedBy: String?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering) = AktivitetskravVurdering(
            uuid = pAktivitetskravVurdering.uuid,
            personIdent = pAktivitetskravVurdering.personIdent,
            createdAt = pAktivitetskravVurdering.createdAt,
            updatedAt = pAktivitetskravVurdering.updatedAt,
            status = AktivitetskravVurderingStatus.valueOf(pAktivitetskravVurdering.status),
            beskrivelse = pAktivitetskravVurdering.beskrivelse,
            stoppunktAt = pAktivitetskravVurdering.stoppunktAt,
            updatedBy = pAktivitetskravVurdering.updatedBy,
        )

        fun ny(personIdent: PersonIdent, tilfelleStart: LocalDate): AktivitetskravVurdering =
            create(
                personIdent = personIdent,
                status = AktivitetskravVurderingStatus.NY,
                stoppunktAt = tilfelleStart.plusWeeks(AKTIVITETSKRAV_VURDERING_STOPPUNKT_WEEKS),
            )

        fun automatiskOppfyltGradert(
            personIdent: PersonIdent,
        ): AktivitetskravVurdering = create(
            personIdent = personIdent,
            status = AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT,
            beskrivelse = "Gradert aktivitet",
        )

        private fun create(
            personIdent: PersonIdent,
            status: AktivitetskravVurderingStatus,
            stoppunktAt: LocalDate? = null,
            beskrivelse: String? = null,
        ) = AktivitetskravVurdering(
            uuid = UUID.randomUUID(),
            personIdent = personIdent,
            createdAt = nowUTC(),
            updatedAt = nowUTC(),
            status = status,
            beskrivelse = beskrivelse,
            stoppunktAt = stoppunktAt,
            updatedBy = null,
        )
    }
}

fun AktivitetskravVurdering.toKafkaAktivitetskravVurdering() = KafkaAktivitetskravVurdering(
    uuid = this.uuid.toString(),
    personIdent = this.personIdent.value,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    status = this.status.name,
    beskrivelse = this.beskrivelse,
    stoppunktAt = this.stoppunktAt,
    updatedBy = this.updatedBy,
)
