package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravRepository
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.kafka.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.cronjob.launchCronjobModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.application.kafka.launchKafkaModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.krr.KRRClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.client.wellknown.getWellKnown
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

    val redisConfig = environment.redisConfig
    val cache = RedisStore(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(redisConfig.host, redisConfig.port),
            DefaultJedisClientConfig.builder()
                .ssl(redisConfig.ssl)
                .user(redisConfig.redisUsername)
                .password(redisConfig.redisPassword)
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
    val krrClient = KRRClient(
        azureAdClient = azureAdClient,
        baseUrl = environment.clients.krr.baseUrl,
        clientId = environment.clients.krr.clientId,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.syfotilgangskontroll,
    )
    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.isaktivitetskravpdfgen.baseUrl,
    )

    val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
        kafkaProducerAktivitetskravVurdering = KafkaProducer(
            aktivitetskravVurderingProducerConfig(kafkaEnvironment = environment.kafka)
        )
    )
    val arbeidstakervarselProducer = ArbeidstakervarselProducer(
        kafkaArbeidstakervarselProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaArbeidstakervarselSerializer>(
                kafkaEnvironment = environment.kafka,
            )
        )
    )
    val aktivitetskravVarselProducer = AktivitetskravVarselProducer(
        kafkaProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaAktivitetskravVarselSerializer>(
                kafkaEnvironment = environment.kafka,
            )
        )
    )
    val expiredVarselProducer = ExpiredVarselProducer(
        producer = KafkaProducer(
            kafkaAivenProducerConfig<ExpiredVarselSerializer>(kafkaEnvironment = environment.kafka)
        )
    )

    lateinit var aktivitetskravService: AktivitetskravService
    lateinit var aktivitetskravRepository: AktivitetskravRepository
    lateinit var aktivitetskravVarselRepository: AktivitetskravVarselRepository
    lateinit var aktivitetskravVarselService: AktivitetskravVarselService

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }
        module {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            aktivitetskravRepository = AktivitetskravRepository(database = applicationDatabase)
            aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = applicationDatabase)
            aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                database = applicationDatabase,
                arenaCutoff = environment.arenaCutoff,
            )
            aktivitetskravVarselService = AktivitetskravVarselService(
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                arbeidstakervarselProducer = arbeidstakervarselProducer,
                aktivitetskravVarselProducer = aktivitetskravVarselProducer,
                expiredVarselProducer = expiredVarselProducer,
                pdfGenClient = pdfGenClient,
                pdlClient = pdlClient,
                krrClient = krrClient,
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
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready, running Java VM ${Runtime.version()}")
        launchKafkaModule(
            applicationState = applicationState,
            environment = environment,
            database = applicationDatabase,
            pdlClient = pdlClient,
            aktivitetskravService = aktivitetskravService,
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

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
