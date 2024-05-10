package no.nav.syfo.infrastructure.database

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.VarselType
import no.nav.syfo.infrastructure.kafka.domain.ExpiredVarsel
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVarsel
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
    val expiredVarselPublishedAt: OffsetDateTime?,
    val type: String
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

    fun toExpiredVarsel(personIdent: PersonIdent, aktivitetskravUuid: UUID) = ExpiredVarsel(
        aktivitetskravUuid = aktivitetskravUuid,
        varselUuid = uuid,
        createdAt = createdAt.toLocalDateTime(),
        personIdent = personIdent,
        varselType = VarselType.valueOf(type),
        svarfrist = svarfrist!!,
    )

    fun toKafkaAktivitetskravVarsel(references: VarselReferences) = KafkaAktivitetskravVarsel(
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
