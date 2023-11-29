package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.HistorikkDTO
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
    val updatedAt: OffsetDateTime,
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val referanseTilfelleBitUuid: UUID? = null,
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
            tilfelleStart.plusWeeks(AKTIVITETSKRAV_STOPPUNKT_WEEKS)
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

fun Aktivitetskrav.toHistorikkDTOs(): List<HistorikkDTO> {
    val historikk = mutableListOf<HistorikkDTO>()
    historikk.add(
        HistorikkDTO(
            tidspunkt = createdAt.toLocalDateTime(),
            status = if (createdAt.toLocalDate() == stoppunktAt) AktivitetskravStatus.NY_VURDERING else AktivitetskravStatus.NY,
            vurdertAv = null,
        )
    )
    vurderinger.forEach {
        historikk.add(
            HistorikkDTO(
                tidspunkt = it.createdAt.toLocalDateTime(),
                status = it.status,
                vurdertAv = it.createdBy,
            )
        )
    }
    if (status == AktivitetskravStatus.LUKKET) {
        historikk.add(
            HistorikkDTO(
                tidspunkt = updatedAt.toLocalDateTime(),
                status = AktivitetskravStatus.LUKKET,
                vurdertAv = null,
            )
        )
    }
    return historikk
}

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

internal fun Aktivitetskrav.oppfyllAutomatisk(): Aktivitetskrav = this.copy(
    status = AktivitetskravStatus.AUTOMATISK_OPPFYLT,
)
