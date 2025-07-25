package no.nav.syfo.testhelper.generator

import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.Oppfolgingstilfelle
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateOppfolgingstilfelle(
    start: LocalDate,
    end: LocalDate,
    antallSykedager: Int?,
) = Oppfolgingstilfelle(
    uuid = UUID.randomUUID(),
    createdAt = OffsetDateTime.now(),
    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    tilfelleGenerert = OffsetDateTime.now(),
    tilfelleStart = start,
    tilfelleEnd = end,
    antallSykedager = antallSykedager,
    referanseTilfelleBitUuid = UUID.randomUUID(),
    referanseTilfelleBitInntruffet = OffsetDateTime.now(),
    gradertAtTilfelleEnd = false,
    dodsdato = null,
)
