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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dokumentvariant

        if (filnavn != other.filnavn) return false
        if (filtype != other.filtype) return false
        if (!fysiskDokument.contentEquals(other.fysiskDokument)) return false
        if (variantformat != other.variantformat) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filnavn.hashCode()
        result = 31 * result + filtype.hashCode()
        result = 31 * result + fysiskDokument.contentHashCode()
        result = 31 * result + variantformat.hashCode()
        return result
    }
}
