package no.kartverket.komreg.parameter.test

import io.kotest.core.spec.style.BehaviorSpec
import no.kartverket.komreg.parameter.domain.MatrikkelReceiverFunction
import no.kartverket.komreg.parameter.domain.withMatrikkelTypes

abstract class MatrikkelBehaviourSpec(
    body: MatrikkelReceiverFunction<BehaviorSpec, Unit>
) : BehaviorSpec({
    withMatrikkelTypes(this, body)
})