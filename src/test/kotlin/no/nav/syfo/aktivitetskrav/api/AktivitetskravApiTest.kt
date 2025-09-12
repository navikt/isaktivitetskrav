package no.nav.syfo.aktivitetskrav.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.AktivitetskravService
import no.nav.syfo.application.VarselPdfService
import no.nav.syfo.api.dto.AktivitetskravResponseDTO
import no.nav.syfo.api.dto.Arsak
import no.nav.syfo.api.dto.ForhandsvarselDTO
import no.nav.syfo.api.dto.GetAktivitetskravForPersonsResponseDTO
import no.nav.syfo.api.dto.GetVurderingerRequestBody
import no.nav.syfo.api.dto.HistorikkDTO
import no.nav.syfo.api.dto.NewAktivitetskravDTO
import no.nav.syfo.api.endpoints.aktivitetskravApiHistorikkPath
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVurderingRecord
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.createAktivitetskravAutomatiskOppfylt
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.createAktivitetskravUnntak
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.nowUTC
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
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.Future

class AktivitetskravApiTest {
    private val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
    private val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/personident"

    private val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(6)
    )
    private val nyAktivitetskravAnnenPerson = createAktivitetskravNy(
        personIdent = OTHER_ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    private val automatiskOppfyltAktivitetskrav = createAktivitetskravAutomatiskOppfylt(
        tilfelleStart = LocalDate.now().minusYears(1),
    ).copy(
        createdAt = nowUTC().minusWeeks(80)
    )
    private val unntakAktivitetskrav = createAktivitetskravUnntak(
        nyAktivitetskrav = createAktivitetskravNy(
            tilfelleStart = LocalDate.now().minusWeeks(10),
        )
    )

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
        navIdent = VEILEDER_IDENT,
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

    @Nested
    @DisplayName("Get aktivitetskrav for person")
    inner class GetAktivitetskravForPerson {

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Returns aktivitetskrav (uten vurderinger) for person`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    listOf(nyAktivitetskrav, nyAktivitetskravAnnenPerson, automatiskOppfyltAktivitetskrav).forEach {
                        aktivitetskravRepository.createAktivitetskrav(it)
                    }
                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val aktivitetskravList = response.body<List<AktivitetskravResponseDTO>>()
                    assertEquals(2, aktivitetskravList.size)
                    val firstAktivitetskravDTO = aktivitetskravList.first()
                    assertEquals(AktivitetskravStatus.NY, firstAktivitetskravDTO.status)
                    assertEquals(0, firstAktivitetskravDTO.vurderinger.size)
                    assertFalse(firstAktivitetskravDTO.inFinalState)
                    val secondAktivitetskravDTO = aktivitetskravList.last()
                    assertEquals(AktivitetskravStatus.AUTOMATISK_OPPFYLT, secondAktivitetskravDTO.status)
                    assertEquals(0, secondAktivitetskravDTO.vurderinger.size)
                    assertTrue(secondAktivitetskravDTO.inFinalState)
                }
            }

            @Test
            fun `Returns aktivitetskrav (med vurderinger) for person`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                    val avventVurdering = AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.AVVENT,
                        createdBy = VEILEDER_IDENT,
                        beskrivelse = "Avvent",
                        arsaker = listOf(
                            Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                            Arsak.INFORMASJON_SYKMELDT,
                            Arsak.INFORMASJON_BEHANDLER,
                            Arsak.DROFTES_MED_ROL,
                            Arsak.DROFTES_INTERNT
                        ),
                    )
                    val oppfyltVurdering = AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.OPPFYLT,
                        createdBy = VEILEDER_IDENT,
                        beskrivelse = "Oppfylt",
                        arsaker = listOf(Arsak.GRADERT),
                    )

                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = avventVurdering,
                            document = generateDocumentComponentDTO("Litt fritekst"),
                            callId = "",
                        )
                        delay(10)
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = oppfyltVurdering,
                            document = generateDocumentComponentDTO("Litt fritekst"),
                            callId = "",
                        )
                    }

                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val aktivitetskravList = response.body<List<AktivitetskravResponseDTO>>()
                    assertEquals(1, aktivitetskravList.size)
                    val aktivitetskravDTO = aktivitetskravList.first()
                    assertEquals(AktivitetskravStatus.OPPFYLT, aktivitetskravDTO.status)
                    assertEquals(2, aktivitetskravDTO.vurderinger.size)
                    val latestVurdering = aktivitetskravDTO.vurderinger.first()
                    assertEquals(VEILEDER_IDENT, latestVurdering.createdBy)
                    assertEquals(AktivitetskravStatus.OPPFYLT, latestVurdering.status)
                    assertEquals(oppfyltVurdering.beskrivelse, latestVurdering.beskrivelse)
                    assertEquals(
                        listOf(Arsak.GRADERT),
                        latestVurdering.arsaker
                    )
                    assertNull(latestVurdering.frist)

                    val oldestVurdering = aktivitetskravDTO.vurderinger.last()
                    assertEquals(VEILEDER_IDENT, oldestVurdering.createdBy)
                    assertEquals(AktivitetskravStatus.AVVENT, oldestVurdering.status)
                    assertEquals(avventVurdering.beskrivelse, oldestVurdering.beskrivelse)
                    assertEquals(
                        listOf(
                            Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                            Arsak.INFORMASJON_SYKMELDT,
                            Arsak.INFORMASJON_BEHANDLER,
                            Arsak.DROFTES_MED_ROL,
                            Arsak.DROFTES_INTERNT
                        ),
                        oldestVurdering.arsaker
                    )
                    assertNull(oldestVurdering.varsel)
                }
            }

            @Test
            fun `Returns aktivitetskrav with stoppunkt after cutoff`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val cutoffDate = externalMockEnvironment.environment.arenaCutoff
                    val aktivitetskravAtCutoffDate = createAktivitetskravNy(
                        tilfelleStart = LocalDate.now().minusYears(1),
                    ).copy(
                        stoppunktAt = cutoffDate
                    )
                    val automatiskOppfyltAktivitetskravBeforeCutoff = createAktivitetskravAutomatiskOppfylt(
                        tilfelleStart = LocalDate.now().minusYears(1),
                    ).copy(
                        stoppunktAt = cutoffDate.minusDays(1)
                    )
                    listOf(
                        nyAktivitetskrav,
                        aktivitetskravAtCutoffDate,
                        automatiskOppfyltAktivitetskravBeforeCutoff
                    ).forEach {
                        aktivitetskravRepository.createAktivitetskrav(it)
                    }
                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val responseDTOList = response.body<List<AktivitetskravResponseDTO>>()
                    assertEquals(1, responseDTOList.size)

                    val aktivitetskrav = responseDTOList.first()
                    assertEquals(AktivitetskravStatus.NY, aktivitetskrav.status)
                    assertFalse(aktivitetskrav.inFinalState)
                    assertEquals(0, aktivitetskrav.vurderinger.size)
                    assertNotNull(aktivitetskrav.createdAt)
                    assertEquals(nyAktivitetskrav.uuid, aktivitetskrav.uuid)
                    assertEquals(nyAktivitetskrav.stoppunktAt, aktivitetskrav.stoppunktAt)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val response = client.get(urlAktivitetskravPerson) {
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                    }
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                }
            }

            @Test
            fun `Returns status Forbidden if denied access to person`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, PERSONIDENT_VEILEDER_NO_ACCESS.value)
                    }
                    assertEquals(HttpStatusCode.Forbidden, response.status)
                }
            }

            @Test
            fun `Returns status BadRequest if personIdent is missing`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                    }
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `Returns status BadRequest if personIdent has invalid format`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val response = client.get(urlAktivitetskravPerson) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, "not-a-personident")
                    }
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }
        }
    }

    @Nested
    @DisplayName("Create aktivitetskrav for person")
    inner class CreateAktivitetskravForPerson {

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Creates aktivitetskrav from API request with previous aktivitetskrav in final state`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    aktivitetskravRepository.createAktivitetskrav(unntakAktivitetskrav)
                    val response = client.post(aktivitetskravApiBasePath) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(NewAktivitetskravDTO(unntakAktivitetskrav.uuid))
                    }
                    assertEquals(HttpStatusCode.Created, response.status)
                    val responseDTO = response.body<AktivitetskravResponseDTO>()
                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()

                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    assertEquals(responseDTO.uuid, kafkaAktivitetskravVurdering.uuid)
                    assertEquals(kafkaAktivitetskravVurdering.previousAktivitetskravUuid, unntakAktivitetskrav.uuid)
                }
            }

            @Test
            fun `Creates aktivitetskrav from API request without previous aktivitetskrav`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.post(aktivitetskravApiBasePath) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    assertEquals(HttpStatusCode.Created, response.status)
                    val responseDTO = response.body<AktivitetskravResponseDTO>()
                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()

                    verify(exactly = 1) {
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    assertEquals(responseDTO.uuid, kafkaAktivitetskravVurdering.uuid)
                    assertEquals(kafkaAktivitetskravVurdering.previousAktivitetskravUuid, null)
                }
            }
        }

        @Nested
        @DisplayName("Unhappy path")
        inner class UnhappyPath {

            @Test
            fun `Returns status Unauthorized if no token is supplied`() {
                testMissingToken(aktivitetskravApiBasePath, HttpMethod.Post)
            }

            @Test
            fun `returns status Forbidden if denied access to person`() {
                testDeniedPersonAccess(aktivitetskravApiBasePath, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if personIdent is missing`() {
                testMissingPersonIdent(aktivitetskravApiBasePath, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if personIdent has invalid format`() {
                testInvalidPersonIdent(aktivitetskravApiBasePath, validToken, HttpMethod.Post)
            }

            @Test
            fun `returns status BadRequest if no previous aktivitetskrav exists for uuid`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.post(aktivitetskravApiBasePath) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(NewAktivitetskravDTO(UUID.randomUUID()))
                    }
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

            @Test
            fun `returns status Conflict if previous aktivitetskrav is not final`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)

                    val previousAktivitetskravUuid = nyAktivitetskrav.uuid
                    aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                    val response = client.post(aktivitetskravApiBasePath) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(NewAktivitetskravDTO(previousAktivitetskravUuid))
                    }
                    assertEquals(HttpStatusCode.Conflict, response.status)
                }
            }
        }
    }

    @Nested
    @DisplayName("Get historikk")
    inner class GetHistorikk {

        private val urlHistorikk = "$aktivitetskravApiBasePath/$aktivitetskravApiHistorikkPath"

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {

            @Test
            fun `Returns empty list if no aktivitetskrav`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.get(urlHistorikk) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val historikkDTOs = response.body<List<HistorikkDTO>>()
                    assertTrue(historikkDTOs.isEmpty())
                }
            }

            @Test
            fun `Returns historikk when aktivitetskrav has status NY`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                    val response = client.get(urlHistorikk) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val historikkDTOs = response.body<List<HistorikkDTO>>()
                    assertEquals(1, historikkDTOs.size)
                    assertEquals(AktivitetskravStatus.NY, historikkDTOs[0].status)
                }
            }

            @Test
            fun `Returns historikk when aktivitetskrav has status NY_VURDERING`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    aktivitetskravRepository.createAktivitetskrav(
                        nyAktivitetskrav.copy(
                            createdAt = OffsetDateTime.of(
                                nyAktivitetskrav.stoppunktAt,
                                LocalTime.MIDNIGHT,
                                ZoneOffset.UTC
                            ) // createdAt same date as stoppunkt
                        )
                    )
                    val response = client.get(urlHistorikk) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    assertEquals(HttpStatusCode.OK, response.status)

                    val historikkDTOs = response.body<List<HistorikkDTO>>()
                    assertEquals(1, historikkDTOs.size)
                    assertEquals(AktivitetskravStatus.NY_VURDERING, historikkDTOs[0].status)
                }
            }

            @Test
            fun `Returns historikk for aktivitetskrav with unntak-vurdering`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = AktivitetskravVurdering.create(
                                status = AktivitetskravStatus.UNNTAK,
                                createdBy = VEILEDER_IDENT,
                                beskrivelse = "Unntak",
                                arsaker = listOf(Arsak.SJOMENN_UTENRIKS),
                            ),
                            document = generateDocumentComponentDTO("Litt fritekst"),
                            callId = "",
                        )
                    }

                    val response = client.get(urlHistorikk) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                    val historikkDTOs = response.body<List<HistorikkDTO>>()
                    assertEquals(2, historikkDTOs.size)
                    assertEquals(AktivitetskravStatus.UNNTAK, historikkDTOs[0].status)
                    assertEquals(AktivitetskravStatus.NY, historikkDTOs[1].status)
                }
            }

            @Test
            fun `Returns historikk for lukket aktivitetskrav`() {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                    aktivitetskravService.lukkAktivitetskrav(nyAktivitetskrav)
                    val response = client.get(urlHistorikk) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                    val historikkDTOs = response.body<List<HistorikkDTO>>()
                    assertEquals(2, historikkDTOs.size)
                    assertEquals(AktivitetskravStatus.LUKKET, historikkDTOs[0].status)
                    assertEquals(AktivitetskravStatus.NY, historikkDTOs[1].status)
                }
            }
        }
    }

    @Nested
    @DisplayName("POST /get-vurderinger")
    inner class PostGetVurderinger {

        private val fritekst = "Et forhåndsvarsel"
        private val document = generateDocumentComponentDTO(fritekst = fritekst)
        private val forhandsvarselDTO = ForhandsvarselDTO(
            fritekst = fritekst,
            document = document,
            frist = LocalDate.now().plusDays(30),
        )
        private val pdf = byteArrayOf(0x2E, 100)

        fun createVurdering(status: AktivitetskravStatus, arsaker: List<Arsak> = emptyList(), frist: LocalDate? = null) =
            AktivitetskravVurdering.create(
                status = status,
                createdBy = VEILEDER_IDENT,
                beskrivelse = "En test vurdering",
                arsaker = arsaker,
                frist = frist,
            )

        @Test
        fun `Returns NoContent 204 when persons have no aktivitetskrav`() {
            testApplication {
                val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(GetVurderingerRequestBody(listOf(ARBEIDSTAKER_PERSONIDENT.value)))
                }
                assertEquals(HttpStatusCode.NoContent, response.status)
            }
        }

        @Test
        fun `Return BadRequest 400 when no request payload is received`() {
            testApplication {
                val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

        @Test
        fun `Returns OK 200 when persons have aktivitetskrav with most recent vurdering and varsel`() {
            testApplication {
                val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                val newVurdering = createVurdering(
                    AktivitetskravStatus.FORHANDSVARSEL,
                    frist = LocalDate.now().plusDays(30)
                ).also { firstAktivitetskrav.vurder(it) }
                val newVarsel = AktivitetskravVarsel.create(
                    type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    frist = LocalDate.now().plusDays(30),
                    document = forhandsvarselDTO.document,
                )
                aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                    firstAktivitetskrav,
                    newVurdering,
                    newVarsel,
                    pdf
                )
                val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(GetVurderingerRequestBody(listOf(ARBEIDSTAKER_PERSONIDENT.value)))
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val responseContent = response.body<GetAktivitetskravForPersonsResponseDTO>()
                assertEquals(1, responseContent.aktivitetskravvurderinger.size)
                val aktivitetskrav = responseContent.aktivitetskravvurderinger[ARBEIDSTAKER_PERSONIDENT.value]
                assertEquals(1, aktivitetskrav?.vurderinger?.size)
                val forhandsvarselVurdering = aktivitetskrav?.vurderinger?.find { it.status == AktivitetskravStatus.FORHANDSVARSEL }
                assertNotNull(forhandsvarselVurdering?.varsel?.svarfrist)
            }
        }

        @Test
        fun `Returns OK 200 for mulitple persons with most recent vurdering`() {
            testApplication {
                val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                createVurdering(
                    status = AktivitetskravStatus.AVVENT,
                    arsaker = listOf(Arsak.INFORMASJON_SYKMELDT),
                ).also {
                    firstAktivitetskrav.vurder(it)
                    aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                }
                val newVurdering = createVurdering(
                    AktivitetskravStatus.FORHANDSVARSEL,
                    frist = LocalDate.now().plusDays(30)
                ).also { firstAktivitetskrav.vurder(it) }
                val newVarsel = AktivitetskravVarsel.create(
                    type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    frist = LocalDate.now().plusDays(30),
                    document = forhandsvarselDTO.document,
                )
                aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                    firstAktivitetskrav,
                    newVurdering,
                    newVarsel,
                    pdf
                )
                val secondAktivitetskrav = Aktivitetskrav.create(OTHER_ARBEIDSTAKER_PERSONIDENT)
                aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
                createVurdering(AktivitetskravStatus.FORHANDSVARSEL, frist = LocalDate.now().plusDays(14)).also {
                    secondAktivitetskrav.vurder(it)
                    aktivitetskravRepository.createAktivitetskravVurdering(secondAktivitetskrav, it)
                }
                createVurdering(AktivitetskravStatus.IKKE_OPPFYLT).also {
                    secondAktivitetskrav.vurder(it)
                    aktivitetskravRepository.createAktivitetskravVurdering(secondAktivitetskrav, it)
                }
                val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                    bearerAuth(validToken)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(
                        GetVurderingerRequestBody(
                            personidenter = listOf(
                                ARBEIDSTAKER_PERSONIDENT.value,
                                OTHER_ARBEIDSTAKER_PERSONIDENT.value
                            )
                        )
                    )
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val responseContent = response.body<GetAktivitetskravForPersonsResponseDTO>()
                assertEquals(2, responseContent.aktivitetskravvurderinger.size)
                val firstAktivitetskravResponse = responseContent.aktivitetskravvurderinger[ARBEIDSTAKER_PERSONIDENT.value]
                val secondAktivitetskravResponse = responseContent.aktivitetskravvurderinger[OTHER_ARBEIDSTAKER_PERSONIDENT.value]
                assertEquals(1, firstAktivitetskravResponse?.vurderinger?.size)
                assertEquals(1, secondAktivitetskravResponse?.vurderinger?.size)
                assertEquals(AktivitetskravStatus.FORHANDSVARSEL, firstAktivitetskravResponse?.vurderinger?.first()?.status)
                assertEquals(AktivitetskravStatus.IKKE_OPPFYLT, secondAktivitetskravResponse?.vurderinger?.first()?.status)
            }
        }
    }

    @Test
    fun `Only returns aktivitetskrav for persons with access`() {
        testApplication {
            val client = setupApiAndClient(kafkaProducer = kafkaProducer)
            val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
            aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
            val secondAktivitetskrav = Aktivitetskrav.create(OTHER_ARBEIDSTAKER_PERSONIDENT)
            aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
            val thirdAktivitetskrav = Aktivitetskrav.create(PERSONIDENT_VEILEDER_NO_ACCESS)
            aktivitetskravRepository.createAktivitetskrav(thirdAktivitetskrav, UUID.randomUUID())
            val personsWithAktivitetskrav =
                listOf(ARBEIDSTAKER_PERSONIDENT.value, OTHER_ARBEIDSTAKER_PERSONIDENT.value, PERSONIDENT_VEILEDER_NO_ACCESS.value)
            val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                bearerAuth(validToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    GetVurderingerRequestBody(
                        personidenter = personsWithAktivitetskrav,
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val responseContent = response.body<GetAktivitetskravForPersonsResponseDTO>()
            assertEquals(2, responseContent.aktivitetskravvurderinger.size)
            assertTrue(responseContent.aktivitetskravvurderinger.any { it.value.uuid == firstAktivitetskrav.uuid })
            assertTrue(responseContent.aktivitetskravvurderinger.any { it.value.uuid == secondAktivitetskrav.uuid })
            assertTrue(responseContent.aktivitetskravvurderinger.none { it.value.uuid == thirdAktivitetskrav.uuid })
        }
    }
}
