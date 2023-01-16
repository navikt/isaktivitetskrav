package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravResponseDTO
import no.nav.syfo.aktivitetskrav.database.PAktivitetskrav
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val AKTIVITETSKRAV_STOPPUNKT_WEEKS = 8L

enum class AktivitetskravStatus {
    NY,
    AVVENT,
    UNNTAK,
    OPPFYLT,
    AUTOMATISK_OPPFYLT,
    STANS,
    IKKE_OPPFYLT,
}

data class Aktivitetskrav private constructor(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val vurderinger: List<AktivitetskravVurdering>,
) {
    companion object {
        fun createFromDatabase(
            pAktivitetskrav: PAktivitetskrav,
            aktivitetskravVurderinger: List<AktivitetskravVurdering>,
        ) = Aktivitetskrav(
            uuid = pAktivitetskrav.uuid,
            personIdent = pAktivitetskrav.personIdent,
            createdAt = pAktivitetskrav.createdAt,
            updatedAt = pAktivitetskrav.updatedAt,
            status = AktivitetskravStatus.valueOf(pAktivitetskrav.status),
            stoppunktAt = pAktivitetskrav.stoppunktAt,
            vurderinger = aktivitetskravVurderinger,
        )

        fun ny(personIdent: PersonIdent, tilfelleStart: LocalDate): Aktivitetskrav =
            create(
                personIdent = personIdent,
                status = AktivitetskravStatus.NY,
                tilfelleStart = tilfelleStart,
            )

        fun automatiskOppfylt(
            personIdent: PersonIdent,
            tilfelleStart: LocalDate,
        ): Aktivitetskrav = create(
            personIdent = personIdent,
            status = AktivitetskravStatus.AUTOMATISK_OPPFYLT,
            tilfelleStart = tilfelleStart,
        )

        private fun create(
            personIdent: PersonIdent,
            status: AktivitetskravStatus,
            tilfelleStart: LocalDate,
        ) = Aktivitetskrav(
            uuid = UUID.randomUUID(),
            personIdent = personIdent,
            createdAt = nowUTC(),
            updatedAt = nowUTC(),
            status = status,
            stoppunktAt = stoppunktDato(tilfelleStart),
            vurderinger = emptyList(),
        )

        fun stoppunktDato(tilfelleStart: LocalDate): LocalDate =
            tilfelleStart.plusWeeks(AKTIVITETSKRAV_STOPPUNKT_WEEKS)
    }
}

fun Aktivitetskrav.toKafkaAktivitetskravVurdering(): KafkaAktivitetskravVurdering {
    val latestVurdering = this.vurderinger.firstOrNull()
    return KafkaAktivitetskravVurdering(
        uuid = this.uuid.toString(),
        personIdent = this.personIdent.value,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        status = this.status.name,
        beskrivelse = latestVurdering?.beskrivelse,
        stoppunktAt = this.stoppunktAt,
        updatedBy = latestVurdering?.createdBy,
        arsaker = latestVurdering?.arsaker?.map { it.name } ?: emptyList(),
    )
}

infix fun Aktivitetskrav.gjelder(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
    this.personIdent == oppfolgingstilfelle.personIdent && this.stoppunktAt.isAfter(oppfolgingstilfelle.tilfelleStart) && oppfolgingstilfelle.tilfelleEnd.isAfter(
        stoppunktAt
    )

fun Aktivitetskrav.isAutomatiskOppfylt(): Boolean =
    this.status == AktivitetskravStatus.AUTOMATISK_OPPFYLT

fun Aktivitetskrav.isNy(): Boolean = this.status == AktivitetskravStatus.NY

fun Aktivitetskrav.isVurdert(): Boolean =
    this.status != AktivitetskravStatus.AUTOMATISK_OPPFYLT && this.status != AktivitetskravStatus.NY

fun List<Aktivitetskrav>.toResponseDTOList() = this.map {
    AktivitetskravResponseDTO(
        uuid = it.uuid.toString(),
        createdAt = it.createdAt.toLocalDateTime(),
        updatedAt = it.updatedAt.toLocalDateTime(),
        status = it.status,
        stoppunktAt = it.stoppunktAt,
        vurderinger = it.vurderinger.toVurderingResponseDTOs()
    )
}

internal fun Aktivitetskrav.updateStoppunkt(oppfolgingstilfelle: Oppfolgingstilfelle): Aktivitetskrav {
    val stoppunktDato = Aktivitetskrav.stoppunktDato(oppfolgingstilfelle.tilfelleStart)
    return this.copy(
        stoppunktAt = stoppunktDato,
    )
}

internal fun Aktivitetskrav.vurder(
    aktivitetskravVurdering: AktivitetskravVurdering,
): Aktivitetskrav = this.copy(
    status = aktivitetskravVurdering.status,
    vurderinger = listOf(aktivitetskravVurdering) + this.vurderinger,
)

internal fun Aktivitetskrav.oppfyllAutomatisk(): Aktivitetskrav = this.copy(
    status = AktivitetskravStatus.AUTOMATISK_OPPFYLT,
)
