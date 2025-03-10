package no.nav.syfo.aktivitetskrav.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
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
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
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
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        listOf(nyAktivitetskrav, nyAktivitetskravAnnenPerson, automatiskOppfyltAktivitetskrav).forEach {
                            aktivitetskravRepository.createAktivitetskrav(it)
                        }
                        val response = client.get(urlAktivitetskravPerson) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOList = response.body<List<AktivitetskravResponseDTO>>()
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
                        val beskrivelse = "Oppfylt"
                        val oppfyltVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.OPPFYLT,
                            createdBy = VEILEDER_IDENT,
                            beskrivelse = beskrivelse,
                            arsaker = listOf(Arsak.GRADERT),
                        )
                        runBlocking {
                            aktivitetskravService.vurderAktivitetskrav(
                                aktivitetskrav = nyAktivitetskrav,
                                aktivitetskravVurdering = oppfyltVurdering,
                                document = generateDocumentComponentDTO(beskrivelse),
                                callId = "",
                            )
                        }
                        val response = client.get(urlAktivitetskravPerson) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOList = response.body<List<AktivitetskravResponseDTO>>()
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
                        latestVurdering.arsaker.first() shouldBeEqualTo Arsak.GRADERT
                        latestVurdering.varsel.shouldNotBeNull()

                        oldestVurdering.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                        oldestVurdering.beskrivelse shouldBeEqualTo "Avvent"
                        oldestVurdering.createdBy shouldBeEqualTo VEILEDER_IDENT
                        oldestVurdering.arsaker shouldBeEqualTo listOf(
                            Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                            Arsak.INFORMASJON_SYKMELDT,
                            Arsak.INFORMASJON_BEHANDLER,
                            Arsak.DROFTES_MED_ROL,
                            Arsak.DROFTES_INTERNT,
                        )
                        oldestVurdering.varsel.shouldBeNull()
                    }
                }

                it("Returns aktivitetskrav with stoppunkt after cutoff") {
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
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseDTOList = response.body<List<AktivitetskravResponseDTO>>()
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
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        aktivitetskravRepository.createAktivitetskrav(unntakAktivitetskrav)
                        val response = client.post(aktivitetskravApiBasePath) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(newAktivitetskravDto)
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Created
                        val responseDTO = response.body<AktivitetskravResponseDTO>()
                        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()

                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                        responseDTO.uuid shouldBeEqualTo kafkaAktivitetskravVurdering.uuid
                        kafkaAktivitetskravVurdering.previousAktivitetskravUuid shouldBeEqualTo newAktivitetskravDto.previousAktivitetskravUuid
                    }
                }
                it("Creates aktivitetskrav from API request without previous aktivitetskrav") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.post(aktivitetskravApiBasePath) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                        response.status shouldBeEqualTo HttpStatusCode.Created
                        val responseDTO = response.body<AktivitetskravResponseDTO>()
                        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()

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
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.post(aktivitetskravApiBasePath) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            setBody(NewAktivitetskravDTO(UUID.randomUUID()))
                        }
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("returns status Conflict if previous aktivitetskrav is not final") {
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
                        response.status shouldBeEqualTo HttpStatusCode.Conflict
                    }
                }
            }
        }

        describe("Get historikk") {
            val urlHistorikk = "$aktivitetskravApiBasePath/$aktivitetskravApiHistorikkPath"

            describe("Happy path") {
                it("Returns empty list if no aktivitetskrav") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.get(urlHistorikk) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikkDTOs = response.body<List<HistorikkDTO>>()
                        historikkDTOs.size shouldBeEqualTo 0
                    }
                }

                it("Returns historikk when aktivitetskrav has status NY") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                        val response = client.get(urlHistorikk) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikkDTOs = response.body<List<HistorikkDTO>>()
                        historikkDTOs.size shouldBeEqualTo 1
                        historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.NY
                    }
                }
                it("Returns historikk when aktivitetskrav has status NY_VURDERING") {
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
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val historikkDTOs = response.body<List<HistorikkDTO>>()
                        historikkDTOs.size shouldBeEqualTo 1
                        historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.NY_VURDERING
                    }
                }

                it("Returns historikk for aktivitetskrav with unntak-vurdering") {
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
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val historikkDTOs = response.body<List<HistorikkDTO>>()
                        historikkDTOs.size shouldBeEqualTo 2
                        historikkDTOs[0].status shouldBeEqualTo AktivitetskravStatus.UNNTAK
                        historikkDTOs[1].status shouldBeEqualTo AktivitetskravStatus.NY
                    }
                }

                it("Returns historikk for lukket aktivitetskrav") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
                        aktivitetskravService.lukkAktivitetskrav(nyAktivitetskrav)
                        val response = client.get(urlHistorikk) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENT.value)
                            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val historikkDTOs = response.body<List<HistorikkDTO>>()
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
                frist = LocalDate.now().plusDays(30),
            )
            val pdf = byteArrayOf(0x2E, 100)

            fun createVurdering(status: AktivitetskravStatus, arsaker: List<Arsak> = emptyList(), frist: LocalDate? = null) =
                AktivitetskravVurdering.create(
                    status = status,
                    createdBy = VEILEDER_IDENT,
                    beskrivelse = "En test vurdering",
                    arsaker = arsaker,
                    frist = frist,
                )

            it("Returns NoContent 204 when persons have no aktivitetskrav") {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                        bearerAuth(validToken)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(GetVurderingerRequestBody(listOf(ARBEIDSTAKER_PERSONIDENT.value)))
                    }
                    response.status shouldBeEqualTo HttpStatusCode.NoContent
                }
            }

            it("Return BadRequest 400 when no request payload is received") {
                testApplication {
                    val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                    val response = client.post("$aktivitetskravApiBasePath/get-vurderinger") {
                        bearerAuth(validToken)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            it("Returns OK 200 when persons have aktivitetskrav with most recent vurdering and varsel") {
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
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val responseContent = response.body<GetAktivitetskravForPersonsResponseDTO>()
                    responseContent.aktivitetskravvurderinger.size shouldBeEqualTo 1
                    val aktivitetskrav = responseContent.aktivitetskravvurderinger[ARBEIDSTAKER_PERSONIDENT.value]
                    aktivitetskrav?.vurderinger?.size shouldBeEqualTo 1
                    val forhandsvarselVurdering = aktivitetskrav?.vurderinger?.find { it.status == AktivitetskravStatus.FORHANDSVARSEL }
                    forhandsvarselVurdering?.varsel?.svarfrist shouldNotBe null
                }
            }

            it("Returns OK 200 for mulitple persons with most recent vurdering") {
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
                                personidenter = listOf(ARBEIDSTAKER_PERSONIDENT.value, OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                            )
                        )
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val responseContent = response.body<GetAktivitetskravForPersonsResponseDTO>()
                    responseContent.aktivitetskravvurderinger.size shouldBeEqualTo 2
                    val firstAktivitetskravResponse = responseContent.aktivitetskravvurderinger[ARBEIDSTAKER_PERSONIDENT.value]
                    val secondAktivitetskravResponse = responseContent.aktivitetskravvurderinger[OTHER_ARBEIDSTAKER_PERSONIDENT.value]
                    firstAktivitetskravResponse?.vurderinger?.size shouldBeEqualTo 1
                    secondAktivitetskravResponse?.vurderinger?.size shouldBeEqualTo 1
                    firstAktivitetskravResponse?.vurderinger?.first()?.status shouldBeEqualTo AktivitetskravStatus.FORHANDSVARSEL
                    secondAktivitetskravResponse?.vurderinger?.first()?.status shouldBeEqualTo AktivitetskravStatus.IKKE_OPPFYLT
                }
            }

            it("Only returns aktivitetskrav for persons with access") {
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
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val responseContent = response.body<GetAktivitetskravForPersonsResponseDTO>()
                    responseContent.aktivitetskravvurderinger.size shouldBeEqualTo 2
                    responseContent.aktivitetskravvurderinger.any { it.value.uuid == firstAktivitetskrav.uuid } shouldBe true
                    responseContent.aktivitetskravvurderinger.any { it.value.uuid == secondAktivitetskrav.uuid } shouldBe true
                    responseContent.aktivitetskravvurderinger.none { it.value.uuid == thirdAktivitetskrav.uuid } shouldBe true
                }
            }
        }
    }
})
