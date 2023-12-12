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
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.*
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
    val aktivitetskravUuid = nyAktivitetskrav.uuid

    describe(AktivitetskravApiVurderSpek::class.java.simpleName) {
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
                aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
            }
            afterEachTest {
                database.dropData()
            }

            fun postEndreVurdering(
                aktivitetskravUuid: UUID,
                vurderingDTO: AktivitetskravVurderingRequestDTO,
                personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            ) = run {
                val urlVurderAktivitetskrav =
                    "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
                handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, personIdent.value)
                    setBody(objectMapper.writeValueAsString(vurderingDTO))
                }
            }

            describe("Vurder aktivitetskrav for person") {
                val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                    status = AktivitetskravStatus.OPPFYLT,
                    beskrivelse = "Aktivitetskravet er oppfylt",
                    arsaker = listOf(Arsak.FRISKMELDT),
                )

                describe("Happy path") {
                    it("Updates Aktivitetskrav with vurdering and produces to Kafka if request is succesful") {
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingOppfyltRequestDTO,
                            )
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
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.OppfyltArsak.Friskmeldt.toString())
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.sisteVurderingUuid shouldBeEqualTo latestAktivitetskravVurdering.uuid
                            kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo latestAktivitetskravVurdering.createdAt.millisekundOpplosning()
                        }
                    }
                    it("Updates Aktivitetskrav already vurdert with new vurdering and produces to Kafka if request is succesful") {
                        val avventVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.AVVENT,
                            createdBy = UserConstants.VEILEDER_IDENT,
                            beskrivelse = "Avvent",
                            arsaker = listOf(
                                VurderingArsak.AvventArsak.OppfolgingsplanArbeidsgiver,
                                VurderingArsak.AvventArsak.InformasjonBehandler,
                                VurderingArsak.AvventArsak.DroftesMedROL,
                                VurderingArsak.AvventArsak.DroftesInternt,
                            ),
                        )
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = nyAktivitetskrav,
                            aktivitetskravVurdering = avventVurdering
                        )

                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingOppfyltRequestDTO,
                            )
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
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.OppfyltArsak.Friskmeldt.toString())
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.sisteVurderingUuid shouldBeEqualTo latestAktivitetskravVurdering.uuid
                            kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo latestAktivitetskravVurdering.createdAt.millisekundOpplosning()
                        }
                    }
                    it("Updates Aktivitetskrav with Avvent-vurdering and frist and produces to Kafka if request is successful") {
                        val oneWeekFromNow = LocalDate.now().plusWeeks(1)
                        val vurderingAvventRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.AVVENT,
                            beskrivelse = "Avventer mer informasjon",
                            arsaker = listOf(Arsak.INFORMASJON_BEHANDLER),
                            frist = oneWeekFromNow,
                        )

                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingAvventRequestDTO,
                            )
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
                    val urlVurderExistingAktivitetskrav =
                        "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
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
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = UUID.randomUUID(),
                                vurderingDTO = vurderingOppfyltRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER differs from personIdent for vurdering") {
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingOppfyltRequestDTO,
                                personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
                            )
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
                            postEndreVurdering(
                                aktivitetskravUuid = aktivitetskravUuid,
                                vurderingDTO = vurderingMissingArsakRequestDTO
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if vurdering has invalid arsak") {
                        val vurderingInvalidArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.UNNTAK,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = listOf(Arsak.TILTAK),
                        )

                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingInvalidArsakRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if existing vurdering is final") {
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingOppfyltRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(
                            postEndreVurdering(
                                aktivitetskravUuid = nyAktivitetskrav.uuid,
                                vurderingDTO = vurderingOppfyltRequestDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
