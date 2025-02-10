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
    val nyAktivitetskrav = createAktivitetskravNy(
        tilfelleStart = LocalDate.now().minusWeeks(10),
    ).copy(
        createdAt = nowUTC().minusWeeks(2)
    )
    val aktivitetskravUuid = nyAktivitetskrav.uuid

    describe(AktivitetskravApiVurderSpek::class.java.simpleName) {
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

        suspend fun HttpClient.postVurdering(
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

        describe("Vurder aktivitetskrav for person") {
            val vurderingOppfyltRequestDTO = AktivitetskravVurderingRequestDTO(
                status = AktivitetskravStatus.OPPFYLT,
                beskrivelse = "Aktivitetskravet er oppfylt",
                arsaker = listOf(Arsak.FRISKMELDT),
                document = generateDocumentComponentDTO("Aktivitetskravet er oppfylt")
            )

            describe("Happy path") {
                it("Updates Aktivitetskrav with vurdering and produces to Kafka if request is succesful") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postVurdering(
                            aktivitetskravUuid = nyAktivitetskrav.uuid,
                            vurderingDTO = vurderingOppfyltRequestDTO,
                        )
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val latestAktivitetskrav =
                            aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                .first()
                        latestAktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status
                        latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                        val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()

                        val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                        kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                        kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                        kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                        kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.Oppfylt.Friskmeldt.value)
                        kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                        kafkaAktivitetskravVurdering.sisteVurderingUuid shouldBeEqualTo latestAktivitetskravVurdering.uuid
                        kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo latestAktivitetskravVurdering.createdAt.millisekundOpplosning()
                    }
                }
                it("Updates Aktivitetskrav already vurdert with new vurdering and produces to Kafka if request is succesful") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val avventVurdering = AktivitetskravVurdering.create(
                            status = AktivitetskravStatus.AVVENT,
                            createdBy = UserConstants.VEILEDER_IDENT,
                            beskrivelse = "Avvent",
                            arsaker = listOf(
                                VurderingArsak.Avvent.OppfolgingsplanArbeidsgiver,
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
                        val response = client.postVurdering(
                            aktivitetskravUuid = nyAktivitetskrav.uuid,
                            vurderingDTO = vurderingOppfyltRequestDTO,
                        )
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val latestAktivitetskrav =
                            aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                .first()
                        latestAktivitetskrav.status shouldBeEqualTo vurderingOppfyltRequestDTO.status
                        latestAktivitetskrav.updatedAt shouldBeGreaterThan latestAktivitetskrav.createdAt

                        val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()

                        val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                        kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                        kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                        kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Aktivitetskravet er oppfylt"
                        kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.Oppfylt.Friskmeldt.value)
                        kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.VEILEDER_IDENT
                        kafkaAktivitetskravVurdering.sisteVurderingUuid shouldBeEqualTo latestAktivitetskravVurdering.uuid
                        kafkaAktivitetskravVurdering.sistVurdert?.millisekundOpplosning() shouldBeEqualTo latestAktivitetskravVurdering.createdAt.millisekundOpplosning()
                    }
                }
                it("Updates Aktivitetskrav with Avvent-vurdering and frist and produces to Kafka if request is successful") {
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
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
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
                it("Updates Aktivitetskrav with Unntak-vurdering and varsel if request is successful") {
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
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val latestAktivitetskrav =
                            aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                .first()
                        val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()
                        latestAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravStatus.UNNTAK

                        val varsel =
                            aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestAktivitetskravVurdering.uuid)
                        varsel.shouldNotBeNull()
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
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postVurdering(
                            aktivitetskravUuid = UUID.randomUUID(),
                            vurderingDTO = vurderingOppfyltRequestDTO,
                        )
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("returns status BadRequest if $NAV_PERSONIDENT_HEADER differs from personIdent for vurdering") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postVurdering(
                            aktivitetskravUuid = nyAktivitetskrav.uuid,
                            vurderingDTO = vurderingOppfyltRequestDTO,
                            personIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT,
                        )
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("returns status BadRequest if vurdering is missing arsak") {
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
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("returns status BadRequest if vurdering has invalid arsak") {
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
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
                it("returns status Conflict if existing vurdering is final") {
                    testApplication {
                        val client = setupApiAndClient(kafkaProducer = kafkaProducer)
                        val response = client.postVurdering(
                            aktivitetskravUuid = nyAktivitetskrav.uuid,
                            vurderingDTO = vurderingOppfyltRequestDTO,
                        )
                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val responseNew = client.postVurdering(
                            aktivitetskravUuid = nyAktivitetskrav.uuid,
                            vurderingDTO = vurderingOppfyltRequestDTO,
                        )
                        responseNew.status shouldBeEqualTo HttpStatusCode.Conflict
                    }
                }
                it("returns status BadRequest if UNNTAK is missing document") {
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
                        responseNew.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }

            describe("Innstilling om stans vurdering") {
                it("Creates stans vurdering and returns 200 OK") {
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
                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                        verify(exactly = 1) {
                            kafkaProducer.send(capture(producerRecordSlot))
                        }

                        val latestAktivitetskrav =
                            aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT).first()
                        val latestAktivitetskravVurdering = latestAktivitetskrav.vurderinger.first()
                        latestAktivitetskravVurdering.status shouldBe AktivitetskravStatus.INNSTILLING_OM_STANS
                        latestAktivitetskravVurdering.stansFom shouldBeEqualTo vurderingInnstillingOmStansRequestDTO.stansFom
                    }
                }

                it("Fails to create stans vurdering when no document") {
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
                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
