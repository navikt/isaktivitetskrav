package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravAutomatiskOppfylt
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateKafkaIdenthendelseDTO
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate

private val aktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
private val aktivIdentIkkeOppdatert = UserConstants.THIRD_ARBEIDSTAKER_PERSONIDENT
private val inaktivIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT

class IdenthendelseServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val aktivitetskravRepository = AktivitetskravRepository(database)

    private val identhendelseService = IdenthendelseService(
        pdlClient = externalMockEnvironment.pdlClient,
        aktivitetskravRepository = aktivitetskravRepository,
    )

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @Nested
    @DisplayName("Aktivitetskrav eksisterer for person")
    inner class AktivitetskravEksistererForPerson {

        private val aktivitetskravNy = createAktivitetskravNy(
            personIdent = inaktivIdent,
            tilfelleStart = LocalDate.now().minusDays(50),
        )
        private val aktivitetskravAutomatiskOppfylt =
            createAktivitetskravAutomatiskOppfylt(
                personIdent = inaktivIdent,
                tilfelleStart = LocalDate.now().minusDays(400),
            )

        @BeforeEach
        fun setUp() {
            aktivitetskravRepository.createAktivitetskrav(aktivitetskravNy)
            aktivitetskravRepository.createAktivitetskrav(aktivitetskravAutomatiskOppfylt)
        }

        @Test
        fun `Oppdaterer aktivitetskrav når person har fått ny ident`() {
            val previousUpdatedAt =
                aktivitetskravRepository.getAktivitetskrav(personIdent = inaktivIdent).first().updatedAt
            val kafkaIdenthendelseDTO =
                generateKafkaIdenthendelseDTO(
                    aktivIdent = aktivIdent,
                    inaktiveIdenter = listOf(inaktivIdent)
                )
            runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

            val aktivitetskravMedInaktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = inaktivIdent)
            assertEquals(0, aktivitetskravMedInaktivIdent.size)

            val aktivitetskravMedAktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = aktivIdent)
            assertEquals(2, aktivitetskravMedAktivIdent.size)
            val updatedAktivitetskravNy = aktivitetskravMedAktivIdent.first()
            assertTrue(updatedAktivitetskravNy.updatedAt > previousUpdatedAt)
        }

        @Test
        fun `Oppdaterer ingenting når person har fått ny ident uten gamle identer`() {
            val kafkaIdenthendelseDTO =
                generateKafkaIdenthendelseDTO(
                    aktivIdent = aktivIdent,
                    inaktiveIdenter = emptyList()
                )
            runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

            val aktivitetskravMedInaktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = inaktivIdent)
            assertEquals(2, aktivitetskravMedInaktivIdent.size)

            val aktivitetskravMedAktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = aktivIdent)
            assertEquals(0, aktivitetskravMedAktivIdent.size)
        }

        @Test
        fun `Oppdaterer ingenting når person mangler aktiv ident`() {
            val kafkaIdenthendelseDTO =
                generateKafkaIdenthendelseDTO(
                    aktivIdent = null,
                    inaktiveIdenter = listOf(inaktivIdent)
                )
            runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

            val aktivitetskravMedInaktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = inaktivIdent)
            assertEquals(2, aktivitetskravMedInaktivIdent.size)

            val aktivitetskravMedAktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = aktivIdent)
            assertEquals(0, aktivitetskravMedAktivIdent.size)
        }

        @Test
        fun `Kaster feil og oppdaterer ingenting når person har fått ny ident, men ident er ikke oppdatert i PDL`() {
            val kafkaIdenthendelseDTO =
                generateKafkaIdenthendelseDTO(
                    aktivIdent = aktivIdentIkkeOppdatert,
                    inaktiveIdenter = listOf(inaktivIdent)
                )
            runBlocking {
                assertThrows<IllegalStateException> {
                    identhendelseService.handle(
                        kafkaIdenthendelseDTO
                    )
                }
            }

            val aktivitetskravMedInaktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = inaktivIdent)
            assertEquals(2, aktivitetskravMedInaktivIdent.size)

            val aktivitetskravMedAktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = aktivIdentIkkeOppdatert)
            assertEquals(0, aktivitetskravMedAktivIdent.size)
        }
    }

    @Nested
    @DisplayName("Aktivitetskrav eksisterer ikke for person")
    inner class AktivitetskravEksistererIkkeForPerson {

        @Test
        fun `Oppdaterer ingenting når person har fått ny ident`() {
            val kafkaIdenthendelseDTO =
                generateKafkaIdenthendelseDTO(
                    aktivIdent = aktivIdent,
                    inaktiveIdenter = listOf(inaktivIdent)
                )
            runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

            val aktivitetskravMedInaktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = inaktivIdent)
            assertEquals(0, aktivitetskravMedInaktivIdent.size)

            val aktivitetskravMedAktivIdent =
                aktivitetskravRepository.getAktivitetskrav(personIdent = aktivIdent)
            assertEquals(0, aktivitetskravMedAktivIdent.size)
        }
    }
}
