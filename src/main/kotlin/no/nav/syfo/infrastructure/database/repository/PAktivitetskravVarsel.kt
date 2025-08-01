package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVarselRecord
import no.nav.syfo.domain.PersonIdent
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
    val svarfrist: LocalDate?,
    val type: String,
) {
    fun toAktivitetkravVarsel() = AktivitetskravVarsel.createFromDatabase(
        uuid = uuid,
        createdAt = createdAt,
        journalpostId = journalpostId,
        document = document,
        svarfrist = svarfrist,
        isPublished = publishedAt != null,
        type = type,
    )

    fun toKafkaAktivitetskravVarsel(references: VarselReferences) = AktivitetskravVarselRecord(
        personIdent = references.personIdent.value,
        aktivitetskravUuid = references.aktivitetskravUuid,
        vurderingUuid = references.vurderingUuid,
        varselUuid = uuid,
        createdAt = createdAt,
        journalpostId = journalpostId!!,
        svarfrist = svarfrist,
        document = document,
        type = type,
    )
}

data class VarselReferences(
    val personIdent: PersonIdent,
    val aktivitetskravUuid: UUID,
    val vurderingUuid: UUID,
)
