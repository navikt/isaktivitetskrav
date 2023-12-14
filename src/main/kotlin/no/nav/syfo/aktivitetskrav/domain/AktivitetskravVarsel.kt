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
    val svarfrist: LocalDate?,
    val type: VarselType,
    val isPublished: Boolean = false,
) {

    companion object {
        fun create(
            document: List<DocumentComponentDTO>,
            type: VarselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
        ) =
            AktivitetskravVarsel(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                journalpostId = null,
                svarfrist = if (type == VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER) LocalDate.now().plusWeeks(3) else null,
                document = document,
                type = type,
            )

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
}
