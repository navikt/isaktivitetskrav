package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.database.createAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import java.sql.Connection

class AktivitetskravVurderingService(
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
) {

    fun createAktivitetskravVurdering(connection: Connection, aktivitetskravVurdering: AktivitetskravVurdering) {
        connection.createAktivitetskravVurdering(aktivitetskravVurdering = aktivitetskravVurdering)
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskravVurdering = aktivitetskravVurdering
        )
    }
}
