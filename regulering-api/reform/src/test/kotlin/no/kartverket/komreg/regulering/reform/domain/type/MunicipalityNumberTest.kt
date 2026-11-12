package no.kartverket.komreg.regulering.reform.domain.type

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MunicipalityNumberTest {
    @Test
    fun `production splits 4 digits into county and serial`() = with(NumberingScheme.Production) {
        val number = MunicipalityNumber("0301")
        assertThat(number.county.value).isEqualTo("03")
        assertThat(number.serial.value).isEqualTo("01")
        assertThat(number.value).isEqualTo("0301")
    }

    @Test
    fun `production rejects test length and non-digits`() = with(NumberingScheme.Production) {
        assertFailsWith<IllegalArgumentException> { MunicipalityNumber("1000101") }
        assertFailsWith<IllegalArgumentException> { MunicipalityNumber("030") }
        assertFailsWith<IllegalArgumentException> { MunicipalityNumber("03a1") }
        assertFailsWith<IllegalArgumentException> { CountyNumber("10001") }
    }

    @Test
    fun `test scheme accepts both 4 and 7 digits`() = with(NumberingScheme.Test) {
        assertThat(MunicipalityNumber("0301").value).isEqualTo("0301")
        val number = MunicipalityNumber("1000101")
        assertThat(number.county.value).isEqualTo("10001")
        assertThat(number.serial.value).isEqualTo("01")
        assertFailsWith<IllegalArgumentException> { MunicipalityNumber("100001") }
        assertFailsWith<IllegalArgumentException> { MunicipalityNumber("10001001") }
    }

    @Test
    fun `serial is always two digits`() {
        assertFailsWith<IllegalArgumentException> { MunicipalitySerial("1") }
        assertFailsWith<IllegalArgumentException> { MunicipalitySerial("001") }
    }

    @Test
    fun `round trips through serializers in both schemes`() {
        for ((scheme, raw) in listOf(NumberingScheme.Production to "0301", NumberingScheme.Test to "1000101")) {
            val number = with(scheme) { MunicipalityNumber(raw) }
            val serializer = MunicipalityNumber.Serializer(scheme)
            val encoded = Json.encodeToString(serializer, number)
            assertThat(encoded).isEqualTo("\"$raw\"")
            assertThat(Json.decodeFromString(serializer, encoded)).isEqualTo(number)

            val county = number.county
            val countySerializer = CountyNumber.Serializer(scheme)
            assertThat(Json.decodeFromString(countySerializer, Json.encodeToString(countySerializer, county)))
                .isEqualTo(county)
        }
    }
}
