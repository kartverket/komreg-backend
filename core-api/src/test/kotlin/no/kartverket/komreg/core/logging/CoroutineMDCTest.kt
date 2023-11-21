package no.kartverket.komreg.core.logging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import org.slf4j.MDC

private const val TEST_KEY = "testKey"
private const val FOO = "foo"
private const val BAR = "bar"

private const val INHERIT_KEY = "inheritKey"
private const val INHERIT_VALUE = "inheritValue"

class CoroutineMDCTest : FunSpec({
    test(FOO) {
        MDC.put(INHERIT_KEY, INHERIT_VALUE)
        runBlocking(CoroutineMDC()) {
            MDC.get(TEST_KEY) shouldBe null
            MDC.put(TEST_KEY, FOO)
            launch {
                MDC.get(TEST_KEY) shouldBe FOO
                val notfoo = "not$FOO"
                MDC.put(TEST_KEY, notfoo)
                launch {
                    MDC.get(INHERIT_KEY) shouldBe INHERIT_VALUE
                    MDC.get(TEST_KEY) shouldBe notfoo
                    MDC.put(TEST_KEY, "abracadabra")
                }.join()
                MDC.get(TEST_KEY) shouldBe notfoo
            }.join()

            MDC.get(TEST_KEY) shouldBe FOO
            MDC.put(TEST_KEY, BAR)

            delay(50)

            MDC.get(TEST_KEY) shouldBe BAR
        }
        MDC.get(TEST_KEY) shouldBe null
        MDC.get(INHERIT_KEY) shouldBe INHERIT_VALUE
    }
})

