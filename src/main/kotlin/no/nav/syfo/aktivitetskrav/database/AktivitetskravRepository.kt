package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.NoElementInsertedException
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdent
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class AktivitetskravRepository(private val database: DatabaseInterface) {

    fun getAktivitetskrav(uuid: UUID): PAktivitetskrav? =
        database.connection.use { connection ->
            connection.prepareStatement(GET_AKTIVITETSKRAV_BY_UUID_QUERY).use {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPAktivitetskrav() }.firstOrNull()
            }?.run {
                val vurderinger = connection.getAktivitetskravVurderinger(aktivitetskravId = id)
                copy(vurderinger = vurderinger)
            }
        }

    fun getAktivitetskrav(
        personIdent: PersonIdent,
    ): List<PAktivitetskrav> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_AKTIVITETSKRAV_BY_PERSONIDENT_QUERY).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPAktivitetskrav() }
            }.map {
                val vurderinger = connection.getAktivitetskravVurderinger(aktivitetskravId = it.id)
                it.copy(vurderinger = vurderinger)
            }
        }

    fun createAktivitetskrav(
        newAktivitetskrav: Aktivitetskrav,
        previousAktivitetskravUuid: UUID?,
    ): PAktivitetskrav {
        val createdRecord = database.connection.use { connection ->
            connection.prepareStatement(CREATE_AKTIVITETSKRAV_NY_VURDERING).use {
                it.setString(1, newAktivitetskrav.uuid.toString())
                it.setObject(2, newAktivitetskrav.createdAt)
                it.setObject(3, newAktivitetskrav.createdAt)
                it.setString(4, newAktivitetskrav.personIdent.value)
                it.setString(5, newAktivitetskrav.status.name)
                it.setDate(6, Date.valueOf(newAktivitetskrav.stoppunktAt))
                it.setString(7, null)
                it.setString(8, previousAktivitetskravUuid.toString())
                it.executeQuery().toList { toPAktivitetskrav() }
            }
        }
        if (createdRecord.size != 1) {
            throw NoElementInsertedException("Creating AKTIVITETSKRAV failed, no rows affected.")
        }
        return createdRecord.first()
    }

    private fun Connection.getAktivitetskravVurderinger(
        aktivitetskravId: Int
    ): List<PAktivitetskravVurdering> =
        prepareStatement(GET_AKTIVIETSKRAV_VURDERINGER_QUERY).use {
            it.setInt(1, aktivitetskravId)
            it.executeQuery().toList { toPAktivitetskravVurdering() }
        }

    companion object {

        private const val GET_AKTIVITETSKRAV_BY_UUID_QUERY =
            """
            SELECT *
            FROM AKTIVITETSKRAV
            WHERE uuid = ?
            """

        private const val GET_AKTIVIETSKRAV_VURDERINGER_QUERY =
            """
            SELECT *
            FROM AKTIVITETSKRAV_VURDERING
            WHERE aktivitetskrav_id = ?
            ORDER BY created_at DESC
            """

        private const val CREATE_AKTIVITETSKRAV_NY_VURDERING =
            """
            INSERT INTO AKTIVITETSKRAV (
                id,
                uuid,
                created_at,
                updated_at,
                personident,
                status,
                stoppunkt_at,
                referanse_tilfelle_bit_uuid,
                previous_aktivitetskrav_uuid,
            ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """

        private const val GET_AKTIVITETSKRAV_BY_PERSONIDENT_QUERY =
            """
            SELECT *
            FROM AKTIVITETSKRAV
            WHERE personident = ?
            ORDER BY created_at DESC;
            """
    }
}

private fun ResultSet.toPAktivitetskrav(): PAktivitetskrav = PAktivitetskrav(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    personIdent = PersonIdent(getString("personident")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    status = getString("status"),
    stoppunktAt = getDate("stoppunkt_at").toLocalDate(),
    referanseTilfelleBitUuid = getString("referanse_tilfelle_bit_uuid")?.let { UUID.fromString(it) },
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
    frist = getDate("frist")?.toLocalDate(),
)
