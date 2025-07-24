package no.nav.syfo.client.pdl

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.infrastructure.pdl.PdlClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class PdlClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val cacheMock = mockk<ValkeyStore>(relaxed = true)
    private val pdlClient = PdlClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        cache = cacheMock,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    @BeforeEach
    fun setUp() {
        clearMocks(cacheMock)
        every { cacheMock.get(any()) } returns null
    }

    @Nested
    @DisplayName("Navn")
    inner class Navn {
        @Test
        fun `returns full name when person has name`() {
            runBlocking {
                assertEquals(
                    UserConstants.PERSON_FULLNAME,
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                )
            }
        }

        @Test
        fun `returns full name when person has name with dashes`() {
            runBlocking {
                assertEquals(
                    UserConstants.PERSON_FULLNAME_WITH_DASHES,
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH)
                )
            }
        }

        @Test
        fun `throws exception when person is missing name`() {
            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }
        }

        @Test
        fun `caches name when person has name in pdl`() {
            runBlocking {
                assertEquals(
                    UserConstants.PERSON_FULLNAME,
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                )
            }

            verify(exactly = 1) { cacheMock.set(any(), any(), any()) }
        }

        @Test
        fun `caches no name when person is missing name in pdl`() {
            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }

            verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
        }

        @Test
        fun `returns cached name when name is cached`() {
            val cachedName = "Navn Navnesen"
            every { cacheMock.get(any()) } returns cachedName

            runBlocking {
                assertEquals(
                    cachedName,
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                )
            }

            verify(exactly = 1) { cacheMock.get(any()) }
            verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
        }
    }
}
