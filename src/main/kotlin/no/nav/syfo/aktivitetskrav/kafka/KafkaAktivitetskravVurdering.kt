package no.nav.syfo.aktivitetskrav.kafka

import java.time.LocalDate
import java.time.OffsetDateTime

data class KafkaAktivitetskravVurdering(
    val uuid: String,
    val personIdent: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: String,
    val tilfelleStart: LocalDate,
    val beskrivelse: String?,
)
