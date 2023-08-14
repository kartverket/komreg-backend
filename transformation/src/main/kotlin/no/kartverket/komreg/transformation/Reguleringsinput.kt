package no.kartverket.komreg.transformation

import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenummer

data class Reguleringsinput(
    val id: String,
    val ikrafttredelsesdato: LocalDate,
    val endringer: List<Kommuneendring>,
    val fylker: List<Fylke>,
    val kommuner: List<Kommune>,
)

data class Kommuneendring(
    val fra: Kommunenummer,
    val til: Kommunenummer,
)
