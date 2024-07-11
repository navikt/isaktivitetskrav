package no.nav.syfo.aktivitetskrav

import no.nav.syfo.infrastructure.database.repository.PAktivitetskravVarsel
import no.nav.syfo.infrastructure.database.repository.VarselReferences
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.infrastructure.kafka.domain.ExpiredVarsel
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVarsel
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

    fun setPublished(varsel: KafkaAktivitetskravVarsel)

    fun getVarselForVurdering(vurderingUuid: UUID): PAktivitetskravVarsel?

    suspend fun getExpiredVarsler(): List<Triple<PersonIdent, UUID, PAktivitetskravVarsel>>

    suspend fun updateExpiredVarselPublishedAt(publishedExpiredVarsel: ExpiredVarsel): Int
}
