package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.repository.PAktivitetskravVarsel
import no.nav.syfo.infrastructure.database.repository.VarselReferences
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVarselRecord
import no.nav.syfo.domain.PersonIdent
import java.util.*

interface IAktivitetskravVarselRepository {

    fun createAktivitetskravVurderingWithVarselPdf(
        aktivitetskrav: Aktivitetskrav,
        newVurdering: AktivitetskravVurdering,
        varsel: AktivitetskravVarsel,
        pdf: ByteArray,
    ): PAktivitetskravVarsel

    fun getIkkeJournalforte(): List<Triple<PersonIdent, PAktivitetskravVarsel, ByteArray>>

    fun getIkkePubliserte(): List<Pair<PAktivitetskravVarsel, VarselReferences>>

    fun updateJournalpostId(varsel: AktivitetskravVarsel, journalpostId: String)

    fun setPublished(varsel: AktivitetskravVarselRecord)

    fun getVarselForVurdering(vurderingUuid: UUID): PAktivitetskravVarsel?
}
