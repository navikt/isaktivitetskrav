package no.nav.syfo.client.dokarkiv.domain

enum class FiltypeType(
    val value: String,
) {
    PDFA("PDFA"),
}

enum class VariantformatType(
    val value: String,
) {
    ARKIV("ARKIV"),
}

const val DOKUMENTVARIANT_FILNAVN_MAX_LENGTH = 200

data class Dokumentvariant private constructor(
    val filnavn: String,
    val filtype: String,
    val fysiskDokument: ByteArray,
    val variantformat: String,
) {
    companion object {
        fun create(
            filnavn: String,
            filtype: FiltypeType,
            fysiskDokument: ByteArray,
            variantformat: VariantformatType,
        ): Dokumentvariant {
            if ((filnavn.length + filtype.value.length) >= DOKUMENTVARIANT_FILNAVN_MAX_LENGTH) {
                throw IllegalArgumentException("Filnavn of Dokumentvariant is too long, max size is $DOKUMENTVARIANT_FILNAVN_MAX_LENGTH")
            }
            return Dokumentvariant(
                filnavn = filnavn,
                filtype = filtype.value,
                fysiskDokument = fysiskDokument,
                variantformat = variantformat.value,
            )
        }
    }
}
