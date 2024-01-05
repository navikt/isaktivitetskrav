package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.sanitizeForPdfGen
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class ForhandsvarselPdfDTO private constructor(
    val mottakerNavn: String,
    val mottakerFodselsnummer: String,
    val datoSendt: String,
    val documentComponents: List<DocumentComponentDTO>,
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no", "NO"))

        fun create(
            mottakerNavn: String,
            mottakerPersonIdent: PersonIdent,
            documentComponents: List<DocumentComponentDTO>
        ): ForhandsvarselPdfDTO =
            ForhandsvarselPdfDTO(
                mottakerNavn = mottakerNavn,
                mottakerFodselsnummer = mottakerPersonIdent.value,
                datoSendt = LocalDate.now().format(formatter),
                documentComponents = documentComponents.sanitizeForPdfGen()
            )
    }
}
