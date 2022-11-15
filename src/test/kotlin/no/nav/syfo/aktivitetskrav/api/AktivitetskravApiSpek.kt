package no.nav.syfo.aktivitetskrav.api

import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.bearerHeader
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AktivitetskravApiSpek : Spek({
    val urlAktivitetskravVurderingerPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    describe(AktivitetskravApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

            describe("Get aktivitetskrav-vurderinger for person") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {
                    it("Returns aktivitetskrav-vurderinger for person") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravVurderingerPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.NoContent
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravVurderingerPerson) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                }
            }
        }
    }
})
