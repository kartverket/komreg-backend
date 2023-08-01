package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.*

fun transformerKommunenummer(input: Reguleringsinput, entity: Entity): Transformation? {
    val newIdent = entity.ident.transformKommunenr(input)
    val newAssociatedIdents = entity.associatedIdents
        ?.mapNotNull { it.transformKommunenr(input) }
        ?.toSet()?.ifEmpty { null }

    if (newIdent == entity.ident && newAssociatedIdents == entity.associatedIdents) return null

    return Transformation(
        id = entity.id,
        transformationType = "ChangeKommunenummer",
        sourceEntity = entity,
        transformedIdent = newIdent,
        transformedAssociatedIdents = newAssociatedIdents?.ifEmpty { null },
    )
}

private fun Ident?.transformKommunenr(input: Reguleringsinput): Ident? {
    if (this == null) return null

    val fylkesnummer = getOrNull<Fylkesnummer>()
    val lopenummer = getOrNull<Kommunenummer.Lopenummer>()

    if (fylkesnummer == null || lopenummer == null) return this

    val newKommunenr = input.endringer
        .find { it.fra.fylkesnummer == fylkesnummer && it.fra.lopenummer == lopenummer }
        ?.til ?: return this

    return this
        .updateOrThrow { _: Fylkesnummer -> newKommunenr.fylkesnummer }
        .updateOrThrow { _: Kommunenummer.Lopenummer -> newKommunenr.lopenummer }
}
