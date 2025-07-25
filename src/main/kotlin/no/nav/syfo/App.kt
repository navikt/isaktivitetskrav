package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.api.cache.ValkeyStore
import no.nav.syfo.application.*
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.client.wellknown.getWellKnown
import no.nav.syfo.infrastructure.cronjob.launchCronjobModule
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()

    val valkeyConfig = environment.valkeyConfig
    val cache = ValkeyStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(valkeyConfig.host, valkeyConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(valkeyConfig.ssl)
                .user(valkeyConfig.valkeyUsername)
                .password(valkeyConfig.valkeyPassword)
                .database(valkeyConfig.valkeyDB)
                .build()
        )
    )

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl,
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
        cache = cache,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.istilgangskontroll,
    )
    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.ispdfgen.baseUrl,
    )

    val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
        producer = KafkaProducer(
            aktivitetskravVurderingProducerConfig(kafkaEnvironment = environment.kafka)
        )
    )
    val aktivitetskravVarselProducer = AktivitetskravVarselProducer(
        kafkaProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaAktivitetskravVarselSerializer>(
                kafkaEnvironment = environment.kafka,
            )
        )
    )

    lateinit var aktivitetskravService: AktivitetskravService
    lateinit var aktivitetskravVarselService: AktivitetskravVarselService
    lateinit var aktivitetskravRepository: AktivitetskravRepository

    val applicationEngineEnvironment = applicationEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
    }
    val server = embeddedServer(
        Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            aktivitetskravRepository = AktivitetskravRepository(database = applicationDatabase)
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = applicationDatabase)
            val varselPdfService = VarselPdfService(
                pdfGenClient = pdfGenClient,
                pdlClient = pdlClient,
            )
            aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                varselPdfService = varselPdfService,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                arenaCutoff = environment.arenaCutoff,
            )
            aktivitetskravVarselService = AktivitetskravVarselService(
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                aktivitetskravVarselProducer = aktivitetskravVarselProducer,
                varselPdfService = varselPdfService,
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                aktivitetskravService = aktivitetskravService,
                aktivitetskravVarselService = aktivitetskravVarselService,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            )

            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                logger.info("Application is ready, running Java VM ${Runtime.version()}")
                launchKafkaModule(
                    applicationState = applicationState,
                    environment = environment,
                    database = applicationDatabase,
                    pdlClient = pdlClient,
                    aktivitetskravService = aktivitetskravService,
                    aktivitetskravRepository = aktivitetskravRepository,
                )
                launchCronjobModule(
                    applicationState = applicationState,
                    environment = environment,
                    database = applicationDatabase,
                    aktivitetskravService = aktivitetskravService,
                    aktivitetskravVarselService = aktivitetskravVarselService,
                    pdlClient = pdlClient,
                    azureAdClient = azureAdClient,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
