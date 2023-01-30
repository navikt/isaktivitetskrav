package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.application.database.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.sql.Date
import java.time.OffsetDateTime
import java.util.*

const val queryCreateAktivitetskrav =
    """
    INSERT INTO AKTIVITETSKRAV (
        id,
        uuid,
        created_at,
        updated_at,
        personident,
        status,
        stoppunkt_at
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createAktivitetskrav(
    aktivitetskrav: Aktivitetskrav,
): Int {
    val idList = this.prepareStatement(queryCreateAktivitetskrav).use {
        it.setString(1, aktivitetskrav.uuid.toString())
        it.setObject(2, aktivitetskrav.createdAt)
        it.setObject(3, nowUTC())
        it.setString(4, aktivitetskrav.personIdent.value)
        it.setString(5, aktivitetskrav.status.name)
        it.setDate(6, Date.valueOf(aktivitetskrav.stoppunktAt))
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV failed, no rows affected.")
    }

    return idList.first()
}

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
        arsaker
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createAktivitetskravVurdering(
    aktivitetskravId: Int,
    aktivitetskravVurdering: AktivitetskravVurdering,
) {
    val idList = this.prepareStatement(queryCreateAktivitetskravVurdering).use {
        it.setString(1, aktivitetskravVurdering.uuid.toString())
        it.setInt(2, aktivitetskravId)
        it.setObject(3, aktivitetskravVurdering.createdAt)
        it.setString(4, aktivitetskravVurdering.createdBy)
        it.setString(5, aktivitetskravVurdering.status.name)
        it.setString(6, aktivitetskravVurdering.beskrivelse)
        it.setString(7, aktivitetskravVurdering.arsakerToString())
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VURDERING failed, no rows affected.")
    }
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

const val queryUpdateAktivitetskravPersonIdent =
    """
        UPDATE AKTIVITETSKRAV
        SET personident = ?, updated_at=?
        WHERE personident = ?
    """

fun DatabaseInterface.updateAktivitetskravPersonIdent(
    nyPersonIdent: PersonIdent,
    inactiveIdenter: List<PersonIdent>,
): Int {
    var updatedRows = 0

    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateAktivitetskravPersonIdent).use {
            inactiveIdenter.forEach { inactiveIdent ->
                it.setString(1, nyPersonIdent.value)
                it.setObject(2, nowUTC())
                it.setString(3, inactiveIdent.value)
                it.executeUpdate()
                updatedRows++
            }
        }
        connection.commit()
    }

    return updatedRows
}

fun DatabaseInterface.getAktivitetskrav(
    personIdent: PersonIdent,
    connection: Connection? = null,
): List<PAktivitetskrav> {
    return connection?.getAktivitetskrav(
        personIdent = personIdent
    )
        ?: this.connection.use {
            it.getAktivitetskrav(
                personIdent = personIdent
            )
        }
}

const val queryGetAktivitetskravByPersonident =
    """
        SELECT *
        FROM AKTIVITETSKRAV
        WHERE personident = ?
        ORDER BY created_at DESC;
    """

private fun Connection.getAktivitetskrav(
    personIdent: PersonIdent,
): List<PAktivitetskrav> = prepareStatement(queryGetAktivitetskravByPersonident).use {
    it.setString(1, personIdent.value)
    it.executeQuery().toList { toPAktivitetskrav() }
}

const val queryGetAktivitetskravByUuid =
    """
        SELECT *
        FROM AKTIVITETSKRAV
        WHERE uuid = ?
    """

fun DatabaseInterface.getAktivitetskrav(
    uuid: UUID,
): PAktivitetskrav? = this.connection.use { connection ->
    connection.prepareStatement(queryGetAktivitetskravByUuid).use {
        it.setString(1, uuid.toString())
        it.executeQuery().toList { toPAktivitetskrav() }.firstOrNull()
    }
}

fun DatabaseInterface.getAktivitetskravVurderinger(
    aktivitetskravId: Int,
): List<PAktivitetskravVurdering> = this.connection.use { connection ->
    connection.prepareStatement(queryGetAktivitetskravVurderinger).use {
        it.setInt(1, aktivitetskravId)
        it.executeQuery().toList { toPAktivitetskravVurdering() }
    }
}

const val queryGetAktivitetskravVurderinger =
    """
        SELECT *
        FROM AKTIVITETSKRAV_VURDERING
        WHERE aktivitetskrav_id = ?
        ORDER BY created_at DESC
    """

private fun ResultSet.toPAktivitetskrav(): PAktivitetskrav = PAktivitetskrav(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    personIdent = PersonIdent(getString("personident")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    status = getString("status"),
    stoppunktAt = getDate("stoppunkt_at").toLocalDate(),
)

private fun ResultSet.toPAktivitetskravVurdering(): PAktivitetskravVurdering = PAktivitetskravVurdering(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    aktivitetskravId = getInt("aktivitetskrav_id"),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    createdBy = getString("created_by"),
    status = getString("status"),
    beskrivelse = getString("beskrivelse"),
    arsaker = getString("arsaker").split(",").map(String::trim).filter(String::isNotEmpty),
)
