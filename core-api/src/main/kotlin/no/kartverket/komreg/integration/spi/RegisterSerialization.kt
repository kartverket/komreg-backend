package no.kartverket.komreg.integration.spi

import kotlinx.serialization.modules.PolymorphicModuleBuilder
import no.kartverket.komreg.core.domain.IdType

interface RegisterSerialization {
    fun PolymorphicModuleBuilder<IdType<*, *>>.registerIdTypes()
    fun PolymorphicModuleBuilder<Payload>.registerPayloadTypes()
    fun PolymorphicModuleBuilder<Comparable<*>>.registerComparableTypes()
}
