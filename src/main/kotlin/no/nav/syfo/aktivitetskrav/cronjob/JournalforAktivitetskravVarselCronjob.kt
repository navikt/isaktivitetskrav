package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdent
import org.slf4j.LoggerFactory

class JournalforAktivitetskravVarselCronjob(
    private val aktivitetskravVarselService: AktivitetskravVarselService,
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed journalføring of varsel with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJob(): CronjobResult {
        val result = CronjobResult()
        val ikkeJournalforteVarsler = aktivitetskravVarselService.getIkkeJournalforte()

        ikkeJournalforteVarsler.forEach { (personIdent, varsel, pdf) ->
            try {
                val navn = pdlClient.navn(personIdent = personIdent)
                val journalpostRequest = createJournalpostRequest(
                    personIdent = personIdent,
                    navn = navn,
                    pdf = pdf,
                    varsel = varsel,
                )

                dokarkivClient.journalfor(
                    journalpostRequest = journalpostRequest,
                ).also {
                    aktivitetskravVarselService.setJournalfort(
                        varsel = varsel,
                        journalpostId = it.journalpostId.toString()
                    )
                    result.updated++
                }
            } catch (e: Exception) {
                log.error("Exception caught while attempting journalforing of varsel", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforAktivitetskravVarselCronjob::class.java)
    }
}

fun createJournalpostRequest(
    personIdent: PersonIdent,
    navn: String,
    pdf: ByteArray,
    varsel: AktivitetskravVarsel
): JournalpostRequest {
    val avsenderMottaker = AvsenderMottaker.create(
        id = personIdent.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = navn,
    )
    val bruker = Bruker.create(
        id = personIdent.value,
        idType = BrukerIdType.PERSON_IDENT,
    )

    val dokumentTittel = varsel.getDokumentTittel()

    val dokumenter = listOf(
        Dokument.create(
            brevkode = varsel.getBrevkode(),
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = dokumentTittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
            tittel = dokumentTittel,
        )
    )

    return JournalpostRequest(
        journalpostType = varsel.getJournalpostType().name,
        avsenderMottaker = avsenderMottaker,
        tittel = dokumentTittel,
        bruker = bruker,
        dokumenter = dokumenter,
        eksternReferanseId = varsel.uuid.toString(),
    )
}
