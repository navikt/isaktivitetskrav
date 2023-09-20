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
        frist = LocalDate.now().plusWeeks(3), // På sikt avhengig av om bruker er reservert mot digital kommunikasjon eller ikke
    )
}
