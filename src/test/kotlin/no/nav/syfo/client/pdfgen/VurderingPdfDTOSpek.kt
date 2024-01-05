package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.DocumentComponentType
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VurderingPdfDTOSpek : Spek({

    val documentWithIllegalChar = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = "tittel",
            texts = listOf("text1\u0002dsa", "text2"),
        )
    )
    val expectedDocument = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = "tittel",
            texts = listOf("text1dsa", "text2"),
        )
    )

    describe(VurderingPdfDTO::class.java.simpleName) {
        it("creates dto with illegal characters removed") {
            val vurderingPdfDTO = VurderingPdfDTO.create(documentWithIllegalChar)

            vurderingPdfDTO.documentComponents shouldBeEqualTo expectedDocument
        }
    }
})
