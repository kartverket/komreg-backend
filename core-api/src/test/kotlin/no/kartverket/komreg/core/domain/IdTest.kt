package no.kartverket.komreg.core.domain

import io.kotest.core.spec.style.ExpectSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class IdTest : ExpectSpec({
    context("compare") {
        val alpha1 = Id(Foo.Alpha, 1L)
        val alpha3 = Id(Foo.Alpha, 3L)
        val charlie2 = Id(Foo.Charlie, 2L)
        val bravoX = Id(Bar.Bravo, "X")
        val bravoY = Id(Bar.Bravo, "Y")

        expect("alpha1 < alpha3") {
            alpha1.compareTo(alpha3) shouldBeLessThan 0
        }

        expect("alpha1 > alpha3") {
            alpha3.compareTo(alpha1) shouldBeGreaterThan 0
        }

        expect("alpha1 == alpha1") {
            alpha1.compareTo(alpha1) shouldBe 0
        }

        expect("alpha1 < charlie2") {
            alpha1.compareTo(charlie2) shouldBeLessThan 0
        }

        expect("alpha3 < charlie2") {
            alpha3.compareTo(charlie2) shouldBeLessThan 0
        }

        expect("alpha1 > bravoX") {
            alpha1.compareTo(bravoX) shouldBeGreaterThan 0
        }

        expect("bravoY > bravoX") {
            bravoY.compareTo(bravoX) shouldBeGreaterThan 0
        }
    }

    context("equals") {

        expect("alpha1 != alpha2") {
            Id(Foo.Alpha, 1L) shouldNotBe Id(Foo.Alpha, 2L)
        }

        expect("alpha1 == alpha1") {
            Id(Foo.Alpha, 1L) shouldBe Id(Foo.Alpha, 1L)
        }

        expect("alpha1 != charlie1") {
            Id(Foo.Alpha, 1L) shouldNotBe Id(Foo.Charlie, 1L)
        }

        expect("charlie1 == charlie1") {
            Id(Foo.Charlie, 1L) shouldBe Id(Foo.Charlie, 1L)
        }

        expect("bravoX == bravoX") {
            Id(Bar.Bravo, "X") shouldBe Id(Bar.Bravo, "X")
        }

        expect("bravoX != bravoY") {
            Id(Bar.Bravo, "X") shouldNotBe Id(Bar.Bravo, "Y")
        }
    }
})

private enum class Foo : IdType<Long, Foo> {
    Alpha,
    Charlie,
    ;

    override fun compare(o1: Long, o2: Long): Int {
        return o1.compareTo(o2)
    }
}

private enum class Bar : IdType<String, Bar> {
    Bravo,
    ;

    override fun compare(o1: String, o2: String): Int {
        return o1.compareTo(o2)
    }
}
