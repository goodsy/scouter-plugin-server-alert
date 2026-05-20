package scouter.plugin.server.alert.common

enum class Channel {
    TELEGRAM,
    SLACK,
    EMAIL,
    UNKNOWN,
    ;

    companion object {
        fun of(name: String?): Channel {
            if (name == null) return UNKNOWN

            return Channel.entries.find { it.name.equals(name, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
