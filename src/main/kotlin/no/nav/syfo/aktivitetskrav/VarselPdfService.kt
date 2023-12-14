package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.VarselType
import no.nav.syfo.client.pdfgen.VarselPdfDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdent

class VarselPdfService(
    private val pdfGenClient: PdfGenClient,
    private val pdlClient: PdlClient,
) {
    suspend fun createVarselPdf(
        personIdent: PersonIdent,
        varsel: AktivitetskravVarsel,
        callId: String
    ): ByteArray {
        val personNavn = pdlClient.navn(personIdent)
        val varselPdfDTO = VarselPdfDTO.create(
            documentComponents = varsel.document,
            mottakerNavn = personNavn,
            mottakerPersonIdent = personIdent,
        )

        return when (varsel.type) {
            VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> pdfGenClient.createForhandsvarselPdf(
                callId,
                varselPdfDTO
            )
        }
    }
}
