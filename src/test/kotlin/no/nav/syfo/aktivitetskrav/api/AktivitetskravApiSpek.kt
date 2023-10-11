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
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.createAktivitetskravAutomatiskOppfylt
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
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

            fun createAktivitetskrav(vararg aktivitetskrav: Aktivitetskrav) {
                database.connection.use { connection ->
                    aktivitetskrav.forEach {
                        aktivitetskravService.createAktivitetskrav(
                            connection = connection,
                            aktivitetskrav = it,
                            referanseTilfelleBitUUID = UUID.randomUUID()
                        )
                    }
                    connection.commit()
                }
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

            fun postForhandsvarsel(
                aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
                arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                newForhandsvarselDTO: ForhandsvarselDTO,
            ) = run {
                val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$forhandsvarselPath"
                handleRequest(HttpMethod.Post, url) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                    setBody(objectMapper.writeValueAsString(newForhandsvarselDTO))
                }
            }

            fun postAvvent(
                aktivitetskravUuid: UUID = nyAktivitetskrav.uuid,
                arbeidstakerPersonIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            ) = run {
                val url = "$aktivitetskravApiBasePath/${aktivitetskravUuid}$vurderAktivitetskravPath"
                handleRequest(HttpMethod.Post, url) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                    addHeader(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdent.value)
                    setBody(
                        objectMapper.writeValueAsString(
                            AktivitetskravVurderingRequestDTO(
                                status = AktivitetskravStatus.AVVENT,
                                beskrivelse = "venter litt",
                                arsaker = listOf(VurderingArsak.ANNET),
                            )
                        )
                    )
                }
            }

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
                            first.uuid shouldNotBeEqualTo null

                            val last = responseDTOList.last()
                            last.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                            last.vurderinger.size shouldBeEqualTo 0
                            last.createdAt shouldNotBeEqualTo null
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
                            latestVurdering.varsel.shouldBeNull()

                            oldestVurdering.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                            oldestVurdering.beskrivelse shouldBeEqualTo "Avvent"
                            oldestVurdering.createdBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                            oldestVurdering.arsaker shouldBeEqualTo listOf(
                                VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
                                VurderingArsak.INFORMASJON_BEHANDLER
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
                        createAktivitetskrav(
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
                            aktivitetskrav.vurderinger.size shouldBeEqualTo 0
                            aktivitetskrav.createdAt shouldNotBeEqualTo null
                            aktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid.toString()
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

            describe("Create and vurder aktivitetskrav for person") {
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

            describe("Forhåndsvarsel") {
                beforeEachTest { createAktivitetskrav(nyAktivitetskrav) }
                val fritekst = "Dette er et forhåndsvarsel"
                val forhandsvarselDTO = ForhandsvarselDTO(
                    fritekst = fritekst,
                    document = generateDocumentComponentDTO(
                        fritekst = fritekst,
                        header = "Forhåndsvarsel"
                    )
                )
                describe("Happy path") {
                    it("Successfully creates a new forhandsvarsel") {
                        with(postForhandsvarsel(newForhandsvarselDTO = forhandsvarselDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                            val createdForhandsvarsel =
                                objectMapper.readValue(response.content, AktivitetskravVarsel::class.java)
                            createdForhandsvarsel.document shouldBeEqualTo forhandsvarselDTO.document
                        }

                        with(
                            handleRequest(HttpMethod.Get, urlAktivitetskravPerson) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOList =
                                objectMapper.readValue<List<AktivitetskravResponseDTO>>(response.content!!)
                            val aktivitetskravResponseDTO = responseDTOList.first()
                            val varselResponseDTO = aktivitetskravResponseDTO.vurderinger.first().varsel
                            varselResponseDTO.shouldNotBeNull()
                            varselResponseDTO.svarfrist shouldBeEqualTo varselResponseDTO.createdAt.toLocalDate()
                                .plusWeeks(3)
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Can't find aktivitetskrav for given uuid") {
                        with(
                            postForhandsvarsel(
                                aktivitetskravUuid = UUID.randomUUID(),
                                newForhandsvarselDTO = forhandsvarselDTO,
                            )
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("Fails if document is empty") {
                        val varselWithoutDocument = forhandsvarselDTO.copy(document = emptyList())
                        with(postForhandsvarsel(newForhandsvarselDTO = varselWithoutDocument)) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Fails if already forhandsvarsel") {
                        with(postForhandsvarsel(newForhandsvarselDTO = forhandsvarselDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                        }
                        with(postForhandsvarsel(newForhandsvarselDTO = forhandsvarselDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Fails if already forhandsvarsel before") {
                        with(postForhandsvarsel(newForhandsvarselDTO = forhandsvarselDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
                        }
                        with(postAvvent()) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                        with(postForhandsvarsel(newForhandsvarselDTO = forhandsvarselDTO)) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }

            describe("Vurder existing aktivitetskrav for person") {
                beforeEachTest {
                    createAktivitetskrav(
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

private fun TestApplicationEngine.testMissingToken(url: String, httpMethod: HttpMethod) {
    with(
        handleRequest(httpMethod, url) {}
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

private fun TestApplicationEngine.testMissingPersonIdent(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    with(
        handleRequest(httpMethod, url) {
            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
        }
    ) {
        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
    }
}

private fun TestApplicationEngine.testInvalidPersonIdent(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    with(
        handleRequest(httpMethod, url) {
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

private fun TestApplicationEngine.testDeniedPersonAccess(
    url: String,
    validToken: String,
    httpMethod: HttpMethod,
) {
    with(
        handleRequest(httpMethod, url) {
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
