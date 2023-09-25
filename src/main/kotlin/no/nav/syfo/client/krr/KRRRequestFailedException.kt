package no.nav.syfo.client.krr

const val krrErrorMessage = "Request to get Kontakinformasjon from KRR Failed"

class KRRRequestFailedException(
    message: String = ""
) : RuntimeException("$krrErrorMessage $message")
