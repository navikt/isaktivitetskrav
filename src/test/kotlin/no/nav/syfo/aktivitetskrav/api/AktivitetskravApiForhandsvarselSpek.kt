package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

class AktivitetskravApiForhandsvarselSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )

    describe(AktivitetskravApiForhandsvarselSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
                    producer = kafkaProducer,
                ),
            )
            val aktivitetskravRepository = AktivitetskravRepository(database)
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )
            val fritekst = "Dette er et forhåndsvarsel"
            val forhandsvarselDTO = ForhandsvarselDTO(
                fritekst = fritekst,
                document = generateDocumentComponentDTO(
                    fritekst = fritekst,
                    header = "Forhåndsvarsel"
                )
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

            fun postForhandsvarsel(
                aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
                arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                newForhandsvarselDTO: ForhandsvarselDTO = forhandsvarselDTO,
            ) = run {
                val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$forhandsvarselPath"
                handleRequest(HttpMethod.Post, url) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                    setBody(objectMapper.writeValueAsString(newForhandsvarselDTO))
                }
            }

            fun postAvvent(
                aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
                arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            ) = run {
                val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
                handleRequest(HttpMethod.Post, url) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                    setBody(
                        objectMapper.writeValueAsString(
                            AktivitetskravVurderingRequestDTO(
                                status = AktivitetskravStatus.AVVENT,
                                beskrivelse = "venter litt",
                                arsaker = listOf(Arsak.ANNET),
                            )
                        )
                    )
                }
            }

            fun postUnntak(
                aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
                arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            ) = run {
                val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
                handleRequest(HttpMethod.Post, url) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                    setBody(
                        objectMapper.writeValueAsString(
                            AktivitetskravVurderingRequestDTO(
                                status = AktivitetskravStatus.UNNTAK,
                                beskrivelse = "venter litt",
                                arsaker = listOf(Arsak.MEDISINSKE_GRUNNER),
                                document = generateDocumentComponentDTO("Litt fritekst"),
                            )
                        )
                    )
                }
            }

            describe("Forhåndsvarsel") {
                beforeEachTest { aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav) }
                describe("Happy path") {
                    it("Successfully creates a new forhandsvarsel") {
                        with(postForhandsvarsel()) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                            val createdForhandsvarsel =
                                objectMapper.readValue(response.content, AktivitetskravVarsel::class.java)
                            createdForhandsvarsel.document shouldBeEqualTo forhandsvarselDTO.document
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravResponseDTO>>(response.content!!)
                            val aktivitetskravResponseDTO = responseDTOList.first()
                            val varselResponseDTO = aktivitetskravResponseDTO.vurderinger.first().varsel
                            varselResponseDTO.shouldNotBeNull()
                            varselResponseDTO.svarfrist shouldBeEqualTo varselResponseDTO.createdAt.toLocalDate()
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Can't find aktivitetskrav for given uuid") {
                        with(
                            postForhandsvarsel(
                                aktivitetskravUuid = UUID.randomUUID(),
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("Fails if document is empty") {
                        val varselWithoutDocument = forhandsvarselDTO.copy(document = emptyList())
                        with(postForhandsvarsel(newForhandsvarselDTO = varselWithoutDocument)) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Fails if already forhandsvarsel") {
                        with(postForhandsvarsel()) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                        }
                        with(postForhandsvarsel()) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Fails if already forhandsvarsel before") {
                        with(postForhandsvarsel()) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                        }
                        with(postAvvent()) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(postForhandsvarsel()) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Fails if already unntak") {
                        with(postUnntak()) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(postForhandsvarsel()) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
