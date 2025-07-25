package no.nav.syfo.infrastructure.client.dokarkiv.model

const val JOURNALFORENDE_ENHET = 9999

enum class JournalpostType {
    UTGAAENDE,
    NOTAT,
}

enum class JournalpostTema(
    val value: String,
) {
    OPPFOLGING("OPP"),
}

data class JournalpostRequest(
    val avsenderMottaker: AvsenderMottaker?,
    val tittel: String,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int? = JOURNALFORENDE_ENHET,
    val journalpostType: String,
    val tema: String = JournalpostTema.OPPFOLGING.value,
    val sak: Sak = Sak(),
    val eksternReferanseId: String,
    val overstyrInnsynsregler: String? = null,
)
