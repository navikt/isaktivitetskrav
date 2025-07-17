package no.nav.syfo.aktivitetskrav.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

@DisplayName("AktivitetskravApiVurder")
class AktivitetskravApiVurderTest {
    private val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    private val aktivitetskravUuid = nyAktivitetskrav.uuid

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
    private val aktivitetskravRepository = AktivitetskravRepository(database)
    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val aktivitetskravService = AktivitetskravService(
        aktivitetskravRepository = aktivitetskravRepository,
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = mockk(relaxed = true),
        arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )
    )
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )

    @BeforeEach
    fun setUp() {
        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    private suspend fun HttpClient.postVurdering(
        aktivitetskravUuid: UUID,
        vurderingDTO: AktivitetskravVurderingRequestDTO,
        personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    ): HttpResponse = run {
        val urlVurderAktivitetskrav =
            "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
        post(urlVurderAktivitetskrav) {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personIdent.value)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(vurderingDTO)
        }
    }

    @Nested
    @DisplayName("Vurder aktivitetskrav for person")
    inner class VurderAktivitetskrav {
        private val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
            status = AktivitetskravStatus.OPPFYLT,
            beskrivelse = "Aktivitetskravet er oppfylt",
            arsaker = listOf(Arsak.FRISKMELDT),
            document = generateDocumentComponentDTO("Aktivitetskravet er oppfylt")
        )

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `updates Aktivitetskrav with vurdering and produces to Kafka if request is succesful`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingOppfyltRequestDTO,
                    )
                    assertEquals(HttpStatusCode.OK, response.status)
                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val latestAktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                            .first()
                    assertEquals(vurderingOppfyltRequestDTO.status, latestAktivitetskrav.status)
                    assertTrue(latestAktivitetskrav.updatedAt.isAfter(latestAktivitetskrav.createdAt))

                    val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, kafkaAktivitetskravVurdering.personIdent)
                    assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
                    assertEquals("Aktivitetskravet er oppfylt", kafkaAktivitetskravVurdering.beskrivelse)
                    assertEquals(listOf(Arsak.FRISKMELDT.toString()), kafkaAktivitetskravVurdering.arsaker)
                    assertEquals(UserConstants.VEILEDER_IDENT, kafkaAktivitetskravVurdering.updatedBy)
                    assertEquals(latestAktivitetskravVurdering.uuid, kafkaAktivitetskravVurdering.sisteVurderingUuid)
                    assertEquals(
                        latestAktivitetskravVurdering.createdAt.millisekundOpplosning(),
                        kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning()
                    )
                }
            }

            @Test
            fun `updates Aktivitetskrav already vurdert with new vurdering and produces to Kafka if request is succesful`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val avventVurdering = AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.AVVENT,
                        createdBy = UserConstants.VEILEDER_IDENT,
                        beskrivelse = "Avvent",
                        arsaker = listOf(
                            Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                            Arsak.INFORMASJON_BEHANDLER,
                            Arsak.DROFTES_MED_ROL,
                            Arsak.DROFTES_INTERNT,
                        ),
                    )
                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = avventVurdering,
                            document = emptyList(),
                            callId = "",
                        )
                    }
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingOppfyltRequestDTO,
                    )
                    assertEquals(HttpStatusCode.OK, response.status)
                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val latestAktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                            .first()
                    assertEquals(vurderingOppfyltRequestDTO.status, latestAktivitetskrav.status)
                    assertTrue(latestAktivitetskrav.updatedAt.isAfter(latestAktivitetskrav.createdAt))

                    val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, kafkaAktivitetskravVurdering.personIdent)
                    assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
                    assertEquals("Aktivitetskravet er oppfylt", kafkaAktivitetskravVurdering.beskrivelse)
                    assertEquals(listOf(AktivitetskravVurdering.Oppfylt.Arsak.FRISKMELDT.toString()), kafkaAktivitetskravVurdering.arsaker)
                    assertEquals(UserConstants.VEILEDER_IDENT, kafkaAktivitetskravVurdering.updatedBy)
                    assertEquals(latestAktivitetskravVurdering.uuid, kafkaAktivitetskravVurdering.sisteVurderingUuid)
                    assertEquals(
                        latestAktivitetskravVurdering.createdAt.millisekundOpplosning(),
                        kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning()
                    )
                }
            }

            @Test
            fun `updates Aktivitetskrav with Avvent-vurdering and frist and produces to Kafka if request is successful`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val oneWeekFromNow = LocalDate.now().plusWeeks(1)
                    val vurderingAvventRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.AVVENT,
                        beskrivelse = "Avventer mer informasjon",
                        arsaker = listOf(Arsak.INFORMASJON_BEHANDLER),
                        frist = oneWeekFromNow,
                    )
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingAvventRequestDTO,
                    )
                    assertEquals(HttpStatusCode.OK, response.status)
                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val latestAktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                            .first()
                    val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()
                    assertEquals(oneWeekFromNow, latestAktivitetskravVurdering.frist)

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    assertEquals(latestAktivitetskravVurdering.frist, kafkaAktivitetskravVurdering.frist)
                }
            }

            @Test
            fun `updates Aktivitetskrav with Unntak-vurdering and varsel if request is successful`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val unntakVurderingRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.UNNTAK,
                        beskrivelse = "Aktivitetskravet er oppfylt",
                        arsaker = listOf(Arsak.MEDISINSKE_GRUNNER),
                        document = generateDocumentComponentDTO("Litt fritekst")
                    )
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = unntakVurderingRequestDTO,
                    )
                    assertEquals(HttpStatusCode.OK, response.status)

                    val latestAktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                            .first()
                    val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()
                    assertEquals(AktivitetskravStatus.UNNTAK, latestAktivitetskravVurdering.status)

                    val varsel =
                        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestAktivitetskravVurdering.uuid)
                    assertNotNull(varsel)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {
            private val urlVurderExistingAktivitetskrav =
                "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"

            @Test
            fun `returns status Unauthorized if no token is supplied`() {
                testMissingToken(urlVurderExistingAktivitetskrav, HttpMethod.Post)
            }

            @Test
            fun `returns status Forbidden if denied access to person`() {
                testDeniedPersonAccess(urlVurderExistingAktivitetskrav, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
                testMissingPersonIdent(urlVurderExistingAktivitetskrav, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
                testInvalidPersonIdent(urlVurderExistingAktivitetskrav, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no vurdering exists for uuid-param`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postVurdering(
                        aktivitetskravUuid = UUID.randomUUID(),
                        vurderingDTO = vurderingOppfyltRequestDTO,
                    )
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if NAV_PERSONIDENT_HEADER differs from personIdent for vurdering`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingOppfyltRequestDTO,
                        personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
                    )
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if vurdering is missing arsak`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val vurderingMissingArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.OPPFYLT,
                        beskrivelse = "Aktivitetskravet er oppfylt",
                        arsaker = emptyList(),
                    )
                    val response = client.postVurdering(
                        aktivitetskravUuid = aktivitetskravUuid,
                        vurderingDTO = vurderingMissingArsakRequestDTO,
                    )
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status BadRequest if vurdering has invalid arsak`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val vurderingInvalidArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.UNNTAK,
                        beskrivelse = "Aktivitetskravet er oppfylt",
                        arsaker = listOf(Arsak.TILTAK),
                    )
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingInvalidArsakRequestDTO,
                    )
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status Conflict if existing vurdering is final`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val response1 = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingOppfyltRequestDTO,
                    )
                    assertEquals(HttpStatusCode.OK, response1.status)

                    val vurderingAvventRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.AVVENT,
                        beskrivelse = "Avventer mer informasjon",
                        arsaker = listOf(Arsak.INFORMASJON_BEHANDLER),
                    )
                    val response2 = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingAvventRequestDTO,
                    )
                    assertEquals(HttpStatusCode.Conflict, response2.status)
                }
            }

            @Test
            fun `returns status BadRequest if UNNTAK is missing document`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val vurderingUnntakMissingDocumentRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.UNNTAK,
                        beskrivelse = "Aktivitetskravet er oppfylt",
                        arsaker = listOf(Arsak.MEDISINSKE_GRUNNER),
                    )
                    val responseNew = client.postVurdering(
                        aktivitetskravUuid = aktivitetskravUuid,
                        vurderingDTO = vurderingUnntakMissingDocumentRequestDTO,
                    )
                    assertEquals(HttpStatusCode.BadRequest, responseNew.status)
                }
            }
        }

        @Nested
        @DisplayName("Innstilling om stans vurdering")
        inner class InnstillingOmStansVurdering {
            @Test
            fun `creates stans vurdering and returns 200 OK`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val vurderingInnstillingOmStansRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.INNSTILLING_OM_STANS,
                        beskrivelse = "Stans fordi personen ikke er syk",
                        stansFom = LocalDate.now(),
                        document = generateDocumentComponentDTO("Stans fordi personen ikke er syk")
                    )
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingInnstillingOmStansRequestDTO,
                    )
                    assertEquals(HttpStatusCode.OK, response.status)
                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val latestAktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT).first()
                    val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()
                    assertEquals(AktivitetskravStatus.INNSTILLING_OM_STANS, latestAktivitetskravVurdering.status)
                    assertEquals(vurderingInnstillingOmStansRequestDTO.stansFom, latestAktivitetskravVurdering.stansFom)
                }
            }

            @Test
            fun `fails to create stans vurdering when no document`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val vurderingInnstillingOmStansRequestDTO = AktivitetskravVurderingRequestDTO(
                        status = AktivitetskravStatus.INNSTILLING_OM_STANS,
                        beskrivelse = "Stans fordi personen ikke er syk",
                        arsaker = listOf(),
                        document = emptyList(),
                    )
                    val response = client.postVurdering(
                        aktivitetskravUuid = nyAktivitetskrav.uuid,
                        vurderingDTO = vurderingInnstillingOmStansRequestDTO,
                    )
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }
        }
    }
}
