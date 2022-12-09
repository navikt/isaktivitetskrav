package no.nav.syfo.aktivitetskrav.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Future

class AktivitetskravApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlAktivitetskravPerson = "$aktivitetskravApiBasePath/$aktivitetskravApiPersonidentPath"

    val nyAktivitetskrav = Aktivitetskrav.ny(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val nyAktivitetskravAnnenPerson = Aktivitetskrav.ny(
        personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val automatiskOppfyltAktivitetskrav = Aktivitetskrav.automatiskOppfyltGradert(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().minusYears(2),
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
                    kafkaProducerAktivitetskravVurdering = kafkaProducer,
                ),
            )
            val aktivitetskravService = AktivitetskravService(
                aktivitetskravVurderingProducer = mockk(relaxed = true),
                database = database,
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

            fun createAktivitetskrav(vararg aktivitetskrav: Aktivitetskrav) {
                database.connection.use { connection ->
                    aktivitetskrav.forEach {
                        aktivitetskravService.createAktivitetskrav(connection, it)
                    }
                    connection.commit()
                }
            }

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
            )

            describe("Get aktivitetskrav for person") {
                describe("Happy path") {
                    it("Returns aktivitetskrav (uten vurderinger) for person") {
                        createAktivitetskrav(
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
                            first.vurderinger.size shouldBeEqualTo 0
                            first.createdAt shouldNotBeEqualTo null
                            first.sistEndret shouldNotBeEqualTo null
                            first.uuid shouldNotBeEqualTo null

                            val last = responseDTOList.last()
                            last.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                            last.vurderinger.size shouldBeEqualTo 0
                            last.createdAt shouldNotBeEqualTo null
                            last.sistEndret shouldNotBeEqualTo null
                            last.uuid shouldNotBeEqualTo null
                        }
                    }
                    it("Returns aktivitetskrav (med vurderinger) for person") {
                        createAktivitetskrav(
                            nyAktivitetskrav,
                        )

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
                            aktivitetskravResponseDTO.vurderinger.size shouldBeEqualTo 2

                            val latestVurdering = aktivitetskravResponseDTO.vurderinger.first()
                            val oldestVurdering = aktivitetskravResponseDTO.vurderinger.last()

                            latestVurdering.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                            latestVurdering.beskrivelse shouldBeEqualTo "Oppfylt"
                            latestVurdering.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            latestVurdering.createdAt shouldBeGreaterThan oldestVurdering.createdAt
                            latestVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.GRADERT)

                            oldestVurdering.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                            oldestVurdering.beskrivelse shouldBeEqualTo "Avvent"
                            oldestVurdering.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            oldestVurdering.arsaker shouldBeEqualTo listOf(
                                VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                                VurderingArsak.INFORMASJON_BEHANDLER
                            )
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1)
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }

            describe("Vurder aktivitetskrav for person") {
                beforeEachTest {
                    createAktivitetskrav(
                        nyAktivitetskrav,
                    )
                }

                val urlVurderAktivitetskrav =
                    "$aktivitetskravApiBasePath/${nyAktivitetskrav.uuid}$vurderAktivitetskravPath"

                val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                    status = AktivitetskravStatus.OPPFYLT,
                    beskrivelse = "Aktivitetskravet er oppfylt",
                    arsaker = listOf(VurderingArsak.FRISKMELDT),
                )

                describe("Happy path") {
                    it("Updates Aktivitetskrav with vurdering and produces to Kafka if request is succesful") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
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
                                database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .first()
                            latestAktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status.name
                            latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                            kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.updatedAt.truncatedTo(ChronoUnit.MILLIS) shouldBeEqualTo latestAktivitetskrav.updatedAt.truncatedTo(
                                ChronoUnit.MILLIS
                            )
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
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
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
                                database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .first()
                            latestAktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status.name
                            latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                            kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                            kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
                            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            kafkaAktivitetskravVurdering.updatedAt.truncatedTo(ChronoUnit.MILLIS) shouldBeEqualTo latestAktivitetskrav.updatedAt.truncatedTo(
                                ChronoUnit.MILLIS
                            )
                        }
                    }
                }
                describe("Unhappy path") {
                    it("Returns status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }
                    it("returns status Forbidden if denied access to person") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS.value
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                    it("returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(
                                    NAV_PERSONIDENT_HEADER,
                                    UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1)
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
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
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT.value)
                                setBody(objectMapper.writeValueAsString(vurderingOppfyltRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("returns status BadRequest if vurdering missing arsak") {
                        val vurderingMissingArsakRequestDTO = AktivitetskravVurderingRequestDTO(
                            status = AktivitetskravStatus.OPPFYLT,
                            beskrivelse = "Aktivitetskravet er oppfylt",
                            arsaker = emptyList(),
                        )

                        with(
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
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
                            handleRequest(HttpMethod.Post, urlVurderAktivitetskrav) {
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
