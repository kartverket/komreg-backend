package no.kartverket.komreg.integration.spi

import io.kotest.core.spec.style.AnnotationSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import kotlin.IllegalArgumentException

private typealias KommuneIdent = Ident2<Fylkesnummer, Kommunenummer.Lopenummer>
private typealias FylkeIdent = Ident1<Fylkesnummer>

@Suppress("PrivatePropertyName", "RemoveExplicitTypeArguments", "LocalVariableName")
class IdentTest : AnnotationSpec() {

    data class Foo(val n: Int) : Comparable<Foo> {
        override fun compareTo(other: Foo): Int = n.compareTo(other.n)
    }

    abstract class Bar : Comparable<Bar> {
        abstract val n: Int
        override fun compareTo(other: Bar): Int = n.compareTo(other.n)
    }

    data class Baz(override val n: Int) : Bar()

    private lateinit var FooBazIdent: IdentType2<Foo, Baz>
    private lateinit var KommuneIdent: IdentType2<Fylkesnummer, Kommunenummer.Lopenummer>
    private lateinit var FylkeIdent: IdentType1<Fylkesnummer>
    private lateinit var RearrangedIdent: IdentType2<Kommunenummer.Lopenummer, Fylkesnummer>
    private lateinit var LongerIdent: IdentType5<Fylkesnummer, Kommunenummer.Lopenummer, String, Foo, Bar>

    @BeforeClass
    suspend fun setUp() {
        FooBazIdent = identTypeOf2()
        KommuneIdent = identTypeOf2()
        FylkeIdent = identTypeOf1()
        RearrangedIdent = identTypeOf2()
        LongerIdent = identTypeOf5()
    }

    @Test
    fun createAndGet() {
        val ident : KommuneIdent =
            KommuneIdent(Fylkesnummer(98), Kommunenummer.Lopenummer(76))

        assertEquals(Fylkesnummer(98), ident.getOrNull<Fylkesnummer>())
        assertEquals(
            Kommunenummer.Lopenummer(76),
            ident.getOrNull<Kommunenummer.Lopenummer>())
    }

    @Test
    suspend fun duplicatesNotAllowed() {
        val e = assertThrows<IllegalArgumentException> {
            identTypeOf2<Fylkesnummer, Fylkesnummer>()
        }
        assertEquals(
            "Some types does not have a distinct index: " +
                    "{no.kartverket.komreg.core.domain.Fylkesnummer=[0, 1]}",
            e.message
        )
    }

    @Test
    fun canGetValues() {
        val fnr = Fylkesnummer(99)
        val knr = Kommunenummer.Lopenummer(98)
        val ident: KommuneIdent = KommuneIdent(fnr, knr)

        assertEquals(fnr, ident<Fylkesnummer>())
        assertEquals(knr, ident<Kommunenummer.Lopenummer>())
    }

    @Test
    fun canTransformValues() {
        val fnr = Fylkesnummer(99)
        val knr = Kommunenummer.Lopenummer(99)
        val ident: KommuneIdent =
            KommuneIdent(fnr, knr)

        assertEquals(
            KommuneIdent(Fylkesnummer(100), knr),
            ident.updateOrThrow { _: Fylkesnummer -> Fylkesnummer(100)})

        assertEquals(
            KommuneIdent(fnr, Kommunenummer.Lopenummer(100)),
            ident.updateOrThrow { _: Kommunenummer.Lopenummer ->
                Kommunenummer.Lopenummer(100)
            }
        )
    }

    @Test
    suspend fun canDropLast() {
        val TulleIdent: IdentType3<Fylkesnummer, Kommunenummer.Lopenummer, String> =
            identTypeOf3<Fylkesnummer, Kommunenummer.Lopenummer, String>()
        val fnr = Fylkesnummer(99)
        val knr = Kommunenummer.Lopenummer(99)
        val ident: Ident3<Fylkesnummer, Kommunenummer.Lopenummer, String> =
            TulleIdent(fnr, knr, "Tull Og Tøys")

        assertEquals(KommuneIdent(fnr, knr), ident.dropLast())
    }

    @Test
    suspend fun canAppend() {
        val fnr = Fylkesnummer(99)
        val knr = Kommunenummer.Lopenummer(99)
        val fylkeIdent: FylkeIdent = FylkeIdent(fnr)

        val kommuneIdent: KommuneIdent =
            with(identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()) {
                fylkeIdent.append(knr)
            }

        assertEquals(KommuneIdent(fnr, knr), kommuneIdent)
    }

    @Test
    suspend fun cacheReturnsSame() {
        assertSame(KommuneIdent, identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>())
    }


    @Test
    fun updateByIndexThrowsClassCastException() {
        assertThrows<ClassCastException> {
            FylkeIdent(Fylkesnummer(99)).updateOrThrow(0) { it.toString() }
        }
    }

    @Test
    fun destructuringTest() {
        val foo = Foo(99)
        val baz = Baz(99)

        // Destructuring, legg merke til at Bar er en superklasse av Baz
        val (fnr2: Foo, knr2: Bar)=
            FooBazIdent(foo, baz)

        assertEquals(foo, fnr2)
        assertEquals(baz, knr2)
    }

    @Test
    fun mappingTest() {
        val fnr = Fylkesnummer(99)
        val knr = Kommunenummer.Lopenummer(99)
        val toFylkeIdent =
            KommuneIdent.createMapper(FylkeIdent)

        assertNotNull(toFylkeIdent) { "Mapper should be created" }
        assertEquals(FylkeIdent(fnr), toFylkeIdent!!.invoke(KommuneIdent(fnr, knr)))
        assertFalse(toFylkeIdent.complete)

        val toRearrangedIdent =
            KommuneIdent.createMapper(RearrangedIdent)
        assertNotNull(toRearrangedIdent) { "Mapper for rearranged ident should be created" }
        assertEquals(RearrangedIdent(knr, fnr), toRearrangedIdent!!(KommuneIdent(fnr, knr)))
        assertTrue(toRearrangedIdent!!.complete)
    }

    @Test
    fun updateTest() {
        val fnr = Fylkesnummer(99)
        val knr = Kommunenummer.Lopenummer(99)
        val ident: KommuneIdent =
            KommuneIdent(fnr, knr)

        val updatedFnr = ident.update { it: Fylkesnummer -> Fylkesnummer(it.value + 1) }
        assertEquals(KommuneIdent(Fylkesnummer(100), knr), updatedFnr)

        val updatedKnr = ident.update { it: Kommunenummer.Lopenummer -> Kommunenummer.Lopenummer((it.value + 1).toByte()) }
        assertEquals(KommuneIdent(fnr, Kommunenummer.Lopenummer(100)), updatedKnr)

        val longerIdent: Ident5<Fylkesnummer, Kommunenummer.Lopenummer, String, Foo, Bar>  = LongerIdent(fnr, knr, "foo", Foo(1), Baz(2))

        val updatedLongerIdent: Ident5<Fylkesnummer, Kommunenummer.Lopenummer, String, Foo, Bar> = longerIdent
            .update { it: Foo -> Foo(it.n + 1) }
            .update { it: Bar -> Baz(it.n + 1) }
            .update { it: Fylkesnummer -> Fylkesnummer(it.value + 1) }

        assertEquals(LongerIdent(Fylkesnummer(fnr.value + 1), knr, "foo", Foo(2), Baz(3)), updatedLongerIdent)
    }

    @Test
    suspend fun serialization() {
        val identType = identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()
        val json = Json {
            serializersModule = SerializersModule {
                polymorphic(Comparable::class) {
                    subclass(Fylkesnummer::class)
                    subclass(Kommunenummer.Lopenummer::class)
                }
            }
        }

        val ident1: Ident = identType(Fylkesnummer(98), Kommunenummer.Lopenummer(76))
        val str = json.encodeToString(ident1)
        val ident2 = json.decodeFromString<Ident>(str)

        assertEquals(ident1, ident2)
    }
}
