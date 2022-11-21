package no.nav.syfo.application.exception

class ForbiddenAccessVeilederException(
    action: String,
    message: String = "Denied NAVIdent access to personIdent: $action",
) : RuntimeException(message)
