package no.kartverket.komreg.transformation

import arrow.core.*
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import com.google.common.collect.*
import no.kartverket.komreg.transformation.rule.ComponentRule
import no.kartverket.komreg.transformation.error.DomainMismatch
import no.kartverket.komreg.transformation.error.RuleError
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.safeCast

/**
 * A domain for a component of a composite value. The domain is a set of values that the component can take.
 * The domain is defined by a classifier, which is the type of the component, and a range of values.
 * The range is defined by a minimum and maximum value, which may be null if the range is unbounded.
 * The domain is closed, i.e. the minimum and maximum values are included in the domain.
 *
 * @property classifier the type of the component
 * @property minValue the minimum value of the domain, or null if the domain is unbounded below
 * @property maxValue the maximum value of the domain, or null if the domain is unbounded above
 * @property distance a function that calculates the distance between two values in the domain
 *
 * @param A the type of the component
 */
abstract class ComponentDomain<A : Comparable<A>> {
    private val discreteDomain = object : DiscreteDomain<A>() {
        override fun next(value: A): A? = value + 1

        override fun previous(value: A): A? = value - 1

        override fun distance(start: A, end: A): Long =
            when (val distance = this@ComponentDomain.distance(start, end)) {
                Long.MIN_VALUE -> Long.MIN_VALUE
                else -> abs(distance)
            }

        override fun minValue(): A =
            this@ComponentDomain.minValue ?: throw NoSuchElementException()

        override fun maxValue(): A =
            this@ComponentDomain.maxValue ?: throw NoSuchElementException()
    }

    /**
     * The type of the component.
     */
    abstract val classifier: KClass<A>

    /**
     * The minimum value of the domain, or null if the domain is unbounded below.
     */
    abstract val minValue: A?

    /**
     * The maximum value of the domain, or null if the domain is unbounded above.
     */
    abstract val maxValue: A?

    /**
     * Returns the value in the domain that is distance n after the given value.
     *
     * @param n the distance
     * @return the value in the domain that is distance n after the given value
     * @throws ArithmeticException if the value is outside the domain
     * @throws NoSuchElementException if the value is outside the domain
     */
    abstract infix fun A.unsafePlus(n: Long): A

    /**
     * Returns the value in the domain that is n steps before the given value.
     * @param n the distance
     * @return the value in the domain that is n steps before the given value
     * @throws ArithmeticException if the value is outside the domain
     * @throws NoSuchElementException if the value is outside the domain
     */
    abstract infix fun A.unsafeMinus(n: Long): A

    /**
     * Returns the distance between two values in the domain.
     * @param start the start value
     * @param end the end value
     * @return the distance between the two values
     */
    abstract fun distance(start: A, end: A): Long

    /**
     * Returns the only value in the range - or null if the range is empty,
     * unbounded or the range span is greater than 1.
     */
    fun singleOrNull(aRange: Range<A>): A? {
        val a = if (aRange.hasLowerBound()) {
            when (aRange.lowerBoundType()) {
                BoundType.OPEN -> aRange.lowerEndpoint() + 1
                BoundType.CLOSED -> aRange.lowerEndpoint()
                null -> throw IllegalStateException()
            }
        } else {
            minValue
        }
        val b = if (aRange.hasUpperBound()) {
            when (aRange.upperBoundType()) {
                BoundType.OPEN -> discreteDomain.previous(aRange.upperEndpoint())
                BoundType.CLOSED -> aRange.upperEndpoint()
                null -> throw IllegalStateException()
            }
        } else {
            maxValue
        }
        return if (a != null && b != null && a == b) {
            a
        } else {
            null
        }
    }

    /** Returns true if the given value is in the domain, false otherwise. */
    operator fun contains(a: A): Boolean {
        val minValue = minValue
        if (minValue != null && a < minValue) return false
        val maxValue = maxValue
        return !(maxValue != null && a > maxValue)
    }

    /** Returns the value in the domain that is distance n after the given
     *  value, or null if there is no such value. */
    operator fun A.plus(n: Long): A? {
        val result = try {
            this unsafePlus n
        } catch (e: Exception) {
            when (e) {
                is ArithmeticException, is NoSuchElementException -> return null
                else -> throw e
            }
        }
        val maxValue = maxValue
        return if (maxValue == null || result <= maxValue) {
            result
        } else
            null
    }

    /** Returns the value in the domain that is n steps before the given value, or null if there is no such value. */
    operator fun A.minus(n: Long): A? = when (n) {
        Long.MIN_VALUE -> null
        else -> this + Math.negateExact(n)
    }

    fun Long.plus(a: A): A? = a + this

    fun Long.minus(a: A): A? = when (this) {
        Long.MIN_VALUE -> null
        else -> a + Math.negateExact(this)
    }

    /** Returns a range in its canonical form for this domain. */
    fun canonicalRange(range: Range<A>): Range<A> {
        // TODO: Denne burde returnere nullable, og feile hvis range er
        //       utenfor dette domenet.
        //       Grunnen er at:
        //       - gitt ett domene [1, 2] og en range [1,2] så
        //         returnerer denne metoden [1,inf) som er litt overraskende
        //         hvis man har regler som sier at [1,2] skal transformeres, og
        //         reglene utvides til et større domene (f.eks. [1,10])
        //         og man har kombinert en regel for [1,2] (fra det lille
        //         domenet) og [1,5] (fra det større domene), så får
        //         man vel en regel for [1,inf).
        return range.canonical(discreteDomain)
    }

    /**
     * Returns true if this domain encloses the other domain, false otherwise.
     *
     * A domain encloses another domain if the other domain has a classifier
     * that is a subclass of this domain's classifier, and the other domain's
     * range is a subset of this domain's range.
     *
     * @param other the other domain
     * @return true if this domain encloses the other domain, false otherwise
     */
    @JvmName("enclosesAny")
    fun encloses(other: ComponentDomain<*>): Boolean {
        return if (classifier == other.classifier || classifier.isSuperclassOf(other.classifier)) {
            @Suppress("UNCHECKED_CAST")
            encloses(other as ComponentDomain<out A>)
        } else {
            false
        }
    }

    @JvmName("intersectsAny")
    fun intersects(other: ComponentDomain<*>): Boolean {
        return if (classifier == other.classifier || classifier.isSuperclassOf(other.classifier)) {
            @Suppress("UNCHECKED_CAST")
            intersects(other as ComponentDomain<out A>)
        } else if (classifier.isSubclassOf(other.classifier)) {
            @Suppress("UNCHECKED_CAST")
            (other as ComponentDomain<A>).intersects(this)
        } else {
            false
        }
    }

    /**
     * Returns true if this domain intersects the other domain, false otherwise.
     *
     * Two domains intersect if they have the same classifier, and their ranges
     * overlap. (i.e. there is at least one value that is in both domains)
     *
     * @param other the other domain
     * @return true if this domain intersects the other domain, false otherwise
     */
    fun intersects(other: ComponentDomain<out A>): Boolean {

        val otherMinValue = other.minValue

        val otherMaxValue = other.maxValue

        return intersects(otherMinValue, otherMaxValue)
    }

    fun intersects(otherMinValue: A?, otherMaxValue: A?): Boolean {
        val minValue = minValue
        val maxValue = maxValue
        val cmpMinToMin = if (minValue != null) {
            if (otherMinValue != null) minValue.compareTo(otherMinValue) else -1
        } else {
            if (otherMinValue != null) 1 else 0
        }

        val cmpMinToMax = if (minValue != null) {
            if (otherMaxValue != null) minValue.compareTo(otherMaxValue) else 1
        } else {
            if (otherMaxValue != null) -1 else 0
        }

        val cmpMaxToMin = if (maxValue != null) {
            if (otherMinValue != null) maxValue.compareTo(otherMinValue) else -1
        } else {
            if (otherMinValue != null) 1 else 0
        }

        val cmpMaxToMax = if (maxValue != null) {
            if (otherMaxValue != null) maxValue.compareTo(otherMaxValue) else 1
        } else {
            if (otherMaxValue != null) -1 else 0
        }

        return cmpMinToMax <= 0 && cmpMaxToMin >= 0 || cmpMinToMin <= 0 && cmpMaxToMax >= 0
    }

    /** Returns the span of the domain, or null if the domain is unbounded. */
    val span: Long?
        get() {
            val minValue = minValue
            val maxValue = maxValue
            return if (minValue != null && maxValue != null) {
                distance(minValue, maxValue)
            } else {
                null
            }
        }

    fun span(range: Range<out A>) : Long? {
        val canonical = range.widen().canonical(discreteDomain)
        val upperBound = (canonical.takeIf { it.hasUpperBound() }?.upperEndpoint()) ?: return null
        return if (canonical.hasLowerBound()) {
            distance(canonical.lowerEndpoint(), upperBound)
        } else {
            null
        }
    }

    fun encloses(other: ComponentDomain<out A>): Boolean {
        assert(classifier == other.classifier || classifier.isSuperclassOf(other.classifier)) {
            "Classifiers do not match, this (should) only happen because of an erroneous unsafe cast"
        }
        val minValue = minValue
        if (minValue != null) {
            val otherMinValue = other.minValue
            if (otherMinValue == null || otherMinValue < minValue) return false
        }
        val maxValue = maxValue
        if (maxValue != null) {
            val otherMaxValue = other.maxValue
            if (otherMaxValue == null || otherMaxValue > maxValue) return false
        }
        return true
    }

    protected abstract fun isOfSameTypeAs(other: ComponentDomain<*>): Boolean

    /**
     * Returns true if this domain is of the same type as the other domain, false otherwise.
     *
     * Two domains are of the same type if they have the same classifier, and the same range.
     *
     * @param other the other domain
     * @return true if this domain is of the same type as the other domain, false otherwise
     */
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComponentDomain<*>) return false
        if (!isOfSameTypeAs(other)) return false

        if (classifier != other.classifier) return false
        if (minValue != other.minValue) return false
        if (maxValue != other.maxValue) return false

        return true
    }

    /**
     * Returns the hash code of this domain.
     *
     * @return the hash code of this domain
     */
    final override fun hashCode(): Int {
        var result = classifier.hashCode()
        result = 31 * result + (minValue?.hashCode() ?: 0)
        result = 31 * result + (maxValue?.hashCode() ?: 0)
        return result
    }

    /**
     * A base class for integral domains.
     * @see [ComponentDomain]
     */
    abstract class Integral<A : Comparable<A>>(
        final override val classifier: KClass<A>,
        final override val minValue: A?,
        final override val maxValue: A?
    ) : ComponentDomain<A>() {

        abstract fun Long.toA(): Either<*, A>
        abstract fun A.toLong(): Long

        final override fun A.unsafePlus(n: Long): A {
            val value = Math.addExact(this.toLong(), n)
            return verifiedToA(value)
        }

        final override fun A.unsafeMinus(n: Long): A {
            val value = Math.subtractExact(this.toLong(), n)
            return verifiedToA(value)
        }

        final override fun distance(start: A, end: A): Long {
            return abs(end.toLong() - start.toLong())
        }

        final override fun isOfSameTypeAs(other: ComponentDomain<*>): Boolean {
            return other is Integral<*>
        }

        private fun verifiedToA(value: Long): A {
            val a = value.toA().getOrElse { err ->
                throw ArithmeticException(err.toString())
            }
            if (minValue != null && a < minValue || maxValue != null && a > maxValue)
                throw NoSuchElementException("${classifier.simpleName} of $value does not exist")
            return a
        }

    }

    infix fun ComponentRule<out A>.plusInDomain(
        other: ComponentRule<out A>
    ): Either<RuleError, ComponentRule<A>> = either {
        val inThisDomain: (ComponentRule<out A>) -> (RaiseAccumulate<RuleError>.() -> ComponentRule<A>) = { rule ->
            {
                val selfDomain = this@ComponentDomain
                if (selfDomain == rule.domain) {
                    @Suppress("UNCHECKED_CAST")
                    rule as ComponentRule<A>
                } else {
                    rule.asCopy(selfDomain) ?: raise(
                        DomainMismatch(
                            selfDomain,
                            nonEmptySetOf(rule.domain)
                        )
                    )
                }
            }
        }

        zipOrAccumulate(RuleError::plus, inThisDomain(this@plusInDomain), inThisDomain(other)) { lhs, rhs ->
            (lhs + rhs).bind()
        }
    }

    fun ImmutableRangeSet<out A>.toSortedSet(): ImmutableSortedSet<A> = this.widen().asSet(discreteDomain)

    override fun toString(): String {
        return "${classifier.simpleName}[${minValue ?: "∞"}..${maxValue ?: "∞"}]"
    }

}

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A : Comparable<A>, B : A> Range<B>.widen(): Range<A> = this as Range<A>

@Suppress("UNCHECKED_CAST")
fun <A : Comparable<A>, B : A> RangeSet<B>.widen(): ImmutableRangeSet<A> {
    return if (this is ImmutableRangeSet) {
        this as ImmutableRangeSet<A>
    } else {
        ImmutableRangeSet.copyOf(this as RangeSet<A>)
    }
}

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A : Comparable<B>, B : A> ImmutableRangeSet<B>.widen()
        : ImmutableRangeSet<A> = this as ImmutableRangeSet<A>

fun <A : Comparable<A>> Range<in A>.toDomain(
    domain: ComponentDomain<A>
): Range<A>? {
    val range = if (this.hasLowerBound()) {
        val lowerEndpoint = domain.classifier.safeCast(this.lowerEndpoint()) ?: return null
        if (this.hasUpperBound()) {
            val upperBound = domain.classifier.safeCast(this.upperEndpoint()) ?: return null
            Range.range(lowerEndpoint, this.lowerBoundType(), upperBound, this.upperBoundType())
        } else {
            Range.downTo(lowerEndpoint, this.lowerBoundType())
        }
    } else if (this.hasUpperBound()) {
        val upperBound = domain.classifier.safeCast(this.upperEndpoint()) ?: return null
        Range.upTo(upperBound, this.upperBoundType())
    } else {
        Range.all()
    }

    return domain.canonicalRange(range)
}

fun <A : Comparable<A>> Range<in A>.narrowToDomainOrLeft(
    domain: ComponentDomain<A>
): Either<DomainMismatch<A>, Range<A>> {
    return toDomain(domain)?.right() ?: DomainMismatch(domain, nonEmptySetOf(domain)).left()
}

fun <A : Comparable<A>> RangeSet<in A>.narrowToDomainOrNull(domain: ComponentDomain<A>): ImmutableRangeSet<A>? {
    return this.asRanges().map { it.toDomain(domain) ?: return@narrowToDomainOrNull null }.let { ImmutableRangeSet.unionOf(it) }
}

fun <A : Comparable<A>> RangeSet<out A>.widenToDomainOrNull(domain: ComponentDomain<A>): ImmutableRangeSet<A>? {
    return this.asRanges().map { it.widen().toDomain(domain) ?: return@widenToDomainOrNull null }
        .let { ImmutableRangeSet.unionOf(it) }
}

fun <A : Comparable<A>> RangeSet<in A>.narrowToDomainOrLeft(domain: ComponentDomain<A>): Either<DomainMismatch<A>, ImmutableRangeSet<A>> =
    narrowToDomainOrNull(domain)?.right() ?: DomainMismatch(domain, nonEmptySetOf(domain)).left()

fun <A : Comparable<A>> RangeSet<out A>.widenToDomainOrLeft(domain: ComponentDomain<A>): Either<DomainMismatch<A>, ImmutableRangeSet<A>> =
    widenToDomainOrNull(domain)?.right() ?: DomainMismatch(domain, nonEmptySetOf(domain)).left()
