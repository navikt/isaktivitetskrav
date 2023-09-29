package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PAktivitetskravVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val aktivitetskravVurderingId: Int,
    val journalpostId: String?,
    val document: List<DocumentComponentDTO>,
    val publishedAt: OffsetDateTime?,
    val svarfrist: LocalDate,
    val expiredVarselPublishedAt: OffsetDateTime?,
) {
    fun toAktivitetkravVarsel() = AktivitetskravVarsel.createFromDatabase(
        uuid = uuid,
        createdAt = createdAt,
        journalpostId = journalpostId,
        document = document,
        published = publishedAt != null,
    )
}
