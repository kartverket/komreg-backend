package no.kartverket.komreg.integration.spi

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
)

interface EntitySource {
    val id: String
    val entityFlow: Flow<Entity>
    val preValidation: Set<() -> Unit>
    val postValidation: Set<() -> Unit>
}

interface EntitySink {
    val id: String
    suspend fun consumeTransformations(flow: Flow<Transformation>, ikrafttredelsesdato: LocalDate)
    val postValidation: Set<() -> Unit>
    val preValidation: Set<() -> Unit>
}
