package no.kartverket.komreg.transformation

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Ident

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

data class FraEnTilMange<out T>(
    val fra: T,
    val til: List<T>,
)

data class Fylkeendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
) : Endring()

data class Kommuneendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraEnTilMange<Kommunenummer.Lopenummer>,
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
    val kretstype: FraTil<Kretstype>,
) : Endring()

data class Vegendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraEnTilMange<Kommunenummer.Lopenummer>,
    val adressekode: FraTil<Adressekode>,
) : Endring()

data class Teigendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val teigId: FraTil<TeigId>,
) : Endring()

data class Vegadresseendring(
    override val fylkesnummer: FraTil<Fylkesnummer>,
    val kommuneløpenummer: FraTil<Kommunenummer.Lopenummer>,
    val adressekode: FraTil<Adressekode>,
    val adressenummer: FraTil<Adressenummernummer>,
    val adressenummerbokstav: FraTil<Adressenummerbokstav>,
) : Endring()

fun Reguleringsinput.toMappings(): List<Pair<Ident, IdentTransformer.Mapping>> {
    return runBlocking {
        endringer.map { endring ->
            when (endring) {
                is Fylkeendring -> Ident(
                    endring.fylkesnummer.fra,
                ) to IdentTransformer.Mapping.Replace(
                    Ident(
                        endring.fylkesnummer.til,
                    ),
                )

                is Kommuneendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                ) to if (endring.kommuneløpenummer.til.size == 1) {
                    IdentTransformer.Mapping.Replace(
                        Ident(
                            endring.fylkesnummer.til,
                            endring.kommuneløpenummer.til.single(),
                        ),
                        kommuner.find {
                            it.kommunenummer == Kommunenummer(
                                endring.fylkesnummer.til,
                                endring.kommuneløpenummer.til.single(),
                            )
                        }?.tilKommunedata(ikrafttredelsesdato), // TODO: Hva gjør vi hvis ingen kommune?
                    )
                } else {
                    IdentTransformer.Mapping.Split(
                        endring.kommuneløpenummer.til.map { kommuneløpenummerTil ->
                            Ident(
                                endring.fylkesnummer.til,
                                kommuneløpenummerTil,
                            ) to kommuner.find {
                                it.kommunenummer == Kommunenummer(
                                    endring.fylkesnummer.til,
                                    kommuneløpenummerTil,
                                )
                            }?.tilKommunedata(ikrafttredelsesdato) // TODO: Hva gjør vi hvis ingen kommune?
                        },
                    )
                }

                is Matrikkelenhetendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.gårdsnummer.fra,
                ) to IdentTransformer.Mapping.Replace(
                    Ident(
                        endring.fylkesnummer.til,
                        endring.kommuneløpenummer.til,
                        endring.gårdsnummer.til,
                    ),
                )

                is Kretsendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.kretsnummer.fra,
                    endring.kretstype.fra,
                ) to IdentTransformer.Mapping.Replace(
                    Ident(
                        endring.fylkesnummer.til,
                        endring.kommuneløpenummer.til,
                        endring.kretsnummer.til,
                        endring.kretstype.til,
                    ),
                )

                is Vegendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.adressekode.fra,
                ) to if (endring.kommuneløpenummer.til.size == 1) {
                    IdentTransformer.Mapping.Replace(
                        Ident(
                            endring.fylkesnummer.til,
                            endring.kommuneløpenummer.til.single(),
                            endring.adressekode.til,
                        ),
                    )
                } else {
                    IdentTransformer.Mapping.Split(
                        endring.kommuneløpenummer.til.map {
                            Ident(
                                endring.fylkesnummer.til,
                                it,
                                endring.adressekode.til,
                            ) to null
                        },
                    )
                }

                is Teigendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.teigId.fra,
                ) to IdentTransformer.Mapping.Replace(
                    Ident(
                        endring.fylkesnummer.til,
                        endring.kommuneløpenummer.til,
                        endring.teigId.til,
                    ),
                )

                is Vegadresseendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.adressekode.fra,
                    endring.adressenummer.fra,
                    endring.adressenummerbokstav.fra,
                ) to IdentTransformer.Mapping.Replace(
                    Ident(
                        endring.fylkesnummer.til,
                        endring.kommuneløpenummer.til,
                        endring.adressekode.til,
                        endring.adressenummer.til,
                        endring.adressenummerbokstav.til,
                    ),
                )
            }
        }
    }
}
