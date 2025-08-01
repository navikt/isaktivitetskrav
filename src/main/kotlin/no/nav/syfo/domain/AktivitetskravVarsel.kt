package no.nav.syfo.domain

import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.api.dto.VarselResponseDTO
import no.nav.syfo.util.nowUTC
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class AktivitetskravVarsel internal constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val journalpostId: String?,
    val document: List<DocumentComponentDTO>,
    val svarfrist: LocalDate?,
    val type: VarselType,
    val isPublished: Boolean = false,
) {

    companion object {
        fun create(
            type: VarselType,
            frist: LocalDate? = null,
            document: List<DocumentComponentDTO>,
        ): AktivitetskravVarsel {
            if (document.isEmpty()) {
                throw IllegalArgumentException("Varsel can't have empty document")
            }
            if (type == VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER && frist == null) {
                throw IllegalArgumentException("Forhandsvarsel must have frist")
            }
            if (type == VarselType.INNSTILLING_OM_STANS && frist != null) {
                throw IllegalArgumentException("Innstilling om stans can't have frist")
            }
            return AktivitetskravVarsel(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                journalpostId = null,
                svarfrist = if (type == VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER) frist else null,
                document = document,
                type = type,
            )
        }

        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            journalpostId: String?,
            document: List<DocumentComponentDTO>,
            svarfrist: LocalDate?,
            isPublished: Boolean,
            type: String,
        ) = AktivitetskravVarsel(
            uuid = uuid,
            createdAt = createdAt,
            journalpostId = journalpostId,
            document = document,
            svarfrist = svarfrist,
            isPublished = isPublished,
            type = VarselType.valueOf(type)
        )
    }

    fun toVarselResponseDTO() = VarselResponseDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt.toLocalDateTime(),
        svarfrist = this.svarfrist,
        document = this.document,
    )

    fun getDokumentTittel() = this.type.getDokumentTittel()
    fun getBrevkode() = this.type.getBrevkode()
    fun getJournalpostType() = this.type.getJournalpostType()
}
