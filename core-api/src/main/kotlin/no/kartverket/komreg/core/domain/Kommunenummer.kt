package no.kartverket.komreg.core.domain

import no.kartverket.komreg.core.*


data class Kommunenummer(val fylkesnummer: Fylkesnummer, val lopenummer: Lopenummer) {
    class Lopenummer private constructor(override val value: Byte) : NumberWrapper<Lopenummer, Byte>() {
        companion object {
            private const val LOPENUMMER_MAX_VALUE = 99L

            operator fun invoke(value: Long): KrData<Lopenummer> = when {
                value < 0L -> DataError("Kommuneløpenummer kan ikke være mindre enn 0: $value").asFailure()
                value == 0L -> DataError("Kommuneløpenummer kan ikke være 0: $value").asFailure()
                value > LOPENUMMER_MAX_VALUE -> DataError("Kommuneløpenummer kan ikke være større enn $LOPENUMMER_MAX_VALUE").asFailure()
                else -> Lopenummer(value.toByte()).asSuccess()
            }
        }
    }

    companion object {
        operator fun invoke(value: Long): KrData<Kommunenummer> = productMap(
            Fylkesnummer(value / 100L),
            Lopenummer(value % 100L),
            ::Kommunenummer
        )
    }
}