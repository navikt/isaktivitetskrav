package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.testhelper.UserConstants
import java.util.UUID

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    varselId: UUID,
    journalpostType: String,
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker.create(
        id = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = UserConstants.PERSON_FULLNAME,
    ),
    bruker = Bruker.create(
        id = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
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
