package no.nav.syfo.aktivitetskrav.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class AktivitetskravVarselRepository(private val database: DatabaseInterface) {

    fun create(
        aktivitetskrav: Aktivitetskrav,
        varsel: AktivitetskravVarsel,
        pdf: ByteArray,
    ): PAktivitetskravVarsel =
        database.connection.use { connection ->
            val aktivitetskravId = connection.updateAktivitetskrav(aktivitetskrav)
            val newestVurdering = aktivitetskrav.vurderinger.first()
            val vurderingId = connection.createAktivitetskravVurdering(
                aktivitetskravId = aktivitetskravId,
                aktivitetskravVurdering = newestVurdering,
            )
            val nyttVarsel = connection.createAktivitetskravVarsel(
                vurderingId = vurderingId,
                varsel = varsel,
            )
            connection.createAktivitetskravVarselPdf(
                varselId = nyttVarsel.id,
                pdf = pdf,
            )
            connection.commit()
            nyttVarsel
        }

    fun getIkkeJournalforte(): List<Triple<PersonIdent, PAktivitetskravVarsel, ByteArray>> = database.getIkkeJournalforteVarsler()

    fun getIkkePubliserte(): List<Triple<PersonIdent, PAktivitetskravVarsel, UUID>> = database.getIkkePubliserteVarsler()

    fun updateJournalpostId(varsel: AktivitetskravVarsel, journalpostId: String) =
        database.updateVarselJournalpostId(varsel, journalpostId)

    fun setPublished(varsel: AktivitetskravVarsel) =
        database.setPublished(varsel)
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
        journalpost_id
    ) values (DEFAULT, ?, ?, ?, ?, ?::jsonb, ?)
    RETURNING *
    """

private fun Connection.createAktivitetskravVarsel(
    vurderingId: Int,
    varsel: AktivitetskravVarsel
): PAktivitetskravVarsel {
    val varsler = this.prepareStatement(queryCreateAktivitetskravVarsel).use {
        it.setString(1, varsel.uuid.toString())
        it.setObject(2, varsel.createdAt)
        it.setObject(3, nowUTC())
        it.setInt(4, vurderingId)
        it.setObject(5, mapper.writeValueAsString(varsel.document))
        it.setNull(6, Types.VARCHAR)
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

private fun DatabaseInterface.getIkkeJournalforteVarsler(): List<Triple<PersonIdent, PAktivitetskravVarsel, ByteArray>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetIkkeJournalforteVarsler).use {
            it.executeQuery().toList { Triple(PersonIdent(getString("personident")), toPAktivitetskravVarsel(), getBytes("pdf")) }
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
    SELECT av.*, a.personident as personident, a.uuid as aktivitetskravUuid 
    FROM aktivitetskrav_varsel av 
    INNER JOIN aktivitetskrav_vurdering avu
    ON av.aktivitetskrav_vurdering_id = avu.id
    INNER JOIN aktivitetskrav a
    ON avu.aktivitetskrav_id = a.id
    WHERE av.journalpost_id IS NOT NULL and av.published_at IS NULL
"""

private fun DatabaseInterface.getIkkePubliserteVarsler(): List<Triple<PersonIdent, PAktivitetskravVarsel, UUID>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetIkkePubliserteVarsler).use {
            it.executeQuery().toList {
                Triple(
                    PersonIdent(getString("personident")),
                    toPAktivitetskravVarsel(),
                    UUID.fromString(getString("aktivitetskravUuid")),
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

private fun DatabaseInterface.setPublished(varsel: AktivitetskravVarsel) {
    val now = nowUTC()
    this.connection.use { connection ->
        connection.prepareStatement(querySetPublished).use {
            it.setObject(1, now)
            it.setObject(2, now)
            it.setString(3, varsel.uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
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
    )

private fun ResultSet.toPAktivitetskravVarselPdf(): PAktivitetskravVarselPdf =
    PAktivitetskravVarselPdf(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        aktivitetskravVarselId = getInt("aktivitetskrav_varsel_id"),
        pdf = getBytes("pdf"),
    )
