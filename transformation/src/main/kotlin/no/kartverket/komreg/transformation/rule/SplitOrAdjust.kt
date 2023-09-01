package no.kartverket.komreg.transformation.rule

import arrow.core.*
import arrow.core.raise.either
import com.google.common.collect.ImmutableRangeMap
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.TreeRangeMap
import no.kartverket.komreg.transformation.*
import no.kartverket.komreg.transformation.error.*
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses
import kotlin.reflect.safeCast

sealed class SplitOrAdjust<A : Comparable<A>>(
    domain: ComponentDomain<A>,
    sourceValue: A,
    val ruleMap: Map<KClass<*>, ImmutableRangeMap<*, SplitEntry<*, out A>>>
) : ComponentRule.Explicit.SingleSource<A>(
    domain,
    sourceValue,
    ruleMap
        .values
        .flatMap { rangeMap -> rangeMap.asMapOfRanges().values }
        .map { domain.canonicalRange(Range.singleton(it.targetValue)) }
        .let { ImmutableRangeSet.unionOf(it) }
) {
    companion object {
        @JvmStatic
        protected fun <A : Comparable<A>> splitOnRules(
            domain: ComponentDomain<A>,
            sourceValue: A,
            splitEntries: NonEmptySet<SplitEntry<*, out A>>
        ): Either<RuleError, Map<KClass<*>, ImmutableRangeMap<*, SplitEntry<*, out A>>>> = either {

            val rootDomains =
                rootDomainMap(splitEntries.map { (rule, _) -> rule.domain })
                    .entries
                    .mapOrAccumulate(RuleError::plus) { (k, v) -> k to v.bind() }
                    .bind()
                    .toMap()

            fun <C : Comparable<C>, B : C> SplitEntry<C, out A>.withSplitOnRootDomain(): Either<DomainMismatch<B>, SplitEntry<*, out A>> =
                either {
                    @Suppress("UNCHECKED_CAST")
                    val rootDomain =
                        requireNotNull(rootDomains[splitOnRule.domain]) { "No root domain for $domain" } as ComponentDomain<B>
                    if (rootDomain == splitOnRule.domain) {
                        this@withSplitOnRootDomain
                    } else {
                        SplitEntry(splitOnRule.toDomainOrLeft(rootDomain).bind(), targetValue)
                    }
                }

            val splitOnRules: Map<KClass<*>, ImmutableRangeMap<*, SplitEntry<*, out A>>> = splitEntries
                .mapOrAccumulate(RuleError::plus) { splitEntry -> splitEntry.withSplitOnRootDomain().bind() }
                .bind()
                .groupingBy { splitEntry -> splitEntry.splitOnRule.domain }
                .fold({ _, splitEntry ->
                    @Suppress("UNCHECKED_CAST")
                    createSplitEntryTreeMap(splitEntry) as TreeRangeMap<Comparable<Any>, Either<RuleError, SplitEntry<*, out A>>>
                }) { _, acc, splitEntry ->
                    acc.apply {
                        @Suppress("UNCHECKED_CAST")
                        unsafeAddSplitEntry(splitEntry as SplitEntry<*, A>)
                    }
                }
                .mapOrAccumulate(RuleError::plus) { (_, treeRangeMap) ->
                    treeRangeMap
                        .asMapOfRanges()
                        .bindAll()
                        .entries
                        .mapNotNull { (range, entry) ->
                            if (entry.splitOnRule.sourceRanges.span() == range) {
                                range to entry
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                val entryTyped = (entry as SplitEntry<Comparable<Any>, out A>)
                                val splitOnRule = entryTyped.splitOnRule.narrowSourceRange(range.widen()) ?: return@mapNotNull null
                                range to entryTyped.copy(splitOnRule = splitOnRule)
                            }
                        }
                        .fold(ImmutableRangeMap.builder<Comparable<Any>, SplitEntry<*, out A>>()) { acc, (range, splitEntry) ->
                            acc.apply {
                                put(range, splitEntry)
                            }
                        }
                        .build() as ImmutableRangeMap<*, SplitEntry<*, out A>>
                }
                .bind()
                .mapKeys { (k, _) -> k.classifier }
            splitOnRules
        }

        private fun <B : Comparable<B>, A : Comparable<A>> TreeRangeMap<*, out Either<RuleError, SplitEntry<*, out A>>>.unsafeAddSplitEntry(
            splitEntry: SplitEntry<B, A>
        ) {

            @Suppress("UNCHECKED_CAST")
            val self = this@unsafeAddSplitEntry as TreeRangeMap<B, Either<RuleError, SplitEntry<B, out A>>>

            for (sourceRange in splitEntry.splitOnRule.sourceRanges.asRanges()) {
                val intersectMap = ImmutableRangeMap.copyOf(self.subRangeMap(sourceRange))
                val nonConflictSourceRanges = ImmutableRangeSet.of(sourceRange)
                    .difference(ImmutableRangeSet.unionOf(intersectMap.asMapOfRanges().keys))

                for (nonIntersectRange in nonConflictSourceRanges.asRanges()) {
                    require(nonIntersectRange.encloses(splitEntry.splitOnRule.sourceRanges.span()))
                    self.put(nonIntersectRange, splitEntry.right())
                }

                for ((intersectRange, existingEntryOrErr) in intersectMap.asMapOfRanges().entries) {
                    when(existingEntryOrErr) {
                        is Either.Left -> self.put(intersectRange, existingEntryOrErr)
                        is Either.Right -> {
                            val existingEntry = existingEntryOrErr.value
                            if (existingEntry.targetValue != splitEntry.targetValue) {
                                self.put(intersectRange, ConflictingTargetValue(nonEmptySetOf(existingEntry, splitEntry)).left())
                            } else {
                                when(val splitOnRuleOrErr = existingEntry.splitOnRule + splitEntry.splitOnRule) {
                                    is Either.Left -> self.put(intersectRange, splitOnRuleOrErr)
                                    is Either.Right -> {
                                        when (val backingRule = splitOnRuleOrErr.value.toNonCopy()) {
                                            is Switch -> {
                                                backingRule
                                                    .ruleMap
                                                    .asMapOfRanges()
                                                    .values
                                                    .mapNotNull { rule ->
                                                        rule.narrowSourceRange(intersectRange)
                                                    }
                                                    .forEach { splitOnRule ->
                                                        self.put(intersectRange, SplitEntry(splitOnRule, splitEntry.targetValue).right())
                                                    }
                                            }
                                            is ComponentRule.NonSwitch -> {
                                                splitOnRuleOrErr.value.narrowSourceRange(intersectRange)?.let { splitOnRule ->
                                                    self.put(intersectRange, SplitEntry(splitOnRule, splitEntry.targetValue).right())
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }

        private fun <B : Comparable<B>, A : Comparable<A>> createSplitEntryTreeMap(
            splitEntry: SplitEntry<B, out A>
        ): TreeRangeMap<B, Either<RuleError, SplitEntry<B, out A>>> {
            return TreeRangeMap.create<B, Either<RuleError, SplitEntry<B, out A>>>().apply {
                splitEntry.splitOnRule.sourceRanges.asRanges().forEach { sourceRange ->
                    put(sourceRange, splitEntry.right())
                }
            }
        }

        /**
         * Returns a map from each domain to a root domain, given a collection
         * of domains.
         *
         * A root domain is a domain that encloses all subdomains in the
         * collection, and is disjoint from all other root domains
         * in the collection.
         *
         * @param domains The domains to find the root domains for.
         *
         * @return A [Either.Right] of a map from each domain to a root domain,
         * or a [Either.Left] of a [DomainMismatch] if there are any root
         * domains that intersect but do not enclose each other (i.e. are not
         * disjoint).
         */
        private fun rootDomainMap(
            domains: Iterable<ComponentDomain<*>>
        ): Map<ComponentDomain<*>, Either<DomainMismatch<*>, ComponentDomain<*>>> {
            val allDomains = domains.distinct()
            val rootDomainMap = allDomains.associateWith { domain ->
                allDomains.fold<ComponentDomain<out Comparable<*>>, Either<DomainMismatch<*>, ComponentDomain<out Comparable<*>>>>(
                    domain.right()
                ) { superDomainEither, subDomain ->
                    when (superDomainEither) {
                        is Either.Left -> superDomainEither
                        is Either.Right -> either {
                            val superDomain = superDomainEither.value
                            if (superDomain.intersects(subDomain)) {
                                if (superDomain.encloses(subDomain)) {
                                    superDomain
                                } else if (subDomain.encloses(superDomain)) {
                                    subDomain
                                } else {
                                    raise(DomainMismatch(superDomain, nonEmptySetOf(subDomain)))
                                }
                            } else {
                                superDomain
                            }
                        }
                    }
                }
            }
            return rootDomainMap
        }
    }

    /**
     * A split entry. This class is intended to be used as a (mostly) type-safe
     * parameter to the [Split.invoke] constructor.
     *
     * @param B The type of the split-on domain.
     * @param A The type of the split domain.
     *
     */
    data class SplitEntry<B : Comparable<B>, A : Comparable<A>>(
        val splitOnRule: ComponentRule<B>,
        val targetValue: A
    ) : CanCauseRangeErrorImpl<B, A> {

        override val sourceRanges: ImmutableRangeSet<B>
            get() = splitOnRule.sourceRanges
        override val targetRanges: ImmutableRangeSet<A>
            get() = ImmutableRangeSet.of(Range.singleton(targetValue))

    }

    override fun transform(values: Iterable<Comparable<*>>): Either<TransformError, List<Transform<*>>> = either {
        @Suppress("NAME_SHADOWING")
        val values = values.toList()
        val sourceValueIndices = values
            .mapIndexedNotNull { index, value ->
                index.takeIf {
                    domain.classifier.safeCast(value)?.let { sourceRanges.contains(it) } ?: false
                }
            }
            .toNonEmptySetOrNull()
            ?: return@either values.map { NoTransformAny }

        val (tt, targvalue) = values
            .mapOrAccumulate(TransformError::plus) { value ->
                value::class.prependTo(value::class.superclasses)
                    .mapNotNull { ruleMap[it] }
                    .distinct()
                    .mapNotNull { rangeMap ->
                        @Suppress("UNCHECKED_CAST")
                        (rangeMap as ImmutableRangeMap<Comparable<*>, SplitEntry<*, out A>>).get(value)
                    }.reduceOrNull({ splitEntry ->
                        (splitEntry.splitOnRule.transform(values).map { it to splitEntry })
                    }) { acc, otherSplitEntry ->
                        either {
                            val (transforms, accSplitEntry) = acc.bind()
                            if (accSplitEntry.targetValue != otherSplitEntry.targetValue) {
                                raise(
                                    ConflictingSplitTarget(
                                        otherSplitEntry.targetValue,
                                        nonEmptySetOf(accSplitEntry.targetValue)
                                    )
                                )
                            }
                            val moreTransforms = otherSplitEntry.splitOnRule.transform(values).bind()
                            val transformSums = transforms.mapIndexed { index, transform ->
                                index to transform
                            }.mapOrAccumulate(TransformError::plus) { (index, transform) ->
                                (transform + moreTransforms[index]).bind()

                            }.bind()

                            transformSums to accSplitEntry
                        }
                    }?.bind()?.let { it.first to it.second.targetValue } ?: (values.map { NoTransformAny } to null)
            }.bind().reduceOrNull({ it.right() }) { acc, pair ->
                either {
                    val (transforms1, targetValue1) = acc.bind()
                    val (transforms2, targetValue2) = pair
                    if (targetValue1 != null && targetValue2 != null && targetValue1 != targetValue2) {
                        raise(ConflictingSplitTarget(targetValue2, nonEmptySetOf(targetValue1)))
                    }
                    val transformSums = transforms1.mapIndexed { index, transform ->
                        index to transform
                    }.mapOrAccumulate(TransformError::plus) { (index, transform) ->
                        (transform + transforms2[index]).bind()
                    }.bind()

                    transformSums to (targetValue1 ?: targetValue2)
                }
            }?.bind() ?: (values.map { NoTransformAny } to null)


        if (this@SplitOrAdjust is Split) {
            if (targvalue == null) {
                raise(MissingComponentValue(nonEmptySetOf(this@SplitOrAdjust)))
            }
        }

        tt.mapIndexed { index, transform ->
            if (index in sourceValueIndices && targvalue != null) {
                ValueTransform(this@SplitOrAdjust, targvalue) + transform
            } else {
                transform.right()
            }
        }.bindAll()
    }


    final override fun plus(other: ComponentRule<out A>): Either<RuleError, ComponentRule<A>> = either {
        when (other) {
            is Copy -> plus(other).bind()
            is Switch -> other.withRuleAdded(domain, this@SplitOrAdjust).bind()
            is Merge, is Increment, is Specific -> Switch(domain, nonEmptySetOf(this@SplitOrAdjust, other)).bind()
            is SplitOrAdjust -> {
                val x = ruleMap.values.flatMap { it.asMapOfRanges().values }
                val y = other.ruleMap.values.flatMap { it.asMapOfRanges().values }
                val explicitRules = nonEmptySetOf(this@SplitOrAdjust, other)
                copy(x+ y, explicitRules).bind()
            }
        }
    }

    abstract fun copy(
        splitEntries: List<SplitEntry<out Comparable<*>, out A>>,
        explicitRules: NonEmptySet<SplitOrAdjust<out A>>
    ): Either<RuleError, Copy<A>>

}

class Split<A : Comparable<A>> private constructor(
    domain: ComponentDomain<A>,
    sourceValue: A,
    ruleMap: Map<KClass<*>, ImmutableRangeMap<*, SplitEntry<*, out A>>>
) : SplitOrAdjust<A>(
    domain,
    sourceValue,
    ruleMap
) {
    companion object {
        operator fun <A : Comparable<A>> invoke(
            domain: ComponentDomain<A>,
            sourceValue: A,
            splitEntries: Collection<SplitEntry<*, out A>>
        ): Either<RuleError, Split<A>> = either {
            @Suppress("NAME_SHADOWING")
            val splitEntries = splitEntries.toNonEmptySetOrNull() ?: raise(NoSplitEntries(domain))
            Split(domain, sourceValue, splitOnRules(domain, sourceValue, splitEntries).bind())
        }

    }

    override fun copy(
        splitEntries: List<SplitEntry<out Comparable<*>, out A>>,
        explicitRules: NonEmptySet<SplitOrAdjust<out A>>
    ): Either<RuleError, Copy<A>> = either {
        Copy(invoke(domain, sourceValue, splitEntries).bind(), explicitRules)
    }

    override fun canEqual(other: Any): Boolean {
        return other is Split<*>
    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        val sourceValue = otherDomain.classifier.safeCast(sourceValue) ?: return null
        val splitEntries = ruleMap.values.flatMap { it.asMapOfRanges().values }.toNonEmptySetOrNull() ?: return null

        val copiedSplitEntries = splitEntries.mapNotNull { splitEntry ->
            val targetValue = otherDomain.classifier.safeCast(splitEntry.targetValue) ?: return@mapNotNull null
            SplitEntry(splitEntry.splitOnRule, targetValue)
        }.toNonEmptySetOrNull() ?: return null

        return invoke(otherDomain, sourceValue, copiedSplitEntries).getOrNull()
            ?.let { Copy(it, nonEmptySetOf(this@Split)) }
    }

    override fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>> {
        throw UnsupportedOperationException()
    }

}

class Adjust<A : Comparable<A>> private constructor(
    domain: ComponentDomain<A>,
    sourceValue: A,
    ruleMap: Map<KClass<*>, ImmutableRangeMap<*, SplitEntry<*, out A>>>
) : SplitOrAdjust<A>(
    domain,
    sourceValue,
    ruleMap
) {
    companion object {
        operator fun <A : Comparable<A>> invoke(
            domain: ComponentDomain<A>,
            sourceValue: A,
            splitEntries: Collection<SplitEntry<*, out A>>
        ): Either<RuleError, Adjust<A>> = either {
            @Suppress("NAME_SHADOWING")
            val splitEntries = splitEntries.toNonEmptySetOrNull() ?: raise(NoSplitEntries(domain))
            Adjust(domain, sourceValue, splitOnRules(domain, sourceValue, splitEntries).bind())
        }

    }

    override fun copy(
        splitEntries: List<SplitEntry<out Comparable<*>, out A>>,
        explicitRules: NonEmptySet<SplitOrAdjust<out A>>
    ): Either<RuleError, Copy<A>> = either {
        Copy(invoke(domain, sourceValue, splitEntries).bind(), explicitRules)
    }

    override fun canEqual(other: Any): Boolean {
        return other is Split<*>
    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        val sourceValue = otherDomain.classifier.safeCast(sourceValue) ?: return null
        val splitEntries = ruleMap.values.flatMap { it.asMapOfRanges().values }.toNonEmptySetOrNull() ?: return null

        val copiedSplitEntries = splitEntries.mapNotNull { splitEntry ->
            val targetValue = otherDomain.classifier.safeCast(splitEntry.targetValue) ?: return@mapNotNull null
            SplitEntry(splitEntry.splitOnRule, targetValue)
        }.toNonEmptySetOrNull() ?: return null

        return invoke(otherDomain, sourceValue, copiedSplitEntries).getOrNull()
            ?.let { Copy(it, nonEmptySetOf(this@Adjust)) }
    }

    override fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>> {
        throw UnsupportedOperationException()
    }

}


