package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import java.time.OffsetDateTime
import java.util.*

data class KafkaAktivitetskravVarsel(
    val personIdent: String,
    val aktivitetskravUuid: UUID,
    val varselUuid: UUID,
    val createdAt: OffsetDateTime,
    val journalpostId: String,
    val document: List<DocumentComponentDTO>,
)
