package no.nav.syfo.client.dokarkiv.domain

const val JOURNALFORENDE_ENHET = 9999

enum class JournalpostType(
    val value: String,
) {
    UTGAAENDE("UTGAAENDE"),
}

enum class JournalpostTema(
    val value: String,
) {
    OPPFOLGING("OPP"),
}

enum class JournalpostKanal(
    val value: String,
) {
    DITT_NAV("NAV_NO"),
    SENTRAL_UTSKRIFT("S"),
}

data class JournalpostRequest(
    val avsenderMottaker: AvsenderMottaker,
    val tittel: String,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int? = JOURNALFORENDE_ENHET,
    val journalpostType: String = JournalpostType.UTGAAENDE.value,
    val tema: String = JournalpostTema.OPPFOLGING.value,
    val kanal: String,
    val sak: Sak = Sak(),
    val eksternReferanseId: String,
    val overstyrInnsynsregler: String? = null,
)
