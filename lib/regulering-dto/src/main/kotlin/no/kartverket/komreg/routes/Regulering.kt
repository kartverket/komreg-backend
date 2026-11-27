package no.kartverket.komreg.routes

import kotlinx.datetime.LocalDate

interface Regulering {
    val navn: String
    val ikrafttredelse: LocalDate
}
