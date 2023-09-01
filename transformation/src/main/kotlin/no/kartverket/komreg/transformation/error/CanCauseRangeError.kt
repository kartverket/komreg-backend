package no.kartverket.komreg.transformation.error

import com.google.common.collect.ImmutableRangeSet
import no.kartverket.komreg.transformation.ComponentDomain

sealed interface CanCauseRangeError<out A : Comparable<*>> {
    val domain: ComponentDomain<out A>
    val sourceRanges: ImmutableRangeSet<out A>
    val targetRanges: ImmutableRangeSet<out A>
    interface Base<out A :Comparable<*>> : CanCauseRangeError<A>
}

interface CanCauseRangeErrorImpl<X : Comparable<X>> : CanCauseRangeError.Base<X> {
    override val domain: ComponentDomain<X>
    override val sourceRanges: ImmutableRangeSet<X>
    override val targetRanges: ImmutableRangeSet<X>
}