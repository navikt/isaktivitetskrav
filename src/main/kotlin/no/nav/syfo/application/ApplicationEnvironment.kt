package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.application.cache.RedisConfig
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.OpenClientEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
import java.net.URI
import java.time.LocalDate

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISAKTIVITETSKRAV_ISAKTIVITETSKRAV_DB"

data class Environment(
    val database: DatabaseEnvironment = DatabaseEnvironment(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
    ),
    val azure: AzureEnvironment = AzureEnvironment(
        appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
        appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ),
    val kafka: KafkaEnvironment = KafkaEnvironment(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
        aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
        aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    ),
    val redisConfig: RedisConfig = RedisConfig(
        redisUri = URI(getEnvVar("REDIS_URI_ISAKTIVITETSKRAV")),
        redisUsername = getEnvVar("REDIS_USERNAME_ISAKTIVITETSKRAV"),
        redisPassword = getEnvVar("REDIS_PASSWORD_ISAKTIVITETSKRAV"),
    ),
    val arenaCutoff: LocalDate = LocalDate.parse(getEnvVar("ARENA_CUTOFF")),
    val automatiskOppfyltCronJobEnabled: Boolean = getEnvVar("AUTOMATISK_OPPFYLT_CRONJOB_ENABLED").toBoolean(),
    val nyCronjobEnabled: Boolean = getEnvVar("NY_CRONJOB_ENABLED").toBoolean(),
    val outdatedCutoff: LocalDate = LocalDate.parse(getEnvVar("OUTDATED_AKTIVITETSKRAV_CUTOFF")),
    val outdatedCronJobEnabled: Boolean = getEnvVar("OUTDATED_AKTIVITETSKRAV_CRONJOB_ENABLED").toBoolean(),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val clients: ClientsEnvironment = ClientsEnvironment(
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
            clientId = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
        ),
        pdl = ClientEnvironment(
            baseUrl = getEnvVar("PDL_URL"),
            clientId = getEnvVar("PDL_CLIENT_ID"),
        ),
        isaktivitetskravpdfgen = OpenClientEnvironment(
            baseUrl = "http://isaktivitetskravpdfgen",
        ),
        dokarkiv = ClientEnvironment(
            baseUrl = getEnvVar("DOKARKIV_URL"),
            clientId = getEnvVar("DOKARKIV_CLIENT_ID"),
        ),
        krr = ClientEnvironment(
            baseUrl = getEnvVar("KRR_URL"),
            clientId = getEnvVar("KRR_CLIENT_ID"),
        )

    ),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

val Application.envKind get() = environment.config.property("ktor.environment").getString()

fun Application.isDev(block: () -> Unit) {
    if (envKind == "dev") block()
}

fun Application.isProd(block: () -> Unit) {
    if (envKind == "production") block()
}
