package no.kartverket.komreg.transformation

import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.core.domain.Matrikkelnummer.Bruksnummer
import no.kartverket.komreg.core.domain.Matrikkelnummer.Gardsnummer

data class Reguleringsinput(
    val id: String,
    val ikrafttredelsesdato: LocalDate,
    val endringer: List<Endring>,
    val fylker: List<Fylke>,
    val kommuner: List<Kommune>,
)

sealed class Endring

sealed interface FraEn<out T>

data class FraTil<out T>(
    val fra: T,
    val til: T,
) : FraEn<T>

data class FraEnTilMange<out T>(
    val fra: T,
    val til: List<T>,
) : FraEn<T>

data class Fylkeendring(
    val fylkesnummer: FraEnTilMange<Fylkesnummer>,
) : Endring()

data class Kommuneendring(
    val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraEnTilMange<Kommunenummer.Lopenummer>,
) : Endring()

data class Matrikkelenhetendring(
    val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val fraGardsnummer: Gardsnummer,
    val tilGardsnummer: Gardsnummer?,
    val bruksnummer: Map<Bruksnummer, GrunneiendomIdent> = emptyMap()
) : Endring() {
    init {
        require(tilGardsnummer != null || bruksnummer.isNotEmpty()) {
            "Either tilGardsnummer or bruksnummer must be set"
        }
    }
}

data class Kretsendring(
    val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val kretsnummer: FraTil<Kretsnummer>,
    val kretstype: FraTil<Kretstype>,
) : Endring()

data class Vegendring(
    val fylkesnummer: FraEnTilMange<Fylkesnummer>,
    val kommuneløpenummer: FraEnTilMange<Kommunenummer.Lopenummer>,
    val adressekode: FraEnTilMange<Adressekode>,
) : Endring()

data class Teigendring(
    val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val teigId: FraTil<TeigId>,
) : Endring()

data class Vegadresseendring(
    val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val adressekode: FraTil<Adressekode>,
    val adressenummer: FraTil<Adressenummernummer>,
) : Endring()
