![Build status](https://github.com/navikt/isaktivitetskrav/workflows/main/badge.svg?branch=master)

# isaktivitetskrav
Applikasjon for vurdering av aktivitetskravet i sykefraværsoppfølgingen

## Technologies used

* Docker
* Gradle
* Kafka
* Kotlin
* Ktor
* Postgres

##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 17

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`

## Kafka

This application produces the following topic(s):

* teamsykefravr.aktivitetskrav-vurdering

This application consumes the following topic(s):

* teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.
