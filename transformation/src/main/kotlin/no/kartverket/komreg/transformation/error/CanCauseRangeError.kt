package no.kartverket.komreg.transformation.error

import com.google.common.collect.ImmutableRangeSet

sealed interface CanCauseRangeError<out A : Comparable<*>, out B : Comparable<*>> {
    val sourceRanges: ImmutableRangeSet<out A>
    val targetRanges: ImmutableRangeSet<out B>
}

interface CanCauseRangeErrorImpl<A : Comparable<A>, B : Comparable<B>> : CanCauseRangeError<A, B> {
    override val sourceRanges: ImmutableRangeSet<A>
    override val targetRanges: ImmutableRangeSet<B>
}