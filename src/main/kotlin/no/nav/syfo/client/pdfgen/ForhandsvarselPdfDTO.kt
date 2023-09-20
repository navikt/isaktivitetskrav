package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
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
        fun create(
            mottakerNavn: String,
            mottakerFodselsnummer: String,
            documentComponents: List<DocumentComponentDTO>
        ): ForhandsvarselPdfDTO =
            ForhandsvarselPdfDTO(mottakerNavn, mottakerFodselsnummer, LocalDate.now().toReadable(), documentComponents)

        /**
         * Constructs a date on the form 'dd. MMM yyyy' to be displayed as the date the letter was sent in the pdf
         */
        private fun LocalDate.toReadable(): String {
            val formatter = DateTimeFormatter.ofPattern("dd. MMM yyyy", Locale("no", "NO"))
            val formattedDate = format(formatter)
            return formattedDate
        }
    }
}