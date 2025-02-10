package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVurdering
import java.time.LocalDate

data class ForhandsvarselDTO(
    val fritekst: String,
    val frist: LocalDate,
    val document: List<DocumentComponentDTO> = emptyList(),
) {
    fun toAktivitetskravVurdering(veilederIdent: String) = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.FORHANDSVARSEL,
        createdBy = veilederIdent,
        beskrivelse = this.fritekst,
        frist = this.frist,
    )
}
