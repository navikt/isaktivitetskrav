package no.nav.syfo.client.pdl

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.infrastructure.pdl.PdlClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val cacheMock = mockk<RedisStore>(relaxed = true)
    val pdlClient = PdlClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        cache = cacheMock,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    beforeEachTest {
        clearMocks(cacheMock)
    }

    describe("${PdlClient::class.java.simpleName}: navn") {
        beforeEachTest {
            every { cacheMock.get(any()) } returns null
        }
        it("returns full name when person has name") {
            runBlocking { pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT) shouldBeEqualTo UserConstants.PERSON_FULLNAME }
        }
        it("returns full name when person has name with dashes") {
            runBlocking { pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH) shouldBeEqualTo UserConstants.PERSON_FULLNAME_WITH_DASHES }
        }
        it("throws exception when person is missing name") {
            runBlocking {
                assertFailsWith(RuntimeException::class) {
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }
        }
        it("caches name when person has name in pdl") {
            runBlocking { pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT) shouldBeEqualTo UserConstants.PERSON_FULLNAME }

            verify(exactly = 1) { cacheMock.set(any(), any(), any()) }
        }
        it("caches no name when person is missing name in pdl") {
            runBlocking {
                assertFailsWith(RuntimeException::class) {
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }

            verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
        }
        it("returns cached name when name is cached") {
            val cachedName = "Navn Navnesen"
            every { cacheMock.get(any()) } returns cachedName

            runBlocking { pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT) shouldBeEqualTo cachedName }

            verify(exactly = 1) { cacheMock.get(any()) }
            verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
        }
    }
})
