package no.nav.syfo.application.kafka

class KafkaEnvironment(
    val aivenKeystoreLocation: String,
    val aivenCredstorePassword: String,
    val aivenTruststoreLocation: String,
    val aivenSecurityProtocol: String,
    val aivenBootstrapServers: String,
    val aivenSchemaRegistryUrl: String,
    val aivenRegistryUser: String,
    val aivenRegistryPassword: String,
)
