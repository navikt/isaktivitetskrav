package no.nav.syfo.application

import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.VarselType
import no.nav.syfo.infrastructure.client.pdfgen.ForhandsvarselPdfDTO
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.pdfgen.VurderingPdfDTO
import no.nav.syfo.infrastructure.client.pdl.PdlClient

class VarselPdfService(
    private val pdfGenClient: PdfGenClient,
    private val pdlClient: PdlClient,
) {
    suspend fun createVarselPdf(
        personIdent: PersonIdent,
        varsel: AktivitetskravVarsel,
        callId: String,
    ): ByteArray = when (varsel.type) {
        VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER -> {
            val personNavn = pdlClient.navn(personIdent)
            val forhandsvarselPdfDTO = ForhandsvarselPdfDTO.create(
                documentComponents = varsel.document,
                mottakerNavn = personNavn,
                mottakerPersonIdent = personIdent,
            )

            pdfGenClient.createForhandsvarselPdf(
                callId,
                forhandsvarselPdfDTO
            )
        }

        VarselType.UNNTAK, VarselType.OPPFYLT, VarselType.IKKE_AKTUELL -> {
            val vurderingPdfDTO = VurderingPdfDTO.create(varsel.document)
            pdfGenClient.createVurderingPdf(
                callId,
                vurderingPdfDTO,
            )
        }

        VarselType.INNSTILLING_OM_STANS -> {
            val vurderingPdfDTO = VurderingPdfDTO.create(varsel.document)
            pdfGenClient.createInnstillingOmStansPdf(callId, vurderingPdfDTO)
        }
    }
}
