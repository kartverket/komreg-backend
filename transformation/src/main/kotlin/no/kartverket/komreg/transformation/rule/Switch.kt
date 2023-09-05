package no.kartverket.komreg.transformation.rule

import arrow.core.*
import arrow.core.raise.either
import com.google.common.collect.*
import no.kartverket.komreg.transformation.*
import no.kartverket.komreg.transformation.NoTransformAny
import no.kartverket.komreg.transformation.NoTransformNothing
import no.kartverket.komreg.transformation.error.*
import java.lang.UnsupportedOperationException
import kotlin.reflect.safeCast

class Switch<A : Comparable<A>> private constructor(
    domain: ComponentDomain<A>,
    val ruleMap: ImmutableRangeMap<A, out ComponentRule.NonSwitch<A>>,
    override val explicitRules: NonEmptySet<ComponentRule.Explicit<*>>
) : NonCopyImpl<A>(domain), ComponentRule.Implicit<A>, ComponentRule.NonSplit<A> {
    companion object {

        operator fun <A : Comparable<A>> invoke(
            domain: ComponentDomain<A>,
            explicitRule: ComponentRule<out A>,
            vararg moreRules: ComponentRule<out A>
        ): Either<RuleError, Switch<A>> =
            invoke(domain, nonEmptySetOf(explicitRule, *moreRules))

        operator fun <A : Comparable<A>> invoke(
            domain: ComponentDomain<A>,
            explicitRules: NonEmptySet<ComponentRule<out A>>
        ): Either<RuleError, Switch<A>> = either {
            val rangeRulePairs =
                (explicitRules as Set<ComponentRule<out A>>)
                    .flatMap { rule -> rule.sourceRanges.asRanges().map { it to rule } }
                    .toNonEmptySetOrNone().toEither {
                        NoRules(
                            domain,
                            explicitRules
                        )
                    }
                    .bind()
            make(domain, rangeRulePairs).bind()
        }

        private fun <A : Comparable<A>> make(
            domain: ComponentDomain<A>,
            rulePairs: NonEmptySet<Pair<Range<out A>, ComponentRule<out A>>>
        ): Either<RuleError, Switch<A>> = either {
            TreeRangeMap
                .create<A, Either<RuleError, ComponentRule.NonSwitch<A>>>()
                .apply {
                    rulePairs
//                        .toList()
//                        .flatMap { rangeRulePair ->
//                            when (val rule = rangeRulePair.second) {
//                                is Switch -> {
//                                    rule.ruleMap.asMapOfRanges().entries.map { (range, rule) ->
//                                        range to rule
//                                    }
//                                }
//
//                                else -> listOf(rangeRulePair)
//                            }
//                        }
//                        .mapOrAccumulate(RuleError::plus) { (range, rule) ->
//                            range.widen().narrowToDomainOrLeft(domain).bind() to rule.toDomainOrLeft(domain)
//                        }
//                        .bind()
                        //.filterNot { (range, _) -> range.isEmpty }
                        .forEach { (range, rule) ->
                            either {
                                val sourceRangeBounds = range.widen().narrowToDomainOrLeft(domain).bind()
                                require(sourceRangeBounds.encloses(rule.sourceRanges.span().widen()))

                                when(val backingRule = rule.toNonCopy()) {
                                    is ComponentRule.NonSwitch -> {
                                        put(sourceRangeBounds, rule.toDomainOrLeft(domain).map { it as ComponentRule.NonSwitch<A> })
                                    }
                                    is Switch -> {
                                        for ((switchedRuleRange,switchedRule) in backingRule.ruleMap.asMapOfRanges().entries) {
                                            require(switchedRule.sourceRanges.span().widen().encloses(switchedRuleRange.widen()))
                                            val intersectMap = ImmutableRangeMap.copyOf(subRangeMap(switchedRuleRange.widen()))
                                            val nonIntersectRanges = ImmutableRangeSet.of(switchedRuleRange.widen()).difference(ImmutableRangeSet.unionOf(intersectMap.asMapOfRanges().keys))
                                            for (nonIntersectRange in nonIntersectRanges.asRanges()) {
                                                put(nonIntersectRange, switchedRule.toDomainOrLeft(domain).map { it as ComponentRule.NonSwitch<A> })
                                            }
                                            for ((intersectRange, existingRuleOrErr) in intersectMap.asMapOfRanges()) {
                                                val existingRule = existingRuleOrErr.bind()
                                                val either = (existingRule + switchedRule).bind()
                                                if (either is ComponentRule.NonSwitch) {
                                                    put(intersectRange, either.right())
                                                } else {
                                                    put(
                                                        intersectRange,
                                                        ConflictingTargetValue(
                                                            nonEmptySetOf(
                                                                switchedRule,
                                                                existingRule
                                                            )
                                                        ).left()
                                                    )
                                                }
                                            }

                                        }
                                    }
                                }

                            }
                        }
                }
                .asMapOfRanges()
                .bindAll()
                .entries
                .flatMap { (range, rule) ->
                    when (val rule = rule.toNonCopy()) {
                        is Switch -> throw IllegalStateException("NonCopy of NonSwitch should not be Switch")
                        is ComponentRule.Explicit -> listOf(range to rule)
                        is Merge -> listOf(range to rule)
                    }
                }
                .filterNot { (range, _) -> range.isEmpty }
                .let { entries ->
                    if (entries.isEmpty()) {
                        raise(NoRules(domain, rulePairs.map { it.second }.toNonEmptySet()))
                    }
                    val newRuleMap = ImmutableRangeMap
                        .builder<A, ComponentRule.NonSwitch<A>>()
                        .run {
                            for ((range, mergedRule) in entries) {
                                put(range, mergedRule)
                            }
                            build()
                        }

                    val explicitRules = newRuleMap
                        .asMapOfRanges()
                        .values
                        .flatMap { rule ->
                            when (rule) {
                                is ComponentRule.Explicit -> nonEmptySetOf(rule)
                                is ComponentRule.Implicit<*> -> rule.explicitRules
                            }
                        }
                        .toNonEmptySetOrNull()!!
                    Switch(domain, newRuleMap, explicitRules)
                }
        }
    }

    override val sourceRanges: ImmutableRangeSet<A>
        get() = ruleMap
            .asMapOfRanges()
            .keys
            .map { range -> domain.canonicalRange(range.widen()) }
            .let { ImmutableRangeSet.unionOf(it) }

    override val targetRanges: ImmutableRangeSet<A>
        get() = ruleMap
            .asMapOfRanges()
            .values
            .flatMap { rule -> rule.targetRanges.asRanges() }
            .map { range -> domain.canonicalRange(range.widen()) }
            .let { ImmutableRangeSet.unionOf(it) }

    override fun narrowSourceRange(newSourceRange: Range<out A>): ComponentRule<A>? {
        if (newSourceRange.widen().encloses(sourceRanges.span())) {
            return this
        }
        val newRuleMap = ruleMap.asMapOfRanges()
            .values
            .mapNotNull { v ->
                v.narrowSourceRange(newSourceRange) as ComponentRule.NonSwitch<A>?
            }
            .fold(ImmutableRangeMap.builder<A, ComponentRule.NonSwitch<A>>()) { acc, nonSwitch ->
                acc.apply {
                    nonSwitch.sourceRanges.asRanges().forEach { range -> put(range, nonSwitch) }
                }
            }
            .build()
        return if (ruleMap.asMapOfRanges().isNotEmpty()) {
            Switch(domain, newRuleMap, explicitRules)
        } else {
            null
        }
    }

    override fun plus(other: ComponentRule<out A>): Either<RuleError, Switch<A>> = either {
        val rulePairs = other.sourceRanges.asRanges().mapOrAccumulate(RuleError::plus) { range ->
            range.widen().narrowToDomainOrLeft(other.domain).bind() to other.toDomainOrLeft(other.domain).bind()
        }.bind()
        val moreRulePairs = ruleMap.asMapOfRanges().entries.map { (k, v) -> k to v }

        make(
            domain,
            (rulePairs + moreRulePairs).toNonEmptySetOrNull() ?: raise(
                NoRules(
                    domain,
                    nonEmptySetOf(this@Switch, other)
                )
            )
        ).bind()

    }

    override fun <X : A> asCopy(otherDomain: ComponentDomain<X>): Copy<X>? {
        val rangeToRulePairs = ruleMap.asMapOfRanges().entries
            .mapOrAccumulate(RuleError::plus) { (k, v) ->
                k.narrowToDomainOrLeft(otherDomain).bind() to v.toDomainOrLeft(otherDomain).bind()
            }
            .getOrNull()
            ?.toNonEmptySetOrNull()
            ?: return null
        val backingRule = make(otherDomain, rangeToRulePairs).getOrNull() ?: return null
        return Copy(backingRule, backingRule.explicitRules)
    }

    fun <X : A> withRuleAdded(domain: ComponentDomain<X>, rule: ComponentRule<in X>): Either<RuleError, Switch<X>> =
        either {
            if (this@Switch.domain == domain) {
                @Suppress("UNCHECKED_CAST")
                val ruleToAdd = rule as ComponentRule<X>
                val existingRules = ruleMap
                    .asMapOfRanges()
                    .entries
                    .map { (range, rule) ->
                        @Suppress("UNCHECKED_CAST")
                        range as Range<out X> to rule as ComponentRule<out X>
                    }.toNonEmptySetOrNull()!!
                make(
                    domain,
                    existingRules + ruleToAdd.sourceRanges.asRanges().map { range -> range to ruleToAdd }).bind()
            } else {
                val ruleToAdd = rule.toDomainOrLeft(domain).bind()
                val existingRules = ruleMap.asMapOfRanges().entries.mapOrAccumulate(RuleError::plus) { (range, rule) ->
                    range.narrowToDomainOrLeft(domain).bind() to rule.toDomainOrLeft(domain).bind()
                }.bind().toNonEmptySetOrNull()!!
                make(
                    domain,
                    existingRules + ruleToAdd.sourceRanges.asRanges().map { range -> range to ruleToAdd }).bind()
            }
        }

    override fun canEqual(other: Any): Boolean {
        return other is Switch<*>
    }

    override fun transformComponentUnconditionally(value: A): Either<TransformError.Uni, Transform<A>> {
        throw UnsupportedOperationException()
    }

    override fun transform(values: Iterable<Comparable<*>>): Either<TransformError, List<Transform<*>>> = either {
        @Suppress("NAME_SHADOWING")
        val values = values.toList()
        val transformsLists = values
            .mapOrAccumulate(TransformError::plus) { value ->
                val rule = domain.classifier.safeCast(value)
                    ?.let { ruleMap.get(it)!! }
                    ?: return@mapOrAccumulate List(values.size) { NoTransformNothing }
                rule.transform(values).bind()
            }
            .bind()

        List(transformsLists.size) { n ->
            transformsLists
                .map { it[n] }
                .reduceOrNull<Transform<*>, Either<AmbiguousTransform<*>, Transform<*>>>({ it.right() }) { sum, transform ->
                    sum + transform
                }
                ?: NoTransformAny.right()
        }.bindAll()
    }
}