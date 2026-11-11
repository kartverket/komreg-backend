package no.kartverket.komreg.regulering.reform.format

/**
 * JSON-eksempler på reform-spec-er, under `reform-spec/` i testFixtures-ressursene.
 *
 * - [valid]: gyldige spec-er, i kanonisk form (`format` med, ingen tomme `then`), så de kan kodes tilbake til
 *   samme JSON. Payloadene er komplette.
 * - [invalidShape]: kan ikke dekodes til [ReformSpec].
 * - [invalidValues]: kan dekodes til [ReformSpec], men har ett ugyldig segment (med `NumberingScheme.Production`).
 */
object ReformSpecFixtures {
    val valid = listOf("design-example", "adjust", "move", "merge-existing", "merge-new", "split", "create")

    val invalidShape = listOf(
        "unknown-op",
        "missing-kind",
        "missing-ikrafttredelse",
        "into-both-existing-and-new",
        "into-neither-existing-nor-new",
        "null-segment",
        "nested-array-segment",
        "target-not-array",
        "unknown-role",
        "payload-not-object",
    )

    /** Det ugyldige segmentet: JSON-stien og kinden det skal dekodes som. */
    data class InvalidSegment(val path: String, val kind: String)

    val invalidValues = mapOf(
        "county-one-digit" to InvalidSegment("$.operasjoner[0].key", V1Kinds.COUNTY),
        "county-as-number" to InvalidSegment("$.operasjoner[0].key", V1Kinds.COUNTY),
        "address-code-zero" to InvalidSegment("$.operasjoner[0].key[2]", V1Kinds.ADDRESS_CODE),
        "title-number-as-string" to InvalidSegment("$.operasjoner[0].key[3]", V1Kinds.TITLE_NUMBER),
        "address-number-overflow" to InvalidSegment("$.operasjoner[0].key[3]", V1Kinds.ADDRESS_NUMBER),
        "municipality-serial-in-target" to InvalidSegment("$.operasjoner[0].to[1]", V1Kinds.MUNICIPALITY),
    )

    fun valid(name: String): String = read("valid", name)

    fun invalidShape(name: String): String = read("invalid-shape", name)

    fun invalidValues(name: String): String = read("invalid-values", name)

    private fun read(group: String, name: String): String =
        requireNotNull(javaClass.getResource("/reform-spec/$group/$name.json")) { "Fant ikke fixture $group/$name" }
            .readText()
}
