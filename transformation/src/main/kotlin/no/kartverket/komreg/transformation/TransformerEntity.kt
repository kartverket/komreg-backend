/*
package no.kartverket.komreg.transformation

import kotlinx.coroutines.runBlocking
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*

fun transformerEntity(
    input: Reguleringsinput,
    entity: Entity,
    idGeneratorManager: IdGeneratorManager,
): List<Transformation>? {
    val transformations = mutableListOf<Transformation>()
    val matchedEntity = matchEntitetMotReguleringsInput(input, entity) ?: return null

    // TODO: Bygge mappings fra input
    // TODO: Kalle på IdentTransformer.transform

    transformations.add(opprettTransformation(entity, input, 0))

    // TODO - Håndterer kun vegendring da denne er den eneste som har flere kommunenummer, og bruker FraEnTilMange<Kommunenummer.Lopenummer> som type.
    //  Men i fremtiden vil dette også kunne gjelde andre endringer
    */
/*if (matchedEntity is Vegendring && matchedEntity.kommuneløpenummer.til.size > 1) {
        for (index in 1 until matchedEntity.kommuneløpenummer.til.size) {
            val newId = idGeneratorManager.idFor(entity.id.type)
            transformations.add(opprettTransformation(entity, input, index, newId))
        }
    }*//*


    return transformations.ifEmpty { null }
}

private fun opprettTransformation(
    entity: Entity,
    input: Reguleringsinput,
    tilIndex: Int,
    entityId: Id = entity.id,
): Transformation {
    var newIdent = entity.ident.transformerIdent(input, tilIndex)
    val newAssociatedIdents = entity.associatedIdents
        ?.mapNotNull { it.transformerIdent(input, tilIndex) }
        ?.toSet()

    // Spesialhåndtering av at Sefrakminner som skal transformeres ikke kan ha kommunetilknytning i ident
    // TODO: Dette burde håndteres på en bedre måte
    val entityType = entity.id.type.toString()
    if (entityType.contains("Sefrakminne")) {
        newIdent = Ident.Empty
    }

    return Transformation(
        id = entityId,
        sourceEntity = entity,
        transformedIdent = newIdent,
        transformedAssociatedIdents = newAssociatedIdents?.ifEmpty { null },
    )
}

fun matchEntitetMotReguleringsInput(input: Reguleringsinput, entity: Entity): Endring? {
    val identer = entity.associatedIdents?.toList().orEmpty().plus(entity.ident).filterNotNull()

    for (ident in identer) {
        val fylkesnummer = ident.getOrNull<Fylkesnummer>()
        val kommuneløpenummer = ident.getOrNull<Kommunenummer.Lopenummer>()
        val adressekode = ident.getOrNull<Adressekode>()
        val adressenummer = ident.getOrNull<Adressenummernummer>()
        val adressenummerbokstav = ident.getOrNull<Adressenummerbokstav>()
        val teigId = ident.getOrNull<TeigId>()
        val kretsnummer = ident.getOrNull<Kretsnummer>()
        val kretstype = ident.getOrNull<Kretstype>()
        val gårdsnummer = ident.getOrNull<Matrikkelnummer.Gardsnummer>()

        // Rekkefølgen på dette matchpatternet er viktig. Trakten går fra det mest spesifikke til det generelle caset som matcher i reguleringsinputtet. Dette bør gjøres på en tryggere måte senere.

        if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null && adressenummer != null) {
            input.endringer.matchVegadresse(
                fylkesnummer,
                kommuneløpenummer,
                adressekode,
                adressenummer,
                adressenummerbokstav,
            )?.let { return it }
        }

        if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null) {
            input.endringer.matchAdressekode(fylkesnummer, kommuneløpenummer, adressekode)?.let { return it }
        }

        if (fylkesnummer != null && kommuneløpenummer != null && teigId != null) {
            input.endringer.matchTeigId(fylkesnummer, kommuneløpenummer, teigId)?.let { return it }
        }

        if (fylkesnummer != null && kommuneløpenummer != null && kretsnummer != null && kretstype != null) {
            input.endringer.matchKretsnummer(fylkesnummer, kommuneløpenummer, kretsnummer, kretstype)?.let { return it }
        }

        if (fylkesnummer != null && kommuneløpenummer != null && gårdsnummer != null) {
            input.endringer.matchGårdsnummer(fylkesnummer, kommuneløpenummer, gårdsnummer)?.let { return it }
        }

        if (fylkesnummer != null && kommuneløpenummer != null) {
            input.endringer.matchKommunenummer(fylkesnummer, kommuneløpenummer)?.let { return it }
        }

        if (fylkesnummer != null) {
            input.endringer.matchFylkesnummer(fylkesnummer)?.let { return it }
        }
    }
    return null
}

private fun Ident?.transformerIdent(input: Reguleringsinput, tilIndex: Int): Ident? {
    if (this == null) return null

    val fylkesnummer = getOrNull<Fylkesnummer>()
    val kommuneløpenummer = getOrNull<Kommunenummer.Lopenummer>()
    val gårdsnummer = getOrNull<Matrikkelnummer.Gardsnummer>()
    val kretsnummer = getOrNull<Kretsnummer>()
    val kretstype = getOrNull<Kretstype>()
    val adressekode = getOrNull<Adressekode>()
    val adressenummer = getOrNull<Adressenummernummer>()
    val adressenummerbokstav = getOrNull<Adressenummerbokstav>()
    val teigId = getOrNull<TeigId>()
    val bygningsnummer = getOrNull<Bygningsnummer>()

    // val sefrakObjektnummer = toArray().find { it.toString().contains("SefrakObjektnummer") } TODO: Dette burde vært via en kjent type som over

    if (fylkesnummer != null && kommuneløpenummer != null && teigId != null) {
        input.endringer.matchTeigId(fylkesnummer, kommuneløpenummer, teigId)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til }
                .updateOrThrow { _: TeigId -> it.teigId.til }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null && adressenummer != null && adressenummerbokstav != null) {
        input.endringer.matchVegadresse(
            fylkesnummer,
            kommuneløpenummer,
            adressekode,
            adressenummer,
            adressenummerbokstav,
        )?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til }
                .updateOrThrow { _: Adressekode -> it.adressekode.til }
                .updateOrThrow { _: Adressenummernummer -> it.adressenummer.til }
                .updateOrThrow { _: Adressenummerbokstav -> it.adressenummerbokstav.til }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null) {
        input.endringer.matchAdressekode(fylkesnummer, kommuneløpenummer, adressekode)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til[tilIndex] }
                .updateOrThrow { _: Adressekode -> it.adressekode.til }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && kretsnummer != null && kretstype != null) {
        input.endringer.matchKretsnummer(fylkesnummer, kommuneløpenummer, kretsnummer, kretstype)?.let {
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

    if (fylkesnummer != null && kommuneløpenummer != null && bygningsnummer != null) {
        return bygningidentUtenKommune(bygningsnummer)
    }

    */
/*if (fylkesnummer != null && kommuneløpenummer != null && sefrakObjektnummer != null) {
        return Ident.Empty
    }*//*


    return this
}

private fun bygningidentUtenKommune(bygningsnummer: Bygningsnummer) =
    runBlocking {
        Ident(bygningsnummer)
    }

fun List<Endring>.matchFylkesnummer(fylkesnummer: Fylkesnummer): Fylkeendring? {
    return this.find { it is Fylkeendring && it.fylkesnummer.fra == fylkesnummer } as Fylkeendring?
}

fun List<Endring>.matchKommunenummer(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
): Kommuneendring? {
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
    kretstype: Kretstype,
): Kretsendring? {
    return this.find { it is Kretsendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.kretsnummer.fra == kretsnummer && it.kretstype.fra == kretstype } as Kretsendring?
}

fun List<Endring>.matchAdressekode(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    adressekode: Adressekode,
): Vegendring? {
    return this.find { it is Vegendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.adressekode.fra == adressekode } as Vegendring?
}

fun List<Endring>.matchVegadresse(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    adressekode: Adressekode,
    adressenummer: Adressenummernummer,
    adressenummerbokstav: Adressenummerbokstav?,
): Vegadresseendring? {
    return this.find { it is Vegadresseendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.adressekode.fra == adressekode && it.adressenummer.fra == adressenummer && it.adressenummerbokstav.fra == adressenummerbokstav } as Vegadresseendring?
}

fun List<Endring>.matchTeigId(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    teigId: TeigId,
): Teigendring? {
    return this.find { it is Teigendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.teigId.fra == teigId } as Teigendring?
}
*/
