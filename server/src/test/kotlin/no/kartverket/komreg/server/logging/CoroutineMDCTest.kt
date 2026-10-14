package no.kartverket.komreg.core.logging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.*
import org.slf4j.MDC
import java.util.concurrent.Executors

private const val TEST_KEY = "testKey"
private const val TEST_KEY2 = "testKey2"
private const val TEST_KEY3 = "testKey3"
private const val FOO = "foo"
private const val BAR = "bar"

private const val INHERIT_KEY = "inheritKey"
private const val INHERIT_VALUE = "inheritValue"

class CoroutineMDCTest : FunSpec({
    test("Test MDC inheritance 1") {
        MDC.put(INHERIT_KEY, INHERIT_VALUE)
        runBlocking(CoroutineMDC()) {
            MDC.get(TEST_KEY) shouldBe null
            MDC.put(TEST_KEY, FOO)
            async {
                MDC.get(TEST_KEY) shouldBe FOO
                val notfoo = "not$FOO"
                MDC.put(TEST_KEY, notfoo)
                async {
                    MDC.get(INHERIT_KEY) shouldBe INHERIT_VALUE
                    MDC.get(TEST_KEY) shouldBe notfoo
                    MDC.put(TEST_KEY, "abracadabra")
                }.await()
                MDC.get(TEST_KEY) shouldBe notfoo
            }.await()

            MDC.get(TEST_KEY) shouldBe FOO
            MDC.put(TEST_KEY, BAR)

            delay(50)

            MDC.get(TEST_KEY) shouldBe BAR
        }
        MDC.get(TEST_KEY) shouldBe null
        MDC.get(INHERIT_KEY) shouldBe INHERIT_VALUE
    }

    test("Test MDC inheritance 2") {
        val dispatcher1 = Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher()
        val dispatcher2 = Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher()
        val dispatcher3 = Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher()

        MDC.put("theOutside", "theOutsideValue")
        async (dispatcher1 + CoroutineMDC()) {
            val outerTread = Thread.currentThread()

            MDC.get("theOutside") shouldBe "theOutsideValue"

            MDC.put(TEST_KEY, FOO)
            async (dispatcher2 + CoroutineMDC()) {
                Thread.currentThread() shouldNotBe outerTread
                MDC.get(TEST_KEY) shouldBe FOO
                MDC.put(TEST_KEY, BAR)
            }.await()
            Thread.currentThread() shouldBe outerTread
            MDC.get(TEST_KEY) shouldBe FOO

            async (dispatcher2 + CoroutineMDC()) {
                Thread.currentThread() shouldNotBe outerTread
                MDC.get(TEST_KEY) shouldBe FOO
            }.await()
            Thread.currentThread() shouldBe outerTread
            MDC.get(TEST_KEY) shouldBe FOO
            MDC.put(TEST_KEY, BAR)
            async(dispatcher3) {
                Thread.currentThread() shouldNotBe outerTread
                MDC.get(TEST_KEY) shouldBe BAR
            }.await()
            Thread.currentThread() shouldBe outerTread
            MDC.get(TEST_KEY) shouldBe BAR
        }.await()
        MDC.get(TEST_KEY) shouldBe null
        MDC.get("theOutside") shouldBe "theOutsideValue"
    }

    test("Test MDC inheritance 3") {

        val dispatcher1 = Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher()
        val dispatcher2 = Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher()


        runBlocking(dispatcher1 + CoroutineMDC()) {
            MDC.put(TEST_KEY, FOO)

            CoroutineScope(dispatcher2).async {
                MDC.get(TEST_KEY) shouldBe null
                MDC.put(TEST_KEY, BAR)
            }.await()
            MDC.get(TEST_KEY) shouldBe FOO

            async {
                MDC.get(TEST_KEY) shouldBe FOO
                MDC.put(TEST_KEY, BAR)
            }.await()
            MDC.get(TEST_KEY) shouldBe FOO
        }

        MDC.get(TEST_KEY) shouldBe null
    }
})

