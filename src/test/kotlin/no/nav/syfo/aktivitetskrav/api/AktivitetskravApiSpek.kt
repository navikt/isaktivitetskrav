package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.generator.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*
import java.util.*
import java.util.concurrent.Future

class AktivitetskravApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
    val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/personident"

    val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(6)
    )
    val nyAktivitetskravAnnenPerson = createAktivitetskravNy(
        personIdent = OTHER_ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val automatiskOppfyltAktivitetskrav = createAktivitetskravAutomatiskOppfylt(
        tilfelleStart = LocalDate.now().minusYears(1),
    ).copy(
        createdAt = nowUTC().minusWeeks(80)
    )
    val unntakAktivitetskrav = createAktivitetskravUnntak(
        nyAktivitetskrav = createAktivitetskravNy(
            tilfelleStart = LocalDate.now().minusWeeks(10),
        )
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
                    producer = kafkaProducer,
                ),
            )
            val aktivitetskravRepository = AktivitetskravRepository(database)
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
            val aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = mockk(relaxed = true),
                arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
                varselPdfService = VarselPdfService(
                    pdfGenClient = externalMockEnvironment.pdfgenClient,
                    pdlClient = externalMockEnvironment.pdlClient,
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

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = VEILEDER_IDENT,
            )

            describe("Get aktivitetskrav for person") {
                describe("Happy path") {
                    it("Returns aktivitetskrav (uten vurderinger) for person") {
                        listOf(nyAktivitetskrav, nyAktivitetskravAnnenPerson, automatiskOppfyltAktivitetskrav).forEach {
                            aktivitetskravRepository.createAktivitetskrav(it)
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravResponseDTO>>(response.content!!)
                            responseDTOList.size shouldBeEqualTo 2

                            val first = responseDTOList.first()
                            first.status shouldBeEqualTo AktivitetskravStatus.NY
                            first.inFinalState.shouldBeFalse()
                            first.vurderinger.size shouldBeEqualTo 0
                            first.createdAt shouldNotBeEqualTo null
                            first.uuid shouldNotBeEqualTo null

                            val last = responseDTOList.last()
                            last.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                            last.inFinalState.shouldBeTrue()
                            last.vurderinger.size shouldBeEqualTo 0
                            last.createdAt shouldNotBeEqualTo null
                            last.uuid shouldNotBeEqualTo null
                        }
                    }
                    it("Returns aktivitetskrav (med vurderinger) for person") {
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                        val avventVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.AVVENT,
                            createdBy = VEILEDER_IDENT,
                            beskrivelse = "Avvent",
                            arsaker = listOf(
                                VurderingArsak.Avvent.OppfolgingsplanArbeidsgiver,
                                VurderingArsak.Avvent.InformasjonSykmeldt,
                                VurderingArsak.Avvent.InformasjonBehandler,
                                VurderingArsak.Avvent.DroftesMedROL,
                                VurderingArsak.Avvent.DroftesInternt,
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
                        val beskrivelse = "Oppfylt"
                        val oppfyltVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.OPPFYLT,
                            createdBy = VEILEDER_IDENT,
                            beskrivelse = beskrivelse,
                            arsaker = listOf(VurderingArsak.Oppfylt.Gradert),
                        )
                        runBlocking {
                            aktivitetskravService.vurderAktivitetskrav(
                                aktivitetskrav = nyAktivitetskrav,
                                aktivitetskravVurdering = oppfyltVurdering,
                                document = generateDocumentComponentDTO(beskrivelse),
                                callId = "",
                            )
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravResponseDTO>>(response.content!!)
                            responseDTOList.size shouldBeEqualTo 1

                            val aktivitetskravResponseDTO = responseDTOList.first()
                            aktivitetskravResponseDTO.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                            aktivitetskravResponseDTO.inFinalState.shouldBeTrue()
                            aktivitetskravResponseDTO.vurderinger.size shouldBeEqualTo 2

                            val latestVurdering = aktivitetskravResponseDTO.vurderinger.first()
                            val oldestVurdering = aktivitetskravResponseDTO.vurderinger.last()

                            latestVurdering.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                            latestVurdering.beskrivelse shouldBeEqualTo beskrivelse
                            latestVurdering.createdBy shouldBeEqualTo VEILEDER_IDENT
                            latestVurdering.createdAt shouldBeGreaterThan oldestVurdering.createdAt
                            latestVurdering.arsaker.first()
                                .toVurderingArsak(AktivitetskravStatus.OPPFYLT) shouldBeEqualTo VurderingArsak.Oppfylt.Gradert
                            latestVurdering.varsel.shouldNotBeNull()

                            oldestVurdering.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                            oldestVurdering.beskrivelse shouldBeEqualTo "Avvent"
                            oldestVurdering.createdBy shouldBeEqualTo VEILEDER_IDENT
                            oldestVurdering.arsaker.map { it.toVurderingArsak(AktivitetskravStatus.AVVENT) } shouldBeEqualTo listOf(
                                VurderingArsak.Avvent.OppfolgingsplanArbeidsgiver,
                                VurderingArsak.Avvent.InformasjonSykmeldt,
                                VurderingArsak.Avvent.InformasjonBehandler,
                                VurderingArsak.Avvent.DroftesMedROL,
                                VurderingArsak.Avvent.DroftesInternt,
                            )
                            oldestVurdering.varsel.shouldBeNull()
                        }
                    }

                    it("Returns aktivitetskrav with stoppunkt after cutoff") {
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

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravResponseDTO>>(response.content!!)
                            responseDTOList.size shouldBeEqualTo 1

                            val aktivitetskrav = responseDTOList.first()
                            aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                            aktivitetskrav.inFinalState.shouldBeFalse()
                            aktivitetskrav.vurderinger.size shouldBeEqualTo 0
                            aktivitetskrav.createdAt shouldNotBeEqualTo null
                            aktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                            aktivitetskrav.stoppunktAt shouldBeEqualTo nyAktivitetskrav.stoppunktAt
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlAktivitetskravPerson, HttpMethod.Get)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlAktivitetskravPerson, validToken, HttpMethod.Get)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlAktivitetskravPerson, validToken, HttpMethod.Get)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlAktivitetskravPerson, validToken, HttpMethod.Get)
                    }
                }
            }

            describe("Create aktivitetskrav for person") {
                val newAktivitetskravDto = NewAktivitetskravDTO(unntakAktivitetskrav.uuid)

                describe("Happy path") {
                    it("Creates aktivitetskrav from API request with previous aktivitetskrav in final state") {
                        aktivitetskravRepository.createAktivitetskrav(unntakAktivitetskrav)
                        with(
                            handleRequest(HttpMethod.Post, aktivitetskravApiBasePath) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(newAktivitetskravDto))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                            val responseDTO = objectMapper.readValue<AktivitetskravResponseDTO>(response.content!!)
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()

                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            responseDTO.uuid shouldBeEqualTo kafkaAktivitetskravVurdering.uuid
                            kafkaAktivitetskravVurdering.previousAktivitetskravUuid shouldBeEqualTo newAktivitetskravDto.previousAktivitetskravUuid
                        }
                    }
                    it("Creates aktivitetskrav from API request without previous aktivitetskrav") {
                        with(
                            handleRequest(HttpMethod.Post, aktivitetskravApiBasePath) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                            val responseDTO = objectMapper.readValue<AktivitetskravResponseDTO>(response.content!!)
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()

                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            responseDTO.uuid shouldBeEqualTo kafkaAktivitetskravVurdering.uuid
                            kafkaAktivitetskravVurdering.previousAktivitetskravUuid shouldBeEqualTo null
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(aktivitetskravApiBasePath, HttpMethod.Post)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(aktivitetskravApiBasePath, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(aktivitetskravApiBasePath, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(aktivitetskravApiBasePath, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no previous aktivitetskrav exists for uuid") {
                        with(
                            handleRequest(HttpMethod.Post, aktivitetskravApiBasePath) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(NewAktivitetskravDTO(UUID.randomUUID())))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if previous aktivitetskrav is not final") {
                        val previousAktivitetskravUuid = nyAktivitetskrav.uuid
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                        with(
                            handleRequest(HttpMethod.Post, aktivitetskravApiBasePath) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(NewAktivitetskravDTO(previousAktivitetskravUuid)))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }

            describe("Get historikk") {
                val urlHistorikk = "$aktivitetskravApiBasePath/$aktivitetskravApiHistorikkPath"

                describe("Happy path") {
                    it("Returns empty list if no aktivitetskrav") {
                        with(
                            handleRequest(HttpMethod.Get, urlHistorikk) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val historikkDTOs = objectMapper.readValue<List<HistorikkDTO>>(response.content!!)
                            historikkDTOs.size shouldBeEqualTo 0
                        }
                    }

                    it("Returns historikk when aktivitetskrav has status NY") {
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                        with(
                            handleRequest(HttpMethod.Get, urlHistorikk) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val historikkDTOs = objectMapper.readValue<List<HistorikkDTO>>(response.content!!)
                            historikkDTOs.size shouldBeEqualTo 1
                            historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.NY
                        }
                    }
                    it("Returns historikk when aktivitetskrav has status NY_VURDERING") {
                        aktivitetskravRepository.createAktivitetskrav(
                            nyAktivitetskrav.copy(
                                createdAt = OffsetDateTime.of(
                                    nyAktivitetskrav.stoppunktAt,
                                    LocalTime.MIDNIGHT,
                                    ZoneOffset.UTC
                                ) // createdAt same date as stoppunkt
                            )
                        )
                        with(
                            handleRequest(HttpMethod.Get, urlHistorikk) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val historikkDTOs = objectMapper.readValue<List<HistorikkDTO>>(response.content!!)
                            historikkDTOs.size shouldBeEqualTo 1
                            historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.NY_VURDERING
                        }
                    }

                    it("Returns historikk for aktivitetskrav with unntak-vurdering") {
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                        runBlocking {
                            aktivitetskravService.vurderAktivitetskrav(
                                aktivitetskrav = nyAktivitetskrav,
                                aktivitetskravVurdering = AktivitetskravVurdering.create(
                                    status = AktivitetskravStatus.UNNTAK,
                                    createdBy = VEILEDER_IDENT,
                                    beskrivelse = "Unntak",
                                    arsaker = listOf(VurderingArsak.Unntak.SjomennUtenriks),
                                ),
                                document = generateDocumentComponentDTO("Litt fritekst"),
                                callId = "",
                            )
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlHistorikk) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val historikkDTOs = objectMapper.readValue<List<HistorikkDTO>>(response.content!!)
                            historikkDTOs.size shouldBeEqualTo 2
                            historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.UNNTAK
                            historikkDTOs[1].status shouldBeEqualTo AktivitetskravStatus.NY
                        }
                    }

                    it("Returns historikk for lukket aktivitetskrav") {
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                        aktivitetskravService.lukkAktivitetskrav(nyAktivitetskrav)

                        with(
                            handleRequest(HttpMethod.Get, urlHistorikk) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val historikkDTOs = objectMapper.readValue<List<HistorikkDTO>>(response.content!!)
                            historikkDTOs.size shouldBeEqualTo 2
                            historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.LUKKET
                            historikkDTOs[1].status shouldBeEqualTo AktivitetskravStatus.NY
                        }
                    }
                }
            }

            describe("POST /get-vurderinger") {

                val fritekst = "Et forhåndsvarsel"
                val document = generateDocumentComponentDTO(fritekst = fritekst)
                val forhandsvarselDTO = ForhandsvarselDTO(
                    fritekst = fritekst,
                    document = document,
                )
                val pdf = byteArrayOf(0x2E, 100)

                fun createVurdering(status: AktivitetskravStatus, arsaker: List<VurderingArsak> = emptyList(), frist: LocalDate? = null) =
                    AktivitetskravVurdering.create(
                        status = status,
                        createdBy = VEILEDER_IDENT,
                        beskrivelse = "En test vurdering",
                        arsaker = arsaker,
                        frist = frist,
                    )

                it("Returns NoContent 204 when persons have no aktivitetskrav") {
                    with(
                        handleRequest(HttpMethod.Post, "$aktivitetskravApiBasePath/get-vurderinger") {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(GetVurderingerRequestBody(listOf(ARBEIDSTAKER_PERSONIDENT.value))))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.NoContent
                    }
                }

                it("Return BadRequest 400 when no request payload is received") {
                    with(
                        handleRequest(HttpMethod.Post, "$aktivitetskravApiBasePath/get-vurderinger") {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("Returns OK 200 when persons have aktivitetskrav with most recent vurdering and varsel") {
                    val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                    val newVurdering = createVurdering(AktivitetskravStatus.FORHANDSVARSEL).also { firstAktivitetskrav.vurder(it) }
                    val newVarsel = AktivitetskravVarsel.create(
                        VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                        forhandsvarselDTO.document
                    )
                    aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                        firstAktivitetskrav,
                        newVurdering,
                        newVarsel,
                        pdf
                    )
                    with(
                        handleRequest(HttpMethod.Post, "$aktivitetskravApiBasePath/get-vurderinger") {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(
                                objectMapper.writeValueAsString(
                                    GetVurderingerRequestBody(personidenter = listOf(ARBEIDSTAKER_PERSONIDENT.value))
                                )
                            )
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val responseContent = objectMapper.readValue<GetAktivitetskravForPersonsResponseDTO>(response.content!!)
                        responseContent.aktivitetskravvurderinger.size shouldBeEqualTo 1
                        val aktivitetskrav = responseContent.aktivitetskravvurderinger[ARBEIDSTAKER_PERSONIDENT.value]
                        aktivitetskrav?.vurderinger?.size shouldBeEqualTo 1
                        val forhandsvarselVurdering = aktivitetskrav?.vurderinger?.find { it.status == AktivitetskravStatus.FORHANDSVARSEL }
                        forhandsvarselVurdering?.varsel?.svarfrist shouldNotBe null
                    }
                }

                it("Returns OK 200 for mulitple persons with most recent vurdering") {
                    val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                    createVurdering(
                        status = AktivitetskravStatus.AVVENT,
                        arsaker = listOf(VurderingArsak.Avvent.InformasjonSykmeldt)
                    ).also {
                        firstAktivitetskrav.vurder(it)
                        aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                    }
                    val newVurdering = createVurdering(AktivitetskravStatus.FORHANDSVARSEL).also { firstAktivitetskrav.vurder(it) }
                    val newVarsel = AktivitetskravVarsel.create(
                        VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                        forhandsvarselDTO.document
                    )
                    aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                        firstAktivitetskrav,
                        newVurdering,
                        newVarsel,
                        pdf
                    )
                    val secondAktivitetskrav = Aktivitetskrav.create(OTHER_ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
                    createVurdering(AktivitetskravStatus.FORHANDSVARSEL).also {
                        secondAktivitetskrav.vurder(it)
                        aktivitetskravRepository.createAktivitetskravVurdering(secondAktivitetskrav, it)
                    }
                    createVurdering(AktivitetskravStatus.IKKE_OPPFYLT).also {
                        secondAktivitetskrav.vurder(it)
                        aktivitetskravRepository.createAktivitetskravVurdering(secondAktivitetskrav, it)
                    }
                    with(
                        handleRequest(HttpMethod.Post, "$aktivitetskravApiBasePath/get-vurderinger") {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(
                                objectMapper.writeValueAsString(
                                    GetVurderingerRequestBody(
                                        personidenter = listOf(ARBEIDSTAKER_PERSONIDENT.value, OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                                    )
                                )
                            )
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val responseContent = objectMapper.readValue<GetAktivitetskravForPersonsResponseDTO>(response.content!!)
                        responseContent.aktivitetskravvurderinger.size shouldBe 2
                        val firstAktivitetskravResponse = responseContent.aktivitetskravvurderinger[ARBEIDSTAKER_PERSONIDENT.value]
                        val secondAktivitetskravResponse = responseContent.aktivitetskravvurderinger[OTHER_ARBEIDSTAKER_PERSONIDENT.value]
                        firstAktivitetskravResponse?.vurderinger?.size shouldBe 1
                        secondAktivitetskravResponse?.vurderinger?.size shouldBe 1
                        firstAktivitetskravResponse?.vurderinger?.first()?.status shouldBe AktivitetskravStatus.FORHANDSVARSEL
                        secondAktivitetskravResponse?.vurderinger?.first()?.status shouldBe AktivitetskravStatus.IKKE_OPPFYLT
                    }
                }

                it("Only returns aktivitetskrav for persons with access") {
                    val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                    val secondAktivitetskrav = Aktivitetskrav.create(OTHER_ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
                    val thirdAktivitetskrav = Aktivitetskrav.create(PERSONIDENT_VEILEDER_NO_ACCESS)
                    aktivitetskravRepository.createAktivitetskrav(thirdAktivitetskrav, UUID.randomUUID())
                    val personsWithAktivitetskrav =
                        listOf(ARBEIDSTAKER_PERSONIDENT.value, OTHER_ARBEIDSTAKER_PERSONIDENT.value, PERSONIDENT_VEILEDER_NO_ACCESS.value)

                    with(
                        handleRequest(HttpMethod.Post, "$aktivitetskravApiBasePath/get-vurderinger") {
                            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            setBody(objectMapper.writeValueAsString(GetVurderingerRequestBody(personidenter = personsWithAktivitetskrav)))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val responseContent = objectMapper.readValue<GetAktivitetskravForPersonsResponseDTO>(response.content!!)
                        responseContent.aktivitetskravvurderinger.size shouldBeEqualTo 2
                        responseContent.aktivitetskravvurderinger.any { it.value.uuid == firstAktivitetskrav.uuid } shouldBe true
                        responseContent.aktivitetskravvurderinger.any { it.value.uuid == secondAktivitetskrav.uuid } shouldBe true
                        responseContent.aktivitetskravvurderinger.none { it.value.uuid == thirdAktivitetskrav.uuid } shouldBe true
                    }
                }
            }
        }
    }
})
