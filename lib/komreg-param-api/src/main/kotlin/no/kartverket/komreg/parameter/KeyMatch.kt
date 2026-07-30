package no.kartverket.komreg.parameter

import no.kartverket.komreg.parameter.data.HList

sealed interface KeyMatch<KI : HList, KL : Any> {
    val updatedKey: HList.Snoc<KI, KL>?
    val size: Int

    sealed interface Matched<KI : HList, KL : Any> : KeyMatch<KI, KL>

    data class Perfect<KI : HList, KL : Any>(
        override val updatedKey: HList.Snoc<KI, KL>?,
        val parameter: Parameter.Applied<Parameter<KI, KL>, KL>,
        val targetsParameters: Map<HList.Snoc<KI, KL>, Parameter.Applied<Parameter<KI, KL>, KL>>,
        override val size: Int
    ) : Matched<KI, KL>


    data class Partial<KI : HList, KL : Any>(
        override val updatedKey: HList.Snoc<KI, KL>,
        private val parameterMatch: Matched<*, *>,
        override val size: Int
    ) : Matched<KI, KL> {
        val parameter: Parameter.Applied<*, *> get() = parameterRec(parameterMatch)

        companion object {
            private fun parameterRec(matched: Matched<*, *>): Parameter.Applied<*, *> {
                return when (matched) {
                    is Partial<*, *> -> parameterRec(matched.parameterMatch)
                    is Perfect<*, *> -> matched.parameter
                }
            }
        }
    }

    data class Unmatched<KI : HList, KL : Any> (
        override val updatedKey: HList.Snoc<KI, KL>
    ) : KeyMatch<KI, KL> {
        override val size: Int
            get() = updatedKey.size
    }
}