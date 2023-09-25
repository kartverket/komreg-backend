package no.kartverket.komreg

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import no.kartverket.komreg.core.domain.*
import no.kartverket.komreg.integration.spi.Payload
import no.statkart.matrikkel.komreg.model.*


fun jsonSerializer() = Json {
    // val x = ServiceLoader.load(RegisterSerialization::class.java).toList()
    serializersModule = SerializersModule {
        polymorphic(IdType::class) {
            // TODO: fjern og gi x bedre navn
            subclass(Matrikkeltyper::class, PolymorphicEnumSerializer(serializer()))
//            x.forEach {
//                with(it) {
//                    registerIdTypes()
//                }
//            }
        }
        polymorphic(Payload::class) {
            subclass(Kommunedata::class)
            subclass(Bygningsdata::class)
            subclass(MnrManglerReferanser::class)
            subclass(Matrikkelenhetsdata::class)
//            x.forEach {
//                with(it) {
//                    registerPayloadTypes()
//                }
//            }
        }
        polymorphic(Comparable::class) {
            subclass(Fylkesnummer::class, Fylkesnummer.serializer())
            subclass(Kommunenummer.Lopenummer::class, Kommunenummer.Lopenummer.serializer())
            subclass(Matrikkelnummer.Gardsnummer::class, Matrikkelnummer.Gardsnummer.serializer())
            subclass(Matrikkelnummer.Bruksnummer::class, Matrikkelnummer.Bruksnummer.serializer())
            subclass(Matrikkelnummer.Festenummer::class, Matrikkelnummer.Festenummer.serializer())
            subclass(Matrikkelnummer.Seksjonsnummer::class, Matrikkelnummer.Seksjonsnummer.serializer())
            subclass(Bygningsnummer::class, Bygningsnummer.serializer())
            subclass(SefrakObjektnummer::class, SefrakObjektnummer.serializer())
            subclass(Kretstype::class, Kretstype.serializer())
            subclass(Kretsnummer::class, Kretsnummer.serializer())
            subclass(Adressekode::class, Adressekode.serializer())
            subclass(Adressenummernummer::class, Adressenummernummer.serializer())
            subclass(Adressenummerbokstav::class, Adressenummerbokstav.serializer())
        }
    }
}
