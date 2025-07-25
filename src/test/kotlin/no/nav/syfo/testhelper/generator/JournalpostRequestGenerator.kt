package no.nav.syfo.testhelper.generator

import no.nav.syfo.infrastructure.client.dokarkiv.model.*
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import java.util.*

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    varselId: UUID,
    journalpostType: String,
) = JournalpostRequest(
    avsenderMottaker = if (journalpostType != JournalpostType.NOTAT.name) {
        AvsenderMottaker.create(
            id = ARBEIDSTAKER_PERSONIDENT.value,
            idType = BrukerIdType.PERSON_IDENT,
            navn = UserConstants.PERSON_FULLNAME,
        )
    } else null,
    bruker = Bruker.create(
        id = ARBEIDSTAKER_PERSONIDENT.value,
        idType = BrukerIdType.PERSON_IDENT
    ),
    tittel = tittel,
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    journalpostType = journalpostType,
    eksternReferanseId = varselId.toString(),
)
