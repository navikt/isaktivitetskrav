package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import java.time.LocalDate

data class ForhandsvarselDTO(
    val fritekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
) {
    fun toAktivitetskravVurdering(veilederIdent: String) = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.FORHANDSVARSEL,
        createdBy = veilederIdent,
        beskrivelse = this.fritekst,
        arsaker = emptyList(),
        // Svarfrist for innbygger å svare på forhandsvarsel.
        // Oppgave lages for veileder når denne fristen har gått ut
        // Sjekk om dette er riktig. Forskjellig frist når fysisk brev og digitalt?
        frist = LocalDate.now().plusWeeks(2),
    )
}
