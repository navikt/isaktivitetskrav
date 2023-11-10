package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.aktivitetskrav.domain.AKTIVITETSKRAV_STOPPUNKT_WEEKS
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
    val dodsdato: LocalDate?,
)

fun Oppfolgingstilfelle.passererAktivitetskravStoppunkt(): Boolean =
    durationInWeeks() >= AKTIVITETSKRAV_STOPPUNKT_WEEKS

fun Oppfolgingstilfelle.isGradertAtTilfelleEnd(): Boolean = this.gradertAtTilfelleEnd == true

private fun Oppfolgingstilfelle.durationInWeeks(): Long =
    ChronoUnit.WEEKS.between(this.tilfelleStart, this.tilfelleEnd)
