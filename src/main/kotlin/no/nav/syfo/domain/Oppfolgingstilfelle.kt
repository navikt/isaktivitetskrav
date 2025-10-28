package no.nav.syfo.domain

import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.OLD_TILFELLE_CUTOFF
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
) {
    private val isNotDead = dodsdato == null

    fun isRelevantForAktivitetskrav(): Boolean =
        this.passererAktivitetskravStoppunkt() &&
            isNotDead &&
            this.tilfelleStart.isAfter(OLD_TILFELLE_CUTOFF) &&
            !this.isInactive()

    fun passererAktivitetskravStoppunkt(): Boolean =
        durationInWeeks() >= AKTIVITETSKRAV_STOPPUNKT_WEEKS

    fun isGradertAtTilfelleEnd(): Boolean = this.gradertAtTilfelleEnd == true

    fun isInactive(): Boolean = this.tilfelleEnd isMoreThanDaysAgo 30

    fun durationInWeeks(): Long {
        val durationInDays = if (this.antallSykedager != null) {
            antallSykedager.toLong()
        } else {
            ChronoUnit.DAYS.between(this.tilfelleStart, this.tilfelleEnd) + 1
        }
        return durationInDays / DAYS_IN_WEEK
    }
}
