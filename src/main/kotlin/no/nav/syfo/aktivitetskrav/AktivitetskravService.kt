package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.sql.Connection
import java.time.LocalDate
import java.util.*

class AktivitetskravService(
    private val aktivitetskravRepository: AktivitetskravRepository,
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val database: DatabaseInterface,
    private val arenaCutoff: LocalDate,
) {

    internal fun createAktivitetskrav(
        connection: Connection,
        aktivitetskrav: Aktivitetskrav,
        referanseTilfelleBitUUID: UUID,
    ) {
        connection.createAktivitetskrav(
            aktivitetskrav = aktivitetskrav,
            referanseTilfelleBitUUID = referanseTilfelleBitUUID
        )
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = aktivitetskrav
        )
    }

    fun createAktivitetskrav(personIdent: PersonIdent, previousAktivitetskrav: UUID?): PAktivitetskrav {
        val newAktivitetskrav = Aktivitetskrav.create(personIdent)
        return aktivitetskravRepository.createAktivitetskrav(newAktivitetskrav, previousAktivitetskrav)
    }

    internal fun updateAktivitetskravStoppunkt(
        connection: Connection,
        aktivitetskrav: Aktivitetskrav,
        oppfolgingstilfelle: Oppfolgingstilfelle,
    ) {
        val updatedAktivitetskrav = aktivitetskrav.updateStoppunkt(
            oppfolgingstilfelle = oppfolgingstilfelle,
        )

        updateAktivitetskrav(connection, updatedAktivitetskrav)
    }

    internal fun vurderAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ) {
        val updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = aktivitetskravVurdering)

        database.connection.use { connection ->
            val aktivitetskravId = connection.updateAktivitetskrav(aktivitetskrav = updatedAktivitetskrav)
            connection.createAktivitetskravVurdering(
                aktivitetskravId = aktivitetskravId,
                aktivitetskravVurdering = aktivitetskravVurdering
            )
            connection.commit()
        }
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = updatedAktivitetskrav
        )
    }

    internal fun oppfyllAutomatisk(connection: Connection, aktivitetskrav: Aktivitetskrav) {
        val updatedAktivitetskrav = aktivitetskrav.oppfyllAutomatisk()

        updateAktivitetskrav(connection, updatedAktivitetskrav)
    }

    internal fun getAktivitetskrav(uuid: UUID): Aktivitetskrav? =
        aktivitetskravRepository.getAktivitetskrav(uuid)
            ?.toAktivitetskrav()

    internal fun getAktivitetskrav(personIdent: PersonIdent, connection: Connection? = null): List<Aktivitetskrav> =
        database.getAktivitetskrav(personIdent = personIdent, connection = connection).map { pAktivitetskrav ->
            withVurderinger(pAktivitetskrav = pAktivitetskrav)
        }

    fun getAktivitetskravAfterCutoff(personIdent: PersonIdent): List<Aktivitetskrav> =
        aktivitetskravRepository.getAktivitetskrav(personIdent = personIdent)
            .map { it.toAktivitetskrav() }
            .filter { it.stoppunktAt.isAfter(arenaCutoff) }

    internal fun getOutdatedAktivitetskrav(outdatedCutoff: LocalDate): List<Aktivitetskrav> {
        return database.getOutdatedAktivitetskrav(
            arenaCutoff = arenaCutoff,
            outdatedCutoff = outdatedCutoff
        ).map { it.toAktivitetskrav() }
    }

    internal fun lukk(aktivitetskrav: Aktivitetskrav) {
        val updatedAktivitetskrav = aktivitetskrav.copy(
            status = AktivitetskravStatus.LUKKET
        )
        database.connection.use { connection ->
            connection.updateAktivitetskrav(
                aktivitetskrav = updatedAktivitetskrav
            )
            connection.commit()
        }
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(aktivitetskrav = updatedAktivitetskrav)
    }

    internal fun createAndVurderAktivitetskrav(
        personIdent: PersonIdent,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ) {
        val aktivitetskrav =
            Aktivitetskrav.fromVurdering(personIdent = personIdent, vurdering = aktivitetskravVurdering)

        database.connection.use { connection ->
            val pAktivitetskrav = connection.createAktivitetskrav(
                aktivitetskrav = aktivitetskrav,
                referanseTilfelleBitUUID = null
            )
            connection.createAktivitetskravVurdering(
                aktivitetskravId = pAktivitetskrav.id,
                aktivitetskravVurdering = aktivitetskravVurdering
            )
            connection.commit()
        }
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = aktivitetskrav,
        )
    }

    private fun withVurderinger(pAktivitetskrav: PAktivitetskrav): Aktivitetskrav {
        val aktivitetskravVurderinger =
            database.getAktivitetskravVurderinger(aktivitetskravId = pAktivitetskrav.id)
                .map { it.toAktivitetskravVurdering() }
        return pAktivitetskrav.toAktivitetskrav(vurderinger = aktivitetskravVurderinger)
    }

    internal fun updateAktivitetskrav(
        connection: Connection,
        updatedAktivitetskrav: Aktivitetskrav,
    ) {
        connection.updateAktivitetskrav(
            aktivitetskrav = updatedAktivitetskrav
        )
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = updatedAktivitetskrav
        )
    }
}
