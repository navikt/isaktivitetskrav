package no.nav.syfo.testhelper.generator

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO

fun generateForhandsvarsel(fritekst: String) = ForhandsvarselDTO(
    fritekst = fritekst,
    document = generateDocumentComponentDTO(fritekst = fritekst),
)
