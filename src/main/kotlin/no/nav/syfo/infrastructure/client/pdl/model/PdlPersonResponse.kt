package no.nav.syfo.infrastructure.client.pdl.model

import java.util.*

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?,
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?,
)

data class PdlPerson(
    val navn: List<PdlPersonNavn>,
) {
    fun fullName(): String? =
        navn.firstOrNull()?.run {
            val fornavn = fornavn.lowerCapitalize()
            val etternavn = etternavn.lowerCapitalize()

            if (mellomnavn.isNullOrBlank()) {
                "$fornavn $etternavn"
            } else {
                "$fornavn ${mellomnavn.lowerCapitalize()} $etternavn"
            }
        }
}

data class PdlPersonNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun String.lowerCapitalize() =
    this.split(" ").joinToString(" ") { name ->
        val nameWithDash = name.split("-")
        if (nameWithDash.size > 1) {
            nameWithDash.joinToString("-") { it.capitalizeName() }
        } else {
            name.capitalizeName()
        }
    }

private fun String.capitalizeName() =
    this.lowercase(Locale.getDefault()).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
