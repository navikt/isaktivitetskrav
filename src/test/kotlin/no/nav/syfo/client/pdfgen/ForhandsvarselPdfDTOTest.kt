// filepath: /Users/vetle/projects/NAV/isyfo/isaktivitetskrav/src/test/kotlin/no/nav/syfo/client/pdfgen/ForhandsvarselPdfDTOTest.kt
package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.DocumentComponentType
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("ForhandsvarselPdfDTO")
class ForhandsvarselPdfDTOTest {

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
        val forhandsvarselPdfDTO =
            ForhandsvarselPdfDTO.create(
                mottakerNavn = "navn",
                mottakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                documentComponents = documentWithIllegalChar
            )

        assertEquals(expectedDocument, forhandsvarselPdfDTO.documentComponents)
    }
}
