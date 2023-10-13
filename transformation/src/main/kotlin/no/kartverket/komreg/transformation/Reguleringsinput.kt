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
}

data class FraTil<out T>(
    val fra: T,
    val til: T,
)

data class ListFraTil<T>(
    val fra: T,
    val _tilList: List<T>,
)

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

data class Kretsendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val kretsnummer: FraTil<Kretsnummer>,
) : Endring()

data class Vegendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val adressekode: FraTil<Adressekode>,
) : Endring()

data class Teigendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val teigId: FraTil<TeigId>,
) : Endring()
