package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent

class AktivitetskravVarselService(private val database: DatabaseInterface) {
    fun getIkkeJournalforte(): List<Triple<PersonIdent, AktivitetskravVarsel, ByteArray>> {
        // TODO: Hente varsel uten journalpostId fra databasen (inkl personident og pdf)
        return emptyList()
    }

    fun updateJournalpostId(varsel: AktivitetskravVarsel, journalpostId: String) {
        // TODO: Oppdater journalpostId til varsel i DB
    }
}
