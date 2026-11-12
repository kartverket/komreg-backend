package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.core.domain.Fylke
import no.kartverket.komreg.core.domain.Kommunenavn
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Koordinat
import no.kartverket.komreg.core.domain.Koordinatsystem
import no.kartverket.komreg.core.domain.Postadresse
import no.kartverket.komreg.core.domain.StandardRekvirent
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleObject
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.Postnummeromrade
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.adresse.PostnummeromradeId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Position
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.koder.KoordinatsystemKodeId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.KommuneId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.person.JuridiskPerson
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.person.JuridiskPersonId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.person.MottakerAdresse
import javax.xml.datatype.DatatypeConstants
import javax.xml.datatype.DatatypeFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.LocalDate as WsLocalDate
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Fylke as WsFylke
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommune as WsKommune

class MatrikkelApiMunicipalityCacheTest {
    private fun wsDate(y: Int, m: Int, d: Int) = WsLocalDate(
        DatatypeFactory.newInstance().newXMLGregorianCalendarDate(y, m, d, DatatypeConstants.FIELD_UNDEFINED),
    )

    private fun wsFylke(number: String, name: String, validTo: WsLocalDate? = null) = WsFylke().apply {
        id = MatrikkelBubbleId(number.toLong())
        fylkesnummer = number
        fylkesnavn = name
        gyldigTilDato = validTo
    }

    private fun wsKommune(id: Long, number: String, init: WsKommune.() -> Unit = {}) = WsKommune().apply {
        this.id = KommuneId(id)
        kommunenummer = number
        kommunenavn = "K$number"
        koordinatsystemKodeId = KoordinatsystemKodeId(10)
        init()
    }

    private val fullKommune = wsKommune(1, "3001") {
        gyldigTilDato = wsDate(2030, 6, 1)
        koordinatsystemKodeId = KoordinatsystemKodeId(13)
        senterpunkt = Position().apply { x = 1.5; y = 2.5 }
        setNedsattKonsesjonsgrense(true)
        brukteGardsnummer = "1-9"
        postadresse = MottakerAdresse().apply {
            adresselinje1 = " Rådhuset "
            adresselinje2 = "  "
            adresselinje3 = "Boks 1"
            postnummeromradeId = PostnummeromradeId(100)
        }
        standardRekvirentTinglysingId = JuridiskPersonId(200)
    }

    private val postalArea = Postnummeromrade().apply {
        id = PostnummeromradeId(100)
        kretsnummer = 123
        kretsnavn = "STED"
    }

    private val registrar = JuridiskPerson().apply {
        id = JuridiskPersonId(200)
        nummer = "999888777"
        navn = "Rekvirent AS"
    }

    private class FakeSource(
        var counties: List<WsFylke> = emptyList(),
        var municipalities: List<WsKommune> = emptyList(),
        var objects: List<MatrikkelBubbleObject> = emptyList(),
        var coats: Map<Long, ByteArray> = emptyMap(),
    ) : MunicipalityDataSource {
        var municipalityLoads = 0
        var coatFetches = 0

        override fun fetchCounties() = counties
        override fun fetchMunicipalities(): List<WsKommune> = municipalities.also { municipalityLoads++ }
        override fun fetchObjects(ids: Collection<MatrikkelBubbleId>) = objects.filter { o -> ids.any { it.value == o.id.value } }
        override fun fetchCoatOfArms(municipality: KommuneId): ByteArray? =
            coats[municipality.value].also { coatFetches++ }
    }

    private fun cache(source: FakeSource, scheme: NumberingScheme = NumberingScheme.Production) =
        MatrikkelApiMunicipalityCache(source, scheme)

    @Test
    fun `fylke mappes med nummer, navn og utløpsdato`() {
        val source = FakeSource(counties = listOf(wsFylke("03", "Oslo"), wsFylke("30", "Viken", wsDate(2024, 1, 1))))
        assertThat(cache(source).findAlleFylker()).containsExactly(
            Fylke(3, "Oslo", null),
            Fylke(30, "Viken", LocalDate(2024, 1, 1)),
        )
    }

    @Test
    fun `kommune mappes med alle felt`() {
        val source = FakeSource(
            municipalities = listOf(fullKommune),
            objects = listOf(postalArea, registrar),
            coats = mapOf(1L to byteArrayOf(1, 2)),
        )
        val kommune = cache(source).findAlleKommuner().single()
        assertThat(kommune.kommunenummer).isEqualTo(Kommunenummer(30L * 100 + 1))
        assertThat(kommune.kommunenavn).isEqualTo(Kommunenavn("K3001"))
        assertThat(kommune.gyldigTilDato).isEqualTo(LocalDate(2030, 6, 1))
        assertThat(kommune.koordinatsystem).isEqualTo(Koordinatsystem.UTM35)
        assertThat(kommune.senterpunkt).isEqualTo(Koordinat(1.5, 2.5))
        assertThat(kommune.nedsattKonsesjonsgrense).isEqualTo(true)
        assertThat(kommune.godkjenteGardsnumre).isEqualTo("1-9")
        assertThat(kommune.adresse).isEqualTo(Postadresse("Rådhuset", null, "Boks 1", "0123", "STED"))
        assertThat(kommune.standardRekvirent).isEqualTo(StandardRekvirent("999888777", "Rekvirent AS"))
        assertThat(kommune.kommunevapen.toList()).containsExactly(1.toByte(), 2.toByte())
    }

    @Test
    fun `manglende postnummerområde, rekvirent, våpen og gårdsnumre gir null eller tomme verdier`() {
        val source = FakeSource(municipalities = listOf(fullKommune.apply { brukteGardsnummer = null }))
        val kommune = cache(source).findAlleKommuner().single()
        assertThat(kommune.adresse).isNull()
        assertThat(kommune.standardRekvirent).isNull()
        assertThat(kommune.kommunevapen.size).isEqualTo(0)
        assertThat(kommune.godkjenteGardsnumre).isEqualTo("")
    }

    @Test
    fun `koordinatsystem tolkes fra kodeId`() {
        assertThat(listOf(10L, 11L, 13L, 99L, null).map { koordinatsystemFraKodeId(it) }).containsExactly(
            Koordinatsystem.UTM32, Koordinatsystem.UTM33, Koordinatsystem.UTM35, Koordinatsystem.UKJENT, Koordinatsystem.UKJENT,
        )
    }

    @Test
    fun `testfylker med fem sifre splittes på de to siste sifrene`() {
        val source = FakeSource(municipalities = listOf(wsKommune(7, "1000012")))
        val kommune = cache(source, NumberingScheme.Test).findAlleKommuner().single()
        assertThat(kommune.kommunenummer.fylkesnummer.value).isEqualTo(10000)
        assertThat(kommune.kommunenummer.lopenummer.value).isEqualTo(12.toByte())
    }

    @Test
    fun `ukjent kommune laster på nytt én gang`() {
        val source = FakeSource(municipalities = listOf(wsKommune(1, "3001")))
        val cache = cache(source)
        assertThat(source.municipalityLoads).isEqualTo(1)

        source.municipalities = listOf(wsKommune(1, "3001"), wsKommune(2, "3002"))
        assertThat(cache[MunicipalityId(2)].value).isEqualTo("3002")
        assertThat(source.municipalityLoads).isEqualTo(2)

        cache[MunicipalityId(1)]
        assertThat(source.municipalityLoads).isEqualTo(2)

        assertFailsWith<NoSuchElementException> { cache[MunicipalityId(99)] }
        assertThat(source.municipalityLoads).isEqualTo(3)
    }

    @Test
    fun `kommunevåpen hentes første gang det trengs, og bare én gang`() {
        val source = FakeSource(
            municipalities = listOf(wsKommune(1, "3001"), wsKommune(2, "3002")),
            coats = mapOf(2L to byteArrayOf(9)),
        )
        val cache = cache(source)

        cache.withoutCoatOfArms.findAlleKommuner()
        assertThat(source.coatFetches).isEqualTo(0)

        val kommune = cache.findKommune(Kommunenummer(3002))
        assertThat(kommune).isNotNull()
        assertThat(kommune!!.kommunevapen.toList()).containsExactly(9.toByte())
        cache.findKommune(Kommunenummer(3002))
        assertThat(source.coatFetches).isEqualTo(1)
    }

    @Test
    fun `findKommune laster på nytt ved bom og gir null hvis kommunen fortsatt mangler`() {
        val source = FakeSource(municipalities = listOf(wsKommune(1, "3001")))
        val cache = cache(source)
        assertThat(cache.findKommune(Kommunenummer(3005))).isNull()
        assertThat(source.municipalityLoads).isEqualTo(2)
    }
}
