package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.aktivitetskrav.domain.AKTIVITETSKRAV_STOPPUNKT_WEEKS
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
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

fun Oppfolgingstilfelle.passererAktivitetskravStoppunkt(): Boolean =
    durationInWeeks() >= AKTIVITETSKRAV_STOPPUNKT_WEEKS

fun Oppfolgingstilfelle.toAktivitetskrav(): Aktivitetskrav {
    return if (isGradertAtTilfelleEnd()) {
        Aktivitetskrav.automatiskOppfyltGradert(
            personIdent = this.personIdent,
            tilfelleStart = this.tilfelleStart,
        )
    } else {
        Aktivitetskrav.ny(
            personIdent = this.personIdent,
            tilfelleStart = this.tilfelleStart,
        )
    }
}

fun Oppfolgingstilfelle.isGradertAtTilfelleEnd(): Boolean = this.gradertAtTilfelleEnd == true

private fun Oppfolgingstilfelle.durationInWeeks(): Long =
    ChronoUnit.WEEKS.between(this.tilfelleStart, this.tilfelleEnd)
