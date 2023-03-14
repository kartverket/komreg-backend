package no.kartverket.komreg.integration.spi

import kotlinx.coroutines.flow.Flow

data class Entity(
    val id: String,
    val ident: Map<*, *>? = null,
    val associatedIdents: Set<Map<*, *>>? = null,
    val sourceObject: Any? = null,
)

data class Transformation(
    val id: String,
    val transformationType: Any,
    val transformedIdent: Map<*, *>,
    val transformedAssociatedIdents: Set<Map<*, *>>? = null,
    val sourceObject: Any? = null,
)

interface EntitySource {
    val id: String
    val entityFlow: Flow<Entity>
    val preValidation: Set<() -> Unit>
    val postValidation: Set<() -> Unit>
}

interface EntitySink {
    val id: String
    fun consumeTransformations(flow: Flow<Transformation>)
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
