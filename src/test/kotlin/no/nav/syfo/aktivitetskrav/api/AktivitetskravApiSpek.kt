package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.database.createAktivitetskrav
import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Future

class AktivitetskravApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    val nyAktivitetskrav = Aktivitetskrav.ny(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val nyAktivitetskravAnnenPerson = Aktivitetskrav.ny(
        personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val automatiskOppfyltAktivitetskrav = Aktivitetskrav.automatiskOppfyltGradert(
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
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }
            afterEachTest {
                database.dropData()
            }

            fun createAktivitetskrav(vararg aktivitetskrav: Aktivitetskrav) {
                database.connection.use { connection ->
                    aktivitetskrav.forEach {
                        connection.createAktivitetskrav(it)
                    }
                    connection.commit()
                }
            }

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )

            describe("Get aktivitetskrav for person") {
                describe("Happy path") {
                    it("Returns aktivitetskrav for person") {
                        createAktivitetskrav(
                            nyAktivitetskrav,
                            nyAktivitetskravAnnenPerson,
                            automatiskOppfyltAktivitetskrav
                        )

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravResponseDTO>>(response.content!!)
                            responseDTOList.size shouldBeEqualTo 2

                            val first = responseDTOList.first()
                            first.status shouldBeEqualTo AktivitetskravStatus.NY
                            first.beskrivelse shouldBeEqualTo null
                            first.updatedBy shouldBeEqualTo null
                            first.createdAt shouldNotBeEqualTo null
                            first.sistEndret shouldNotBeEqualTo null
                            first.uuid shouldNotBeEqualTo null

                            val last = responseDTOList.last()
                            last.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                            last.beskrivelse shouldBeEqualTo "Gradert aktivitet"
                            last.updatedBy shouldBeEqualTo null
                            last.createdAt shouldNotBeEqualTo null
                            last.sistEndret shouldNotBeEqualTo null
                            last.uuid shouldNotBeEqualTo null
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
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
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
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

            describe("Vurder aktivitetskrav for person") {
                beforeEachTest {
                    createAktivitetskrav(
                        nyAktivitetskrav,
                    )
                }

                val urlVurderAktivitetskrav =
                    "$aktivitetskravApiBasePath/${nyAktivitetskrav.uuid}$vurderAktivitetskravPath"

                val requestDTO = AktivitetskravVurderingRequestDTO(
                    status = AktivitetskravStatus.OPPFYLT,
                    beskrivelse = "Aktivitetskravet er oppfylt",
                )

                describe("Happy path") {
                    it("Updates Aktivitetskrav with vurdering and produces to Kafka if request is succesful") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val latestAktivitetskrav =
                                database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .first()
                            latestAktivitetskrav.status shouldBeEqualTo requestDTO.status.name
                            latestAktivitetskrav.beskrivelse shouldBeEqualTo requestDTO.beskrivelse
                            latestAktivitetskrav.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                            kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo latestAktivitetskrav.beskrivelse
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo latestAktivitetskrav.updatedBy
                            kafkaAktivitetskravVurdering.updatedAt.truncatedTo(ChronoUnit.MILLIS) shouldBeEqualTo latestAktivitetskrav.updatedAt.truncatedTo(
                                ChronoUnit.MILLIS
                            )
                        }
                    }
                }
                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
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
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
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
                    it("should return status BadRequest if no vurdering exists for uuid-param") {
                        val randomUuid = UUID.randomUUID().toString()

                        with(
                            handleRequest(HttpMethod.Post, "$aktivitetskravApiBasePath/${randomUuid}$vurderAktivitetskravPath") {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER differs from personIdent for vurdering") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(requestDTO))
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
