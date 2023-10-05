package no.kartverket.komreg.transformation

import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.*

data class Reguleringsinput(
    val id: String,
    val ikrafttredelsesdato: LocalDate,
    val endringer: List<Endring>,
    val fylker: List<Fylke>,
    val kommuner: List<Kommune>,
)

sealed class Endring {
    abstract val fylkesnummer: FraTil<Fylkesnummer>

    data class FraTil<out T>(
        val fra: T,
        val til: T,
    )
}

data class Fylkeendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
) : Endring()

data class Kommuneendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
) : Endring()

data class Matrikkelenhetendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val gårdsnummer: FraTil<Matrikkelnummer.Gardsnummer>,
) : Endring()
