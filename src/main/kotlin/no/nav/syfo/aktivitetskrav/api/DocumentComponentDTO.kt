package no.nav.syfo.aktivitetskrav.api

data class DocumentComponentDTO(
    val type: DocumentComponentType,
    val key: String? = null,
    val title: String?,
    val texts: List<String>,
) {

    companion object {
        fun serialize(document: List<DocumentComponentDTO>): String =
            buildString {
                document.forEach { documentComponentDTO ->
                    documentComponentDTO.title?.let {
                        appendLine(it)
                    }
                    documentComponentDTO.texts.forEach {
                        appendLine(it)
                    }
                    appendLine()
                }
            }
    }
}

enum class DocumentComponentType {
    HEADER_H1,
    HEADER_H2,
    PARAGRAPH,
    LINK,
}