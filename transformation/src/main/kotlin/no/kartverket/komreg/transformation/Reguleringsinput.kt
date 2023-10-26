package no.kartverket.komreg.transformation

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Ident
import java.lang.RuntimeException

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
    val til: T?,
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

fun Reguleringsinput.toMappings(): List<Pair<Ident, Ident?>> {
    return runBlocking {
        endringer.map { endring ->
            when (endring) {
                is Fylkeendring -> Ident(endring.fylkesnummer.fra) to endring.fylkesnummer.til?.let { Ident(it) }
                is Kommuneendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                ) to
                    endring.fylkesnummer.til?.let { fnr ->
                        endring.kommuneløpenummer.til?.let { knr ->
                            Ident(
                                fnr,
                                knr,
                            )
                        }
                    }

                is Matrikkelenhetendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.gårdsnummer.fra,
                ) to endring.fylkesnummer.til?.let { fnr ->
                    endring.kommuneløpenummer.til?.let { knr ->
                        endring.gårdsnummer.til?.let { gnr ->
                            Ident(
                                fnr,
                                knr,
                                gnr,
                            )
                        }
                    }
                }

                is Kretsendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.kretsnummer.fra,
                    endring.kretstype.fra,
                ) to endring.fylkesnummer.til?.let { fnr ->
                    endring.kommuneløpenummer.til?.let { knr ->
                        endring.kretsnummer.til?.let { krnr ->
                            endring.kretstype.til?.let { kst ->
                                Ident(
                                    fnr,
                                    knr,
                                    krnr,
                                    kst,
                                )
                            }
                        }
                    }
                }

                /*is Vegendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.adressekode.fra,
                ) to Ident(
                    endring.fylkesnummer.til,
                    endring.kommuneløpenummer.til,
                    endring.adressekode.til,
                )*/

                is Teigendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.teigId.fra,
                ) to endring.fylkesnummer.til?.let { fnr ->
                    endring.kommuneløpenummer.til?.let { knr ->
                        endring.teigId.til?.let { teigId ->
                            Ident(
                                fnr,
                                knr,
                                teigId,
                            )
                        }
                    }
                }

                is Vegadresseendring -> Ident(
                    endring.fylkesnummer.fra,
                    endring.kommuneløpenummer.fra,
                    endring.adressekode.fra,
                    endring.adressenummer.fra,
                    endring.adressenummerbokstav.fra,
                ) to endring.fylkesnummer.til?.let { fnr ->
                    endring.kommuneløpenummer.til?.let { knr ->
                        endring.adressekode.til?.let { ak ->
                            endring.adressenummer.til?.let { an ->
                                endring.adressenummerbokstav.til?.let { anb ->
                                    Ident(
                                        fnr,
                                        knr,
                                        ak,
                                        an,
                                        anb,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> throw RuntimeException("Ukjent endringstype: ${endring::class.simpleName}")
            }
        }
    }
}
