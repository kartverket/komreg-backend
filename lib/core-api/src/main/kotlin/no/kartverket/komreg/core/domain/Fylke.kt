package no.kartverket.komreg.core.domain

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.kartverket.komreg.integration.spi.Ident1
import no.kartverket.komreg.integration.spi.IdentType1
import no.kartverket.komreg.integration.spi.identTypeOf1

@Serializable
data class Fylke(
    val fylkesnummer: Fylkesnummer,
    val fylkesnavn: Fylkesnavn,
    val gyldigTilDato: LocalDate?,
) {
    companion object {
        val Ident: IdentType1<Fylkesnummer> = runBlocking {
            identTypeOf1<Fylkesnummer>()
        }
        operator fun invoke(
            fylkesnummer: Long,
            fylkesnavn: String,
            gyldigTilDato: LocalDate?,
        ): Fylke =
            Fylke(
                Fylkesnummer(fylkesnummer),
                Fylkesnavn(fylkesnavn),
                gyldigTilDato,
            )
    }
}

typealias FylkeIdent = Ident1<Fylkesnummer>

fun Fylke.tilFylkesdata(): Fylkesdata =
    Fylkesdata(
        navn = fylkesnavn.name,
    )
