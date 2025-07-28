package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.AktivitetskravVarselService
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.dokarkiv.model.*
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import org.slf4j.LoggerFactory

class JournalforAktivitetskravVarselCronjob(
    private val aktivitetskravVarselService: AktivitetskravVarselService,
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
    private val isJournalforingRetryEnabled: Boolean,
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
                val journalpostId = try {
                    dokarkivClient.journalfor(journalpostRequest).journalpostId
                } catch (exc: Exception) {
                    if (isJournalforingRetryEnabled) {
                        throw exc
                    } else {
                        log.error("Journalforing failed, skipping retry (should only happen in dev-gcp)", exc)
                        // Defaulting'en til DEFAULT_FAILED_JP_ID skal bare forekomme i dev-gcp:
                        // Har dette fordi vi ellers spammer ned dokarkiv med forsøk på å journalføre
                        // på personer som mangler aktør-id.
                        DEFAULT_FAILED_JP_ID
                    }
                }
                aktivitetskravVarselService.setJournalfort(
                    varsel = varsel,
                    journalpostId = journalpostId.toString(),
                )
                result.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting journalforing of varsel", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private const val DEFAULT_FAILED_JP_ID = 0
        private val log = LoggerFactory.getLogger(JournalforAktivitetskravVarselCronjob::class.java)
    }
}

fun createJournalpostRequest(
    personIdent: PersonIdent,
    navn: String,
    pdf: ByteArray,
    varsel: AktivitetskravVarsel,
): JournalpostRequest {
    val journalpostType = varsel.getJournalpostType()
    val avsenderMottaker = if (journalpostType != JournalpostType.NOTAT) {
        AvsenderMottaker.create(
            id = personIdent.value,
            idType = BrukerIdType.PERSON_IDENT,
            navn = navn,
        )
    } else null

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
        journalpostType = journalpostType.name,
        avsenderMottaker = avsenderMottaker,
        tittel = dokumentTittel,
        bruker = bruker,
        dokumenter = dokumenter,
        eksternReferanseId = varsel.uuid.toString(),
    )
}
