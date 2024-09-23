package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.AKTIVITETSKRAV_STOPPUNKT_WEEKS
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.isMoreThanDaysAgo
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

private const val DAYS_IN_WEEK = 7

data class Oppfolgingstilfelle(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdent: PersonIdent,
    val tilfelleGenerert: OffsetDateTime,
    val tilfelleStart: LocalDate,
    val tilfelleEnd: LocalDate,
    val antallSykedager: Int?,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
    val gradertAtTilfelleEnd: Boolean?,
    val dodsdato: LocalDate?,
)

fun Oppfolgingstilfelle.passererAktivitetskravStoppunkt(): Boolean =
    durationInWeeks() >= AKTIVITETSKRAV_STOPPUNKT_WEEKS

fun Oppfolgingstilfelle.isGradertAtTilfelleEnd(): Boolean = this.gradertAtTilfelleEnd == true

fun Oppfolgingstilfelle.isInactive(): Boolean = this.tilfelleEnd isMoreThanDaysAgo 30

fun Oppfolgingstilfelle.durationInWeeks(): Long {
    val durationInDays = if (this.antallSykedager != null) {
        antallSykedager.toLong()
    } else {
        ChronoUnit.DAYS.between(this.tilfelleStart, this.tilfelleEnd) + 1
    }
    return durationInDays / DAYS_IN_WEEK
}
