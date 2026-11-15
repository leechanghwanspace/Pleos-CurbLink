# CurbLink Drive 0.4 — Pleos 차량·내비게이션 연결 프리뷰

사용자 확정 방향: **현재 핵심 기능을 유지하고 시뮬레이션만 분리한다.**

- APK: `CurbLink-Drive-0.4-pleos-connected-preview.apk` (이전 `0.3-vehicle-app-preview` 및 `0.2-demo` 보존)
- `io.curblink.demo`, versionCode 5, `0.4-pleos-connected-preview`, min29/target35, debug 서명.
- 앱 화면: 도착 안내 / 식사 / 하차 지원 / 현장 인계.
- 제품 APK에 가상 주행·주차 장면, 합성 주변 시야, 시나리오 실행, 발표 설정, 배속을 넣지 않는다 (`scripts/check-vehicle-apk.ps1` 검증 통과).
- `android.hardware.type.automotive` required. Pleos 부트 클래스패스의 AAOS `android.car` API(`CarPropertyManager`, `CarUxRestrictionsManager`)와 공식 Maven의 `ai.pleos.playground:NaviHelper:3.0.5`를 읽기 전용으로 연결했다.
- Pleos Connect 2.1.0 에뮬레이터에서 실제 `CarService` 기어·차속·UX 제한과 `NaviHelper` 현재 목적지·남은 시간·남은 거리·현재 위치 수신 및 생명주기 stop/reconnect를 검증했다.
- 차량 상태를 임의로 P단/안전으로 간주하지 않는다. 권한 미허용, 서비스 단절, 응답 만료 시 즉시 조작·승인을 제한한다.
- **주문·시설·결제·차량 제어 제공자는 아직 미연결**이며 기본 화면에 `주문·시설 서비스: 확인 대기`로 구분 표시한다.
- 웹 시뮬레이터와 Android 시험 APK는 별도 개발 도구다. 시험 APK(`VehicleAppInstrumentation`)가 외부에서 검증용 데이터를 주입할 때만 로컬 시험 원장을 사용하며 화면에 개발 검증용 데이터임을 표시한다.
- 외부 공유는 인계 위치를 확인한 뒤 Android 공유창에서 사용자가 선택한다. 실제 카메라·도어·리프트 제어 없음.
- 주문·여정 기록은 복구하되 안전 승인·센서 상태·위치 확인은 재확인한다. 실제 내비게이션 목적지 이름·주소·GPS 좌표는 영구 저장하지 않는다.
- 기존 0.2 배포물과 체크섬은 보존했다. 서명 불일치가 나도 기존 앱/데이터를 자동 삭제하지 않는다.

```powershell
. ./scripts/use-home-env.ps1
adb -s emulator-5554 install -r -g release/CurbLink-Drive-0.4-pleos-connected-preview.apk
adb -s emulator-5554 shell am start --user 10 -n io.curblink.demo/.MainActivity
```

[0.5.2 게시 기록](../docs/UPDATE_0_5_2.md). 이 프리뷰는 Pleos 에뮬레이터의 실제 `CarService`/`NaviHelper` IPC 연결을 검증한 것이며, 실차 주행 검증이나 실제 주문·결제·시설 계약 완료, 공식 폼팩터/접근성 인증을 의미하지 않는다.
