package no.nav.syfo.infrastructure.kafka.domain

import no.nav.syfo.domain.VarselType
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class ExpiredVarsel(
    val aktivitetskravUuid: UUID,
    val varselUuid: UUID,
    val createdAt: LocalDateTime,
    val personIdent: PersonIdent,
    val varselType: VarselType,
    val svarfrist: LocalDate,
)
