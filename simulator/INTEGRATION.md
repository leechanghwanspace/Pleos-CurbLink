# Pleos CurbLink 3D 시뮬레이터 연동 가이드 (`INTEGRATION.md`)

본 문서는 `simulator/index.html`로 구현된 **Pleos CurbLink 발표용 3D 차량 시뮬레이터**와 Codex가 개발하는 **Android APK 통신 어댑터 및 로컬 WebSocket 브리지** 간의 통합 규격을 정의합니다.

---

## 1. 생성된 파일 및 로컬 실행 방법

### 생성 파일 목록
- `simulator/index.html` — React 18.3.1 + Tailwind CSS 3.4.15 + Three.js 0.160.0 기반 단일 파일 3D 차량 시뮬레이터
- `simulator/INTEGRATION.md` — 브리지 연동 규격 및 상태 소유권 명세서
- `simulator/HANDOFF.json` — 통합 준비 상태 및 미결 협의 사항 정의 파일

### 로컬 HTTP 서버 실행 방법
`BroadcastChannel` 및 `window.postMessage`의 출처(`origin`) 검증이 정상 동작하도록 `file://` 직접 열기 대신 로컬 HTTP 서버에서 실행하십시오.

```powershell
# 작업 폴더 이동
cd "<저장소 폴더>"

# Python 내장 HTTP 서버 실행 (8080 포트)
python -m http.server 8080
```
브라우저에서 `http://127.0.0.1:8080/simulator/index.html` (또는 `http://localhost:8080/simulator/index.html`)로 접속합니다.

---

## 2. CDN 버전 및 인터넷 연결 필요 여부

별도의 번들러/빌드 과정 없이 즉시 실행되도록 아래 **고정 버전 CDN**을 사용합니다. 따라서 **최초 페이지 로드 시 인터넷 연결이 필요**합니다.

| 라이브러리 | 고정 버전 | CDN URL |
|:---|:---|:---|
| Tailwind CSS | `3.4.15` | `https://cdn.tailwindcss.com/3.4.15` |
| React | `18.3.1` | `https://unpkg.com/react@18.3.1/umd/react.production.min.js` |
| ReactDOM | `18.3.1` | `https://unpkg.com/react-dom@18.3.1/umd/react-dom.production.min.js` |
| Three.js | `0.160.0` | `https://unpkg.com/three@0.160.0/build/three.min.js` |
| Babel Standalone | `7.26.2` | `https://unpkg.com/@babel/standalone@7.26.2/babel.min.js` |

---

## 3. 실행 모드 전환 및 상태 소유권

화면 우측 상단 헤더의 모드 스위치로 전환합니다.

### A. 독립 웹 데모 모드 (`WEB_DEMO`, 기본값)
- **상태 최종 판단 주체**: `simulator/index.html` 내장 `MockVehicleAdapter` 로직 및 `evaluateTrunkSafety()`
- 주기적 타이머(200ms)가 `lastVehicleRxAt`, `lastSensorRxAt` 신선도를 갱신하며, 센서 만료 시뮬레이션 토글 시 센서 갱신을 멈춰 3초(`DEFAULT_SENSOR_TTL_MS = 3000`) 후 실제 만료 차단을 재현합니다.
- 트렁크 열기 요청 시 13개 조건을 모두 만족해야만 `COMMAND_RESULT(accepted: true)` 및 `VEHICLE_STATE(trunk: 'OPEN')`로 전이되고 3D 트렁크가 열립니다.

### B. 외부 브리지 모드 (`EXTERNAL_BRIDGE`)
- **상태 최종 판단 주체**: 외부 Android APK / 로컬 WebSocket 브리지 서버
- 모드 전환 또는 WebSocket 최초 연결 직후에는 모든 도어와 기어 상태를 `UNKNOWN`으로 초기화하며, 유효한 `VEHICLE_STATE` 스냅샷이 수신되기 전까지 모든 제어를 차단합니다.
- 사용자가 트렁크 열기를 요청하면 시뮬레이터는 `COMMAND_REQUEST`만 송신하며 **스스로 도어를 열지 않습니다**.
- `COMMAND_RESULT(accepted: true)` 수신만으로도 3D 트렁크를 열지 않으며, 반드시 후속 `VEHICLE_STATE`에서 `doors.trunk === "OPEN"`이 확인된 후에만 3D 애니메이션을 반영합니다.
- 외부 연결 실패 시 자동으로 Mock 성공 상태로 폴백하지 않으며, 사용자가 명시적으로 `A. 독립 웹 데모 모드` 버튼을 클릭해야만 전환됩니다.

---

## 4. WebSocket 브리지 주소 설정 위치

1. **코드 상수 기본값**: `simulator/index.html` 상단의 `DEFAULT_WS_URL = 'ws://127.0.0.1:8765'`
2. **런타임 UI 변경**: 우측 IVI 디스플레이 상단 탭에서 **`외부 AAOS 에뮬레이터(APK) 연결 뷰`**를 선택한 뒤, 하단 입력창에서 WebSocket URL을 수정하고 **`WebSocket 연결 시도`** 버튼을 클릭할 수 있습니다.

> **참고**: `BroadcastChannel('vehicle-bridge')`은 동일 출처(same-origin) 브라우저 탭/컨텍스트 간 통신 수단이며 Android APK와 직접 연결되지 않습니다. APK 연동은 로컬 WebSocket 브리지를 통해 수행합니다.

---

## 5. 핵심 내부 처리 함수 이름 (`simulator/index.html`)

| 함수 이름 | 역할 설명 |
|:---|:---|
| `evaluateTrunkSafety(params)` | 트렁크 개방 13대 안전 조건 및 데이터 신선도(차량 1초, 센서 3초)를 검증하는 단일 안전 판단 함수 |
| `validateProtocolMessage(raw)` | `curblink.vehicle.v1` 공통 헤더, 메시지 타입, enum, 숫자 범위, 필수 필드를 검증하는 스키마 검증기 |
| `handleIncomingProtocolMessage(raw, transportLabel)` | WebSocket / `postMessage` / `BroadcastChannel` 수신 메시지의 중복 `messageId` 제거 및 타입별 라우팅 |
| `applyIncomingVehicleState(payload, sessionId)` | 단조 증가 `sequence` 검증 후 `VEHICLE_STATE` 스냅샷 반영 (새 `sessionId` 시 sequence 기준 리셋) |
| `applyIncomingCommandResult(payload)` | `COMMAND_RESULT`의 승인/거부 상태 및 `reasonCodes`를 UI와 로그에 반영 |
| `handleDoorCommandRequest(target, action, label, reqId)` | 중복 `requestId` 방어 및 모드별(`WEB_DEMO` vs `EXTERNAL_BRIDGE`) 도어 제어 명령 처리 |
| `sendProtocolEnvelope(type, payload, targetChannel)` | `curblink.vehicle.v1` 표준 봉투 생성 및 활성 트랜스포트로 송신 |
| `sendEvaluatorInjection(label, vehiclePatch, scenarioPatch)` | 평가자 제어 독 조작 시 Mock 주입 또는 외부 브리지로 `EVALUATOR_INJECT` 메시지 송신 |

---

## 6. 메시지 규격 및 송수신 주체 (`curblink.vehicle.v1`)

### 메시지별 송신·수신 주체 요약
| 메시지 `type` | 주요 송신 주체 (`source`) | 주요 수신 주체 | 용도 |
|:---|:---|:---|:---|
| `HELLO` | `simulator`, `android-bridge`, `ivi-app` | 상대방 전체 | 초기 연결, 세션 ID 교환, 타임아웃 후 스냅샷 재동기화 요청 |
| `HEARTBEAT` | `android-bridge`, `simulator` | 상대방 전체 | 연결 유지 및 마지막 수신 시각 갱신 |
| `COMMAND_REQUEST` | `simulator`, `ivi-app` | `android-bridge` (또는 Mock 어댑터) | 도어/트렁크 `OPEN` / `CLOSE` 명령 요청 |
| `COMMAND_RESULT` | `android-bridge` (또는 Mock 어댑터) | `simulator`, `ivi-app` | 명령 승인 여부(`accepted`) 및 차단 사유 코드(`reasonCodes`) 반환 |
| `VEHICLE_STATE` | `android-bridge` (또는 Mock 어댑터) | `simulator`, `ivi-app` | 차속, 기어, UX 제한, 도어 상태, 장애물, 센서 유효성 스냅샷 전달 |
| `SCENARIO_STATE` | `android-bridge`, `simulator` | `simulator`, `ivi-app` | 주문 준비, 구역 확인, OTP, 배려 픽업 요청/수락/고객 준비 확인 동기화 |
| `EVALUATOR_INJECT` | `simulator` | `android-bridge` | 외부 브리지 모드에서 평가자 제어 독의 상태 주입 요청 전달 |

### 실제 JSON 메시지 예시

#### 1) `HELLO`
```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9x1-8a2f1",
  "source": "simulator",
  "sessionId": "sess-ws-m2p9x1-19c4d",
  "type": "HELLO",
  "timestamp": 1761489000000,
  "payload": {
    "client": "pleos-curblink-3d-shell",
    "requestSnapshot": true
  }
}
```

#### 2) `HEARTBEAT`
```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9x5-3b7e2",
  "source": "android-bridge",
  "sessionId": "sess-bridge-01",
  "type": "HEARTBEAT",
  "timestamp": 1761489001000,
  "payload": {
    "alive": true
  }
}
```

#### 3) `COMMAND_REQUEST`
```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9xa-7c11a",
  "source": "simulator",
  "sessionId": "sess-ws-m2p9x1-19c4d",
  "type": "COMMAND_REQUEST",
  "timestamp": 1761489002500,
  "payload": {
    "requestId": "req-m2p9xa-4d90e",
    "target": "trunk",
    "action": "OPEN"
  }
}
```

#### 4) `COMMAND_RESULT` (차단 예시)
```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9xb-9f02c",
  "source": "android-bridge",
  "sessionId": "sess-bridge-01",
  "type": "COMMAND_RESULT",
  "timestamp": 1761489002650,
  "payload": {
    "requestId": "req-m2p9xa-4d90e",
    "accepted": false,
    "reasonCodes": ["REAR_OBSTACLE_DETECTED", "CARE_CUSTOMER_NOT_READY"]
  }
}
```

#### 5) `VEHICLE_STATE`
```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9xc-1a44b",
  "source": "android-bridge",
  "sessionId": "sess-bridge-01",
  "type": "VEHICLE_STATE",
  "timestamp": 1761489003000,
  "payload": {
    "sequence": 14,
    "speedKph": 0,
    "gear": "P",
    "uxRestricted": false,
    "doors": {
      "driverDoor": "CLOSED",
      "passengerDoor": "CLOSED",
      "trunk": "OPEN"
    },
    "rearObstacle": false,
    "vehicleDataValid": true,
    "sensorDataValid": true,
    "oemControlAllowed": true
  }
}
```

#### 6) `SCENARIO_STATE`
```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9xd-5e88f",
  "source": "android-bridge",
  "sessionId": "sess-bridge-01",
  "type": "SCENARIO_STATE",
  "timestamp": 1761489003100,
  "payload": {
    "orderId": "ORD-2026-A3",
    "orderReady": true,
    "pickupZoneConfirmed": true,
    "otpVerified": true,
    "carePrefs": {
      "waitInVehicle": true,
      "loadingHelp": true,
      "extraPrepTime": false
    },
    "merchantResponse": "ACCEPTED",
    "customerReadyConfirmed": true,
    "handoffCompleted": false
  }
}
```

---

## 7. 평가자 상태 주입 인터페이스 (`sendEvaluatorInjection`)

외부 브리지 모드(`EXTERNAL_BRIDGE`)에서 심사위원이 하단 **평가자 제어 독** 버튼을 조작하면, 웹 시뮬레이터는 임의로 로컬 상태를 바꾸지 않고 `sendEvaluatorInjection(label, vehiclePatch, scenarioPatch)` 함수를 통해 `type: "EVALUATOR_INJECT"` 메시지를 브리지로 전송합니다.

```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "msg-m2p9xe-2c31d",
  "source": "simulator",
  "sessionId": "sess-ws-m2p9x1-19c4d",
  "type": "EVALUATOR_INJECT",
  "timestamp": 1761489004000,
  "payload": {
    "label": "트렁크 후방 장애물 감지 주입",
    "vehiclePatch": {
      "rearObstacle": true
    },
    "scenarioPatch": null
  }
}
```

---

## 8. 연결 해제 · 재연결 · 타임아웃 동작

1. **초기 연결 및 연결 해제**:
   - 연결 시점 또는 연결 해제 시 `connectionState.snapshotReceived = false`, 기어 및 모든 도어 상태를 `UNKNOWN`으로 전환하고 열기 명령을 차단합니다.
2. **명령 타임아웃 (`COMMAND_TIMEOUT_MS = 4000ms`)**:
   - 외부 브리지 모드에서 `COMMAND_REQUEST` 송신 후 4초 이내에 `COMMAND_RESULT`가 도착하지 않으면 실패로 단정하지 않고 **`[결과 미확인]`** 상태로 표시한 뒤 `HELLO (reason: COMMAND_TIMEOUT_RESYNC)`를 전송하여 최신 스냅샷 재동기화를 요청합니다.
3. **시퀀스 및 세션 복구**:
   - 동일 `sessionId` 내에서는 `payload.sequence <= lastSequence`인 구버전 패킷을 무시합니다.
   - 브리지가 재시작되어 새로운 `sessionId`로 `HELLO` 또는 `VEHICLE_STATE`를 보내면 `lastSequence`를 리셋하고 새 스냅샷으로 동기화합니다.

---

## 9. 검증 완료 항목 및 미검증 항목

### 검증한 항목
- `simulator/index.html` 정적 구문, `curblink.vehicle.v1` 스키마 검증기, 13대 안전 인터록 평가 함수(`evaluateTrunkSafety`) 로직 구성 확인
- 파일 인코딩(UTF-8) 및 생성 경로 확인

### 아직 검증하지 못한 항목 (실제 실행 환경 필요)
- 실제 브라우저 렌더링 및 WebGL 3D 캔버스 프레임레이트/클릭 Raycasting 수동 조작 검증
- Codex가 작성할 로컬 WebSocket 브리지 서버(`ws://127.0.0.1:8765`) 및 실제 Android APK 에뮬레이터와의 종단간(End-to-End) 소켓 연동 검증

---

## 10. Codex와 추가 합의가 필요한 사항

1. **평가자 상태 주입(`EVALUATOR_INJECT`) 수용 규격**:
   - 시뮬레이터는 외부 브리지 모드에서 평가자 제어 독 조작 시 `type: "EVALUATOR_INJECT"`로 `vehiclePatch`와 `scenarioPatch`를 송신합니다. 브리지 서버에서 이를 수신하여 Android 앱의 운영자 상태에 반영할지, 혹은 별도의 메시지 타입 이름을 사용할지 합의가 필요합니다.
2. **배려 픽업 상태 필드명 동기화**:
   - `SCENARIO_STATE` 내 `carePrefs` (`waitInVehicle`, `loadingHelp`, `extraPrepTime`), `merchantResponse` (`PENDING | ACCEPTED | DECLINED`), `customerReadyConfirmed` (`boolean`) 필드명을 Android `CurbLinkModels.kt`의 `ServiceNeeds` (`remainInVehicle`, `trunkLoadingHelp`, `extraPreparationTime`)와 브리지 계층에서 매핑할지 여부에 대한 합의가 필요합니다.
3. **차량 상태 주기적 브로드캐스트 주기**:
   - 시뮬레이터의 기본 차량 데이터 유효 시간(`DEFAULT_VEHICLE_TTL_MS`)이 `1000ms`이므로, 외부 브리지 모드에서 Android 브리지가 최소 `500ms~800ms` 주기로 `VEHICLE_STATE`를 송신해 주어야 신선도 만료 차단이 발생하지 않습니다.

## Codex 통합 결과 (2026-10-27)
외부 모드에서는 APK가 주문/배려/OTP/정상 개폐를 소유합니다. 평가자 초기 상태 주입, 센서 무효 주입, 실제 소켓 해제, heartbeat와 EVALUATOR_INJECT 호환을 반영했습니다. 현재 실행법과 검증 범위는 저장소 README를 기준으로 합니다. 브라우저 렌더링/WS 왕복 확인, 최종 APK 전체 UI 여정은 미완료입니다.
