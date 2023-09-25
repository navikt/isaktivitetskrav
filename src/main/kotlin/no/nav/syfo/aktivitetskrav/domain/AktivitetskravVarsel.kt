package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVarsel internal constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val journalpostId: String?,
    val document: List<DocumentComponentDTO>,
    val isPublished: Boolean = false,
) {

    companion object {
        fun create(document: List<DocumentComponentDTO>) = AktivitetskravVarsel(
            uuid = UUID.randomUUID(),
            createdAt = nowUTC(),
            journalpostId = null,
            document = document,
        )

        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            journalpostId: String?,
            document: List<DocumentComponentDTO>,
            published: Boolean,
        ) = AktivitetskravVarsel(
            uuid = uuid,
            createdAt = createdAt,
            journalpostId = journalpostId,
            document = document,
            isPublished = published,
        )
    }
}
