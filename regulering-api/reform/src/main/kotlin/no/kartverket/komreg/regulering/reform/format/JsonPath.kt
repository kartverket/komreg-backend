package no.kartverket.komreg.regulering.reform.format

/** Sti til en verdi i en reform-spec, for eksempel `$.operasjoner[0].from[1].then[2]`. */
@JvmInline
value class JsonPath(val value: String) {
    operator fun div(field: String): JsonPath = JsonPath("$value.$field")

    operator fun div(index: Int): JsonPath = JsonPath("$value[$index]")

    override fun toString(): String = value

    companion object {
        val Root = JsonPath("$")
    }
}
