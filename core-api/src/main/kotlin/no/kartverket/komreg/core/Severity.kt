package no.kartverket.komreg.core

sealed interface Severity {
    val intValue: Int

    enum class Fatal(
        override val intValue: Int
    ) : Severity {
        ALL(Int.MIN_VALUE),
        FATAL(1400),
        OFF(Int.MAX_VALUE)
    }

    enum class NonFatal(
        override val intValue: Int
    ) : Severity {
        ALL(Int.MIN_VALUE),
        TRACE(400),
        DEBUG(500),
        INFO(800),
        WARNING(900),
        ERROR(1000),
        OFF(Int.MAX_VALUE)
    }
}
