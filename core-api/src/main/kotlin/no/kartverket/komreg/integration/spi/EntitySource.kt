package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.core.domain.Id

data class Entity(
    val id: Id<*>,
    val ident: Ident? = null,
    val associatedIdents: Set<Ident>? = null,
    val sourceObject: Any? = null
)

data class Transformation(
    val id: Id<*>,
    val sourceEntity: Entity?,
    val transformationType: Any,
    val transformedIdent: Ident?,
    val transformedAssociatedIdents: Set<Ident>? = null,
    val resultObject: Any? = null
)

interface EntitySource {
    val id: String
    val entityFlow: Flow<Entity>
    val preValidation: Set<() -> Unit>
    val postValidation: Set<() -> Unit>
}

interface EntitySink {
    val id: String
    suspend fun consumeTransformations(flow: Flow<Transformation>)
    val postValidation: Set<() -> Unit>
    val preValidation: Set<() -> Unit>
}

/*
Entity(
  id = "MatrikkelEnhentX",
  ident = mapOf(
     Flykesnummer:class to Fylkesnummer(2),
     Kommuneløpenr:class to Kommuneløpenr(2),
     Gårdsnummer:class to Gårdsnummer(17),)
  idents = emptyMap(),
  sourceObject = None
)
Entity(
  id = "MatrikkelEnhentY",
  ident = mapOf(
     Flykesnummer:class to Fylkesnummer(2),
     Kommuneløpenr:class to Kommuneløpenr(2),
     Gårdsnummer:class to Gårdsnummer(18),)
  idents = emptyMap(),
  sourceObject = None,
)
Entity(
  id = "TeigZ",
  ident = emptyMap(),
  idents = setOf(
    mapOf(
     Flykesnummer:class to Fylkesnummer(2),
     Kommuneløpenr:class to Kommuneløpenr(2),
     Gårdsnummer:class to Gårdsnummer(18)),
    mapOf(
     Flykesnummer:class to Fylkesnummer(2),
     Kommuneløpenr:class to Kommuneløpenr(2),
     Gårdsnummer:class to Gårdsnummer(17))
  )
  sourceObject = None
)
 */
