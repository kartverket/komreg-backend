package no.kartverket.komreg.transformation.rule3.range

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.pcollections.TreePMap

class TreeRangeMapTest : BehaviorSpec({
    given("a range map where [10..20), [30..40) and [50..60) is mappedTo 'FOO'") {
        val givenRangeMap = TreeRangeMap.makeOrThrow(
            TreePMap
                .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                .plus(10 towards 20, setOf("FOO"))
                .plus(30 towards 40, setOf("FOO"))
                .plus(50 towards 60, setOf("FOO"))
        )

        `when`("adding 5 -> BAR and 25 -> BAR") {
            val newRangeMap = givenRangeMap +
                    (5 towards 6 mappedTo setOf("BAR")) +
                    (25 towards 26 mappedTo setOf("BAR"))
            then("it should be [5..6) -> 'BAR', [10..20) -> 'FOO', [25..26) -> 'BAR', [30..40) -> 'FOO', [50..60) -> 'FOO'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(5 towards 6, setOf("BAR"))
                        .plus(10 towards 20, setOf("FOO"))
                        .plus(25 towards 26, setOf("BAR"))
                        .plus(30 towards 40, setOf("FOO"))
                        .plus(50 towards 60, setOf("FOO"))
                )
            }
        }

//        `when`("removing [25..35)") {
//            val newRangeMap = givenRangeMap - (15 towards 35)
//            then("it should be [10..15) -> 'FOO', [35..40) -> 'FOO', [50..60) -> 'FOO'") {
//                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
//                    TreePMap
//                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
//                        .plus(10 towards 15, setOf("FOO"))
//                        .plus(35 towards 40, setOf("FOO"))
//                        .plus(50 towards 60, setOf("FOO"))
//                )
//            }
//        }

        `when`("adding [5-15) -> BAR, [35-45) -> BAR and [55-65) -> BAR") {
            val newRangeMap = givenRangeMap +
                    (5 towards 15 mappedTo setOf("BAR")) +
                    (35 towards 45 mappedTo setOf("BAR")) +
                    (55 towards 65 mappedTo setOf("BAR"))
            then("it should be " +
                    "[5..10) -> 'BAR', " +
                    "[10..15) -> ['BAR','FOO'], " +
                    "[15..20) -> 'FOO', " +
                    "[30..35) -> 'FOO', " +
                    "[35..40) -> ['BAR','FOO'], " +
                    "[40..45) -> 'BAR', " +
                    "[50..55) -> 'FOO', " +
                    "[55..60) -> ['BAR','FOO'], " +
                    "[60..65) -> 'BAR'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(5 towards 10, setOf("BAR"))
                        .plus(10 towards 15, setOf("BAR", "FOO"))
                        .plus(15 towards 20, setOf("FOO"))
                        .plus(30 towards 35, setOf("FOO"))
                        .plus(35 towards 40, setOf("BAR", "FOO"))
                        .plus(40 towards 45, setOf("BAR"))
                        .plus(50 towards 55, setOf("FOO"))
                        .plus(55 towards 60, setOf("BAR", "FOO"))
                        .plus(60 towards 65, setOf("BAR"))
                )
            }
        }

        `when`("adding [20..30) -> FOO, [40..50) -> FOO") {
            val newRangeMap = givenRangeMap +
                    (20 towards 30 mappedTo setOf("FOO")) +
                    (40 towards 50 mappedTo setOf("FOO"))
            then("it should be [10..60) -> FOO") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(10 towards 60, setOf("FOO"))
                )
            }
        }


        then("it should be [10..20), [30..40) and [50..60) is mappedTo 'FOO'") {
            val rangeMapOf = rangeMapOf(
                10 towards 20 mappedTo setOf("FOO"),
                30 towards 40 mappedTo setOf("FOO"),
                50 towards 60 mappedTo setOf("FOO")
            )
            givenRangeMap shouldBe rangeMapOf

        }

        `when`("adding (-inf..+inf) -> 'BAR'") {
            val newRangeMap = givenRangeMap + (Range.Unbounded<Int>() mappedTo setOf("BAR"))
            then("it should be (-inf..+inf) -> 'BAR'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(lessThan(10), setOf("BAR"))
                        .plus(10 towards 20, setOf("FOO", "BAR"))
                        .plus(20 towards 30, setOf("BAR"))
                        .plus(30 towards 40, setOf("FOO", "BAR"))
                        .plus(40 towards 50, setOf("BAR"))
                        .plus(50 towards 60, setOf("FOO", "BAR"))
                        .plus(atLeast(60), setOf("BAR"))

                )
            }
        }
    }


    given("a rangemap of where [1..10) is mapped to FOO and [20..30) is mapped to BAR") {
        val givenRangeMap = rangeMapOf(
            1 towards 10 mappedTo setOf("FOO"),
            20 towards 30 mappedTo setOf("BAR")
        )

        `when`("adding 15 mapped to FOO") {
            val newRangeMap = givenRangeMap + (15 towards 16 mappedTo "FOO")
            then("it should be [1..11) -> 'FOO', [15..16) -> 'FOO', [20, 21) -> 'BAR'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(1 towards 10, setOf("FOO"))
                        .plus(15 towards 16, setOf("FOO"))
                        .plus(20 towards 30, setOf("BAR"))
                )
            }
        }

        `when`("adding [10, 20) -> 'BAZ'") {
            val newRangeMap = givenRangeMap + (10 towards 20 mappedTo "BAZ")
            then("it should be [1..10) -> 'FOO', [10..20) -> 'BAZ', [20, 21) -> 'BAR'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(1 towards 10, setOf("FOO"))
                        .plus(10 towards 20, setOf("BAZ"))
                        .plus(20 towards 30, setOf("BAR"))
                )
            }
        }

        `when`("adding [5,15) -> 'BAZ'") {
            val newRangeMap = givenRangeMap + (5 towards 15 mappedTo "BAZ")
            then("it should be [1..5) -> 'FOO', [5..15) -> ['FOO','BAZ'], [20, 21) -> 'BAR'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(1 towards 5, setOf("FOO"))
                        .plus(5 towards 10, setOf("FOO", "BAZ"))
                        .plus(10 towards 15, setOf("BAZ"))
                        .plus(20 towards 30, setOf("BAR"))
                )
            }
        }
    }

    given("a rangemap of [1..11) -> 'FOO'") {
        val givenRangeMap = rangeMapOf(1 towards 11 mappedTo setOf("FOO"))
//        `when`("removing [5]") {
//            val newRangeMap = givenRangeMap - (5 towards 6)
//            then("it should be [1..5) -> 'FOO' and [5..11) -> 'FOO'") {
//                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
//                    TreePMap
//                        .empty<Range<Int>, String>(TreeRangeMap.RangeStartComparator())
//                        .plus(1 towards 5, "FOO")
//                        .plus(6 towards 11, "FOO")
//                )
//            }
//        }

        `when`("adding [11] -> 'FOO'") {
            val newRangeMap = givenRangeMap + (11 towards 12 mappedTo  "FOO")
            then("it should be [1..12) -> 'FOO'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(1 towards 12, setOf("FOO"))
                )
            }
        }

        `when`("adding [0]") {
            val newRangeMap = givenRangeMap + (0 towards 1 mappedTo  "FOO")
            then("it should be [0..11) -> 'FOO'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(0 towards 11, setOf("FOO"))
                )
            }
        }

        `when`("adding [5]") {
            val newRangeMap = givenRangeMap + (5 towards 6 mappedTo  "FOO")
            then("it should be [0..11) -> 'FOO'") {
                newRangeMap shouldBe givenRangeMap.mapValues { (_, _) -> setOf("FOO") }
            }
        }

        `when`("adding [0..2)") {
            val newRangeMap = givenRangeMap + (0 towards 2 mappedTo  "BAR")
            then("it should be [0..11) -> 'FOO'") {
                newRangeMap shouldBe TreeRangeMap.makeOrThrow(
                    TreePMap
                        .empty<Range<Int>, Set<String>>(TreeRangeMap.RangeStartComparator())
                        .plus(0 towards 1, setOf("BAR"))
                        .plus(1 towards 2, setOf("FOO", "BAR"))
                        .plus(2 towards 11, setOf("FOO"))
                )
            }
        }
    }
})
