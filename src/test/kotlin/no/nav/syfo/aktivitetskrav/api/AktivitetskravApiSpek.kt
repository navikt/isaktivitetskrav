package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.syfo.aktivitetskrav.database.createAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurderingStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class AktivitetskravApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlAktivitetskravVurderingerPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"
    val nyAktivitetskravVurdering = AktivitetskravVurdering.ny(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val nyAktivitetskravVurderingAnnenPerson = AktivitetskravVurdering.ny(
        personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val automatiskOppfyltVurdering = AktivitetskravVurdering.automatiskOppfyltGradert(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusYears(2),
    ).copy(
        createdAt = nowUTC().minusWeeks(80)
    )

    describe(AktivitetskravApiSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
                    kafkaProducerAktivitetskravVurdering = kafkaProducer,
                ),
            )

            beforeEachTest {
                database.dropData()
            }

            fun createAktivitetskravVurderinger(vararg aktivitetskravVurdering: AktivitetskravVurdering) {
                database.connection.use { connection ->
                    aktivitetskravVurdering.forEach {
                        connection.createAktivitetskravVurdering(it)
                    }
                    connection.commit()
                }
            }

            describe("Get aktivitetskrav-vurderinger for person") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {
                    it("Returns aktivitetskrav-vurderinger for person") {
                        createAktivitetskravVurderinger(
                            nyAktivitetskravVurdering,
                            nyAktivitetskravVurderingAnnenPerson,
                            automatiskOppfyltVurdering
                        )

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravVurderingerPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravVurderingResponseDTO>>(response.content!!)
                            responseDTOList.size shouldBeEqualTo 2

                            val first = responseDTOList.first()
                            first.status shouldBeEqualTo AktivitetskravVurderingStatus.NY.name
                            first.beskrivelse shouldBeEqualTo null
                            first.updatedBy shouldBeEqualTo null
                            first.createdAt shouldNotBeEqualTo null
                            first.updatedAt shouldNotBeEqualTo null
                            first.uuid shouldNotBeEqualTo null

                            val last = responseDTOList.last()
                            last.status shouldBeEqualTo AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT.name
                            last.beskrivelse shouldBeEqualTo "Gradert aktivitet"
                            last.updatedBy shouldBeEqualTo null
                            last.createdAt shouldNotBeEqualTo null
                            last.updatedAt shouldNotBeEqualTo null
                            last.uuid shouldNotBeEqualTo null
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
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravVurderingerPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravVurderingerPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravVurderingerPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1)
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
