package no.nav.syfo.aktivitetskrav.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.domain.*
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
    val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )

    describe(AktivitetskravApiForhandsvarselSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
        val aktivitetskravRepository = AktivitetskravRepository(database)
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        val fritekst = "Dette er et forhåndsvarsel"
        val svarfrist = LocalDate.now().plusDays(30)
        val forhandsvarselDTO = ForhandsvarselDTO(
            fritekst = fritekst,
            document = generateDocumentComponentDTO(
                fritekst = fritekst,
                header = "Forhåndsvarsel"
            ),
            frist = svarfrist,
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

        suspend fun HttpClient.postForhandsvarsel(
            aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
            arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            newForhandsvarselDTO: ForhandsvarselDTO = forhandsvarselDTO,
        ) = run {
            val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$forhandsvarselPath"
            post(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(newForhandsvarselDTO)
            }
        }

        suspend fun HttpClient.postAvvent(
            aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
            arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        ) = run {
            val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
            post(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.AVVENT,
                        beskrivelse = "venter litt",
                        arsaker = listOf(Arsak.ANNET),
                    )
                )
            }
        }

        suspend fun HttpClient.postUnntak(
            aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
            arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        ) = run {
            val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
            post(url) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.UNNTAK,
                        beskrivelse = "venter litt",
                        arsaker = listOf(Arsak.MEDISINSKE_GRUNNER),
                        document = generateDocumentComponentDTO("Litt fritekst"),
                    )
                )
            }
        }

        describe("Forhåndsvarsel") {
            beforeEachTest { aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav) }
            describe("Happy path") {
                it("Successfully creates a new forhandsvarsel") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                        val postResponse = client.postForhandsvarsel()
                        postResponse.status shouldBeEqualTo HttpStatusCode.Created
                        val createdForhandsvarsel = postResponse.body<AktivitetskravVarsel>()
                        createdForhandsvarsel.document shouldBeEqualTo forhandsvarselDTO.document

                        val response = client.get(urlAktivitetskravPerson) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOList = response.body<List<AktivitetskravResponseDTO>>()
                        val aktivitetskravResponseDTO = responseDTOList.first()
                        val vurderingDTO = aktivitetskravResponseDTO.vurderinger.first()
                        val varselResponseDTO = vurderingDTO.varsel
                        varselResponseDTO.shouldNotBeNull()
                        varselResponseDTO.svarfrist shouldBeEqualTo svarfrist
                    }
                }
            }

            describe("Unhappy path") {
                it("Can't find aktivitetskrav for given uuid") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postForhandsvarsel(
                            aktivitetskravUuid = UUID.randomUUID(),
                        )
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("Fails if document is empty") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val varselWithoutDocument = forhandsvarselDTO.copy(document = emptyList())
                        val response = client.postForhandsvarsel(newForhandsvarselDTO = varselWithoutDocument)
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("Fails if already forhandsvarsel") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postForhandsvarsel()
                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val newResponse = client.postForhandsvarsel()
                        newResponse.status shouldBeEqualTo HttpStatusCode.Conflict
                    }
                }
                it("Fails if already forhandsvarsel before") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postForhandsvarsel()
                        response.status shouldBeEqualTo HttpStatusCode.Created

                        val avventResponse = client.postAvvent()
                        avventResponse.status shouldBeEqualTo HttpStatusCode.OK

                        val newResponse = client.postForhandsvarsel()
                        newResponse.status shouldBeEqualTo HttpStatusCode.Conflict
                    }
                }
                it("Fails if already unntak") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postUnntak()
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val newResponse = client.postForhandsvarsel()
                        newResponse.status shouldBeEqualTo HttpStatusCode.Conflict
                    }
                }
            }
        }
    }
})
