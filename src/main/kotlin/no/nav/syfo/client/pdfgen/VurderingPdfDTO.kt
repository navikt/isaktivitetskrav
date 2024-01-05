package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.sanitizeForPdfGen
import java.util.*

data class VurderingPdfDTO private constructor(
    val documentComponents: List<DocumentComponentDTO>,
) {
    companion object {
        fun create(
            documentComponents: List<DocumentComponentDTO>
        ): VurderingPdfDTO =
            VurderingPdfDTO(
                documentComponents = documentComponents.sanitizeForPdfGen()
            )
    }
}
