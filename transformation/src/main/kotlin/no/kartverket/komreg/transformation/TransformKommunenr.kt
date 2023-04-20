package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation

fun transformerKommunenummer(input: Reguleringsinput, entity: Entity): Transformation? {
    val newIdent = entity.ident.transformKommunenr(input)
    val newAssociatedIdents = entity.associatedIdents
        ?.mapNotNull { it.transformKommunenr(input) }
        ?.toSet()

    if (newIdent == null && newAssociatedIdents == null) return null
    return Transformation(
        id = entity.id,
        transformationType = "ChangeKommunenummer",
        sourceEntity = entity,
        transformedIdent = newIdent,
        transformedAssociatedIdents = newAssociatedIdents?.ifEmpty { null },
    )
}

private fun Ident?.transformKommunenr(input: Reguleringsinput): Ident? {
    val fylkesnummer = this?.get<Fylkesnummer?>()
    val lopenummer = this?.get<Kommunenummer.Lopenummer?>()

    if (fylkesnummer == null || lopenummer == null) return null

    val newKommunenr = input.endringer
        .find { it.fra.fylkesnummer == fylkesnummer && it.fra.lopenummer == lopenummer }
        ?.til ?: return null

    return this?.transform(
        Ident(newKommunenr.fylkesnummer, newKommunenr.lopenummer),
    )
}
