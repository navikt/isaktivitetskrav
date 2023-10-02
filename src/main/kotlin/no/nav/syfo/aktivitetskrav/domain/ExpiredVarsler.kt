package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.util.*

data class ExpiredVarsel(
    val uuid: UUID,
    val svarfrist: LocalDate,
)