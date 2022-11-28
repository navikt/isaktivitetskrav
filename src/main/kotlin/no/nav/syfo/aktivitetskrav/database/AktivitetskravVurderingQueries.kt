package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.application.database.*
import no.nav.syfo.domain.PersonIdent
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
        beskrivelse,
        stoppunkt_at,
        updated_by
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createAktivitetskrav(
    aktivitetskrav: Aktivitetskrav,
) {
    val idList = this.prepareStatement(queryCreateAktivitetskrav).use {
        it.setString(1, aktivitetskrav.uuid.toString())
        it.setObject(2, aktivitetskrav.createdAt)
        it.setObject(3, aktivitetskrav.sistEndret)
        it.setString(4, aktivitetskrav.personIdent.value)
        it.setString(5, aktivitetskrav.status.name)
        it.setString(6, aktivitetskrav.beskrivelse)
        it.setDate(7, Date.valueOf(aktivitetskrav.stoppunktAt))
        it.setString(8, aktivitetskrav.updatedBy)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV failed, no rows affected.")
    }
}

const val queryUpdateAktivitetskrav =
    """
        UPDATE AKTIVITETSKRAV SET status=?, stoppunkt_at=?, updated_at=?, beskrivelse=?, updated_by=? WHERE uuid = ?
    """

fun Connection.updateAktivitetskrav(
    aktivitetskrav: Aktivitetskrav,
) {
    val aktivitetskravUuid = aktivitetskrav.uuid
    this.prepareStatement(queryUpdateAktivitetskrav).use { preparedStatement ->
        preparedStatement.setString(1, aktivitetskrav.status.name)
        preparedStatement.setDate(2, Date.valueOf(aktivitetskrav.stoppunktAt))
        preparedStatement.setObject(3, aktivitetskrav.sistEndret)
        preparedStatement.setString(4, aktivitetskrav.beskrivelse)
        preparedStatement.setString(5, aktivitetskrav.updatedBy)
        preparedStatement.setString(6, aktivitetskravUuid.toString())
        preparedStatement.executeUpdate().also { updateCount ->
            if (updateCount != 1) {
                throw SQLException("Failed to update AKTIVITETSKRAV with uuid $aktivitetskravUuid - Unexpected update count: $updateCount")
            }
        }
    }
}

const val queryGetAktivitetskravByPersonident =
    """
        SELECT *
        FROM AKTIVITETSKRAV
        WHERE personident = ?
        ORDER BY created_at DESC;
    """

fun Connection.getAktivitetskrav(
    personIdent: PersonIdent,
): List<PAktivitetskrav> = prepareStatement(queryGetAktivitetskravByPersonident).use {
    it.setString(1, personIdent.value)
    it.executeQuery().toList { toPAktivitetskrav() }
}

fun DatabaseInterface.getAktivitetskrav(
    personIdent: PersonIdent,
): List<PAktivitetskrav> = this.connection.use {
    it.getAktivitetskrav(
        personIdent = personIdent
    )
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

private fun ResultSet.toPAktivitetskrav(): PAktivitetskrav = PAktivitetskrav(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    personIdent = PersonIdent(getString("personident")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    status = getString("status"),
    beskrivelse = getString("beskrivelse"),
    stoppunktAt = getDate("stoppunkt_at").toLocalDate(),
    updatedBy = getString("updated_by"),
)
