package no.nav.syfo.client.pdl

import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.domain.lowerCapitalize
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    describe("${PdlClient::class.java.simpleName}: navn") {
        it("returns full name when person has name") {
            val expectedFullName = "${UserConstants.PERSON_FORNAVN.lowerCapitalize()} ${UserConstants.PERSON_MELLOMNAVN} ${UserConstants.PERSON_ETTERNAVN}"
            runBlocking { pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT) shouldBeEqualTo expectedFullName }
        }
        it("throws exception when person is missing name") {
            runBlocking {
                assertFailsWith(RuntimeException::class) {
                    pdlClient.navn(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }
        }
    }
})
