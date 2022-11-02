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
        tilfelle_start
    ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?)
    RETURNING id
    """

fun Connection.createAktivitetskravVurdering(
    aktivitetskravVurdering: AktivitetskravVurdering,
) {
    val idList = this.prepareStatement(queryCreateAktivitetskravVurdering).use {
        it.setString(1, aktivitetskravVurdering.uuid.toString())
        it.setObject(2, aktivitetskravVurdering.createdAt)
        it.setObject(3, aktivitetskravVurdering.updatedAt)
        it.setString(4, aktivitetskravVurdering.personIdent.value)
        it.setString(5, aktivitetskravVurdering.status.name)
        it.setString(6, aktivitetskravVurdering.beskrivelse)
        it.setDate(7, Date.valueOf(aktivitetskravVurdering.tilfelleStart))
        it.executeQuery().toList { getInt("id") }
    }

    if (idList.size != 1) {
        throw NoElementInsertedException("Creating AKTIVITETSKRAV_VURDERING failed, no rows affected.")
    }
}

const val queryGetAktivitetskravVurdering =
    """
        SELECT *
        FROM AKTIVITETSKRAV_VURDERING
        WHERE personident = ?
    """

fun DatabaseInterface.getAktivitetskravVurderinger(
    personIdent: PersonIdent
): List<PAktivitetskravVurdering> = this.connection.use { connection ->
    connection.prepareStatement(queryGetAktivitetskravVurdering).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPAktivitetskravVurdering() }
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
    tilfelleStart = getDate("tilfelle_start").toLocalDate()
)
