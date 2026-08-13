package no.kartverket.komreg.parameter.data

data class FileLocation(val fileName: String, val lineNumber: Int?) {
    companion object {
        operator fun invoke(): FileLocation = invoke(1)
        operator fun invoke(skipFrames: Long): FileLocation {
            val frame = StackWalker.getInstance().walk { frames ->
                frames.skip(skipFrames + 1).findFirst().orElse(null)
            }

            return if (frame != null) {
                FileLocation(frame.fileName ?: "", if (frame.lineNumber < 0) null else frame.lineNumber)
            } else {
                FileLocation("", null)
            }
        }
    }
}