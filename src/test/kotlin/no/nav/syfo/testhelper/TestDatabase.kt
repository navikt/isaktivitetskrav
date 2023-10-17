package no.nav.syfo.testhelper

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdent
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

private const val queryGetAktivitetskravVarselPdf =
    """
        SELECT *
        FROM AKTIVITETSKRAV_VARSEL_PDF
        WHERE aktivitetskrav_varsel_id = ?
    """

fun TestDatabase.getAktivitetskravVarselPdf(
    aktivitetskravVarselId: Int,
): PAktivitetskravVarselPdf? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetAktivitetskravVarselPdf).use {
            it.setInt(1, aktivitetskravVarselId)
            it.executeQuery()
                .toList { toPAktivitetskravVarselPdf() }
                .firstOrNull()
        }
    }

private fun ResultSet.toPAktivitetskravVarselPdf(): PAktivitetskravVarselPdf =
    PAktivitetskravVarselPdf(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        aktivitetskravVarselId = getInt("aktivitetskrav_varsel_id"),
        pdf = getBytes("pdf"),
    )

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        """
        DELETE FROM AKTIVITETSKRAV
        """.trimIndent(),
        """
        DELETE FROM AKTIVITETSKRAV_VURDERING
        """.trimIndent(),
        """
        DELETE FROM AKTIVITETSKRAV_VARSEL
        """.trimIndent(),
        """
        DELETE FROM AKTIVITETSKRAV_VARSEL_PDF
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.createAktivitetskrav(vararg aktivitetskrav: Aktivitetskrav): List<PAktivitetskrav> =
    this.connection.use { connection ->
        val createdAktivitetskrav = aktivitetskrav.map {
            connection.createAktivitetskrav(aktivitetskrav = it, referanseTilfelleBitUUID = UUID.randomUUID())
        }
        connection.commit()
        createdAktivitetskrav
    }

const val queryGetVarslerForPerson = """
    SELECT av.* 
    FROM aktivitetskrav_varsel av 
    INNER JOIN aktivitetskrav_vurdering avu
    ON av.aktivitetskrav_vurdering_id = avu.id
    INNER JOIN aktivitetskrav a
    ON avu.aktivitetskrav_id = a.id
    WHERE a.personident = ?
"""

fun DatabaseInterface.getVarsler(personIdent: PersonIdent): List<PAktivitetskravVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetVarslerForPerson).use {
            it.setString(1, personIdent.value)
            it.executeQuery().toList { toPAktivitetskravVarsel() }
        }
    }
}

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")
}
