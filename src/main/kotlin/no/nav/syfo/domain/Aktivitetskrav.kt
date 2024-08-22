package no.nav.syfo.domain

import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.plus
import kotlin.let

const val AKTIVITETSKRAV_STOPPUNKT_WEEKS = 8L

data class Aktivitetskrav(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val vurderinger: List<AktivitetskravVurdering>,
) {

    fun vurder(
        aktivitetskravVurdering: AktivitetskravVurdering,
    ): Aktivitetskrav = this.copy(
        status = aktivitetskravVurdering.status,
        vurderinger = listOf(aktivitetskravVurdering) + this.vurderinger,
    )

    fun lukk(): Aktivitetskrav =
        this.copy(status = AktivitetskravStatus.LUKKET)

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

        private fun create(
            personIdent: PersonIdent,
            status: AktivitetskravStatus,
            stoppunktAt: LocalDate,
        ) = Aktivitetskrav(
            uuid = UUID.randomUUID(),
            personIdent = personIdent,
            createdAt = nowUTC(),
            updatedAt = nowUTC(),
            status = status,
            stoppunktAt = stoppunktAt,
            vurderinger = emptyList(),
        )

        fun stoppunktDato(tilfelleStart: LocalDate): LocalDate =
            tilfelleStart.plusWeeks(AKTIVITETSKRAV_STOPPUNKT_WEEKS).minusDays(1)
    }
}

infix fun Aktivitetskrav.gjelder(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
    this.personIdent == oppfolgingstilfelle.personIdent && this.stoppunktAt.isAfter(oppfolgingstilfelle.tilfelleStart) && oppfolgingstilfelle.tilfelleEnd.isAfterOrEqual(
        stoppunktAt
    )

fun Aktivitetskrav.isAutomatiskOppfylt(): Boolean =
    this.status == AktivitetskravStatus.AUTOMATISK_OPPFYLT

fun Aktivitetskrav.isNy(): Boolean = this.status == AktivitetskravStatus.NY

fun Aktivitetskrav.isInFinalState() = this.status.isFinal

internal fun Aktivitetskrav.shouldUpdateStoppunkt(oppfolgingstilfelle: Oppfolgingstilfelle, arenaCutoff: LocalDate): Boolean {
    val updatedStoppunktDato = Aktivitetskrav.stoppunktDato(oppfolgingstilfelle.tilfelleStart)
    return updatedStoppunktDato.isAfter(arenaCutoff) && this.stoppunktAt != updatedStoppunktDato
}

internal fun Aktivitetskrav.updateStoppunkt(oppfolgingstilfelle: Oppfolgingstilfelle): Aktivitetskrav {
    val stoppunktDato = Aktivitetskrav.stoppunktDato(oppfolgingstilfelle.tilfelleStart)
    return this.copy(
        stoppunktAt = stoppunktDato,
    )
}

internal fun Aktivitetskrav.oppfyllAutomatisk(): Aktivitetskrav = this.copy(
    status = AktivitetskravStatus.AUTOMATISK_OPPFYLT,
)
