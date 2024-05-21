package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.sql.Date
import java.time.OffsetDateTime
import java.util.*

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
        frist
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?)
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
        it.setString(7, aktivitetskravVurdering.arsaker.joinToString(",") { arsak -> arsak.value })
        it.setDate(8, aktivitetskravVurdering.frist?.let { frist -> Date.valueOf(frist) })
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

fun ResultSet.toPAktivitetskravVurdering(): PAktivitetskravVurdering {
    val status = AktivitetskravStatus.valueOf(getString("status"))
    return PAktivitetskravVurdering(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        aktivitetskravId = getInt("aktivitetskrav_id"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        createdBy = getString("created_by"),
        status = status,
        beskrivelse = getString("beskrivelse"),
        arsaker = getString("arsaker").split(",").map(String::trim).filter(String::isNotEmpty)
            .map { it.toVurderingArsak(status) },
        frist = getDate("frist")?.toLocalDate(),
    )
}
