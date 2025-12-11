import com.adarshr.gradle.testlogger.theme.ThemeType

group = "no.nav.syfo"
version = "0.0.1"

val confluentVersion = "8.1.0"
val flywayVersion = "11.18.0"
val hikariVersion = "7.0.2"
val jacksonDataTypeVersion = "2.20.1"
val jedisVersion = "6.2.0"
val kafkaVersion = "4.1.0"
val ktorVersion = "3.3.3"
val logbackVersion = "1.5.21"
val logstashEncoderVersion = "8.1"
val micrometerRegistryVersion = "1.12.13"
val mockkVersion = "1.14.7"
val nettyCodecVersion = "4.2.7.Final"
val nimbusJoseJwtVersion = "10.6"
val postgresVersion = "42.7.8"
val postgresEmbeddedVersion = "2.2.0"
val postgresRuntimeVersion = "17.5.0"
val redisEmbeddedVersion = "0.7.3"

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.8"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.adarshr.test-logger") version "4.0.0"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.netty:netty-codec-http2:$nettyCodecVersion") // TODO: Remove when Ktor upgrades Netty Codec

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbeddedVersion")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$postgresRuntimeVersion"))

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDataTypeVersion")

    // Cache
    implementation("redis.clients:jedis:$jedisVersion")
    testImplementation("it.ozimov:embedded-redis:$redisEmbeddedVersion")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://github.com/advisories/GHSA-6qvw-249j-h44c")
            version {
                require("0.9.6")
            }
        }
        implementation("commons-beanutils:commons-beanutils") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://www.cve.org/CVERecord?id=CVE-2025-48734")
            version {
                require("1.11.0")
            }
        }
    }

    implementation("io.confluent:kafka-avro-serializer:$confluentVersion", excludeLog4j)
    constraints {
        implementation("org.apache.avro:avro") {
            because("io.confluent:kafka-avro-serializer:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-39410")
            version {
                require("1.12.0")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.27.1")
            }
        }
    }

    // Tests
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
        testlogger {
            theme = ThemeType.STANDARD_PARALLEL
            showFullStackTraces = true
            showPassed = false
        }
    }
}
