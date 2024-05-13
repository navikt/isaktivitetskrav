package no.nav.syfo.infrastructure.kafka.domain

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class KafkaAktivitetskravVarsel(
    val personIdent: String,
    val aktivitetskravUuid: UUID,
    val vurderingUuid: UUID,
    val varselUuid: UUID,
    val createdAt: OffsetDateTime,
    val journalpostId: String,
    val svarfrist: LocalDate?,
    val type: String,
    val document: List<DocumentComponentDTO>,
)
