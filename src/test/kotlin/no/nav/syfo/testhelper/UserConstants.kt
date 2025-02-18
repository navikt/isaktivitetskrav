package no.nav.syfo.testhelper

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.util.UUID

object UserConstants {

    const val PERSON_FORNAVN = "ULLEN"
    const val PERSON_MELLOMNAVN = "Mellomnavn"
    const val PERSON_ETTERNAVN = "Bamse"
    const val PERSON_FORNAVN_DASH = "JAN-OLA"
    const val PERSON_FULLNAME = "Ullen Mellomnavn Bamse"
    const val PERSON_FULLNAME_WITH_DASHES = "Jan-Ola Mellomnavn Bamse"
    val PDF_FORHANDSVARSEL = byteArrayOf(0x2E, 0x28)
    val PDF_STANS = byteArrayOf(0x2E, 0x28)
    val PDF_VURDERING = byteArrayOf(0x2E, 0x25)
    private const val ARBEIDSTAKER_FNR = "12345678912"
    private const val VIRKSOMHETSNUMMER = "123456789"

    val ARBEIDSTAKER_PERSONIDENT = PersonIdent(ARBEIDSTAKER_FNR)
    val OTHER_ARBEIDSTAKER_PERSONIDENT = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("2", "1"))
    val THIRD_ARBEIDSTAKER_PERSONIDENT = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("4", "1"))
    val ARBEIDSTAKER_PERSONIDENT_NO_NAME = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("5", "1"))
    val ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("6", "1"))
    val PERSONIDENT_VEILEDER_NO_ACCESS = PersonIdent(ARBEIDSTAKER_PERSONIDENT.value.replace("3", "1"))

    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer(VIRKSOMHETSNUMMER)
    const val VEILEDER_IDENT = "Z999999"
    const val OTHER_VEILEDER_IDENT = "Z999998"

    val EXISTING_EKSTERN_REFERANSE_UUID: UUID = UUID.fromString("e7e8e9e0-e1e2-e3e4-e5e6-e7e8e9e0e1e2")
}
