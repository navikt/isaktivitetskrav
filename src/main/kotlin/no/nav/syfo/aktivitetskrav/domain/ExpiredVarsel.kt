package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.domain.VarselType
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
