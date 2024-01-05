package no.nav.syfo.client.pdfgen

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.aktivitetskrav.api.DocumentComponentType
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ForhandsvarselPdfDTOSpek : Spek({

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

    describe(ForhandsvarselPdfDTO::class.java.simpleName) {
        it("creates dto with illegal characters removed") {
            val forhandsvarselPdfDTO =
                ForhandsvarselPdfDTO.create(
                    mottakerNavn = "navn",
                    mottakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    documentComponents = documentWithIllegalChar
                )

            forhandsvarselPdfDTO.documentComponents shouldBeEqualTo expectedDocument
        }
    }
})
