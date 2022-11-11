package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.aktivitetskrav.domain.AKTIVITETSKRAV_VURDERING_STOPPUNKT_WEEKS
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

data class Oppfolgingstilfelle(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdent,
    val tilfelleGenerert: OffsetDateTime,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
    val gradertAtTilfelleEnd: Boolean?,
)

fun Oppfolgingstilfelle.passererAktivitetskravVurderingStoppunkt(): Boolean =
    durationInWeeks() >= AKTIVITETSKRAV_VURDERING_STOPPUNKT_WEEKS

fun Oppfolgingstilfelle.toAktivitetskravVurdering(): AktivitetskravVurdering {
    return if (isGradertAtTilfelleEnd()) {
        AktivitetskravVurdering.automatiskOppfyltGradert(
            personIdent = this.personIdent,
            tilfelleStart = this.tilfelleStart,
        )
    } else {
        AktivitetskravVurdering.ny(
            personIdent = this.personIdent,
            tilfelleStart = this.tilfelleStart,
        )
    }
}

fun Oppfolgingstilfelle.isGradertAtTilfelleEnd(): Boolean = this.gradertAtTilfelleEnd == true

private fun Oppfolgingstilfelle.durationInWeeks(): Long =
    ChronoUnit.WEEKS.between(this.tilfelleStart, this.tilfelleEnd)
