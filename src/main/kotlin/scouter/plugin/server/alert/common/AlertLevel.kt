package scouter.plugin.server.alert.common

/**
 * Alert Level enum.
 *
 * Scouter AlertLevel (int) 와 동일한 매핑:
 *   0 = INFO
 *   1 = WARN
 *   2 = ERROR
 *   3 = FATAL
 *   9 = UNKNOWN (threshold 미지정 등)
 */
enum class AlertLevel(val value: Int) {
    INFO(0),
    WARN(1),
    ERROR(2),
    FATAL(3),
    UNKNOWN(9);

    override fun toString(): String = name

    companion object {
        private val BY_VALUE = AlertLevel.entries.associateBy { it.value }

        /**
         * enum 이름으로 변환 (대소문자 불일치, null 안전)
         */
        fun of(name: String?): AlertLevel {
            if (name == null) return UNKNOWN
            return AlertLevel.entries.find { it.name.equals(name, ignoreCase = true) } ?: UNKNOWN
        }

        /**
         * Scutter AlertLevel int (0=INFO, 1=WARN, 2=ERROR, 3=FATAL) 로 변환
         */
        fun of(value: Int): AlertLevel {
            return BY_VALUE[value] ?: UNKNOWN
        }
    }
}
