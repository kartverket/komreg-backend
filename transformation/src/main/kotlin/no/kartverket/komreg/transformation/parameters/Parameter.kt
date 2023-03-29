package no.kartverket.komreg.transformation.parameters

import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation

sealed interface Parameter {

    interface TransformingParameter : Parameter {
        fun matches(ident: Ident): Int

        fun transform(ident: Ident): Ident
    }

    interface SpawningParameter : Parameter {
        fun order(): Int

        fun spawn(): Transformation
    }
}