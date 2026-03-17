package no.nav.syfo.infrastructure.clients.behandlendeenhet

import java.io.Serializable
import java.time.LocalDateTime

data class BehandlendeEnhetResponseDTO(
    val geografiskEnhet: Enhet,
    val oppfolgingsenhetDTO: OppfolgingsenhetDTO?,
) : Serializable

data class OppfolgingsenhetDTO(
    val enhet: Enhet,
    val createdAt: LocalDateTime,
    val veilederident: String,
) : Serializable

data class Enhet(
    var enhetId: String,
    var navn: String
) : Serializable
