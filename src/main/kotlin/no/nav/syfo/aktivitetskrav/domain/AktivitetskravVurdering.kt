package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.domain.isGradertAtTilfelleEnd
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
    val stoppunktAt: LocalDate,
    val beskrivelse: String?,
    val updatedBy: String?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering) = AktivitetskravVurdering(
            uuid = pAktivitetskravVurdering.uuid,
            personIdent = pAktivitetskravVurdering.personIdent,
            createdAt = pAktivitetskravVurdering.createdAt,
            updatedAt = pAktivitetskravVurdering.updatedAt,
            status = AktivitetskravVurderingStatus.valueOf(pAktivitetskravVurdering.status),
            stoppunktAt = pAktivitetskravVurdering.stoppunktAt,
            beskrivelse = pAktivitetskravVurdering.beskrivelse,
            updatedBy = pAktivitetskravVurdering.updatedBy,
        )

        fun ny(personIdent: PersonIdent, tilfelleStart: LocalDate): AktivitetskravVurdering =
            create(
                personIdent = personIdent,
                status = AktivitetskravVurderingStatus.NY,
                tilfelleStart = tilfelleStart,
            )

        fun automatiskOppfyltGradert(
            personIdent: PersonIdent,
            tilfelleStart: LocalDate,
        ): AktivitetskravVurdering = create(
            personIdent = personIdent,
            status = AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT,
            tilfelleStart = tilfelleStart,
            beskrivelse = "Gradert aktivitet",
        )

        private fun create(
            personIdent: PersonIdent,
            status: AktivitetskravVurderingStatus,
            tilfelleStart: LocalDate,
            beskrivelse: String? = null,
        ) = AktivitetskravVurdering(
            uuid = UUID.randomUUID(),
            personIdent = personIdent,
            createdAt = nowUTC(),
            updatedAt = nowUTC(),
            status = status,
            stoppunktAt = stoppunktDato(tilfelleStart),
            beskrivelse = beskrivelse,
            updatedBy = null,
        )

        fun stoppunktDato(tilfelleStart: LocalDate): LocalDate =
            tilfelleStart.plusWeeks(AKTIVITETSKRAV_VURDERING_STOPPUNKT_WEEKS)

        fun status(oppfolgingstilfelle: Oppfolgingstilfelle): AktivitetskravVurderingStatus =
            if (oppfolgingstilfelle.isGradertAtTilfelleEnd()) AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT else AktivitetskravVurderingStatus.NY
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

infix fun AktivitetskravVurdering.gjelder(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
    this.personIdent == oppfolgingstilfelle.personIdent && this.stoppunktAt.isAfter(oppfolgingstilfelle.tilfelleStart) && oppfolgingstilfelle.tilfelleEnd.isAfter(
        stoppunktAt
    )

fun AktivitetskravVurdering.isNy(): Boolean = this.status == AktivitetskravVurderingStatus.NY
fun AktivitetskravVurdering.isAutomatiskOppfylt(): Boolean =
    this.status == AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT
