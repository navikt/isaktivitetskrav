package no.nav.syfo.client.krr

import java.io.Serializable

data class KRRResponseDTO(
    val personident: String,
    val aktiv: Boolean,
    val kanVarsles: Boolean?,
    val reservasjonOppdatert: String?,
    val reservert: Boolean?,
    val spraak: String?,
    val spraakOppdatert: String?,
    val epostadresse: String?,
    val epostadresseOppdatert: String?,
    val epostadresseVerifisert: String?,
    val mobiltelefonnummer: String?,
    val mobiltelefonnummerOppdatert: String?,
    val mobiltelefonnummerVerifisert: String?,
    val sikkerDigitalPostkasse: SikkerDigitalPostkasse?
) : Serializable

data class SikkerDigitalPostkasse(
    val adresse: String,
    val leverandoerAdresse: String,
    val leverandoerSertifikat: String,
) : Serializable
