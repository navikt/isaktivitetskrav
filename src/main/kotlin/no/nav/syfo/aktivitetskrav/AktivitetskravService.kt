package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.sql.Connection
import java.time.LocalDate
import java.util.*

class AktivitetskravService(
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val database: DatabaseInterface,
    private val arenaCutoff: LocalDate,
    private val pdfGenClient: PdfGenClient,
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
        database.getAktivitetskrav(uuid = uuid)?.let { pAktivitetskrav ->
            withVurderinger(pAktivitetskrav = pAktivitetskrav)
        }

    internal fun getAktivitetskravAfterCutoff(
        personIdent: PersonIdent,
        connection: Connection? = null,
    ): List<Aktivitetskrav> =
        database.getAktivitetskrav(personIdent = personIdent, connection = connection).map { pAktivitetskrav ->
            withVurderinger(pAktivitetskrav = pAktivitetskrav)
        }.filter { it.stoppunktAt.isAfter(arenaCutoff) }

    internal fun getAktivitetskrav(personIdent: PersonIdent, connection: Connection? = null): List<Aktivitetskrav> =
        database.getAktivitetskrav(personIdent = personIdent, connection = connection).map { pAktivitetskrav ->
            withVurderinger(pAktivitetskrav = pAktivitetskrav)
        }

    internal fun createAndVurderAktivitetskrav(
        personIdent: PersonIdent,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ) {
        val aktivitetskrav =
            Aktivitetskrav.fromVurdering(personIdent = personIdent, vurdering = aktivitetskravVurdering)

        database.connection.use { connection ->
            val aktivitetskravId = connection.createAktivitetskrav(
                aktivitetskrav = aktivitetskrav,
                referanseTilfelleBitUUID = null
            )
            connection.createAktivitetskravVurdering(
                aktivitetskravId = aktivitetskravId,
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
                .toAktivitetskravVurderingList()
        return pAktivitetskrav.toAktivitetskrav(aktivitetskravVurderinger = aktivitetskravVurderinger)
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

    suspend fun sendForhandsvarsel(
        personIdent: PersonIdent,
        forhandsvarselDTO: ForhandsvarselDTO,
        callId: String,
    ) {
        // Create a gyldig forhåndsvarsel?
        // forhandsvarselDTO.document can't be an empty list

        // Generate pdf
        val pdfBytes = pdfGenClient.createForhandsvarselPdf(callId, forhandsvarselDTO.document)
        // Store in DB


        // Send to journalføring (chronjob?)
        // update DB with `journalpost_id`

        // Create a source from which eSyfo team can consume from
    }
}
