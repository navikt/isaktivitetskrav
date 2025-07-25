package no.nav.syfo.testhelper.generator

import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.api.dto.DocumentComponentType

fun generateDocumentComponentDTO(fritekst: String, header: String = "Standard header") = listOf(
    DocumentComponentDTO(
        type = DocumentComponentType.HEADER_H1,
        title = null,
        texts = listOf(header),
    ),
    DocumentComponentDTO(
        type = DocumentComponentType.PARAGRAPH,
        title = null,
        texts = listOf(fritekst),
    ),
    DocumentComponentDTO(
        type = DocumentComponentType.PARAGRAPH,
        key = "Standardtekst",
        title = null,
        texts = listOf("Dette er en standardtekst"),
    ),
)
