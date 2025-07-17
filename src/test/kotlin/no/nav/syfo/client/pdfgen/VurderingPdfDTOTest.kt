package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.DocumentComponentType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class VurderingPdfDTOTest {

    private val documentWithIllegalChar = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = "tittel",
            texts = listOf("text1\u0002dsa", "text2"),
        )
    )
    private val expectedDocument = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = "tittel",
            texts = listOf("text1dsa", "text2"),
        )
    )

    @Test
    fun `creates dto with illegal characters removed`() {
        val vurderingPdfDTO = VurderingPdfDTO.create(documentWithIllegalChar)

        assertEquals(expectedDocument, vurderingPdfDTO.documentComponents)
    }
}
