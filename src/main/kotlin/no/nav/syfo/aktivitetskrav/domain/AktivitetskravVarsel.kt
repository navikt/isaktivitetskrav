package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.VarselResponseDTO
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVarsel internal constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val journalpostId: String?,
    val document: List<DocumentComponentDTO>,
    val svarfrist: LocalDate,
    val isPublished: Boolean = false,
) {

    companion object {
        fun create(
            document: List<DocumentComponentDTO>,
            svarfrist: LocalDate = LocalDate.now().plusWeeks(3),
        ) =
            AktivitetskravVarsel(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                journalpostId = null,
                svarfrist = svarfrist,
                document = document,
            )

        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            journalpostId: String?,
            document: List<DocumentComponentDTO>,
            svarfrist: LocalDate,
            isPublished: Boolean,
        ) = AktivitetskravVarsel(
            uuid = uuid,
            createdAt = createdAt,
            journalpostId = journalpostId,
            document = document,
            svarfrist = svarfrist,
            isPublished = isPublished,
        )
    }

    fun toVarselResponseDTO() = VarselResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        svarfrist = this.createdAt.toLocalDate().plusWeeks(3)
    )
}
