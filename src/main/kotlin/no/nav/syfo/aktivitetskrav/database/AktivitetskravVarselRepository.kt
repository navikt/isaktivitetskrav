package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.Connection
import java.sql.Types
import java.util.*

class AktivitetskravVarselRepository(private val database: DatabaseInterface) {

    fun create(
        aktivitetskrav: Aktivitetskrav,
        vurdering: AktivitetskravVurdering,
        varsel: AktivitetskravVarsel,
        pdf: ByteArray,
    ) {
        database.connection.use { connection ->
            val aktivitetskravId = connection.updateAktivitetskrav(aktivitetskrav = aktivitetskrav)
            val vurderingId = connection.createAktivitetskravVurdering(
                aktivitetskravId = aktivitetskravId,
                aktivitetskravVurdering = vurdering,
            )
            val varselId = connection.createAktivitetskravVarsel(
                vurderingId = vurderingId,
                varsel = varsel,
            )
            connection.createAktivitetskravVarselPdf(
                varselId = varselId,
                pdf = pdf,
            )
            connection.commit()
        }
    }
}

private val mapper = configuredJacksonMapper()

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
    RETURNING id
    """

private fun Connection.createAktivitetskravVarsel(vurderingId: Int, varsel: AktivitetskravVarsel): Int {
    val now = nowUTC()
    val idList = this.prepareStatement(queryCreateAktivitetskravVarsel).use {
        it.setString(1, varsel.uuid.toString())
        it.setObject(2, now)
        it.setObject(3, now)
        it.setInt(4, vurderingId)
        it.setObject(5, mapper.writeValueAsString(varsel.document))
        it.setNull(6, Types.VARCHAR)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VARSEL failed, no rows affected.")
    }

    return idList.first()
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
    RETURNING id
    """

private fun Connection.createAktivitetskravVarselPdf(varselId: Int, pdf: ByteArray): Int {
    val idList = this.prepareStatement(queryCreateAktivitetskravVarselPdf).use {
        it.setString(1, UUID.randomUUID().toString())
        it.setObject(2, nowUTC())
        it.setInt(3, varselId)
        it.setBytes(4, pdf)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VARSEL failed, no rows affected.")
    }

    return idList.first()
}
