package no.kartverket.komreg.integration.spi

import arrow.core.EitherNel
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.domain.Id
import java.time.LocalDate

interface Payload

@Serializable
data class Entity(
    val id: Id,
    val ident: Ident? = null,
    val associatedIdents: Set<Ident>? = null,
    val sourceObject: Payload? = null,
)

@Serializable
data class Transformation(
    val id: Id,
    val sourceEntity: Entity?,
    val transformedIdent: Ident?,
    val transformedAssociatedIdents: Set<Ident>? = null,
    val resultObject: Payload? = null,
    /**
     * Ingen sink leser feltet. Det settes bare av `LegacyIdentTransformer`, som bruker flagget fra DTO-en til å la
     * være å opprette et mål flere ganger. Se «Bakgrunn for domenereglene» i docs/reform-format-design.md.
     */
    val sammenslaaing: Boolean?
) {
    constructor(
        id: Id,
        sourceEntity: Entity?,
        transformedIdent: Ident?,
        transformedAssociatedIdents: Set<Ident>? = null,
        resultObject: Payload? = null
    ) : this(id, sourceEntity, transformedIdent, transformedAssociatedIdents, resultObject, false)
}

interface EntitySource {
    val id: String
    val entityFlow: Flow<Entity>
}

interface EntitySink {
    val id: String
    suspend fun consumeTransformations(flow: Flow<Transformation>, ikrafttredelsesdato: LocalDate)

    suspend fun postTransformValidate() : EitherNel<TransformValidationError, Unit> = Unit.right()
}
