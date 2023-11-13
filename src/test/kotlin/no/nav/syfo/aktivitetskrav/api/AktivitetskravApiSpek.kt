package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravRepository
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.createAktivitetskravAutomatiskOppfylt
import no.nav.syfo.testhelper.generator.createAktivitetskravForTest
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

class AktivitetskravApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val nyAktivitetskravAnnenPerson = createAktivitetskravNy(
        personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val automatiskOppfyltAktivitetskrav = createAktivitetskravAutomatiskOppfylt(
        tilfelleStart = LocalDate.now().minusYears(1),
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
                    producer = kafkaProducer,
                ),
            )
            val aktivitetskravRepository = AktivitetskravRepository(database)
            val aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVurderingProducer = mockk(relaxed = true),
                database = database,
                arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
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
                navIdent = UserConstants.VEILEDER_IDENT,
            )

            describe("Get aktivitetskrav for person") {
                describe("Happy path") {
                    it("Returns aktivitetskrav (uten vurderinger) for person") {
                        aktivitetskravService.createAktivitetskravForTest(
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
                        aktivitetskravService.createAktivitetskravForTest(
                            nyAktivitetskrav,
                        )

                        val avventVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.AVVENT,
                            createdBy = UserConstants.VEILEDER_IDENT,
                            beskrivelse = "Avvent",
                            arsaker = listOf(
                                VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                                VurderingArsak.INFORMASJON_BEHANDLER,
                                VurderingArsak.DROFTES_MED_ROL,
                                VurderingArsak.DROFTES_INTERNT,
                            ),
                        )
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = avventVurdering
                        )
                        val oppfyltVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.OPPFYLT,
                            createdBy = UserConstants.VEILEDER_IDENT,
                            beskrivelse = "Oppfylt",
                            arsaker = listOf(VurderingArsak.GRADERT),
                        )
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = oppfyltVurdering
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
                            responseDTOList.size shouldBeEqualTo 1

                            val aktivitetskravResponseDTO = responseDTOList.first()
                            aktivitetskravResponseDTO.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                            aktivitetskravResponseDTO.inFinalState.shouldBeTrue()
                            aktivitetskravResponseDTO.vurderinger.size shouldBeEqualTo 2

                            val latestVurdering = aktivitetskravResponseDTO.vurderinger.first()
                            val oldestVurdering = aktivitetskravResponseDTO.vurderinger.last()

                            latestVurdering.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                            latestVurdering.beskrivelse shouldBeEqualTo "Oppfylt"
                            latestVurdering.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            latestVurdering.createdAt shouldBeGreaterThan oldestVurdering.createdAt
                            latestVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.GRADERT)
                            latestVurdering.varsel.shouldBeNull()

                            oldestVurdering.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                            oldestVurdering.beskrivelse shouldBeEqualTo "Avvent"
                            oldestVurdering.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            oldestVurdering.arsaker shouldBeEqualTo listOf(
                                VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                                VurderingArsak.INFORMASJON_BEHANDLER,
                                VurderingArsak.DROFTES_MED_ROL,
                                VurderingArsak.DROFTES_INTERNT,
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
                        aktivitetskravService.createAktivitetskravForTest(
                            aktivitetskravAtCutoffDate,
                            nyAktivitetskrav,
                            automatiskOppfyltAktivitetskravBeforeCutoff,
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
                val newAktivitetskravDto = NewAktivitetskravDTO(UUID.randomUUID())

                describe("Happy path") {
                    it("Creates aktivitetskrav from API request with previous aktivitetskrav") {
                        with(
                            handleRequest(HttpMethod.Post, aktivitetskravApiBasePath) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
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
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
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
                }
            }
        }
    }
})
