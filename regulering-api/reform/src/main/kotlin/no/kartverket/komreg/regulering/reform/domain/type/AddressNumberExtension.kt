package no.kartverket.komreg.regulering.reform.domain.type

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AddressNumberExtension(val value: Char) {
    init {
        require(value in 'A'..'Z' || value == 'Æ' || value == 'Ø' || value == 'Å') { "Unit number must be between A and Z or Æ, Ø, Å" }
    }
}
