package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.sanitizeForPdfGen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class VurderingPdfDTO private constructor(
    val datoSendt: String,
    val documentComponents: List<DocumentComponentDTO>,
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no", "NO"))

        fun create(documentComponents: List<DocumentComponentDTO>): VurderingPdfDTO =
            VurderingPdfDTO(
                datoSendt = LocalDate.now().format(formatter),
                documentComponents = documentComponents.sanitizeForPdfGen()
            )
    }
}
