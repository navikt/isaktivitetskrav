package no.nav.syfo.client.dokarkiv.domain

data class JournalpostResponse(
    val dokumenter: List<DokumentInfo>? = null,
    val journalpostId: Int,
    val journalpostferdigstilt: Boolean? = null,
    val journalstatus: String,
    val melding: String? = null,
)
