package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Kommunenummer

data class Reguleringsinput(
    val id: String,
    val endringer: List<Kommuneendring>,
)

data class Kommuneendring(
    val fra: Kommunenummer,
    val til: Kommunenummer,
)


