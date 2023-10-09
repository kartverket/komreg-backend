package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*

fun transformerKommunenummer(input: Reguleringsinput, entity: Entity): Transformation? {
    val newIdent = entity.ident.transformerIdent(input)
    val newAssociatedIdents = entity.associatedIdents
        ?.mapNotNull { it.transformerIdent(input) }
        ?.toSet()

    if (newIdent == entity.ident && newAssociatedIdents == entity.associatedIdents) return null

    return Transformation(
        id = entity.id,
        sourceEntity = entity,
        transformedIdent = newIdent,
        transformedAssociatedIdents = newAssociatedIdents?.ifEmpty { null },
    )
}

private fun Ident?.transformerIdent(input: Reguleringsinput): Ident? {
    if (this == null) return null

    val fylkesnummer = getOrNull<Fylkesnummer>()
    val kommuneløpenummer = getOrNull<Kommunenummer.Lopenummer>()
    val gårdsnummer = getOrNull<Matrikkelnummer.Gardsnummer>()
    val kretsnummer = getOrNull<Kretsnummer>()
    val adressekode = getOrNull<Adressekode>()

    if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null) {
        input.endringer.matchAdressekode(fylkesnummer, kommuneløpenummer, adressekode)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til }
                .updateOrThrow { _: Adressekode -> it.adressekode.til }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && kretsnummer != null) {
        input.endringer.matchKretsnummer(fylkesnummer, kommuneløpenummer, kretsnummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til }
                .updateOrThrow { _: Kretsnummer -> it.kretsnummer.til }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && gårdsnummer != null) {
        input.endringer.matchGårdsnummer(fylkesnummer, kommuneløpenummer, gårdsnummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til }
                .updateOrThrow { _: Matrikkelnummer.Gardsnummer -> it.gårdsnummer.til }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null) {
        input.endringer.matchKommunenummer(fylkesnummer, kommuneløpenummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til }
        }
    }

    if (fylkesnummer != null) {
        input.endringer.matchFylkesnummer(fylkesnummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
        }
    }

    return this
}

fun List<Endring>.matchFylkesnummer(fylkesnummer: Fylkesnummer): Fylkeendring? {
    return this.find { it is Fylkeendring && it.fylkesnummer.fra == fylkesnummer } as Fylkeendring?
}

fun List<Endring>.matchKommunenummer(fylkesnummer: Fylkesnummer, lopenummer: Kommunenummer.Lopenummer): Kommuneendring? {
    return this.find { it is Kommuneendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer } as Kommuneendring?
}

fun List<Endring>.matchGårdsnummer(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    gardsnummer: Matrikkelnummer.Gardsnummer,
): Matrikkelenhetendring? {
    return this.find { it is Matrikkelenhetendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.gårdsnummer.fra == gardsnummer } as Matrikkelenhetendring?
}

fun List<Endring>.matchKretsnummer(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    kretsnummer: Kretsnummer,
): Kretsendring? {
    return this.find { it is Kretsendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.kretsnummer.fra == kretsnummer } as Kretsendring?
}

fun List<Endring>.matchAdressekode(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    adressekode: Adressekode,
): Vegendring? {
    return this.find { it is Vegendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.adressekode.fra == adressekode } as Vegendring?
}
