package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdent
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class AktivitetskravRepository(private val database: DatabaseInterface) {

    fun getAktivitetskrav(uuid: UUID): PAktivitetskrav? =
        database.connection.prepareStatement(getAktivitetskravByUuidQuery).use {
            it.setString(1, uuid.toString())
            it.executeQuery()
                .toList { toPAktivitetskrav() }
                .firstOrNull()
        }

    companion object {

        // Add getting vurderinger as well in this query?
        private const val getAktivitetskravByUuidQuery =
            """
            SELECT *
            FROM AKTIVITETSKRAV
            WHERE uuid = ?
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
