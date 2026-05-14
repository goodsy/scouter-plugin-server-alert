package scouter.plugin.server.alert.uitl

import scouter.server.Logger

object LogUtil {
    private const val LOG_PREFIX = "[SA-PLUGIN]"

    fun info(clazz: Class<*>, message: String) {
        Logger.println(format(clazz, message))
    }

    fun info(message: String) {
        Logger.println(format(message))
    }

    fun error(clazz: Class<*>, message: String) {
        Logger.println(format(clazz, "[ERROR] $message"))
    }

    fun error(clazz: Class<*>, message: String, e: Throwable) {
        Logger.println(format(clazz, "[ERROR] $message - ${e.message}"))
    }

    private fun format(clazz: Class<*>, message: String): String {
        return "$LOG_PREFIX [${clazz.simpleName}] $message"
    }

    private fun format(message: String): String {
        return "$LOG_PREFIX $message"
    }
}
