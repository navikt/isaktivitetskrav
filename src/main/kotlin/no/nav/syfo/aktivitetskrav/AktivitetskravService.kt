package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.HistorikkDTO
import no.nav.syfo.aktivitetskrav.api.createHistorikkDTOs
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.updateAktivitetskrav
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.sql.Connection
import java.time.LocalDate
import java.util.*

class AktivitetskravService(
    private val aktivitetskravRepository: IAktivitetskravRepository,
    private val aktivitetskravVarselRepository: IAktivitetskravVarselRepository,
    private val varselPdfService: VarselPdfService,
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val arenaCutoff: LocalDate,
) {

    internal fun createAktivitetskrav(
        connection: Connection,
        aktivitetskrav: Aktivitetskrav,
        referanseTilfelleBitUUID: UUID,
    ) {
        aktivitetskravRepository.createAktivitetskrav(
            connection = connection,
            aktivitetskrav = aktivitetskrav,
            referanseTilfelleBitUuid = referanseTilfelleBitUUID,
        )
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = aktivitetskrav
        )
    }

    fun createAktivitetskrav(personIdent: PersonIdent, previousAktivitetskrav: Aktivitetskrav? = null): Aktivitetskrav {
        if (previousAktivitetskrav != null && !previousAktivitetskrav.isInFinalState()) {
            throw ConflictException("Forrige aktivitetskrav har ikke en avsluttende vurdering")
        }

        val aktivitetskrav = Aktivitetskrav.create(personIdent)
        val createdAktivitetskrav =
            aktivitetskravRepository.createAktivitetskrav(
                aktivitetskrav = aktivitetskrav,
                previousAktivitetskravUuid = previousAktivitetskrav?.uuid,
            ).toAktivitetskrav()
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = createdAktivitetskrav,
            previousAktivitetskravUuid = previousAktivitetskrav?.uuid,
        )

        return createdAktivitetskrav
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

    internal suspend fun vurderAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        aktivitetskravVurdering: AktivitetskravVurdering,
        document: List<DocumentComponentDTO>,
        callId: String,
    ) {
        if (aktivitetskravVurdering.status == AktivitetskravStatus.FORHANDSVARSEL) {
            throw ConflictException("Kan ikke sette FORHANDSVARSEL her, bruk aktivitetskravVarselService.sendForhandsvarsel")
        }
        val currentVurdering = aktivitetskrav.vurderinger.firstOrNull()
        if (currentVurdering?.isFinal() == true) {
            throw ConflictException("Aktivitetskravet har allerede en avsluttende vurdering")
        }
        aktivitetskravVurdering.validate()

        val updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = aktivitetskravVurdering)

        if (aktivitetskravVurdering.requiresVarselPdf()) {
            val varsel = AktivitetskravVarsel.create(
                type = aktivitetskravVurdering.status.toVarselType()!!,
                document = document
            )
            val pdf = varselPdfService.createVarselPdf(
                personIdent = aktivitetskrav.personIdent,
                varsel = varsel,
                callId = callId,
            )
            aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                aktivitetskrav = updatedAktivitetskrav,
                newVurdering = aktivitetskravVurdering,
                varsel = varsel,
                pdf = pdf,
            )
        } else {
            aktivitetskravRepository.createAktivitetskravVurdering(
                aktivitetskrav = updatedAktivitetskrav,
                aktivitetskravVurdering = aktivitetskravVurdering,
            )
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
        aktivitetskravRepository.getAktivitetskrav(personIdent = personIdent, connection = connection)
            .map { it.toAktivitetskrav() }

    fun getAktivitetskravForPersons(personidenter: List<PersonIdent>): List<Aktivitetskrav> =
        aktivitetskravRepository.getAktivitetskravForPersons(personidenter = personidenter)

    fun getAktivitetskravAfterCutoff(personIdent: PersonIdent): List<Aktivitetskrav> =
        aktivitetskravRepository.getAktivitetskrav(personIdent = personIdent)
            .map { it.toAktivitetskrav() }
            .filter { it.stoppunktAt.isAfter(arenaCutoff) }

    fun getAktivitetskravHistorikk(personIdent: PersonIdent): List<HistorikkDTO> =
        getAktivitetskravAfterCutoff(personIdent).filter {
            it.status != AktivitetskravStatus.AUTOMATISK_OPPFYLT
        }.flatMap { aktivitetskrav ->
            createHistorikkDTOs(aktivitetskrav)
        }.sortedByDescending {
            it.tidspunkt
        }

    internal fun getOutdatedAktivitetskrav(outdatedCutoff: LocalDate): List<Aktivitetskrav> =
        aktivitetskravRepository.getOutdatedAktivitetskrav(arenaCutoff, outdatedCutoff)
            .map { it.toAktivitetskrav() }

    internal fun lukkAktivitetskrav(aktivitetskrav: Aktivitetskrav) {
        val lukketAktivitetskrav = aktivitetskrav.lukk()
        aktivitetskravRepository.updateAktivitetskravStatus(lukketAktivitetskrav)
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(aktivitetskrav = lukketAktivitetskrav)
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
