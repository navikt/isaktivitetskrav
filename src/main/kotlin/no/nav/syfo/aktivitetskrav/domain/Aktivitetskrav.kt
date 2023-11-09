package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravResponseDTO
import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingResponseDTO
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val AKTIVITETSKRAV_STOPPUNKT_WEEKS = 8L

data class Aktivitetskrav(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val vurderinger: List<AktivitetskravVurdering>,
) {
    companion object {

        fun create(
            personIdent: PersonIdent,
            oppfolgingstilfelleStart: LocalDate? = null,
            isAutomatiskOppfylt: Boolean = false,
        ): Aktivitetskrav {
            val isGeneratedFromOppfolgingstilfelle = oppfolgingstilfelleStart != null
            val status =
                if (isAutomatiskOppfylt) {
                    AktivitetskravStatus.AUTOMATISK_OPPFYLT
                } else if (isGeneratedFromOppfolgingstilfelle) {
                    AktivitetskravStatus.NY
                } else {
                    AktivitetskravStatus.NY_VURDERING
                }
            return create(
                personIdent = personIdent,
                status = status,
                stoppunktAt = oppfolgingstilfelleStart?.let { stoppunktDato(it) } ?: LocalDate.now(),
            )
        }

        fun fromVurdering(
            personIdent: PersonIdent,
            vurdering: AktivitetskravVurdering,
        ): Aktivitetskrav {
            val aktivitetskravNy = create(
                personIdent = personIdent,
                status = AktivitetskravStatus.NY,
                stoppunktAt = LocalDate.now(),
            )

            return aktivitetskravNy.vurder(vurdering)
        }

        private fun create(
            personIdent: PersonIdent,
            status: AktivitetskravStatus,
            stoppunktAt: LocalDate,
        ) = Aktivitetskrav(
            uuid = UUID.randomUUID(),
            personIdent = personIdent,
            createdAt = nowUTC(),
            status = status,
            stoppunktAt = stoppunktAt,
            vurderinger = emptyList(),
        )

        fun stoppunktDato(tilfelleStart: LocalDate): LocalDate =
            tilfelleStart.plusWeeks(AKTIVITETSKRAV_STOPPUNKT_WEEKS)
    }
}

fun Aktivitetskrav.toKafkaAktivitetskravVurdering(previousAktivitetskravUuid: UUID? = null): KafkaAktivitetskravVurdering {
    val latestVurdering = this.vurderinger.firstOrNull()
    return KafkaAktivitetskravVurdering(
        uuid = this.uuid.toString(),
        personIdent = this.personIdent.value,
        createdAt = this.createdAt,
        status = this.status.name,
        beskrivelse = latestVurdering?.beskrivelse,
        stoppunktAt = this.stoppunktAt,
        updatedBy = latestVurdering?.createdBy,
        arsaker = latestVurdering?.arsaker?.map { it.name } ?: emptyList(),
        sisteVurderingUuid = latestVurdering?.uuid?.toString(),
        sistVurdert = latestVurdering?.createdAt,
        frist = latestVurdering?.frist,
        previousAktivitetskravUuid = previousAktivitetskravUuid,
    )
}

infix fun Aktivitetskrav.gjelder(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
    this.personIdent == oppfolgingstilfelle.personIdent && this.stoppunktAt.isAfter(oppfolgingstilfelle.tilfelleStart) && oppfolgingstilfelle.tilfelleEnd.isAfterOrEqual(
        stoppunktAt
    )

fun Aktivitetskrav.isAutomatiskOppfylt(): Boolean =
    this.status == AktivitetskravStatus.AUTOMATISK_OPPFYLT

fun Aktivitetskrav.isNy(): Boolean = this.status == AktivitetskravStatus.NY

fun Aktivitetskrav.toResponseDTO(vurderinger: List<AktivitetskravVurderingResponseDTO>): AktivitetskravResponseDTO =
    AktivitetskravResponseDTO(
        uuid = uuid.toString(),
        createdAt = createdAt.toLocalDateTime(),
        status = status,
        inFinalState = status.isFinal,
        stoppunktAt = stoppunktAt,
        vurderinger = vurderinger
    )

internal fun Aktivitetskrav.shouldUpdateStoppunkt(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean {
    val updatedStoppunktDato = Aktivitetskrav.stoppunktDato(oppfolgingstilfelle.tilfelleStart)
    return this.stoppunktAt != updatedStoppunktDato
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
