package no.kartverket.komreg.regulering.reform.domain.type

sealed interface NumberingScheme {
    val countyDigits: Set<Int>

    data object Production : NumberingScheme {
        override val countyDigits = setOf(2)
    }

    // Testfylker har 5 siffer (fra 10000)
    data object Test : NumberingScheme {
        override val countyDigits = setOf(2, 5)
    }

    companion object {
        fun fromName(name: String?): NumberingScheme = when (name?.lowercase()) {
            null, "", "production" -> Production
            "test" -> Test
            else -> throw IllegalArgumentException("Unknown numbering scheme: $name")
        }
    }
}
