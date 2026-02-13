package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IAktivitetskravRepository
import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.NoElementInsertedException
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
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

    override fun getAktivitetskrav(personIdent: PersonIdent): List<PAktivitetskrav> =
        database.connection.use { connection ->
            connection.getAktivitetskrav(personIdent = personIdent)
        }

    override fun getAktivitetskravForPersons(personidenter: List<PersonIdent>): List<Aktivitetskrav> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_AKTIVITETSKRAV_FOR_PERSONS_QUERY).use {
                it.setString(1, personidenter.joinToString(transform = { it.value }, separator = ","))
                it.executeQuery().toAktivitetskravWithVurderinger()
            }.map { it.toAktivitetskrav() }
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
        outdatedCutoff: LocalDate,
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
    ): PAktivitetskrav {
        return database.connection.use {
            val created = it.createAktivitetskrav(
                aktivitetskrav = aktivitetskrav,
                previousAktivitetskravUuid = previousAktivitetskravUuid,
                referanseTilfelleBitUuid = referanseTilfelleBitUuid,
            )
            it.commit()
            created
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

    override fun updateAktivitetskravStatus(aktivitetskrav: Aktivitetskrav): PAktivitetskrav {
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
        aktivitetskravId: Int,
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

        private const val GET_AKTIVITETSKRAV_FOR_PERSONS_QUERY =
            """
            SELECT aktivitetskrav.id AS aktivitetskrav_id, aktivitetskrav.uuid AS aktivitetskrav_uuid,
                aktivitetskrav.created_at AS aktivitetskrav_created_at, aktivitetskrav.updated_at AS aktivitetskrav_updated_at,
                aktivitetskrav.personident AS aktivitetskrav_personident, aktivitetskrav.status AS aktivitetskrav_status,
                aktivitetskrav.stoppunkt_at AS aktivitetskrav_stoppunkt_at,
                aktivitetskrav.referanse_tilfelle_bit_uuid AS aktivitetskrav_referanse_tilfelle_bit_uuid,
                aktivitetskrav.previous_aktivitetskrav_uuid AS aktivitetskrav_previous_aktivitetskrav_uuid,
                vurdering.id AS vurdering_id, vurdering.uuid AS vurdering_uuid, vurdering.aktivitetskrav_id AS vurdering_aktivitetskrav_id,
                vurdering.created_at AS vurdering_created_at, vurdering.created_by AS vurdering_created_by,
                vurdering.status AS vurdering_status, vurdering.beskrivelse AS vurdering_beskrivelse,
                vurdering.arsaker AS vurdering_arsaker, vurdering.stans_fom AS vurdering_stans_fom, vurdering.frist AS vurdering_frist,
                varsel.id AS varsel_id, varsel.uuid AS varsel_uuid, varsel.created_at AS varsel_created_at,
                varsel.updated_at AS varsel_updated_at, varsel.aktivitetskrav_vurdering_id AS varsel_aktivitetskrav_vurdering_id,
                varsel.journalpost_id AS varsel_journalpost_id, varsel.document AS varsel_document,
                varsel.published_at AS varsel_published_at, varsel.svarfrist AS varsel_svarfrist,
                varsel.type AS varsel_type
            FROM AKTIVITETSKRAV aktivitetskrav
                LEFT JOIN AKTIVITETSKRAV_VURDERING vurdering ON vurdering.aktivitetskrav_id = aktivitetskrav.id
                    LEFT JOIN AKTIVITETSKRAV_VARSEL varsel ON varsel.aktivitetskrav_vurdering_id = vurdering.id
            WHERE aktivitetskrav.personident = ANY (string_to_array(?, ','))
            ORDER BY aktivitetskrav.created_at DESC, vurdering.created_at DESC
            """

        private const val GET_AKTIVITETSKRAV_OUTDATED =
            """
            SELECT * 
            FROM AKTIVITETSKRAV
            WHERE stoppunkt_at > ?
                AND stoppunkt_at < ?
                AND status = 'NY'
                AND personident NOT IN (SELECT personident FROM AKTIVITETSKRAV WHERE stoppunkt_at >= ?)
                LIMIT 500;
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
            .map { PArsak.valueOf(it) },
        stansFom = getDate("stans_fom")?.toLocalDate(),
        frist = getDate("frist")?.toLocalDate(),
        varsel = null,
    )
}

private fun ResultSet.toAktivitetskravWithVurderinger(): List<PAktivitetskrav> {
    val aktivitetskravMap = mutableMapOf<Int, PAktivitetskrav>()
    while (this.next()) {
        val varselUuid = getString("varsel_uuid")
        val varsel = varselUuid?.let {
            PAktivitetskravVarsel(
                id = getInt("varsel_id"),
                uuid = UUID.fromString(getString("varsel_uuid")),
                createdAt = getObject("varsel_created_at", OffsetDateTime::class.java),
                updatedAt = getObject("varsel_updated_at", OffsetDateTime::class.java),
                aktivitetskravVurderingId = getInt("varsel_aktivitetskrav_vurdering_id"),
                journalpostId = getString("varsel_journalpost_id"),
                document = configuredJacksonMapper().readValue(
                    getString("varsel_document"),
                    object : TypeReference<List<DocumentComponentDTO>>() {}
                ),
                publishedAt = getObject("varsel_published_at", OffsetDateTime::class.java),
                svarfrist = getDate("varsel_svarfrist")?.toLocalDate(),
                type = getString("varsel_type"),
            )
        }
        val vurderingUuid = getString("vurdering_uuid")
        val vurdering =
            vurderingUuid?.let {
                val vurderingStatus = AktivitetskravStatus.valueOf(getString("vurdering_status"))
                PAktivitetskravVurdering(
                    id = getInt("vurdering_id"),
                    uuid = UUID.fromString(vurderingUuid),
                    aktivitetskravId = getInt("vurdering_aktivitetskrav_id"),
                    createdAt = getObject("vurdering_created_at", OffsetDateTime::class.java),
                    createdBy = getString("vurdering_created_by"),
                    status = vurderingStatus,
                    beskrivelse = getString("vurdering_beskrivelse"),
                    arsaker = getString("vurdering_arsaker").split(",").map(String::trim).filter(String::isNotEmpty)
                        .map { PArsak.valueOf(it) },
                    stansFom = getDate("vurdering_stans_fom")?.toLocalDate(),
                    frist = getDate("vurdering_frist")?.toLocalDate(),
                    varsel = varsel,
                )
            }

        val aktivitetskravId = getInt("aktivitetskrav_id")
        if (aktivitetskravMap.containsKey(aktivitetskravId)) {
            vurdering?.let { aktivitetskravMap.updateExistingWithNewVurdering(aktivitetskravId, it) }
        } else {
            val newAktivitetskravEntry =
                PAktivitetskrav(
                    id = aktivitetskravId,
                    uuid = UUID.fromString(getString("aktivitetskrav_uuid")),
                    personIdent = PersonIdent(getString("aktivitetskrav_personident")),
                    createdAt = getObject("aktivitetskrav_created_at", OffsetDateTime::class.java),
                    updatedAt = getObject("aktivitetskrav_updated_at", OffsetDateTime::class.java),
                    status = AktivitetskravStatus.valueOf(getString("aktivitetskrav_status")),
                    stoppunktAt = getDate("aktivitetskrav_stoppunkt_at").toLocalDate(),
                    referanseTilfelleBitUuid = getString("aktivitetskrav_referanse_tilfelle_bit_uuid")?.let { UUID.fromString(it) },
                    previousAktivitetskravUuid = getObject("aktivitetskrav_previous_aktivitetskrav_uuid", UUID::class.java),
                    vurderinger = listOfNotNull(vurdering),
                )
            aktivitetskravMap.put(aktivitetskravId, newAktivitetskravEntry)
        }
    }
    return aktivitetskravMap.values.toList()
}

private fun MutableMap<Int, PAktivitetskrav>.updateExistingWithNewVurdering(keyToUpdate: Int, newVurdering: PAktivitetskravVurdering) {
    this[keyToUpdate]?.let { value ->
        this.put(keyToUpdate, value.copy(vurderinger = value.vurderinger + listOf(newVurdering)))
    }
}
