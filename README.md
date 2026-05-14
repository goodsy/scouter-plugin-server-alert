# Scouter APM Server Built-in Plugin

서버 성능 지표를 실시간으로 모니터링하고, 임계치 초과 시 다양한 채널(Telegram, Slack, Email)로 알림을 전송하는 service용 Scouter 서버 플러그인입니다.

## 📋 Overview

| 항목          | 내용                              |
|-------------|---------------------------------|
| Plugin Type | Scouter Server Built-in Plugin  |
| Scouter 버전  | 2.21.x                          |
| JVM         | Temurin Corretto 21             |
| 개발 언어       | Kotlin                          |
| 빌드 도구       | Gradle (Groovy DSL)             |
| 패키지         | `scouter.plugin.server.alert.*` |

## 🚀 Features

- **멀티 채널 알림**: Telegram, Slack, Email을 통한 장애 알림 발송.
- **실시간 성능 모니터링**: TPS, Active Service, Error Rate, Heap Usage, GC, Xlog 등 주요 지표 감시.
- **JSON 기반 동적 임계치 설정**: `metric-thresholds.json` 파일을 통해 서버 재시작 없이 실시간으로 임계치 및 알림 대상 변경 가능.
- **지능형 알림 필터링**:
    - **Agent 화이트리스트**: 특정 Agent군(IMS, MMS, API 등)만 선별하여 모니터링 가능.
    - **중복 방지 (Cooldown/SentOnce)**: 동일 장애에 대해 과도한 알림이 발송되지 않도록 제어.
    - **추세 감지**: TPS 급증(Spike) 또는 에러율 급증(ErrorRateDiff) 감지 기능.
- **다양한 이벤트 처리**:
    - Scouter 기본 Alert (Agent Up/Down 등)
    - XLog Slow Transaction (응답시간 초과)
    - Counter 성능 지표 (PerfCounter)

## 📂 Architecture
> **반드시 `scouter.plugin.server` 하위에 위치해야 Annotation scan 대상이 됩니다.**

- `common/`: 공통 Enum 및 도메인 객체 (`Channel`, `AlertLevel`)
- `monitoring/`: 지표 감시 로직 및 설정 로더 (`CounterMonitor`, `ThresholdConfigLoader`)
- `sender/`: 채널별 전송 구현체 (`SlackSender`, `TelegramSender`, `EmailSender`)
- `util/`: 메시지 포맷터 및 공통 유틸리티 (`MessageFormatter`, `ChannelDispatcher`, `LogUtil`)
- `serviceAlertPlugin.kt`: Scouter 서버 플러그인 진입점 (ServerPlugin)

```
scouter-plugin-server/
├── build.gradle
├── metric-thresholds.json          ← 임계치 설정 (conf/에 배포)
└── src/main/kotlin/
    └── scouter/plugin/server/alert/
        ├── ScouterAlertPlugin.java        ← 메인 플러그인 (@ServerPlugin)
        ├── monitoring/
        │   ├── CounterMonitor.java        ← 성능 지표 임계치 체크
        │   ├── XLogErrorMonitor.java      ← Xlog 지표
        │   ├── MetricThreshold.java       ← metric-thresholds.json  지표 임계치 모델
        │   ├── ThresholdConfig.java       ← metric-thresholds.json 루트 모델
        │   └── ThresholdConfigLoader.java ← metric-thresholds.json 로드 + 파일 감시(10초 폴링)
        ├── sender/
        │   ├── TelegramSender.java        ← Telegram Bot API
        │   ├── SlackSender.java           ← Slack Incoming Webhook
        │   └── EmailSender.java           ← Jakarta Mail SMTP
        └── util/
            ├── AgentFilter.java           ← agent 화이트리스트 필터
            ├── ChannelDispatcher.java     ← 채널 그룹 기반 전송
            └── MessageFormatter.java      ← 알림 메시지 포맷
```


## ⚙️ Configuration
### scouter.conf

Scouter 서버의 `conf/scouter.conf` 파일에 아래 설정을 추가합니다.

```properties
#heartbeat 미수신 시 INACTIVE 판단까지 대기 시간 (ms), 기본값=8초
object_deadtime_ms=8000

# 알림 최소 레벨 (0:INFO, 1:WARN, 2:ERROR, 3:FATAL)
ext_plugin_service_alert_min_level=1

# Agent 화이트리스트 (포함된 단어가 있으면 모니터링 대상)
ext_plugin_service_agent_whitelist=service_app

# 임계치 설정 파일 경로 (미설정 시 기본값: conf/metric-thresholds.json)
ext_plugin_service_thresholds_path=./conf/metric-thresholds.json

# 플러그인 활성화 여부
ext_plugin_service_alert_enabled=true
ext_plugin_service_xlog_enabled=true

# Telegram 설정
ext_plugin_service_telegram_enabled=true
ext_plugin_service_telegram_token=YOUR_BOT_TOKEN
ext_plugin_service_telegram_chat_id=YOUR_CHAT_ID

# Slack 설정
ext_plugin_service_slack_enabled=true
ext_plugin_service_slack_channel=YOUR_WEBHOOK_CHANNEL_ID

# Email 설정
ext_plugin_service_email_enabled=false
ext_plugin_service_email_api_url=http://your-email-api.com
ext_plugin_service_email_to=admin@example.com

# HTTP 통신 타임아웃 (ms) - 기본값 10000 (10초)
#ext_plugin_service_http_timeout_ms=10000


```

### 임계치 설정 (`metric-thresholds.json`)

`conf/metric-thresholds.json` 파일을 통해 지표별 임계치와 알림을 받을 채널 그룹을 정의합니다. 이 파일은 서버 재시작 없이 60초마다 자동으로 리로드됩니다.

```json
{
  "channelGroups": {
    "ops": {
      "WARN": ["slack"],
      "ERROR": ["email"],
      "CRITICAL": ["email"]
    },
    "dev": {
      "WARN": ["slack"],
      "ERROR": ["telegram", "slack"],
      "CRITICAL": ["telegram", "slack", "email"]
    }
  },
  "thresholds": [
    {
      "metric": "TPS",
      "warnValue": 500,
      "errorValue": 800,
      "criticalValue": 1000,
      "channelGroups": ["ops", "dev"],
      "tpsZeroCheck": true,
      "tpsSpikeRatio": 3.0
    },
    {
      "metric": "HeapPct",
      "warnValue": 75.0,
      "errorValue": 85.0,
      "criticalValue": 90.0,
      "channelGroups": ["dev"]
    }
  ]
}
```

## 구현


### @ServerPlugin 핵심 규칙

| 규칙 | 내용 | 위반 시 |
|------|------|---------|
| 패키지 | `scouter.plugin.server.*` 하위 필수 | Annotation scan 제외 |
| 클래스 접근자 | `public class` 필수 | IllegalAccessException |
| 생성자 | `public` 생성자 명시 필수 | IllegalAccessException |
| 메서드 접근자 | `public void` 필수 | 메서드 등록 실패 |
| Annotation | `@ServerPlugin(PluginConstants.PLUGIN_SERVER_*)` | 플러그인 미등록 |

### 플러그인 메서드 목록

| Annotation 상수 | 메서드 시그니처 | 호출 시점 |
|----------------|----------------|-----------|
| `PLUGIN_SERVER_ALERT` | `public void alert(AlertPack pack)` | scouter Alert 발생 시 |
| `PLUGIN_SERVER_XLOG` | `public void xlog(XLogPack pack)` | XLog 트랜잭션 수신 시 |
| `PLUGIN_SERVER_OBJECT` | `public void object(ObjectPack pack)` | 인스턴스 상태 변경 시 |
| `PLUGIN_SERVER_COUNTER` | `public void counter(PerfCounterPack pack)` | 약 5초 주기 지표 수신 시 |

### 🛠️ 알림 동작 흐름
```
Scouter Agent
     │
     ▼
Scouter Server
     │  (4가지 이벤트 훅)
     ▼
FingerPayAlertPlugin
     │
     ├─ AgentFilter (화이트리스트 필터)
     │
     ├─ [Counter] CounterMonitor → 임계치 체크 → AlertEvent 생성
     ├─ [XLog]    XLogErrorMonitor → 에러 발생 → XlogErrorEvent 생성│
     ├─ ThresholdConfigLoader (metric-thresholds.json → 60초 폴링)
     │
     └─ ChannelDispatcher
           │
           ├─ channelGroup + level → 채널 결정 (ThresholdConfig.resolveChannels)
           │
           ├─ TelegramSender  (Bot API)
           ├─ SlackSender     (Incoming Webhook)
           └─ EmailSender     (내부 메일 API)
```

## 플러그인 훅 (이벤트 진입점)

`FingerPayAlertPlugin` 은 Scouter의 4가지 Server Plugin 어노테이션을 구현합니다.

### ① PLUGIN_SERVER_ALERT — Scouter 자체 Alert 수신

```
AlertPack 수신
  └─ title에 "INACTIVE_OBJECT" 또는 "ACTIVATED_OBJECT" 포함?
       ├─ YES → handleAgentStatusAlert()
       │         └─ DOWN(FATAL) / RECONNECTED(INFO) 메시지 생성
       │              └─ dispatcher.dispatch(channelGroup="all")
       └─ NO  → 일반 Alert
                └─ isAllowed(objName) 통과 시
                     └─ dispatcher.dispatch(channelGroup="all")
```

- Agent DOWN/UP 이벤트는 `all` 그룹으로 전송 (전체 채널)
- 그 외 Scouter 자체 Alert도 `all` 그룹으로 전송

### ② PLUGIN_SERVER_XLOG — 슬로우 트랜잭션

```
XLogPack 수신
  └─ ext_plugin_fingerpay_xlog_enabled=true 이고
     pack.elapsed > ext_plugin_fingerpay_xlog_threshold_ms(기본: 3000ms)
       └─ (dispatcher.dispatch 주석 처리 상태 — 필요 시 활성화)
```

### ③ PLUGIN_SERVER_OBJECT — Agent 최초 연결

```
ObjectPack 수신 (heartbeat)
  └─ isAllowed(objName) 통과 시
       └─ AgentManager에 미등록(최초 연결) + 중복 알림 방지 통과
             └─ dispatcher.dispatch(channelGroup="all", level=INFO)
                  → "UP ✅" 메시지 전송
```

### ④ PLUGIN_SERVER_COUNTER — 성능 지표 임계치 감시

```
PerfCounterPack 수신
  └─ isAllowed(objName) 통과 시
       └─ CounterMonitor.check(pack, thresholdConfig)
             └─ AlertEvent 목록 반환
                  └─ 각 event에 대해
                       dispatcher.dispatch(
                         channelGroup = event.channelGroup,
                         level        = event.level
                       )
```

### CounterMonitor — 임계치 체크 상세

`PerfCounterPack` 수신 시 아래 지표를 순서대로 체크합니다.

| 지표 | 체크 방식 | 특이사항 |
|------|----------|---------|
| TPS | 절대값 초과 + Zero 감지 + Spike 감지 | `tpsZeroCheck`, `tpsSpikeRatio` 옵션 |
| ErrorRate | 절대값 초과 + 5분 전 대비 증가율(%p) | `errorRateDiffWarn`, `errorRateDiffFatal` |
| ActiveService | 절대값 초과 | |
| Elapsed | 절대값 초과 | ms 단위 |
| GcTime | 절대값 초과 | ms 단위 |
| GcCount | 절대값 초과 | |
| HeapPct | 절대값 초과 | % 단위 |
| HeapUsed | 절대값 초과 | bytes → MB 변환 후 비교 |


## 📦 빌드 방법

```bash
./gradlew jar
```
빌드 결과물은 `build/libs/` 폴더에 생성됩니다. 생성된 jar 파일을 Scouter 서버의 `lib/` 또는 지정된 플러그인 경로에 복사하여 사용하세요.
