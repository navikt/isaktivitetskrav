package no.nav.syfo.aktivitetskrav.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.dto.AktivitetskravResponseDTO
import no.nav.syfo.api.dto.AktivitetskravVurderingRequestDTO
import no.nav.syfo.api.dto.Arsak
import no.nav.syfo.api.dto.ForhandsvarselDTO
import no.nav.syfo.api.endpoints.aktivitetskravApiBasePath
import no.nav.syfo.api.endpoints.aktivitetskravApiPersonidentPath
import no.nav.syfo.api.endpoints.forhandsvarselPath
import no.nav.syfo.api.endpoints.vurderAktivitetskravPath
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVurderingRecord
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

class AktivitetskravApiForhandsvarselTest {
    private val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    private val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
    private val aktivitetskravRepository = AktivitetskravRepository(database)
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val fritekst = "Dette er et forhåndsvarsel"
    private val svarfrist = LocalDate.now().plusDays(30)
    private val svarfristTooShort = LocalDate.now().plusDays(20)
    private val svarfristTooLong = LocalDate.now().plusDays(43)
    private val forhandsvarselDTO = ForhandsvarselDTO(
        fritekst = fritekst,
        document = generateDocumentComponentDTO(
            fritekst = fritekst,
            header = "Forhåndsvarsel"
        ),
        frist = svarfrist,
    )

    @BeforeEach
    fun setUp() {
        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    suspend fun HttpClient.postForhandsvarsel(
        aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
        arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        newForhandsvarselDTO: ForhandsvarselDTO = forhandsvarselDTO,
    ) = run {
        val url = "$aktivitetskravApiBasePath/$aktivitetskravUuid$forhandsvarselPath"
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
        val url = "$aktivitetskravApiBasePath/$aktivitetskravUuid$vurderAktivitetskravPath"
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
        val url = "$aktivitetskravApiBasePath/$aktivitetskravUuid$vurderAktivitetskravPath"
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

    @Nested
    @DisplayName("Forhåndsvarsel")
    inner class ForhandsvarselTests {

        @BeforeEach
        fun setUp() {
            aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
        }

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Successfully creates a new forhandsvarsel`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val postResponse = client.postForhandsvarsel()
                    assertEquals(HttpStatusCode.Created, postResponse.status)
                    val createdForhandsvarsel = postResponse.body<AktivitetskravVarsel>()
                    assertEquals(forhandsvarselDTO.document, createdForhandsvarsel.document)

                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val responseDTOList = response.body<List<AktivitetskravResponseDTO>>()
                    val aktivitetskravResponseDTO = responseDTOList.first()
                    val vurderingDTO = aktivitetskravResponseDTO.vurderinger.first()
                    val varselResponseDTO = vurderingDTO.varsel
                    assertNotNull(varselResponseDTO)
                    assertEquals(svarfrist, varselResponseDTO?.svarfrist)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Too short svarfrist`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val postResponse = client.postForhandsvarsel(
                        newForhandsvarselDTO = forhandsvarselDTO.copy(frist = svarfristTooShort),
                    )
                    assertEquals(HttpStatusCode.BadRequest, postResponse.status)
                }
            }

            @Test
            fun `Too long svarfrist`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val postResponse = client.postForhandsvarsel(
                        newForhandsvarselDTO = forhandsvarselDTO.copy(frist = svarfristTooLong),
                    )
                    assertEquals(HttpStatusCode.BadRequest, postResponse.status)
                }
            }

            @Test
            fun `Can't find aktivitetskrav for given uuid`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postForhandsvarsel(
                        aktivitetskravUuid = UUID.randomUUID(),
                    )
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `Fails if document is empty`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val varselWithoutDocument = forhandsvarselDTO.copy(document = emptyList())
                    val response = client.postForhandsvarsel(newForhandsvarselDTO = varselWithoutDocument)
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `Fails if already forhandsvarsel`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postForhandsvarsel()
                    assertEquals(HttpStatusCode.Created, response.status)

                    val newResponse = client.postForhandsvarsel()
                    assertEquals(HttpStatusCode.Conflict, newResponse.status)
                }
            }

            @Test
            fun `Fails if already forhandsvarsel before`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postForhandsvarsel()
                    assertEquals(HttpStatusCode.Created, response.status)

                    val avventResponse = client.postAvvent()
                    assertEquals(HttpStatusCode.OK, avventResponse.status)

                    val newResponse = client.postForhandsvarsel()
                    assertEquals(HttpStatusCode.Conflict, newResponse.status)
                }
            }

            @Test
            fun `Fails if already unntak`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postUnntak()
                    assertEquals(HttpStatusCode.OK, response.status)

                    val newResponse = client.postForhandsvarsel()
                    assertEquals(HttpStatusCode.Conflict, newResponse.status)
                }
            }
        }
    }
}
