package no.nav.syfo.client.pdl

import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PdlClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val cache = externalMockEnvironment.valkeyCache
    private val pdlClient = PdlClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        cache = cache,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    @BeforeEach
    fun setUp() {
        cache.clear()
    }

    @AfterEach
    fun tearDown() {
        cache.clear()
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
            val cacheKey = "pdl-navn-${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}"

            runBlocking {
                assertEquals(
                    UserConstants.PERSON_FULLNAME,
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                )
            }

            assertEquals(UserConstants.PERSON_FULLNAME, cache.get(cacheKey))
        }

        @Test
        fun `caches no name when person is missing name in pdl`() {
            val cacheKey = "pdl-navn-${UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME.value}"

            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }

            assertNull(cache.get(cacheKey))
        }

        @Test
        fun `returns cached name when name is cached`() {
            val cachedName = "Navn Navnesen"
            val cacheKey = "pdl-navn-${UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME.value}"
            cache.set(key = cacheKey, value = cachedName, expireSeconds = 3600)

            runBlocking {
                assertEquals(
                    cachedName,
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                )
            }
        }
    }
}
