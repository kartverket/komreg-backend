package no.kartverket.komreg.core.domain

import io.kotest.core.spec.style.AnnotationSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import no.kartverket.komreg.integration.spi.*
import org.junit.jupiter.api.Assertions.assertEquals

private typealias AdressenummerbokstavIdent = Ident1<Adressenummerbokstav>

class AdressenummerbokstavTest : AnnotationSpec() {

    val json = Json {

        serializersModule = SerializersModule {
            polymorphic(Comparable::class) {

                subclass(Adressenummerbokstav::class, Adressenummerbokstav.serializer())

            }
        }
    }

    private lateinit var AdressenummerbokstavIdent: IdentType1<Adressenummerbokstav>

    @BeforeClass
    suspend fun setUp() {
        AdressenummerbokstavIdent = identTypeOf1()
    }

    @Test
    fun `skal serialisere til 0 for adressenummerbokstav naar denne er NONE`() {
        val ident: AdressenummerbokstavIdent =
            AdressenummerbokstavIdent(Adressenummerbokstav(null))

        assertEquals(Adressenummerbokstav(null), ident.getOrNull<Adressenummerbokstav>())

        val json = json.encodeToString(Ident.serializer(), ident)

        assertEquals(
            """{"type":["no.kartverket.komreg.core.domain.Adressenummerbokstav"],"values":[{"type":"no.kartverket.komreg.core.domain.Adressenummerbokstav","value":null}]}""",
            json
        )

    }
}