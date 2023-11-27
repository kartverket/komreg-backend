package no.kartverket.komreg.validation

import no.kartverket.komreg.routes.*

enum class ErrorType {
    FYLKESDELING_MANGLER_KOMMUNER,
    KOMMUNEDELING_MANGLER_MATRIKKELENHETER,
    KOMMUNEDELING_MANGLER_KRETSER,
    KOMMUNEDELING_MANGLER_TEIGER,
    KOMMUNEDELING_MANGLER_VEGER,
    VEGDELING_MANGLER_VEGADRESSER,
}

class ReguleringValidator {

    companion object {
        fun validateFylkesdeling(regulering: Regulering): Map<String, List<ErrorType>> {
            val errors = mutableMapOf<String, List<ErrorType>>()

            regulering.endringer.forEach { endring ->
                val isFylkesdeling = endring.transformasjoner.any {
                    it is FylkeTransformasjonDTO && it.fylkesnummer.til.size > 1
                }

                if (isFylkesdeling && !hasKommunetransformasjon(endring)) {
                    errors[endring.id] = listOf(ErrorType.FYLKESDELING_MANGLER_KOMMUNER)
                }
            }

            return errors
        }

        fun validateKommunedeling(regulering: Regulering): Map<String, List<ErrorType>> {
            val errors = mutableMapOf<String, List<ErrorType>>()

            regulering.endringer.forEach { endring ->
                val isKommunedeling = endring.transformasjoner.any {
                    it is KommuneTransformasjonDTO && it.kommuneløpenummer.til.size > 1
                }

                if (isKommunedeling) {
                    var existingErrors = errors.getOrDefault(endring.id, emptyList())

                    if (!hasKretstrasformasjon(endring)) {
                        existingErrors = existingErrors.plus(ErrorType.KOMMUNEDELING_MANGLER_KRETSER)
                    }
                    if (!hasMatrikkelenhettransformasjon(endring)) {
                        existingErrors = existingErrors.plus(ErrorType.KOMMUNEDELING_MANGLER_MATRIKKELENHETER)
                    }
                    if (!hasTeigtransformasjon(endring)) {
                        existingErrors = existingErrors.plus(ErrorType.KOMMUNEDELING_MANGLER_TEIGER)
                    }
                    if (!hasVegadressetransformasjon(endring)) {
                        existingErrors = existingErrors.plus(ErrorType.KOMMUNEDELING_MANGLER_VEGER)
                    }

                    println(existingErrors)

                    errors[endring.id] = existingErrors
                }
            }
            return errors
        }

        fun validateVegdeling(regulering: Regulering): Map<String, List<ErrorType>> {
            val errors = mutableMapOf<String, List<ErrorType>>()

            regulering.endringer.forEach { endring ->
                val isVegdeling = endring.transformasjoner.any {
                    it is VegTransformasjonDTO && it.kommuneløpenummer.til.size > 1
                }

                if (isVegdeling && !hasVegadressetransformasjon(endring)) {
                    errors[endring.id] = listOf(ErrorType.VEGDELING_MANGLER_VEGADRESSER)
                }
            }

            return errors
        }

        fun validate(
            regulering: Regulering
        ): Map<String, List<ErrorType>> {
            val errors = mutableMapOf<String, List<ErrorType>>()

            errors.putAll(validateFylkesdeling(regulering))
            errors.putAll(validateKommunedeling(regulering))
            errors.putAll(validateVegdeling(regulering))

            return errors
        }

        private fun hasKommunetransformasjon(endring: EndringDTO) =
            endring.transformasjoner.any { it is KommuneTransformasjonDTO }

        private fun hasKretstrasformasjon(endring: EndringDTO) =
            endring.transformasjoner.any { it is KretsTransformasjonDTO }

        private fun hasMatrikkelenhettransformasjon(endring: EndringDTO) =
            endring.transformasjoner.any { it is MatrikkelenhetTransformasjonDTO }

        private fun hasTeigtransformasjon(endring: EndringDTO) =
            endring.transformasjoner.any { it is TeigTransformasjonDTO }

        private fun hasVegadressetransformasjon(endring: EndringDTO) =
            endring.transformasjoner.any { it is VegadresseTransformasjonDTO }
    }
}