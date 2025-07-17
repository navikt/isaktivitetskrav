package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.domain.VarselType
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.getAktivitetskravVarselPdf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class AktivitetskravRepositoryTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val aktivitetskravRepository = AktivitetskravRepository(database = database)
    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val pdf = byteArrayOf(0x2E, 100)

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @Test
    fun `Successfully creates an aktivitetskrav with previous aktivitetskrav`() {
        val newAktivitetskrav = Aktivitetskrav.create(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        val previousAktivitetskravUuid = UUID.randomUUID()
        aktivitetskravRepository.createAktivitetskrav(
            newAktivitetskrav,
            previousAktivitetskravUuid,
        )
        val storedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(newAktivitetskrav.uuid)

        assertEquals(newAktivitetskrav.personIdent, storedAktivitetskrav?.personIdent)
        assertEquals(previousAktivitetskravUuid, storedAktivitetskrav?.previousAktivitetskravUuid)
    }

    @Nested
    @DisplayName("Forhåndsvarsel")
    inner class Forhandsvarsel {

        private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
        private val newAktivitetskrav = createAktivitetskravNy(
            tilfelleStart = LocalDate.now(),
            personIdent = personIdent,
        )
        private val fritekst = "Et forhåndsvarsel"
        private val document = generateDocumentComponentDTO(fritekst = fritekst)
        private val frist = LocalDate.now().plusDays(30)
        private val forhandsvarselDTO = ForhandsvarselDTO(
            fritekst = fritekst,
            document = document,
            frist = frist,
        )

        @BeforeEach
        fun setUp() {
            aktivitetskravRepository.createAktivitetskrav(newAktivitetskrav)
        }

        @Test
        fun `Should create forhåndsvarsel in db`() {
            val vurdering: AktivitetskravVurdering =
                forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val forhandsvarsel = AktivitetskravVarsel.create(
                type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                frist = LocalDate.now().plusDays(30),
                document = forhandsvarselDTO.document,
            )
            val updatedAktivitetskrav = newAktivitetskrav.vurder(vurdering)

            val newVarsel = aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                aktivitetskrav = updatedAktivitetskrav,
                varsel = forhandsvarsel,
                newVurdering = vurdering,
                pdf = pdf,
            )

            val retrievedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(updatedAktivitetskrav.uuid)
            val vurderinger = retrievedAktivitetskrav!!.vurderinger
            val newVurdering = vurderinger.first()
            val newVarselPdf = database.getAktivitetskravVarselPdf(newVarsel.id)

            assertEquals(updatedAktivitetskrav.status, retrievedAktivitetskrav.status)
            assertTrue(retrievedAktivitetskrav.updatedAt > retrievedAktivitetskrav.createdAt)
            assertEquals(1, vurderinger.size)
            assertEquals(retrievedAktivitetskrav.id, newVurdering.aktivitetskravId)
            assertEquals(vurdering.createdBy, newVurdering.createdBy)
            assertEquals(vurdering.status, newVurdering.status)
            assertEquals(newVurdering.id, newVarsel.aktivitetskravVurderingId)
            assertNull(newVarsel.journalpostId)
            assertEquals(VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER.name, newVarsel.type)
            assertEquals(frist, newVarsel.svarfrist)

            assertEquals(pdf.size, newVarselPdf?.pdf?.size)
            assertEquals(pdf[0], newVarselPdf?.pdf?.get(0))
            assertEquals(pdf[1], newVarselPdf?.pdf?.get(1))
            assertEquals(newVarsel.id, newVarselPdf?.aktivitetskravVarselId)
        }
    }
}
