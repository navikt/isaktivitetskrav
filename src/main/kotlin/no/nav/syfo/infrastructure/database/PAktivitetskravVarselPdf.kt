package no.nav.syfo.infrastructure.database

import java.time.OffsetDateTime
import java.util.*

data class PAktivitetskravVarselPdf(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val aktivitetskravVarselId: Int,
    val pdf: ByteArray,
)
