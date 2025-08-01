package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.application.IAktivitetskravVarselRepository
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVarselRecord
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.sql.Date
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class AktivitetskravVarselRepository(private val database: DatabaseInterface) : IAktivitetskravVarselRepository {

    override fun createAktivitetskravVurderingWithVarselPdf(
        aktivitetskrav: Aktivitetskrav,
        newVurdering: AktivitetskravVurdering,
        varsel: AktivitetskravVarsel,
        pdf: ByteArray,
    ): PAktivitetskravVarsel =
        database.connection.use { connection ->
            val aktivitetskravId = connection.updateAktivitetskrav(aktivitetskrav)
            val vurdering = connection.createAktivitetskravVurdering(
                aktivitetskravId = aktivitetskravId,
                aktivitetskravVurdering = newVurdering,
            )
            val nyttVarsel = connection.createAktivitetskravVarsel(
                vurderingId = vurdering.id,
                varsel = varsel,
            )
            connection.createAktivitetskravVarselPdf(
                varselId = nyttVarsel.id,
                pdf = pdf,
            )
            connection.commit()
            nyttVarsel
        }

    override fun getIkkeJournalforte(): List<Triple<PersonIdent, PAktivitetskravVarsel, ByteArray>> =
        database.getIkkeJournalforteVarsler()

    override fun getIkkePubliserte(): List<Pair<PAktivitetskravVarsel, VarselReferences>> =
        database.getIkkePubliserteVarsler()

    override fun updateJournalpostId(varsel: AktivitetskravVarsel, journalpostId: String) =
        database.updateVarselJournalpostId(varsel, journalpostId)

    override fun setPublished(varsel: AktivitetskravVarselRecord) =
        database.setPublished(varsel.varselUuid)

    override fun getVarselForVurdering(vurderingUuid: UUID) =
        database.getVarselForVurdering(vurderingUuid = vurderingUuid)
}

private const val queryCreateAktivitetskravVarsel =
    """
    INSERT INTO AKTIVITETSKRAV_VARSEL (
        id,
        uuid,
        created_at,
        updated_at,
        aktivitetskrav_vurdering_id,
        document,
        journalpost_id,
        svarfrist,
        type
    ) values (DEFAULT, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
    RETURNING *
    """

private fun Connection.createAktivitetskravVarsel(
    vurderingId: Int,
    varsel: AktivitetskravVarsel,
): PAktivitetskravVarsel {
    val varsler = this.prepareStatement(queryCreateAktivitetskravVarsel).use {
        it.setString(1, varsel.uuid.toString())
        it.setObject(2, varsel.createdAt)
        it.setObject(3, varsel.createdAt)
        it.setInt(4, vurderingId)
        it.setObject(5, mapper.writeValueAsString(varsel.document))
        it.setNull(6, Types.VARCHAR)
        it.setDate(7, varsel.svarfrist?.let { svarFrist -> Date.valueOf(svarFrist) })
        it.setString(8, varsel.type.name)
        it.executeQuery().toList { toPAktivitetskravVarsel() }
    }

    if (varsler.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VARSEL failed, no rows affected.")
    }

    return varsler.first()
}

private const val queryCreateAktivitetskravVarselPdf =
    """
    INSERT INTO AKTIVITETSKRAV_VARSEL_PDF (
        id,
        uuid,
        created_at,
        aktivitetskrav_varsel_id,
        pdf
    ) values (DEFAULT, ?, ?, ?, ?)
    RETURNING *
    """

private fun Connection.createAktivitetskravVarselPdf(varselId: Int, pdf: ByteArray): PAktivitetskravVarselPdf {
    val varselPdfs = this.prepareStatement(queryCreateAktivitetskravVarselPdf).use {
        it.setString(1, UUID.randomUUID().toString())
        it.setObject(2, nowUTC())
        it.setInt(3, varselId)
        it.setBytes(4, pdf)
        it.executeQuery().toList { toPAktivitetskravVarselPdf() }
    }

    if (varselPdfs.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VARSEL_PDF failed, no rows affected.")
    }

    return varselPdfs.first()
}

private const val queryGetIkkeJournalforteVarsler = """
    SELECT av.*, avp.pdf as pdf, a.personident as personident 
    FROM aktivitetskrav_varsel av 
    INNER JOIN aktivitetskrav_varsel_pdf avp
    ON av.id = avp.aktivitetskrav_varsel_id
    INNER JOIN aktivitetskrav_vurdering avu
    ON av.aktivitetskrav_vurdering_id = avu.id
    INNER JOIN aktivitetskrav a
    ON avu.aktivitetskrav_id = a.id
    WHERE av.journalpost_id IS NULL
"""

private fun DatabaseInterface.getIkkeJournalforteVarsler(): List<Triple<PersonIdent, PAktivitetskravVarsel, ByteArray>> =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetIkkeJournalforteVarsler).use {
            it.executeQuery()
                .toList {
                    Triple(
                        PersonIdent(getString("personident")),
                        toPAktivitetskravVarsel(),
                        getBytes("pdf")
                    )
                }
        }
    }

private const val queryUpdateJournalpostId = """
    UPDATE aktivitetskrav_varsel
    SET journalpost_id = ?, updated_at = ?
    WHERE uuid = ?
"""

private fun DatabaseInterface.updateVarselJournalpostId(varsel: AktivitetskravVarsel, journalpostId: String) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateJournalpostId).use {
            it.setString(1, journalpostId)
            it.setObject(2, nowUTC())
            it.setString(3, varsel.uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }
}

private const val queryGetIkkePubliserteVarsler = """
    SELECT av.*, a.personident as personident, a.uuid as aktivitetskravUuid, avu.uuid as vurderingUuid 
    FROM aktivitetskrav_varsel av 
    INNER JOIN aktivitetskrav_vurdering avu
    ON av.aktivitetskrav_vurdering_id = avu.id
    INNER JOIN aktivitetskrav a
    ON avu.aktivitetskrav_id = a.id
    WHERE av.journalpost_id IS NOT NULL and av.published_at IS NULL
"""

private fun DatabaseInterface.getIkkePubliserteVarsler(): List<Pair<PAktivitetskravVarsel, VarselReferences>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetIkkePubliserteVarsler).use {
            it.executeQuery().toList {
                Pair(
                    toPAktivitetskravVarsel(),
                    VarselReferences(
                        personIdent = PersonIdent(getString("personident")),
                        aktivitetskravUuid = UUID.fromString(getString("aktivitetskravUuid")),
                        vurderingUuid = UUID.fromString(getString("vurderingUuid")),
                    )
                )
            }
        }
    }
}

private const val querySetPublished = """
    UPDATE aktivitetskrav_varsel
    SET published_at = ?, updated_at = ?
    WHERE uuid = ?
"""

private fun DatabaseInterface.setPublished(varselUuid: UUID) {
    val now = nowUTC()
    this.connection.use { connection ->
        connection.prepareStatement(querySetPublished).use {
            it.setObject(1, now)
            it.setObject(2, now)
            it.setString(3, varselUuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }
}

private const val queryGetVarselWithVurderingUuid = """
    SELECT av.* 
    FROM aktivitetskrav_varsel av 
    INNER JOIN aktivitetskrav_vurdering avu
    ON av.aktivitetskrav_vurdering_id = avu.id
    WHERE avu.uuid = ?
"""

private fun DatabaseInterface.getVarselForVurdering(vurderingUuid: UUID): PAktivitetskravVarsel? {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetVarselWithVurderingUuid).use {
            it.setString(1, vurderingUuid.toString())
            it.executeQuery().toList { toPAktivitetskravVarsel() }
        }.firstOrNull()
    }
}

fun ResultSet.toPAktivitetskravVarsel(): PAktivitetskravVarsel =
    PAktivitetskravVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        aktivitetskravVurderingId = getInt("aktivitetskrav_vurdering_id"),
        journalpostId = getString("journalpost_id"),
        document = mapper.readValue(
            getString("document"),
            object : TypeReference<List<DocumentComponentDTO>>() {}
        ),
        publishedAt = getObject("published_at", OffsetDateTime::class.java),
        svarfrist = getDate("svarfrist")?.toLocalDate(),
        type = getString("type"),
    )

private fun ResultSet.toPAktivitetskravVarselPdf(): PAktivitetskravVarselPdf =
    PAktivitetskravVarselPdf(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        aktivitetskravVarselId = getInt("aktivitetskrav_varsel_id"),
        pdf = getBytes("pdf"),
    )
