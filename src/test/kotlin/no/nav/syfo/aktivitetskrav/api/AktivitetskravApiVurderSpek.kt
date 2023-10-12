package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravRepository
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
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

class AktivitetskravApiVurderSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )

    describe(AktivitetskravApiVurderSpek::class.java.simpleName) {
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
            val aktivitetskravRepository = AktivitetskravRepository(database)
            val aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVurderingProducer = mockk(relaxed = true),
                database = database,
                arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
            )
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
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

            fun postVurdering(
                vurderingDTO: AktivitetskravVurderingRequestDTO,
            ) = run {
                val urlVurderAktivitetskrav = "$aktivitetskravApiBasePath$vurderAktivitetskravPath"
                handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                    setBody(objectMapper.writeValueAsString(vurderingDTO))
                }
            }

            fun postEndreVurdering(
                aktivitetskravUuid: UUID,
                vurderingDTO: AktivitetskravVurderingRequestDTO,
            ) = run {
                val urlVurderAktivitetskrav = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
                handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                    setBody(objectMapper.writeValueAsString(vurderingDTO))
                }
            }

            describe("Vurder aktivitetskrav for person") {
                val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                    status = AktivitetskravStatus.OPPFYLT,
                    beskrivelse = "Aktivitetskravet er oppfylt",
                    arsaker = listOf(VurderingArsak.FRISKMELDT),
                )

                describe("Happy path") {
                    it("Creates aktivitetskrav with vurdering and stoppunkt today and produces to Kafka if request is succesful") {
                        with(postVurdering(vurderingDTO = vurderingOppfyltRequestDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val pAktivitetskravList =
                                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                            pAktivitetskravList.size shouldBeEqualTo 1
                            val aktivitetskrav = pAktivitetskravList.first()
                            aktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status.name
                            aktivitetskrav.stoppunktAt shouldBeEqualTo LocalDate.now()
                            aktivitetskrav.referanseTilfelleBitUuid shouldBeEqualTo null

                            val pAktivitetskravVurderingList = aktivitetskrav.vurderinger
                            pAktivitetskravVurderingList.size shouldBeEqualTo 1
                            val aktivitetskravVurdering = pAktivitetskravVurderingList.first()

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                            kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status
                            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo aktivitetskravVurdering.createdAt.millisekundOpplosning()
                            kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                        }
                    }
                }
                describe("Unhappy path") {
                    val urlVurderAktivitetskrav = "$aktivitetskravApiBasePath/$vurderAktivitetskravPath"
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlVurderAktivitetskrav, HttpMethod.Post)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlVurderAktivitetskrav, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlVurderAktivitetskrav, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlVurderAktivitetskrav, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if vurdering is missing arsak") {
                        val vurderingMissingArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.OPPFYLT,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = emptyList(),
                        )

                        with(postVurdering(vurderingDTO = vurderingMissingArsakRequestDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if vurdering has invalid arsak") {
                        val vurderingInvalidArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = listOf(VurderingArsak.TILTAK),
                        )

                        with(postVurdering(vurderingDTO = vurderingInvalidArsakRequestDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if already oppfylt") {
                        val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.OPPFYLT,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = listOf(VurderingArsak.FRISKMELDT),
                        )
                        val vurderingUnntakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Unntak",
                            arsaker = listOf(VurderingArsak.MEDISINSKE_GRUNNER),
                        )
                        val aktivitetskravUuid = aktivitetskravService.createAndVurderAktivitetskrav(
                            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            aktivitetskravVurdering = vurderingOppfyltRequestDTO.toAktivitetskravVurdering("X999999"),
                        )

                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = aktivitetskravUuid,
                                vurderingDTO = vurderingUnntakRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if already unntak") {
                        val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.OPPFYLT,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = listOf(VurderingArsak.FRISKMELDT),
                        )
                        val vurderingUnntakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Unntak",
                            arsaker = listOf(VurderingArsak.MEDISINSKE_GRUNNER),
                        )
                        val aktivitetskravUuid = aktivitetskravService.createAndVurderAktivitetskrav(
                            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            aktivitetskravVurdering = vurderingUnntakRequestDTO.toAktivitetskravVurdering("X999999"),
                        )

                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = aktivitetskravUuid,
                                vurderingDTO = vurderingOppfyltRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if already ikke-oppfylt") {
                        val vurderingIkkeOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.IKKE_OPPFYLT,
                            beskrivelse = "Aktivitetskravet er ikke oppfylt",
                            arsaker = emptyList(),
                        )
                        val vurderingUnntakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Unntak",
                            arsaker = listOf(VurderingArsak.MEDISINSKE_GRUNNER),
                        )
                        val aktivitetskravUuid = aktivitetskravService.createAndVurderAktivitetskrav(
                            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            aktivitetskravVurdering = vurderingIkkeOppfyltRequestDTO.toAktivitetskravVurdering("X999999"),
                        )
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = aktivitetskravUuid,
                                vurderingDTO = vurderingUnntakRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if already ikke-aktuell") {
                        val vurderingIkkeAktueltRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.IKKE_AKTUELL,
                            beskrivelse = "Aktivitetskravet er ikke aktuelt",
                            arsaker = emptyList(),
                        )
                        val vurderingUnntakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Unntak",
                            arsaker = listOf(VurderingArsak.MEDISINSKE_GRUNNER),
                        )
                        val aktivitetskravUuid = aktivitetskravService.createAndVurderAktivitetskrav(
                            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            aktivitetskravVurdering = vurderingIkkeAktueltRequestDTO.toAktivitetskravVurdering("X999999"),
                        )
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = aktivitetskravUuid,
                                vurderingDTO = vurderingUnntakRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
            describe("Vurder existing aktivitetskrav for person") {
                beforeEachTest {
                    aktivitetskravService.createAktivitetskravForTest(
                        nyAktivitetskrav,
                    )
                }

                val urlVurderExistingAktivitetskrav =
                    "$aktivitetskravApiBasePath/${nyAktivitetskrav.uuid}$vurderAktivitetskravPath"

                val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                    status = AktivitetskravStatus.OPPFYLT,
                    beskrivelse = "Aktivitetskravet er oppfylt",
                    arsaker = listOf(VurderingArsak.FRISKMELDT),
                )

                describe("Happy path") {
                    it("Updates Aktivitetskrav with vurdering and produces to Kafka if request is succesful") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderExistingAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingOppfyltRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val latestAktivitetskrav =
                                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .first()
                            latestAktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status.name
                            latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                            val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                            kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo latestAktivitetskravVurdering.createdAt.millisekundOpplosning()
                        }
                    }
                    it("Updates Aktivitetskrav already vurdert with new vurdering and produces to Kafka if request is succesful") {
                        val avventVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.AVVENT,
                            createdBy = UserConstants.VEILEDER_IDENT,
                            beskrivelse = "Avvent",
                            arsaker = listOf(
                                VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                                VurderingArsak.INFORMASJON_BEHANDLER
                            ),
                        )
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = avventVurdering
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlVurderExistingAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingOppfyltRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val latestAktivitetskrav =
                                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .first()
                            latestAktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status.name
                            latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                            val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                            kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo latestAktivitetskravVurdering.createdAt.millisekundOpplosning()
                        }
                    }
                    it("Updates Aktivitetskrav with Avvent-vurdering and frist and produces to Kafka if request is succesful") {
                        val oneWeekFromNow = LocalDate.now().plusWeeks(1)
                        val vurderingAvventRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.AVVENT,
                            beskrivelse = "Avventer mer informasjon",
                            arsaker = listOf(VurderingArsak.INFORMASJON_BEHANDLER),
                            frist = oneWeekFromNow,
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlVurderExistingAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingAvventRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                            verify(exactly = 1) {
                                kafkaProducer.send(capture(producerRecordSlot))
                            }

                            val latestAktivitetskrav =
                                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .first()
                            val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()
                            latestAktivitetskravVurdering.frist shouldBeEqualTo oneWeekFromNow

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.frist shouldBeEqualTo latestAktivitetskravVurdering.frist
                        }
                    }
                }
                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlVurderExistingAktivitetskrav, HttpMethod.Post)
                    }
                    it("returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlVurderExistingAktivitetskrav, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlVurderExistingAktivitetskrav, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlVurderExistingAktivitetskrav, validToken, HttpMethod.Post)
                    }
                    it("returns status BadRequest if no vurdering exists for uuid-param") {
                        val randomUuid = UUID.randomUUID().toString()

                        with(
                            handleRequest(
                                HttpMethod.Post,
                                "$aktivitetskravApiBasePath/${randomUuid}$vurderAktivitetskravPath"
                            ) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingOppfyltRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER differs from personIdent for vurdering") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderExistingAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingOppfyltRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if vurdering is missing arsak") {
                        val vurderingMissingArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.OPPFYLT,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = emptyList(),
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlVurderExistingAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingMissingArsakRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if vurdering has invalid arsak") {
                        val vurderingInvalidArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = listOf(VurderingArsak.TILTAK),
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlVurderExistingAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingInvalidArsakRequestDTO))
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
