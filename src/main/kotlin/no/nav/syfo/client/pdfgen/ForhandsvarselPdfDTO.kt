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
        val formatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no", "NO"))

        fun create(
            mottakerNavn: String,
            mottakerFodselsnummer: String,
            documentComponents: List<DocumentComponentDTO>
        ): ForhandsvarselPdfDTO =
            ForhandsvarselPdfDTO(
                mottakerNavn,
                mottakerFodselsnummer,
                LocalDate.now().format(formatter),
                documentComponents
            )
    }
}
