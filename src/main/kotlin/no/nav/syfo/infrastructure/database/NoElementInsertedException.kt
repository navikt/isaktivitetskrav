package no.nav.syfo.infrastructure.database

import java.sql.SQLException

class NoElementInsertedException(message: String) : SQLException(message)
