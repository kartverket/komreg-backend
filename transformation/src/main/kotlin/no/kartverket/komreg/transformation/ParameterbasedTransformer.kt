package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.transformation.parameters.Parameter

class ParameterbasedTransformer(private val parameters: List<Parameter>) {
    fun transform(flow: Flow<Entity>): Flow<Transformation> {
        return flow {
            parameters.filterIsInstance<Parameter.SpawningParameter>()
                .sortedBy { it.order() }
                .forEach {
                    emit(it.spawn())
                }

            flow.collect { entity ->
                val ident = entity.ident
                val transformedIdent = if (ident != null) {
                    parameters.filterIsInstance<Parameter.TransformingParameter>()
                        .map { it to it.matches(ident) }
                        .filter { it.second > 0 }
                        .maxByOrNull { it.second }
                        ?.first?.transform(ident)
                } else null

                if (transformedIdent != null) {
                    emit(
                        Transformation(
                            entity.id,
                            entity,
                            "parameter",
                            transformedIdent,
                            null,
                        )
                    )
                }
            }
        }
    }
}
