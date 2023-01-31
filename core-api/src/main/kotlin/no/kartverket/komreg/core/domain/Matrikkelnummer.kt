package no.kartverket.komreg.core.domain

import no.kartverket.komreg.core.*

data class Matrikkelnummer(
    val kommunenummer: Kommunenummer,
    val gardsnummer: Gardsnummer,
    val bruksnummer: Bruksnummer,
    val festenummer: Festenummer?,
    val seksjonsnummer: Seksjonsnummer?
) {
    class Gardsnummer private constructor(override val value: Int) : NumberWrapper<Gardsnummer, Int>() {
            companion object {
                private const val MAX_VALUE: Int = 99_999

                operator fun invoke(value: Long): KrData<Gardsnummer> = when {
                    value < 0L -> DataError("Gårdsnummer kan ikke være mindre enn 0: $value").asFailure()
                    value == 0L -> DataError("Gårdsnummer kan ikke være 0").asFailure()
                    value > Int.MAX_VALUE -> DataError("Gårdsnummer kan ikke være større enn ${Int.MAX_VALUE}: $value").asFailure()
                    else -> Gardsnummer(value.toInt()).asSuccess().withLog().useLogTap {
                        if (it.value > MAX_VALUE) {
                            warning("Gårdsnummer er større enn $MAX_VALUE: $it")
                        }
                    }
                }
            }
        }

    class Bruksnummer private constructor(override val value: Short) : NumberWrapper<Bruksnummer, Short>() {
            companion object {
                private const val MAX_VALUE: Short = 9999

                operator fun invoke(value: Long): KrData<Bruksnummer> = when {
                    value < 0L -> DataError("Bruksnummer kan ikke være mindre enn 0: $value").asFailure()
                    value == 0L -> DataError("Bruksnummer kan ikke være 0").asFailure()
                    value > Short.MAX_VALUE -> DataError("Bruksnummer kan ikke være større enn ${Short.MAX_VALUE}: $value").asFailure()
                    else -> Bruksnummer(value.toShort()).asSuccess().withLog().useLogTap {
                        if (it.value > MAX_VALUE) {
                            warning("Bruksnummer er større enn $MAX_VALUE: $it")
                        }
                    }
                }
            }
        }

    class Festenummer private constructor(override val value: Short) : NumberWrapper<Festenummer, Short>() {
        companion object {
            private const val MAX_VALUE: Short = 9999

            operator fun invoke(value: Long): KrData<Festenummer> = when {
                value < 0L -> DataError("Festenummer kan ikke være mindre enn 0: $value").asFailure()
                value == 0L -> DataError("Festenummer kan ikke være 0").asFailure()
                value > Short.MAX_VALUE -> DataError("Festenummer kan ikke være større enn ${Short.MAX_VALUE}: $value").asFailure()
                else -> Festenummer(value.toShort()).asSuccess().withLog().useLogTap {
                    if (it.value > MAX_VALUE) {
                        warning("Festenummer er større enn $MAX_VALUE: $it")
                    }
                }
            }
        }
    }

    class Seksjonsnummer private constructor(override val value: Short) : NumberWrapper<Seksjonsnummer, Short>() {
            companion object {
                private const val MAX_VALUE: Short = 9999

                operator fun invoke(value: Long): KrData<Seksjonsnummer> = when {
                    value < 0L -> DataError("Seksjonsnummer kan ikke være mindre enn 0: $value").asFailure()
                    value == 0L -> DataError("Seksjonsnummer kan ikke være 0").asFailure()
                    value > Short.MAX_VALUE -> DataError("Seksjonsnummer kan ikke være større enn ${Short.MAX_VALUE}: $value").asFailure()
                    else -> Seksjonsnummer(value.toShort()).asSuccess().withLog().useLogTap {
                        if (it.value > MAX_VALUE) {
                            warning("Seksjonsnummer er større enn $MAX_VALUE: $it")
                        }
                    }
                }
            }
    }

    companion object {
        operator fun invoke(
            kommuenummer: Long,
            gardsnummer: Long,
            bruksnummer: Long,
            festenummer: Long?,
            seksjonsnummer: Long?
        ): KrData<Matrikkelnummer> =
            productMap(
                Kommunenummer(kommuenummer),
                Gardsnummer(gardsnummer),
                Bruksnummer(bruksnummer),
                festenummer
                    ?.takeUnless { it == 0L }
                    ?.let { Festenummer(it) }
                    ?: (null as Festenummer?).asSuccess(),
                seksjonsnummer
                    ?.takeUnless { it == 0L }
                    ?.let { Seksjonsnummer(it) }
                    ?: (null as Seksjonsnummer?).asSuccess(),
                ::Matrikkelnummer
            )
    }

    val fylkesnummer: Fylkesnummer = kommunenummer.fylkesnummer
}
