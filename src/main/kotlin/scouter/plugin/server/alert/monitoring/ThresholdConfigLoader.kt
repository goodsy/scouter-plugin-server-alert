package scouter.plugin.server.alert.monitoring

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import scouter.plugin.server.alert.uitl.LogUtil
import scouter.server.Configure
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * metric-thresholds.json 로더
 *
 * - 기본 경로: {scouter_server_home}/conf/metric-thresholds.json
 * - scouter.conf 오버라이드:
 *     ext_plugin_scouter_alert_thresholds_path=/custom/path/metric-thresholds.json
 * - 60초 폴링 → 파일 변경 시 자동 리로드 (재시작 불필요)
 */
object ThresholdConfigLoader {
    private val GSON: Gson = GsonBuilder().create()
    private const val POLL_INTERVAL_SEC: Long = 60

    private val configRef = AtomicReference<ThresholdConfig>()

    @Volatile
    private var lastModified: Long = -1

    @Volatile
    private var lastPath: String? = null

    private val scheduler =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "scouter-plugin-threshold-watcher").apply {
                isDaemon = true
            }
        }

    init {
        reload()
        scheduler.scheduleWithFixedDelay(
            { reloadIfChanged() },
            POLL_INTERVAL_SEC,
            POLL_INTERVAL_SEC,
            TimeUnit.SECONDS,
        )
        LogUtil.info(this.javaClass, "설정 파일 감시 시작 (interval=${POLL_INTERVAL_SEC}s)")
    }

    fun get(): ThresholdConfig? = configRef.get()

    private fun resolveConfigPath(): String {
        val conf = Configure.getInstance()
        val custom = conf.getValue("ext_plugin_scouter_alert_thresholds_path", "")
        return if (!custom.isNullOrBlank()) {
            custom
        } else {
            Paths.get(System.getProperty("user.dir"), "conf", "metric-thresholds.json").toString()
        }
    }

    private fun reload() {
        val path = resolveConfigPath()
        try {
            val filePath = Paths.get(path)
            if (!Files.exists(filePath)) {
                LogUtil.info(this.javaClass, "설정 파일 없음: $path → Counter 모니터링 비활성")
                val empty = ThresholdConfig().apply { enabled = false }
                configRef.set(empty)
                return
            }
            Files.newBufferedReader(filePath).use { reader ->
                val config = GSON.fromJson(reader, ThresholdConfig::class.java)
                config.init()
                configRef.set(config)
                lastModified = Files.getLastModifiedTime(filePath).toMillis()
                lastPath = path

                LogUtil.info(this.javaClass, "설정 파일 Load 완료: $path")
            }
        } catch (e: Exception) {
            LogUtil.error(this.javaClass, "설정 파일 Load 실패: $path", e)
        }
    }

    private fun reloadIfChanged() {
        try {
            val path = resolveConfigPath()
            val filePath = Paths.get(path)
            if (!Files.exists(filePath)) return

            val modified = Files.getLastModifiedTime(filePath).toMillis()
            if (path != lastPath || modified != lastModified) {
                LogUtil.info(this.javaClass, "설정 파일 변경 됨 → reload: $path")
                reload()
            }
        } catch (e: IOException) {
            LogUtil.error(this.javaClass, "설정 파일 변경 오류", e)
        }
    }
}
