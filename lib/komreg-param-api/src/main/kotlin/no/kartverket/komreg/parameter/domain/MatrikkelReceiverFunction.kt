package no.kartverket.komreg.parameter.domain

import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.parameter.data.DomainType

typealias MatrikkelReceiverFunction<A, R> = context(
Fylkesnummer.Type,
Kommunenummer.Lopenummer.Type,
Matrikkelnummer.Gardsnummer.Type,
Matrikkelnummer.Bruksnummer.Type,
Matrikkelnummer.Festenummer.Type,
Matrikkelnummer.Seksjonsnummer.Type,
Adressekode.Type,
Adressenummernummer.Type,
Adressenummerbokstav.Type,
DomainType<Kretstype>,
Kretsnummer.Type,
) A.() -> R

fun <A, R> withMatrikkelTypes(
    a: A,
    block: MatrikkelReceiverFunction<A, R>
): R {
    return block(
        Fylkesnummer.Type,
        Kommunenummer.Lopenummer.Type,
        Matrikkelnummer.Gardsnummer.Type,
        Matrikkelnummer.Bruksnummer.Type,
        Matrikkelnummer.Festenummer.Type,
        Matrikkelnummer.Seksjonsnummer.Type,
        Adressekode.Type,
        Adressenummernummer.Type,
        Adressenummerbokstav.Type,
        Kretstype.type,
        Kretsnummer.Type,
        a
    )
}