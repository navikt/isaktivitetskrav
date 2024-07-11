package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVurdering

data class ForhandsvarselDTO(
    val fritekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
) {
    fun toAktivitetskravVurdering(veilederIdent: String) = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.FORHANDSVARSEL,
        createdBy = veilederIdent,
        beskrivelse = this.fritekst,
        arsaker = emptyList(),
    )
}
