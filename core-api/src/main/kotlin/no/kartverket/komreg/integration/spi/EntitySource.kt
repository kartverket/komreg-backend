package no.kartverket.komreg.integration.spi

import arrow.core.EitherNel
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import no.kartverket.komreg.core.domain.Id
import java.time.LocalDate

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
