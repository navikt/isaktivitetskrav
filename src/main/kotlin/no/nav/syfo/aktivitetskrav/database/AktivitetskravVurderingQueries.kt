package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.application.database.*
import no.nav.syfo.domain.PersonIdent
import java.sql.*
import java.sql.Date
import java.time.OffsetDateTime
import java.util.*

const val queryCreateAktivitetskravVurdering =
    """
    INSERT INTO AKTIVITETSKRAV_VURDERING (
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

fun Connection.createAktivitetskravVurdering(
    aktivitetskravVurdering: AktivitetskravVurdering,
) {
    val idList = this.prepareStatement(queryCreateAktivitetskravVurdering).use {
        it.setString(1, aktivitetskravVurdering.uuid.toString())
        it.setObject(2, aktivitetskravVurdering.createdAt)
        it.setObject(3, aktivitetskravVurdering.sistEndret)
        it.setString(4, aktivitetskravVurdering.personIdent.value)
        it.setString(5, aktivitetskravVurdering.status.name)
        it.setString(6, aktivitetskravVurdering.beskrivelse)
        it.setDate(7, Date.valueOf(aktivitetskravVurdering.stoppunktAt))
        it.setString(8, aktivitetskravVurdering.updatedBy)
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VURDERING failed, no rows affected.")
    }
}

const val queryUpdateAktivitetskravVurdering =
    """
        UPDATE AKTIVITETSKRAV_VURDERING SET status=?, stoppunkt_at=?, updated_at=?, beskrivelse=?, updated_by=? WHERE uuid = ?
    """

fun Connection.updateAktivitetskravVurdering(
    aktivitetskravVurdering: AktivitetskravVurdering,
) {
    val vurderingUuid = aktivitetskravVurdering.uuid
    this.prepareStatement(queryUpdateAktivitetskravVurdering).use { preparedStatement ->
        preparedStatement.setString(1, aktivitetskravVurdering.status.name)
        preparedStatement.setDate(2, Date.valueOf(aktivitetskravVurdering.stoppunktAt))
        preparedStatement.setObject(3, aktivitetskravVurdering.sistEndret)
        preparedStatement.setString(4, aktivitetskravVurdering.beskrivelse)
        preparedStatement.setString(5, aktivitetskravVurdering.updatedBy)
        preparedStatement.setString(6, vurderingUuid.toString())
        preparedStatement.executeUpdate().also { updateCount ->
            if (updateCount != 1) {
                throw SQLException("Failed to update aktivitetskrav-vurdering with uuid $vurderingUuid - Unexpected update count: $updateCount")
            }
        }
    }
}

const val queryGetAktivitetskravVurderinger =
    """
        SELECT *
        FROM AKTIVITETSKRAV_VURDERING
        WHERE personident = ?
        ORDER BY created_at DESC;
    """

fun Connection.getAktivitetskravVurderinger(
    personIdent: PersonIdent,
): List<PAktivitetskravVurdering> = prepareStatement(queryGetAktivitetskravVurderinger).use {
    it.setString(1, personIdent.value)
    it.executeQuery().toList { toPAktivitetskravVurdering() }
}

fun DatabaseInterface.getAktivitetskravVurderinger(
    personIdent: PersonIdent,
): List<PAktivitetskravVurdering> = this.connection.use {
    it.getAktivitetskravVurderinger(
        personIdent = personIdent
    )
}

const val queryGetAktivitetskravVurdering =
    """
        SELECT *
        FROM AKTIVITETSKRAV_VURDERING
        WHERE uuid = ?
    """

fun DatabaseInterface.getAktivitetskravVurdering(
    uuid: UUID,
): PAktivitetskravVurdering? = this.connection.use { connection ->
    connection.prepareStatement(queryGetAktivitetskravVurdering).use {
        it.setString(1, uuid.toString())
        it.executeQuery().toList { toPAktivitetskravVurdering() }.firstOrNull()
    }
}

private fun ResultSet.toPAktivitetskravVurdering(): PAktivitetskravVurdering = PAktivitetskravVurdering(
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
