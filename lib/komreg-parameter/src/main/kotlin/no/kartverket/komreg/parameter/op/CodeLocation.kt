package no.kartverket.komreg.parameter.op

open class CodeLocation(val stackTrace: List<StackTraceElement>) {
    constructor() : this(computeStacktrace(2))
    companion object {
        @JvmStatic
        @Suppress("NOTHING_TO_INLINE") // Inlined since we don't want the first frame
        protected inline fun computeStacktrace(
            @Suppress("SameParameterValue") skipFrames: Int
        ): List<StackTraceElement> {
            require(skipFrames >= 0) { "skipFrames must be non-negative" }
            return StackWalker
                .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk { frameStream ->
                    frameStream
                        .skip(skipFrames.toLong())
                        .map { frame ->
                            StackTraceElement(
                                frame.declaringClass.name,
                                frame.methodName,
                                frame.fileName,
                                frame.lineNumber,
                            )
                        }
                        .toList()
                }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CodeLocation

        return stackTrace == other.stackTrace
    }

    override fun hashCode(): Int {
        return 31 * stackTrace.hashCode() + javaClass.hashCode()
    }

    override fun toString(): String {
        return "CodeLocation(${
            stackTrace.firstOrNull().let { (it?.fileName ?: "unknown") + ':' + (it?.lineNumber ?: -1) }
        })"
    }
}