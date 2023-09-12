package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import java.util.*

data class AktivitetskravVarsel internal constructor(
    val uuid: UUID,
    val journalpostId: String?,
    val document: List<DocumentComponentDTO>,
) {

    companion object {
        fun create(document: List<DocumentComponentDTO>) = AktivitetskravVarsel(
            uuid = UUID.randomUUID(),
            journalpostId = null,
            document = document,
        )
    }
}
