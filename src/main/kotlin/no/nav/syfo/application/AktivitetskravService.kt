package no.nav.syfo.application

import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.api.dto.HistorikkDTO
import no.nav.syfo.api.dto.createHistorikkDTOs
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.isInFinalState
import no.nav.syfo.domain.oppfyllAutomatisk
import no.nav.syfo.domain.updateStoppunkt
import no.nav.syfo.domain.Oppfolgingstilfelle
import java.time.LocalDate
import java.util.*

class AktivitetskravService(
    private val aktivitetskravRepository: IAktivitetskravRepository,
    private val aktivitetskravVarselRepository: IAktivitetskravVarselRepository,
    private val varselPdfService: VarselPdfService,
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val arenaCutoff: LocalDate,
) {

    internal fun createAktivitetskrav(oppfolgingstilfelle: Oppfolgingstilfelle) {
        val aktivitetskrav = Aktivitetskrav.create(
            personIdent = oppfolgingstilfelle.personIdent,
            oppfolgingstilfelleStart = oppfolgingstilfelle.tilfelleStart,
            isAutomatiskOppfylt = oppfolgingstilfelle.isGradertAtTilfelleEnd(),
        )
        aktivitetskravRepository.createAktivitetskrav(
            aktivitetskrav = aktivitetskrav,
            referanseTilfelleBitUuid = oppfolgingstilfelle.referanseTilfelleBitUuid,
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
        aktivitetskrav: Aktivitetskrav,
        oppfolgingstilfelle: Oppfolgingstilfelle,
    ) {
        val updatedAktivitetskrav = aktivitetskrav.updateStoppunkt(
            oppfolgingstilfelle = oppfolgingstilfelle,
        )
        updateAktivitetskrav(updatedAktivitetskrav)
    }

    internal suspend fun vurderAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        aktivitetskravVurdering: AktivitetskravVurdering,
        document: List<DocumentComponentDTO>,
        callId: String,
    ) {
        if (aktivitetskravVurdering is AktivitetskravVurdering.Forhandsvarsel) {
            throw ConflictException("Kan ikke sette FORHANDSVARSEL her, bruk aktivitetskravVarselService.sendForhandsvarsel")
        }
        val currentVurdering = aktivitetskrav.vurderinger.firstOrNull()
        if (currentVurdering?.isFinal == true) {
            throw ConflictException("Aktivitetskravet har allerede en avsluttende vurdering")
        }

        val updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = aktivitetskravVurdering)

        if (aktivitetskravVurdering.requiresPdfDocument()) {
            val varsel = AktivitetskravVarsel.create(
                type = aktivitetskravVurdering.toVarselType(),
                document = document,
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

    internal fun oppfyllAutomatisk(aktivitetskrav: Aktivitetskrav) {
        val updatedAktivitetskrav = aktivitetskrav.oppfyllAutomatisk()
        updateAktivitetskrav(updatedAktivitetskrav)
    }

    internal fun getAktivitetskrav(uuid: UUID): Aktivitetskrav? =
        aktivitetskravRepository.getAktivitetskrav(uuid)
            ?.toAktivitetskrav()

    internal fun getAktivitetskrav(personIdent: PersonIdent): List<Aktivitetskrav> =
        aktivitetskravRepository.getAktivitetskrav(personIdent = personIdent)
            .map { it.toAktivitetskrav() }

    fun getAktivitetskravForPersons(personidenter: List<PersonIdent>): Map<PersonIdent, Aktivitetskrav> =
        aktivitetskravRepository.getAktivitetskravForPersons(personidenter = personidenter)
            .mapMostRecentAktivitetskrav()

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

    internal fun updateAktivitetskrav(updatedAktivitetskrav: Aktivitetskrav) {
        aktivitetskravRepository.updateAktivitetskravStatus(aktivitetskrav = updatedAktivitetskrav)
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = updatedAktivitetskrav
        )
    }
}

private fun List<Aktivitetskrav>.mapMostRecentAktivitetskrav() =
    this.groupBy { it.personIdent }
        .mapValues { it.value.maxBy { it.createdAt }.mapMostRecentVurdering() }

private fun Aktivitetskrav.mapMostRecentVurdering() =
    this.copy(
        vurderinger = this.vurderinger.maxByOrNull { it.createdAt }
            ?.let { listOf(it) } ?: emptyList()
    )
