package no.nav.syfo.testhelper.generator

import no.nav.syfo.api.dto.ForhandsvarselDTO
import no.nav.syfo.infrastructure.client.pdfgen.ForhandsvarselPdfDTO
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDate

fun generateForhandsvarsel(fritekst: String) = ForhandsvarselDTO(
    fritekst = fritekst,
    document = generateDocumentComponentDTO(fritekst = fritekst),
    frist = LocalDate.now().plusDays(30),
)

fun generateForhandsvarselPdfDTO(forhandsvarsel: ForhandsvarselDTO) = ForhandsvarselPdfDTO.create(
    mottakerNavn = UserConstants.PERSON_FULLNAME,
    mottakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    documentComponents = forhandsvarsel.document,
)
