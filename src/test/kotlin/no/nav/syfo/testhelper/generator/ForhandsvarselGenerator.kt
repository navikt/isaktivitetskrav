package no.nav.syfo.testhelper.generator

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.client.pdfgen.ForhandsvarselPdfDTO
import no.nav.syfo.testhelper.UserConstants

fun generateForhandsvarsel(fritekst: String) = ForhandsvarselDTO(
    fritekst = fritekst,
    document = generateDocumentComponentDTO(fritekst = fritekst),
)

fun generateForhandsvarselPdfDTO(forhandsvarsel: ForhandsvarselDTO) = ForhandsvarselPdfDTO.create(
    mottakerNavn = UserConstants.PERSON_FULLNAME,
    mottakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    documentComponents = forhandsvarsel.document,
)
