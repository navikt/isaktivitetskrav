package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.database.AktivitetskravRepository
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.createAktivitetskravOppfylt
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

class AktivitetskravServiceSpek : Spek({

    describe(AktivitetskravService::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val vurderingProducerMock = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
            val aktivitetskravRepository = AktivitetskravRepository(database = database)
            val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(vurderingProducerMock)
            val aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                database = database,
                arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
            )

            beforeEachTest {
                clearMocks(vurderingProducerMock)
                coEvery {
                    vurderingProducerMock.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }
            afterEachTest {
                database.dropData()
            }

            describe("create aktivitetskrav") {
                it("creates aktivitetskrav with previous aktivitetskrav from service") {
                    val previousAktivitetskrav = createAktivitetskravOppfylt(
                        createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    )
                    val createdAktivitetskrav =
                        aktivitetskravService.createAktivitetskrav(
                            UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            previousAktivitetskrav = previousAktivitetskrav
                        )
                    val savedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(createdAktivitetskrav.uuid)
                    val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()

                    verify(exactly = 1) {
                        vurderingProducerMock.send(capture(producerRecordSlot))
                    }

                    val aktivitetskravVurderingRecord = producerRecordSlot.captured.value()
                    createdAktivitetskrav.personIdent.value shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                    savedAktivitetskrav?.previousAktivitetskravUuid shouldBeEqualTo previousAktivitetskrav.uuid
                    aktivitetskravVurderingRecord.previousAktivitetskravUuid shouldBeEqualTo previousAktivitetskrav.uuid
                    aktivitetskravVurderingRecord.uuid shouldBeEqualTo createdAktivitetskrav.uuid
                }
                it("create aktivitetskrav with previous aktivitetskrav not final throws exception") {
                    val previousAktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    assertFailsWith(ConflictException::class) {
                        aktivitetskravService.createAktivitetskrav(
                            UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            previousAktivitetskrav = previousAktivitetskrav
                        )
                    }
                }
                it("creates aktivitetskrav without previous aktivitetskrav from service") {
                    val createdAktivitetskrav =
                        aktivitetskravService.createAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    val savedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(createdAktivitetskrav.uuid)
                    val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()

                    verify(exactly = 1) {
                        vurderingProducerMock.send(capture(producerRecordSlot))
                    }

                    val aktivitetskravVurderingRecord = producerRecordSlot.captured.value()
                    createdAktivitetskrav.personIdent.value shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                    savedAktivitetskrav?.previousAktivitetskravUuid shouldBeEqualTo null
                    aktivitetskravVurderingRecord.previousAktivitetskravUuid shouldBeEqualTo null
                    aktivitetskravVurderingRecord.uuid shouldBeEqualTo createdAktivitetskrav.uuid
                }
            }
        }
    }
})
