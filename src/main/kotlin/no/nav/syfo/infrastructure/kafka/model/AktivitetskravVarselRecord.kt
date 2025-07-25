package no.nav.syfo.infrastructure.kafka.model

import no.nav.syfo.api.dto.DocumentComponentDTO
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVarselRecord(
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
