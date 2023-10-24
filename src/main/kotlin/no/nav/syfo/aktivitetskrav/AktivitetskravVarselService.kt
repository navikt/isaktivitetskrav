package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.domain.vurder
import no.nav.syfo.aktivitetskrav.kafka.*
import no.nav.syfo.aktivitetskrav.kafka.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVarsel
import no.nav.syfo.client.krr.KRRClient
import no.nav.syfo.client.pdfgen.ForhandsvarselPdfDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdent
import java.util.UUID

class AktivitetskravVarselService(
    private val aktivitetskravVarselRepository: AktivitetskravVarselRepository,
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val arbeidstakervarselProducer: ArbeidstakervarselProducer,
    private val aktivitetskravVarselProducer: AktivitetskravVarselProducer,
    private val expiredVarselProducer: ExpiredVarselProducer,
    private val pdfGenClient: PdfGenClient,
    private val pdlClient: PdlClient,
    private val krrClient: KRRClient,
) {
    fun getIkkeJournalforte(): List<Triple<PersonIdent, AktivitetskravVarsel, ByteArray>> {
        return aktivitetskravVarselRepository.getIkkeJournalforte()
            .map { Triple(it.first, it.second.toAktivitetkravVarsel(), it.third) }
    }

    fun getIkkePubliserte(): List<KafkaAktivitetskravVarsel> {
        return aktivitetskravVarselRepository.getIkkePubliserte()
            .map { it.first.toKafkaAktivitetskravVarsel(it.second) }
    }

    fun publiser(varsel: KafkaAktivitetskravVarsel) {
        aktivitetskravVarselProducer.sendAktivitetskravVarsel(
            varsel = varsel,
        )
        // TODO: Koden under kan fjernes når eSyfo konsumerer varselet over og sender til esyfovarsel
        arbeidstakervarselProducer.sendArbeidstakervarsel(
            varselHendelse = ArbeidstakerHendelse(
                type = HendelseType.SM_FORHANDSVARSEL_STANS,
                arbeidstakerFnr = varsel.personIdent,
                data = VarselData(
                    journalpost = VarselDataJournalpost(
                        uuid = varsel.varselUuid.toString(),
                        id = varsel.journalpostId,
                    ),
                ),
                orgnummer = null,
            )
        )
        aktivitetskravVarselRepository.setPublished(varsel)
    }

    fun setJournalfort(varsel: AktivitetskravVarsel, journalpostId: String) {
        aktivitetskravVarselRepository.updateJournalpostId(
            varsel = varsel,
            journalpostId = journalpostId,
        )
    }

    fun getVarsel(vurderingUuid: UUID): AktivitetskravVarsel? =
        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = vurderingUuid)
            ?.toAktivitetkravVarsel()

    suspend fun sendForhandsvarsel(
        aktivitetskrav: Aktivitetskrav,
        veilederIdent: String,
        personIdent: PersonIdent,
        forhandsvarselDTO: ForhandsvarselDTO,
        callId: String,
    ): AktivitetskravVarsel {
        val personNavn = pdlClient.navn(personIdent)
        val pdf = pdfGenClient.createForhandsvarselPdf(
            callId,
            ForhandsvarselPdfDTO.create(
                mottakerNavn = personNavn,
                mottakerFodselsnummer = personIdent.value,
                documentComponents = forhandsvarselDTO.document,
            )
        )

        val vurdering: AktivitetskravVurdering = forhandsvarselDTO.toAktivitetskravVurdering(veilederIdent)
        val updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = vurdering)
        val forhandsvarsel = AktivitetskravVarsel.create(forhandsvarselDTO.document)

        val nyttForhandsvarsel = aktivitetskravVarselRepository.create(
            aktivitetskrav = updatedAktivitetskrav,
            varsel = forhandsvarsel,
            pdf = pdf,
        )

        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(aktivitetskrav = updatedAktivitetskrav)

        return nyttForhandsvarsel.toAktivitetkravVarsel()
    }

    suspend fun getExpiredVarsler(): List<ExpiredVarsel> =
        aktivitetskravVarselRepository.getExpiredVarsler().map { (personIdent, aktivitetskravUuid, varsel) ->
            varsel.toExpiredVarsel(personIdent, aktivitetskravUuid)
        }

    suspend fun publishExpiredVarsel(expiredVarselToBePublished: ExpiredVarsel) {
        expiredVarselProducer.publishExpiredVarsel(expiredVarselToBePublished)
        aktivitetskravVarselRepository.updateExpiredVarselPublishedAt(expiredVarselToBePublished)
    }
}
