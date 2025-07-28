package no.nav.syfo.application

import io.ktor.server.application.*
import no.nav.syfo.api.cache.ValkeyConfig
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.ClientsEnvironment
import no.nav.syfo.infrastructure.client.OpenClientEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
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
    val valkeyConfig: ValkeyConfig = ValkeyConfig(
        valkeyUri = URI(getEnvVar("VALKEY_URI_CACHE")),
        valkeyDB = 3, // se https://github.com/navikt/istilgangskontroll/blob/master/README.md
        valkeyUsername = getEnvVar("VALKEY_USERNAME_CACHE"),
        valkeyPassword = getEnvVar("VALKEY_PASSWORD_CACHE"),
    ),
    val arenaCutoff: LocalDate = LocalDate.parse(getEnvVar("ARENA_CUTOFF")),
    val isJournalforingRetryEnabled: Boolean = getEnvVar("JOURNALFORING_RETRY_ENABLED").toBoolean(),
    val outdatedCutoff: LocalDate = LocalDate.parse(getEnvVar("OUTDATED_AKTIVITETSKRAV_CUTOFF")),
    val outdatedCronJobEnabled: Boolean = getEnvVar("OUTDATED_AKTIVITETSKRAV_CRONJOB_ENABLED").toBoolean(),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val clients: ClientsEnvironment = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
            clientId = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID"),
        ),
        pdl = ClientEnvironment(
            baseUrl = getEnvVar("PDL_URL"),
            clientId = getEnvVar("PDL_CLIENT_ID"),
        ),
        ispdfgen = OpenClientEnvironment(
            baseUrl = "http://ispdfgen",
        ),
        dokarkiv = ClientEnvironment(
            baseUrl = getEnvVar("DOKARKIV_URL"),
            clientId = getEnvVar("DOKARKIV_CLIENT_ID"),
        ),
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
