## Kafka Topic

### Topic Name:
teamsykefravr.aktivitetskrav-vurdering

### Properties

Beskrivelse av feltene i en record ([KafkaAktivitetskravVurdering](../../src/main/kotlin/no/nav/syfo/aktivitetskrav/kafka/KafkaAktivitetskravVurdering.kt))

* `uuid` UUID til aktivitetskravet.
* `personIdent` Fødselsnummer til den sykmeldte.
* `createdAt` Dato-tid aktivitetskravet ble opprettet.
* `stoppunktAt` Dato oppfølgingstilfellet som genererte aktivitetskravet passerer 8 uker. Eller dato aktivitetskravet ble opprettet av veileder dersom det er vurdert uten aktivt oppfølgingstilfelle.
* `status` Status til aktivitetskravet. Mulige verdier: `NY, AUTOMATISK_OPPFYLT, AVVENT, UNNTAK, OPPFYLT, IKKE_OPPFYLT, IKKE_AKTUELL, FORHANDSVARSEL, LUKKET`.
* `beskrivelse` Beskrivelse fritekst angitt i vurdering.
* `arsaker` Årsakskoder valgt i vurdering. Mulige verdier: `OPPFOLGINGSPLAN_ARBEIDSGIVER, INFORMASJON_BEHANDLER, ANNET, MEDISINSKE_GRUNNER, TILRETTELEGGING_IKKE_MULIG, SJOMENN_UTENRIKS, FRISKMELDT, GRADERT, TILTAK`.
* `updatedBy` Veileder-ident som vurderte aktivitetskravet.
* `sistVurdert` Dato-tid aktivitetskravet ble vurdert av veileder.
* `frist`: Valgfri frist-dato knyttet til vurderingen.
