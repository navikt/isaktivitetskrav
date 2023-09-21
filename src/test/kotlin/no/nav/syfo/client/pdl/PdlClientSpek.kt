package no.nav.syfo.client.pdl

import kotlinx.coroutines.runBlocking
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val pdlClient = externalMockEnvironment.pdlClient

    describe("${PdlClient::class.java.simpleName}: navn") {
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
    }
})
