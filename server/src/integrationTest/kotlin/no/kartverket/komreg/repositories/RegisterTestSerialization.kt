package no.kartverket.komreg.repositories

import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import no.kartverket.komreg.PolymorphicEnumSerializer
import no.kartverket.komreg.core.domain.IdType
import no.kartverket.komreg.integration.spi.Payload
import no.kartverket.komreg.integration.spi.RegisterSerialization

class RegisterTestSerialization : RegisterSerialization {
    override fun PolymorphicModuleBuilder<IdType<*, *>>.registerIdTypes() {
        subclass(TestIdType::class, PolymorphicEnumSerializer(serializer()))
    }

    override fun PolymorphicModuleBuilder<Payload>.registerPayloadTypes() {
        subclass(TestPayload::class)
    }

    override fun PolymorphicModuleBuilder<Comparable<*>>.registerComparableTypes() {
    }
}
