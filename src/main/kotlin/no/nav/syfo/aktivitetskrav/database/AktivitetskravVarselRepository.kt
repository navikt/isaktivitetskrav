package no.nav.syfo.aktivitetskrav.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.Connection
import java.sql.ResultSet
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
    val now = nowUTC()
    val varsler = this.prepareStatement(queryCreateAktivitetskravVarsel).use {
        it.setString(1, varsel.uuid.toString())
        it.setObject(2, now)
        it.setObject(3, now)
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

private fun ResultSet.toPAktivitetskravVarsel(): PAktivitetskravVarsel =
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
    )

private fun ResultSet.toPAktivitetskravVarselPdf(): PAktivitetskravVarselPdf =
    PAktivitetskravVarselPdf(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        aktivitetskravVarselId = getInt("aktivitetskrav_varsel_id"),
        pdf = getBytes("pdf"),
    )