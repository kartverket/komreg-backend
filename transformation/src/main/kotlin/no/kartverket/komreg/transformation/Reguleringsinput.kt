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
    abstract val fylkesnummer: FraEnTilMange<Fylkesnummer>
}

data class FraTil<out T>(
    val fra: T,
    val til: T,
)

data class FraEnTilMange<out T>(
    val fra: T,
    val til: List<T>,
)

data class Fylkeendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
) : Endring()

data class Kommuneendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraEnTilMange<Kommunenummer.Lopenummer>,
) : Endring()

data class Matrikkelenhetendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val gårdsnummer: FraTil<Matrikkelnummer.Gardsnummer>,
) : Endring()

data class Kretsendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val kretsnummer: FraTil<Kretsnummer>,
    val kretstype: FraTil<Kretstype>,
) : Endring()

data class Vegendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraEnTilMange<Kommunenummer.Lopenummer>,
    val adressekode: FraTil<Adressekode>,
) : Endring()

data class Teigendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val teigId: FraTil<TeigId>,
) : Endring()

data class Vegadresseendring(
    override val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val adressekode: FraTil<Adressekode>,
    val adressenummer: FraTil<Adressenummernummer>,
) : Endring()
