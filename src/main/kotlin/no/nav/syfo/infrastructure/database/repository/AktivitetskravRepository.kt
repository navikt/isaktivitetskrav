package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.aktivitetskrav.IAktivitetskravRepository
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.nowUTC
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class AktivitetskravRepository(private val database: DatabaseInterface) : IAktivitetskravRepository {

    override fun getAktivitetskrav(uuid: UUID): PAktivitetskrav? =
        database.connection.use { connection ->
            connection.prepareStatement(GET_AKTIVITETSKRAV_BY_UUID_QUERY).use {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPAktivitetskrav() }.firstOrNull()
            }?.run {
                val vurderinger = connection.getAktivitetskravVurderinger(aktivitetskravId = id)
                copy(vurderinger = vurderinger)
            }
        }

    override fun getAktivitetskrav(
        personIdent: PersonIdent,
        connection: Connection?,
    ): List<PAktivitetskrav> = connection?.getAktivitetskrav(personIdent = personIdent)
        ?: database.connection.use {
            it.getAktivitetskrav(personIdent = personIdent)
        }

    private fun Connection.getAktivitetskrav(
        personIdent: PersonIdent,
    ): List<PAktivitetskrav> = this.prepareStatement(GET_AKTIVITETSKRAV_BY_PERSONIDENT_QUERY).use {
        it.setString(1, personIdent.value)
        it.executeQuery().toList { toPAktivitetskrav() }
    }.map {
        val vurderinger = this.getAktivitetskravVurderinger(aktivitetskravId = it.id)
        it.copy(vurderinger = vurderinger)
    }

    override fun getOutdatedAktivitetskrav(
        arenaCutoff: LocalDate,
        outdatedCutoff: LocalDate
    ): List<PAktivitetskrav> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_AKTIVITETSKRAV_OUTDATED).use {
                it.setObject(1, arenaCutoff)
                it.setObject(2, outdatedCutoff)
                it.setObject(3, outdatedCutoff)
                it.executeQuery().toList { toPAktivitetskrav() }
            }
        }

    override fun createAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        previousAktivitetskravUuid: UUID?,
        referanseTilfelleBitUuid: UUID?,
        connection: Connection?,
    ): PAktivitetskrav {
        return connection?.createAktivitetskrav(
            aktivitetskrav = aktivitetskrav,
            previousAktivitetskravUuid = previousAktivitetskravUuid,
            referanseTilfelleBitUuid = referanseTilfelleBitUuid,
        )
            ?: database.connection.use {
                val created = it.createAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    previousAktivitetskravUuid = previousAktivitetskravUuid,
                    referanseTilfelleBitUuid = referanseTilfelleBitUuid,
                )
                it.commit()

                return created
            }
    }

    override fun createAktivitetskravVurdering(
        aktivitetskrav: Aktivitetskrav,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ): PAktivitetskravVurdering {
        database.connection.use { connection ->
            val aktivitetskravId = connection.updateAktivitetskrav(aktivitetskrav = aktivitetskrav)
            val created = connection.createAktivitetskravVurdering(
                aktivitetskravId = aktivitetskravId,
                aktivitetskravVurdering = aktivitetskravVurdering
            )
            connection.commit()
            return created
        }
    }

    override fun updateAktivitetskravStatus(
        aktivitetskrav: Aktivitetskrav
    ): PAktivitetskrav {
        val updatedAktivitetskravRecords = database.connection.use { connection ->
            val aktivitetskravRecords =
                connection.prepareStatement(UPDATE_AKTIVITETSKRAV_STATUS).use { preparedStatement ->
                    preparedStatement.setString(1, aktivitetskrav.status.name)
                    preparedStatement.setDate(2, Date.valueOf(aktivitetskrav.stoppunktAt))
                    preparedStatement.setObject(3, nowUTC())
                    preparedStatement.setString(4, aktivitetskrav.uuid.toString())
                    preparedStatement.executeQuery().toList { toPAktivitetskrav() }
                }
            if (aktivitetskravRecords.size != 1) {
                throw SQLException("Failed to update aktivitetskrav with uuid ${aktivitetskrav.uuid} - Unexpected update count: ${aktivitetskravRecords.size}")
            }
            connection.commit()
            aktivitetskravRecords
        }
        return updatedAktivitetskravRecords.first()
    }

    override fun updateAktivitetskravPersonIdent(
        nyPersonIdent: PersonIdent,
        inactiveIdenter: List<PersonIdent>,
    ): Int {
        var updatedRows = 0
        database.connection.use { connection ->
            connection.prepareStatement(UPDATE_AKTIVITETSKRAV_PERSONIDENT).use {
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

    private fun Connection.createAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        previousAktivitetskravUuid: UUID? = null,
        referanseTilfelleBitUuid: UUID? = null,
    ): PAktivitetskrav {
        val createdRecords = prepareStatement(CREATE_AKTIVITETSKRAV).use {
            it.setString(1, aktivitetskrav.uuid.toString())
            it.setObject(2, aktivitetskrav.createdAt)
            it.setObject(3, aktivitetskrav.createdAt)
            it.setString(4, aktivitetskrav.personIdent.value)
            it.setString(5, aktivitetskrav.status.name)
            it.setDate(6, Date.valueOf(aktivitetskrav.stoppunktAt))
            it.setObject(7, referanseTilfelleBitUuid)
            it.setObject(8, previousAktivitetskravUuid)
            it.executeQuery().toList { toPAktivitetskrav() }
        }
        if (createdRecords.size != 1) {
            throw NoElementInsertedException("Creating AKTIVITETSKRAV failed, no rows affected.")
        }

        return createdRecords.first()
    }

    private fun Connection.getAktivitetskravVurderinger(
        aktivitetskravId: Int
    ): List<PAktivitetskravVurdering> =
        prepareStatement(GET_AKTIVITETSKRAV_VURDERINGER_QUERY).use {
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

        private const val GET_AKTIVITETSKRAV_VURDERINGER_QUERY =
            """
            SELECT *
            FROM AKTIVITETSKRAV_VURDERING
            WHERE aktivitetskrav_id = ?
            ORDER BY created_at DESC
            """

        private const val GET_AKTIVITETSKRAV_OUTDATED =
            """
            SELECT * 
            FROM AKTIVITETSKRAV
            WHERE stoppunkt_at > ?
                AND stoppunkt_at < ?
                AND status = 'NY'
                AND personident NOT IN (SELECT personident FROM AKTIVITETSKRAV WHERE stoppunkt_at >= ?);
            """

        private const val CREATE_AKTIVITETSKRAV =
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
                previous_aktivitetskrav_uuid
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

        const val UPDATE_AKTIVITETSKRAV_PERSONIDENT =
            """
            UPDATE AKTIVITETSKRAV
            SET personident = ?, updated_at=?
            WHERE personident = ?
            """

        private const val UPDATE_AKTIVITETSKRAV_STATUS =
            """
            UPDATE AKTIVITETSKRAV 
            SET status=?, stoppunkt_at=?, updated_at=? WHERE uuid = ? 
            RETURNING *
            """
    }
}

private fun ResultSet.toPAktivitetskrav(): PAktivitetskrav = PAktivitetskrav(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    personIdent = PersonIdent(getString("personident")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    status = AktivitetskravStatus.valueOf(getString("status")),
    stoppunktAt = getDate("stoppunkt_at").toLocalDate(),
    referanseTilfelleBitUuid = getString("referanse_tilfelle_bit_uuid")?.let { UUID.fromString(it) },
    previousAktivitetskravUuid = getObject("previous_aktivitetskrav_uuid", UUID::class.java),
)
