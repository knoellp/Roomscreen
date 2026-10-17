package io.github.knoellp.roomscreen.roon

/** Minimal logging seam so the client stays free of Android and JVM logging frameworks. */
interface Logger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)

    companion object {
        val None: Logger = object : Logger {
            override fun debug(message: String) {}
            override fun info(message: String) {}
            override fun warn(message: String, error: Throwable?) {}
        }
    }
}
