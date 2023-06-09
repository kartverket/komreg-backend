package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenummer
import java.time.LocalDate

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
