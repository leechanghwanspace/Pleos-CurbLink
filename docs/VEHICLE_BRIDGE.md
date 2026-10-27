# CurbLink 차량 시연 브리지 v1

실차·Pleos·Gleo·CarPay에 연결하지 않는 로컬 시연용 모의 차량 제어기다. Android APK는 기존 주문/직원 인증/배려 준비 안전 판단을 유지한다. 브리지는 명령 처리 순간 차량 조건을 추가 확인한다. 브라우저는 3D 표시와 평가자용 상태 주입을 담당한다.

## 실행

저장소 루트에서 Python 3.11 이상으로 실행한다. 개발 PC에서 Python 3.13 및 `websockets==17.0.1`로 검증했다.

```powershell
python -m pip install -r bridge/requirements.txt
python bridge/server.py
```

- WebSocket: `ws://127.0.0.1:8765`
- Android HTTP: `http://127.0.0.1:8766`
- Android 에뮬레이터에서는 `adb reverse tcp:8766 tcp:8766` 이후 위 HTTP 주소 사용을 권장한다. 에뮬레이터 호스트 별칭 `http://10.0.2.2:8766`도 지원한다.
- 서버는 **127.0.0.1에만 바인딩**한다. 인터넷/공용망에 노출하지 않는다.
- 종료: 서버 터미널에서 Ctrl+C. 재시작하면 새로운 sessionId이며 기존 요청은 재사용하지 않는다.
- HTTP `/health`로 실행 여부를 확인한다.

Gemini가 생성하는 파일은 저장소 밖 상위 작업 폴더의 `simulator/index.html`이다. 작업 폴더에서 다음과 같이 별도 정적 서버를 실행하면 허용된 출처를 사용한다.

```powershell
python -m http.server 8000 --bind 127.0.0.1
# http://127.0.0.1:8000/simulator/index.html
```

기본 허용 브라우저 출처는 정확히 `http://127.0.0.1:8000`, `http://localhost:8000` 및 Gemini 안내에 맞춘 두 호스트의 `8080` 포트다. `file://` 실행은 허용되지 않는다. 다른 로컬 포트를 사용하려면 `--origin http://127.0.0.1:포트`를 지정한다. `--origin`을 주면 기본 목록을 대체한다. 와일드카드는 없다.

## 상태 소유권

| 주체 | 소유 상태/행동 |
|---|---|
| Android 앱 | 주문, 직원 인증, 고객 배려 요청·준비 확인, 열기/닫기 요청 |
| 로컬 브리지 | 모의 차량 상태, 세션, 차량 조건 재검사, 명령 중복 제거 |
| 브라우저 쉘 | 3D 렌더링, 평가자 입력 요청, 카메라·화면 모드 |

브라우저는 앱 고객 준비 상태를 확정하거나 앱 대신 COMMAND_REQUEST를 보내지 못한다. 평가자 강제 개방은 STATE_INJECTION이며 정상 앱 명령과 구분해서 표시해야 한다. 이는 실제 차량 보안 인증이 아니다. 같은 PC의 네이티브 프로세스는 HTTP에 접근할 수 있으므로 신뢰된 로컬 시연 환경으로 한정한다.

## 공통 envelope

```json
{
  "protocol": "curblink.vehicle.v1",
  "messageId": "고유-문자열",
  "source": "simulator",
  "sessionId": "서버-HELLO에서-받은-UUID",
  "type": "HEARTBEAT",
  "timestamp": 1790000000000,
  "payload": {}
}
```

- 서버 송신 source는 `android-bridge`, Android 송신은 `ivi-app`, 브라우저 송신은 `simulator`다.
- `timestamp`는 epoch milliseconds 정수다. 신선도 판단에 원격 시계를 신뢰하지 않는다.
- messageId/sessionId는 1~128자, 메시지는 16KB 이하 JSON object다.
- 클라이언트는 먼저 서버 HELLO/VEHICLE_STATE를 받고 sessionId를 채운다. 서버가 접속 즉시 HELLO와 상태를 보낸다.
- 서버→클라이언트 VEHICLE_STATE의 `sequence`는 단조 증가한다. 같은 세션에서 이미 처리한 sequence 이하는 무시한다.
- 연결 끊김은 즉시 상태 미확인으로 표시한다. 새 세션 수신 시 과거 명령/sequence/준비 상태를 재동기화한다.

## 브라우저 WebSocket 연동

서버는 약 250ms마다 VEHICLE_STATE를 보내며 이벤트 발생 시 COMMAND_RESULT/SCENARIO_STATE를 보낸다. HELLO payload는 `{ "simulation": true, "heartbeatIntervalMs": 1000 }`이다.

### STATE_INJECTION — 평가자 전용 확장

기존 6개 메시지를 변경하지 않고 추가한 요청이다. 다음은 **첫 전체 동기화** payload다.

```json
{
  "injectionSequence": 0,
  "speedKph": 0,
  "gear": "P",
  "uxRestricted": false,
  "doors": {"driverDoor": "CLOSED", "passengerDoor": "CLOSED", "trunk": "CLOSED"},
  "rearObstacle": false,
  "vehicleDataValid": true,
  "sensorDataValid": true,
  "oemControlAllowed": true
}
```

이후 부분 변경도 허용한다. 예: `{ "injectionSequence": 1, "rearObstacle": true }`.

- injectionSequence는 송신 WS 연결별 0부터 증가시키며 중복·역순 입력을 거절한다. 서버 차량 sequence와는 다른 값이다.
- 최초 유효 상태에는 기어와 모든 도어 상태가 필요하다. 초기 UNKNOWN을 임의로 CLOSED로 바꾸지 않는다.
- 차속은 유한한 0~200 숫자, 기어 P/D/R/UNKNOWN, 도어 OPEN/CLOSED/UNKNOWN, 나머지 값은 JSON boolean만 허용한다.
- 마지막 입력의 유효성 플래그를 유지한다. 센서 만료 시연은 `sensorDataValid:false`를 주입한다. HEARTBEAT만으로 이를 true로 복원하지 않는다.
- 첫 상태 주입 WS가 평가자 제어권을 소유한다. 다른 WS는 읽기 가능하지만 해당 연결 종료/만료 전 입력할 수 없다.
- 제어 WS는 **1초마다 HEARTBEAT**를 보내야 한다. 상태 주입도 생존 갱신으로 인정한다.
- 제어 WS 종료 또는 3초 생존 신호 만료 시 차량·센서 유효성/OEM 허용을 false, 도어를 UNKNOWN으로 만든다. HEARTBEAT 재개만으로 회복하지 않으며 전체 상태를 재주입해야 한다.
- 외부 모드 애니메이션은 이 요청을 보낸 직후가 아니라 **서버 VEHICLE_STATE 수신 후**에 변경한다.
- 오류는 ERROR envelope의 payload `{ "code": "STALE_INJECTION" }`처럼 반환한다.
- 브라우저 COMMAND_REQUEST/SCENARIO_STATE 송신은 거절한다. 브라우저는 수신된 앱 시나리오를 표시만 한다.

서버 상태 payload는 주입 필드에서 injectionSequence를 제외하고 서버 `sequence`를 추가한 형태다.

### Gemini EVALUATOR_INJECT 호환

전달된 Gemini 쉘이 보내는 `{ "label": "평가자 입력", "vehiclePatch": {"rearObstacle":true}, "scenarioPatch": null }` 형식을 추가 지원한다. 브리지는 vehiclePatch를 동일한 차량 검증 함수에 전달한다. 이 경로는 injectionSequence가 없어 같은 WS의 messageId 중복 제거(최근 1,024개)와 역순 timestamp 거절을 적용한다. 같은 millisecond의 서로 다른 입력은 순서대로 처리한다.

`scenarioPatch`가 비어 있지 않으면 **APK_SCENARIO_AUTHORITY 오류로 요청 전체를 거절**한다. 웹에서 주문·OTP·매장 응답·고객 준비를 덮어쓰지 않으며 해당 조작은 APK에서 한다. 브라우저 COMMAND_REQUEST도 계속 거절한다.

Gemini 쉘은 application HEARTBEAT를 보내지 않으므로 이 호환 경로에만 서버가 매초 WebSocket ping을 전송하고 실제 pong 응답을 받은 경우에만 제어권 생존 시간을 갱신한다. 서버 타이머만으로 생존을 연장하지 않는다. STATE_INJECTION 표준 경로는 명시적 HEARTBEAT가 필요하다.

앱 SCENARIO_STATE에 표시용 호환 별칭을 추가한다. 기존 앱 필드는 보존한다.

| Android 필드 | Gemini 표시 필드 |
|---|---|
| orderState = READY/OPEN_REQUESTED/OPEN | orderReady |
| zoneConfirmed | pickupZoneConfirmed |
| staffAuthenticated | otpVerified |
| care.remainInVehicle | carePrefs.waitInVehicle |
| care.trunkLoadingHelp | carePrefs.loadingHelp |
| care.extraPreparationTime | carePrefs.extraPrepTime |
| care.response | merchantResponse |
| care.customerReady | customerReadyConfirmed |
| orderState = COMPLETE | handoffCompleted |

**전달된 쉘의 외부 모드 제한:** 브리지는 안전한 초기 상태를 자동 생성하지 않는다. 전체 유효 스냅샷을 먼저 명시적으로 주입해야 한다. 전달본의 센서 정지 버튼·통신 상태 토글·일부 고객 배려 UI는 외부 모드에서도 웹 로컬 상태만 바꾸므로 APK 상태를 바꿨다고 해석하면 안 된다. 센서 만료는 실제 `sensorDataValid:false` 주입, 통신 해제는 실제 소켓 종료로 재현한다. 전달본은 ERROR 타입을 UI 스키마에서 허용하지 않아 원문 오류 대신 형식 오류 로그가 나타날 수 있다. 이 제한은 브리지에서 안전 판단을 우회해 보정하지 않는다.

## Android HTTP 연동

Android 기본 라이브러리 HttpURLConnection을 사용하므로 APK에 WebSocket 의존성을 추가하지 않는다. 250ms 간격 상태 폴링은 작은 로컬 시연용이며 실차 통신 설계가 아니다.

| 요청 | 본문/응답 |
|---|---|
| GET `/state` | `{ "vehicle": VEHICLE_STATE_envelope, "results": [COMMAND_RESULT_envelope] }` |
| GET `/health` | `{ "ok": true, "protocol": "curblink.vehicle.v1", "sessionId": "...", "simulation": true }` |
| POST `/command` | COMMAND_REQUEST envelope → COMMAND_RESULT envelope |
| POST `/scenario` | SCENARIO_STATE envelope → `{ "ok": true }` |

POST는 `Content-Type: application/json`이 필요하다. 브라우저 Origin이 포함된 HTTP 변경 요청은 허용하지 않는다. 허용된 브라우저의 GET만 CORS로 읽을 수 있다.

COMMAND_REQUEST payload:

```json
{"requestId":"고유-명령-ID","target":"trunk","action":"OPEN"}
```

현재 Android 제어 대상은 trunk, 동작은 OPEN/CLOSE만 지원한다. 운전석/조수석 상태는 평가자 주입으로 시연한다.

COMMAND_RESULT payload:

```json
{"requestId":"고유-명령-ID","accepted":false,"reasonCodes":["GEAR_NOT_P"]}
```

- 모든 요청은 현재 세션과 일치해야 한다.
- 차량 0km/h/P, UX 제한 없음, 차량·센서 유효, 장애물 없음, OEM 허용을 명령 처리 시 다시 검사한다.
- OPEN은 기존 CLOSED, CLOSE는 기존 OPEN 상태를 요구한다.
- 앱이 먼저 주문/OTP/구역/고객 준비를 검사한다. 브리지는 주문을 새로 만들거나 직원 인증을 대신하지 않는다.
- 같은 requestId와 내용은 최초 결과를 반환하며 동작을 반복하지 않는다. 같은 ID에 다른 내용은 오류다.
- 한 서버 세션당 1,024개 고유 명령 제한이다. 이전 ID를 삭제해 오래된 명령을 재실행하지 않고 한도 이후 거절한다.
- GET /state의 results는 최신 32개 결과다. 수신 앱은 자신의 pending requestId와 일치하는 결과만 사용한다.
- 승인 결과가 도착해도 앱은 후속 차량 상태에서 실제 모의 도어 상태를 확인해야 한다. 타임아웃을 성공/실패로 추측하거나 새 ID로 자동 재시도하지 않는다.

SCENARIO_STATE payload는 앱 소유의 표시용 스냅샷이다. `orderState`, `zoneConfirmed`, `staffAuthenticated`, `care`(필요 도움 3개/response/customerReady), `pendingRequestId`, `commandStatus`를 표시할 수 있다. 서버는 bounded JSON을 전달할 뿐 차량 상태에 적용하지 않는다.

## 검증

```powershell
python -m unittest discover -s bridge -v
```

16개 테스트로 초기 미확인, 차량 조건별 차단, 명령 중복/ID 충돌, 세션/출처 검증, 입력 원자성, 입력 순서/제어권, 연결 종료·생존 만료, 시나리오 격리, JSON 제한을 검증했다. 실제 임시 포트 WebSocket↔HTTP 왕복, 명령 결과/상태/시나리오 송수신, 브라우저 출처 거절, Gemini 메시지/필드 호환·역순/중복 제거·실제 ping/pong 생존 확인도 포함한다.

Gemini의 3D 파일을 읽어 통신 규격을 맞췄으나 이 서버 테스트만으로 브라우저 렌더링 또는 APK 전체 여정 검증을 주장하지 않는다. 실제 OEM 권한·센서·차량 보안, NHTSA/현대차 인증을 검증한 결과가 아니다.

사용한 WebSocket 서버 API 참고: [websockets 공식 asyncio 서버 문서](https://websockets.readthedocs.io/en/stable/reference/asyncio/server.html).
