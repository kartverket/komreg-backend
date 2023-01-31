package no.kartverket.komreg.core.domain

import no.kartverket.komreg.core.*

class Fylkesnummer private constructor(
    override val value: Long
) : NumberWrapper<Fylkesnummer, Long>() {
    companion object {
        private const val MAX_VALUE = Long.MAX_VALUE / 100L
        private const val WEAK_MAX_VALUE = 99L

        operator fun invoke(value: Long): KrData<Fylkesnummer> = when {
            value < 0L -> DataError("Fylkesnummer kan ikke være mindre enn 0: $value").asFailure()
            value == 0L -> DataError("Fylkesnummer kan ikke være 0: $value").asFailure()
            value > MAX_VALUE -> DataError("Fylkesnummer kan ikke være større enn $MAX_VALUE: $value").asFailure()
            else -> Fylkesnummer(value).asSuccess().withLog().useLogTap {
                if (it.value > WEAK_MAX_VALUE) {
                    warning("Fylkesnummer kan ikke være større enn $WEAK_MAX_VALUE: ${it.value}")
                }
            }
        }
    }
}

