package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.sql.Date

const val queryCreateAktivitetskravVurdering =
    """
    INSERT INTO AKTIVITETSKRAV_VURDERING (
        id,
        uuid,
        aktivitetskrav_id,
        created_at,
        created_by,
        status,
        beskrivelse,
        arsaker,
        stans_fom,
        frist
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    RETURNING *
    """

fun Connection.createAktivitetskravVurdering(
    aktivitetskravId: Int,
    aktivitetskravVurdering: AktivitetskravVurdering,
): PAktivitetskravVurdering {
    val aktivitetskravVurderinger = this.prepareStatement(queryCreateAktivitetskravVurdering).use {
        it.setString(1, aktivitetskravVurdering.uuid.toString())
        it.setInt(2, aktivitetskravId)
        it.setObject(3, aktivitetskravVurdering.createdAt)
        it.setString(4, aktivitetskravVurdering.createdBy)
        it.setString(5, aktivitetskravVurdering.status.name)
        it.setString(6, aktivitetskravVurdering.beskrivelse)
        it.setString(7, aktivitetskravVurdering.arsaker().joinToString(","))
        it.setDate(
            8,
            if (aktivitetskravVurdering is AktivitetskravVurdering.InnstillingOmStans)
                aktivitetskravVurdering.stansFom.let { stansFom -> Date.valueOf(stansFom) }
            else null
        )
        it.setDate(9, aktivitetskravVurdering.frist()?.let { frist -> Date.valueOf(frist) })
        it.executeQuery().toList { toPAktivitetskravVurdering() }
    }

    if (aktivitetskravVurderinger.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VURDERING failed, no rows affected.")
    }

    return aktivitetskravVurderinger.first()
}

const val queryUpdateAktivitetskrav =
    """
        UPDATE AKTIVITETSKRAV SET status=?, stoppunkt_at=?, updated_at=? WHERE uuid = ? RETURNING id
    """

fun Connection.updateAktivitetskrav(
    aktivitetskrav: Aktivitetskrav,
): Int {
    val aktivitetskravUuid = aktivitetskrav.uuid
    val updatedIds = this.prepareStatement(queryUpdateAktivitetskrav).use { preparedStatement ->
        preparedStatement.setString(1, aktivitetskrav.status.name)
        preparedStatement.setDate(2, Date.valueOf(aktivitetskrav.stoppunktAt))
        preparedStatement.setObject(3, nowUTC())
        preparedStatement.setString(4, aktivitetskravUuid.toString())
        preparedStatement.executeQuery().toList { getInt("id") }
    }

    if (updatedIds.size != 1) {
        throw SQLException("Failed to update aktivitetskrav with uuid $aktivitetskravUuid - Unexpected update count: ${updatedIds.size}")
    }

    return updatedIds.first()
}
