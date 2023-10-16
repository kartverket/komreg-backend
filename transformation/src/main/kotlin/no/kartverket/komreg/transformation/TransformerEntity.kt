package no.kartverket.komreg.transformation
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun transformerEntity(
    input: Reguleringsinput,
    entity: Entity,
    idGeneratorManager: IdGeneratorManager,
): List<Transformation>? {
    val transformations = mutableListOf<Transformation>()
    val logger: Logger = LoggerFactory.getLogger("TransformerEntity")

    val matchedEntity = matchEntitetMotReguleringsInput(input, entity) ?: return null

    fun addTransformation(index: Int, entityId: Id = entity.id) {
        val newIdent = entity.ident.transformerIdent(input, index)
        val newAssociatedIdents = entity.associatedIdents
            ?.mapNotNull { it.transformerIdent(input, index) }
            ?.toSet()

        if (newIdent != entity.ident && newAssociatedIdents != entity.associatedIdents) {
            transformations.add(
                Transformation(
                    id = entityId,
                    sourceEntity = entity,
                    transformedIdent = newIdent,
                    transformedAssociatedIdents = newAssociatedIdents?.ifEmpty { null },
                ),
            )
        }
    }

    addTransformation(0)

    if (matchedEntity is Vegendring && matchedEntity.kommuneløpenummer.til.size > 1) {
        for (index in 1 until matchedEntity.kommuneløpenummer.til.size) {
            val newId = idGeneratorManager.idFor(entity.id.type)
            addTransformation(index, newId)
        }
    }

    return transformations.ifEmpty { null }
}

fun matchEntitetMotReguleringsInput(input: Reguleringsinput, entity: Entity): Endring? {
    val fylkesnummer = entity.ident?.getOrNull<Fylkesnummer>()
    val kommuneløpenummer = entity.ident?.getOrNull<Kommunenummer.Lopenummer>()
    val adressekode = entity.ident?.getOrNull<Adressekode>()
    val teigId = entity.ident?.getOrNull<TeigId>()
    val kretsnummer = entity.ident?.getOrNull<Kretsnummer>()
    val gårdsnummer = entity.ident?.getOrNull<Matrikkelnummer.Gardsnummer>()

    if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null) {
        val vegendring = input.endringer.matchAdressekode(fylkesnummer, kommuneløpenummer, adressekode)
        return vegendring
    }

    if (fylkesnummer != null && kommuneløpenummer != null && teigId != null) {
        return input.endringer.matchTeigId(fylkesnummer, kommuneløpenummer, teigId)
    }

    if (fylkesnummer != null && kommuneløpenummer != null && kretsnummer != null) {
        return input.endringer.matchKretsnummer(fylkesnummer, kommuneløpenummer, kretsnummer)
    }

    if (fylkesnummer != null && kommuneløpenummer != null && gårdsnummer != null) {
        return input.endringer.matchGårdsnummer(fylkesnummer, kommuneløpenummer, gårdsnummer)
    }

    if (fylkesnummer != null && kommuneløpenummer != null) {
        return input.endringer.matchKommunenummer(fylkesnummer, kommuneløpenummer)
    }

    if (fylkesnummer != null) {
        return input.endringer.matchFylkesnummer(fylkesnummer)
    }

    return null
}

private fun Ident?.transformerIdent(input: Reguleringsinput, index: Int): Ident? {
    if (this == null) return null

    val fylkesnummer = getOrNull<Fylkesnummer>()
    val kommuneløpenummer = getOrNull<Kommunenummer.Lopenummer>()
    val gårdsnummer = getOrNull<Matrikkelnummer.Gardsnummer>()
    val kretsnummer = getOrNull<Kretsnummer>()
    val adressekode = getOrNull<Adressekode>()
    val teigId = getOrNull<TeigId>()

    if (fylkesnummer != null && kommuneløpenummer != null && teigId != null) {
        input.endringer.matchTeigId(fylkesnummer, kommuneløpenummer, teigId)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til.first() }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til[index] }
                .updateOrThrow { _: TeigId -> it.teigId.til.first() }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && adressekode != null) {
        input.endringer.matchAdressekode(fylkesnummer, kommuneløpenummer, adressekode)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til.first() }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til[index] }
                .updateOrThrow { _: Adressekode -> it.adressekode.til.first() }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && kretsnummer != null) {
        input.endringer.matchKretsnummer(fylkesnummer, kommuneløpenummer, kretsnummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til.first() }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til.first() }
                .updateOrThrow { _: Kretsnummer -> it.kretsnummer.til.first() }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null && gårdsnummer != null) {
        input.endringer.matchGårdsnummer(fylkesnummer, kommuneløpenummer, gårdsnummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til[0] }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til.first() }
                .updateOrThrow { _: Matrikkelnummer.Gardsnummer -> it.gårdsnummer.til.first() }
        }
    }

    if (fylkesnummer != null && kommuneløpenummer != null) {
        input.endringer.matchKommunenummer(fylkesnummer, kommuneløpenummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til.first() }
                .updateOrThrow { _: Kommunenummer.Lopenummer -> it.kommuneløpenummer.til.first() }
        }
    }

    if (fylkesnummer != null) {
        input.endringer.matchFylkesnummer(fylkesnummer)?.let {
            return this
                .updateOrThrow { _: Fylkesnummer -> it.fylkesnummer.til.first() }
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

fun List<Endring>.matchTeigId(
    fylkesnummer: Fylkesnummer,
    lopenummer: Kommunenummer.Lopenummer,
    teigId: TeigId,
): Teigendring? {
    return this.find { it is Teigendring && it.fylkesnummer.fra == fylkesnummer && it.kommuneløpenummer.fra == lopenummer && it.teigId.fra == teigId } as Teigendring?
}
