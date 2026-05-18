package scouter.plugin.server.alert.formatter

import scouter.lang.AlertLevel as ScouterAlertLevel
import scouter.lang.pack.AlertPack
import scouter.lang.pack.ObjectPack
import scouter.lang.pack.XLogPack
import scouter.plugin.server.alert.common.AlertLevel
import scouter.plugin.server.alert.monitoring.AlertEvent
import scouter.plugin.server.fingerpay.monitoring.XlogErrorEvent

/**
 * Email 채널 메시지 포맷.
 *
 * billtag 전문 API 기반 발송.
 *   - subject: EmailSender 의 buildPayload() "subject" 필드
 *   - body   : 전문(jonmun) 필드에 삽입되는 HTML 문자열
 *
 * [HTML 구조]
 *   현재 billtag 전문 플랫폼의 JSP 템플릿(pg_alert_template.html)에서
 *   jeonmun 객체로 데이터를 주입받는 방식과 달리,
 *   여기서는 body 를 완성된 HTML 문자열로 생성하여 전문에 직접 삽입한다.
 *
 *   -> 추후 전문 플랫폼의 템플릿 변수 주입 방식으로 전환 시
 *      buildPayload() 만 수정하면 됨 (이 클래스 변경 불필요)
 *
 * [확장 포인트]
 *   - pg_alert_template.html 과 연동하려면 body를 HTML 대신
 *     jeonmun 필드 변수 맵으로 반환하도록 FormattedMessage 확장 고려
 */
class EmailMessageFormat : BaseMessageFormat() {

    // ── subject 오버라이드 ───────────────────────────────
    override fun alertSubject(pack: AlertPack): String =
        "[${ScouterAlertLevel.getName(pack.level)}] ${agentName(pack.objHash)} - ${pack.title}"

    override fun counterSubject(event: AlertEvent): String =
        "[Counter][${event.level}] ${event.objName} - ${event.metric}"

    override fun xlogErrorSubject(event: XlogErrorEvent): String =
        "[Xlog Error] ${event.objName} - ${event.service}"

    override fun xlogSlowSubject(pack: XLogPack, thresholdMs: Int): String =
        "[SLOW TX] ${agentName(pack.objHash)} - ${pack.service}"

    // ── body: HTML ───────────────────────────────────────
    override fun buildAlertBody(pack: AlertPack): String {
        val level = AlertLevel.of(pack.level.toInt())
        return htmlWrap(
            levelName  = level.name,
            title      = pack.title,
            metricValue= "",
            rows = listOf(
                "발생 시각" to ts(pack.time),
                "대상 인스턴스" to "${agentName(pack.objHash)} (${pack.objType})",
                "알림 레벨" to level.name,
            ),
            message = pack.message ?: ""
        )
    }

    override fun buildObjectBody(pack: ObjectPack, status: String): String =
        htmlWrap(
            levelName  = "INFO",
            title      = "OBJECT $status",
            metricValue= "",
            rows = listOf(
                "발생 시각" to ts(System.currentTimeMillis()),
                "대상 인스턴스" to pack.objName,
                "타입" to pack.objType,
            ),
            message = "${pack.objName} 상태 변경: $status"
        )

    override fun buildCounterBody(event: AlertEvent): String =
        htmlWrap(
            levelName  = event.level.name,
            title      = "[Counter] ${event.metric}",
            metricValue= "%.2f".format(event.value),
            rows = listOf(
                "서비스 명" to event.objName,
                "발생 시각" to ts(System.currentTimeMillis()),
                "대상 인스턴스" to event.objName,
                "지표" to event.metric,
                "알림 레벨" to event.level.name,
            ),
            message = event.message
        )

    override fun buildXlogErrorBody(event: XlogErrorEvent): String =
        htmlWrap(
            levelName  = event.level.name,
            title      = "Xlog Error",
            metricValue= "${event.elapsedMs}ms",
            rows = listOf(
                "발생 시각" to ts(event.endTime),
                "대상 인스턴스" to event.objName,
                "서비스" to event.service,
                "에러" to event.errorMessage,
                "TXID" to event.txid.toString(),
            ),
            message = "응답시간 ${event.elapsedMs}ms - ${event.errorMessage}"
        )

    override fun buildXlogSlowBody(pack: XLogPack, thresholdMs: Int): String =
        htmlWrap(
            levelName  = "WARN",
            title      = "SLOW TX (임계치: ${thresholdMs}ms)",
            metricValue= "${pack.elapsed}ms",
            rows = listOf(
                "발생 시각" to ts(pack.endTime),
                "대상 인스턴스" to agentName(pack.objHash),
                "서비스" to pack.service.toString(),
            ),
            message = "응답시간 ${pack.elapsed}ms 초과 (임계치: ${thresholdMs}ms)"
        )

    // ── HTML 템플릿 ──────────────────────────────────────
    /**
     * pg_alert_template.html 의 inline style 구조와 동일한 HTML 생성.
     * levelName → 헤더 배경색 / 지표값 색상 결정.
     */
    private fun htmlWrap(
        levelName: String,
        title: String,
        metricValue: String,
        rows: List<Pair<String, String>>,
        message: String,
    ): String {
        val headerBg = levelColor(levelName)
        val valueColor = headerBg

        val rowsHtml = rows.joinToString("") { (k, v) ->
            """
            <tr>
              <td style="background:#F9F9FA;padding:10px 14px;font-size:12px;font-weight:500;color:#71717A;border-bottom:1px solid #E4E4E7;width:100px;">$k</td>
              <td style="background:#fff;padding:10px 14px;font-size:13px;color:#18181B;border-bottom:1px solid #E4E4E7;">${v.escapeHtml()}</td>
            </tr>
            """.trimIndent()
        }

        val valueBox = if (metricValue.isNotBlank()) """
            <div style="background:#F9F9FA;border:1px solid #E4E4E7;border-radius:6px;padding:14px 18px;margin-bottom:20px;">
              <div style="font-size:11px;color:#71717A;font-weight:500;margin-bottom:4px;">현재 상태</div>
              <span style="font-size:32px;font-weight:600;color:$valueColor;line-height:1;">$metricValue</span>
            </div>
        """.trimIndent() else ""

        return """
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>FingerPay 모니터링 알림</title></head>
<body style="margin:0;padding:0;background:#F4F4F5;font-family:'Apple SD Gothic Neo','Malgun Gothic',Arial,sans-serif;">
<div style="max-width:600px;margin:32px auto;background:#fff;border-radius:8px;overflow:hidden;border:1px solid #E4E4E7;">
  <div style="padding:20px 28px;background:$headerBg;">
    <div style="font-size:12px;color:rgba(255,255,255,0.75);letter-spacing:0.08em;margin-bottom:8px;">FINGERPAY MONITORING</div>
    <div style="display:inline-block;font-size:11px;font-weight:600;padding:2px 10px;border-radius:4px;background:rgba(255,255,255,0.2);color:#fff;margin-bottom:10px;">🚨 $levelName</div>
    <p style="font-size:20px;font-weight:600;color:#fff;line-height:1.4;margin:0;">${title.escapeHtml()}</p>
  </div>
  <div style="padding:24px 28px;">
    <table style="width:100%;border-collapse:collapse;margin-bottom:20px;border:1px solid #E4E4E7;">
      $rowsHtml
    </table>
    $valueBox
    <div style="background:#F9F9FA;border-left:3px solid #D4D4D8;border-radius:0 6px 6px 0;padding:12px 16px;margin-bottom:20px;font-size:13px;color:#3F3F46;line-height:1.7;">
      ${message.escapeHtml()}
    </div>
  </div>
  <div style="padding:16px 28px;background:#F9F9FA;border-top:1px solid #E4E4E7;font-size:11px;color:#A1A1AA;">
    <span style="font-weight:600;color:#71717A;">FingerPay Monitoring</span>
  </div>
</div>
</body></html>
        """.trimIndent()
    }

    private fun levelColor(levelName: String): String = when (levelName.uppercase()) {
        "FATAL" -> "#A32D2D"
        "ERROR" -> "#D95C3F"
        "WARN"  -> "#D9B300"
        else    -> "#185FA5" // INFO
    }

    private fun String.escapeHtml(): String =
        this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
