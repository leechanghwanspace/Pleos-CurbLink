# CurbLink Drive — 제출용 시연 자료

공모전 제출용으로 정리한 영상과 스크린샷입니다. 출처가 두 가지이므로 섞지 않고 구분합니다.

- **네이티브 APK 화면**: Pleos Connect v2.1.0 에뮬레이터에서 실제 APK를 실행해 촬영했습니다.
- **웹 주행 시뮬레이션**: `simulator/`의 가상 도로·차량 내부·합성 주변 시야입니다. 앱 규칙을 시험하려고 **팀이 따로 만든 개발용 환경**이며, **화면과 디자인이 실제 앱 UI와 다릅니다.**

## 시연 영상

| 영상 | 길이 | 내용 |
|---|---|---|
| [curblink-apk-demo.mp4](video/curblink-apk-demo.mp4) | 2분 4초 | **release APK**를 Pleos Connect v2.1.0 에뮬레이터에서 실행. 실제 CarService·NaviHelper 연결(P단) 상태에서 식사 추천→주문 예약, 하차 후보 비교→A-2 이용 불가→A-3 대안→안전 확인→입구 도착, 위치 카드 확인까지 |
| [curblink-simulation-demo.mp4](video/curblink-simulation-demo.mp4) | 3분 48초 | **(앱 UI 아님 · 개발용 검증 환경)** 웹 주행 시뮬레이션의 여섯 여정(픽업·주차·잠시 정차·식사·한강 인계·배리어프리)을 처음부터 끝까지 |

- APK 영상: `adb screenrecord` 원본(2560×1440)을 저장소 용량 때문에 1920×1080 H.264로 다시 인코딩했습니다. 자르거나 덧입힌 장면은 없습니다. 기록: [video/apk-verification.json](video/apk-verification.json)
- APK 영상은 차량이 **P단 정차** 상태이고 시험용 주행 입력을 넣지 않았습니다. 그래서 주행 중 차선 안내 화면은 이 영상에 없고, 웹 시뮬레이션 영상에서 볼 수 있습니다. 매장·메뉴·시설·주문은 견본 데이터이며 실제로 전송·결제되지 않습니다. 에뮬레이터 GPS에는 시험 좌표(서울시청)를 넣었습니다.
- 웹 영상: Chrome 헤드리스 소프트웨어 렌더링이며, 물리 GPU나 실차 주행 검증이 아닙니다. 기록: [video/verification.json](video/verification.json)

다시 녹화하려면:

```powershell
python scripts/record-apk-demo.py --serial emulator-5554
python scripts/record-submission.py --rate 5 --hold 2.5 --channel chrome
```

## release APK 화면 (Pleos Connect v2.1.0, 2560×1440)

APK 영상과 같은 실행에서 `adb screencap`으로 촬영한 원본 PNG입니다. 에뮬레이터 전체 화면이라 Pleos 시스템 UI가 함께 보입니다.

| 실제 차량 연결 · 도착 안내 | 도착 시간 맞춤 메뉴 추천 |
|---|---|
| ![연결](screenshots/apk/01-arrival-connected.png) | ![메뉴 추천](screenshots/apk/02-dining-recommended.png) |

| 주문 전 확인 | 견본 주문 예약 · 도착 대기 |
|---|---|
| ![주문 확인](screenshots/apk/03-dining-review.png) | ![주문 추적](screenshots/apk/04-dining-order-tracking.png) |

| 하차 후보 비교 | A-2 이용 불가 → A-3 대안 |
|---|---|
| ![후보 비교](screenshots/apk/05-access-comparison.png) | ![대안](screenshots/apk/06-access-a2-unavailable-alternative.png) |

| 안전 확인 · 입구 도착 | 위치 카드 확인 |
|---|---|
| ![입구 도착](screenshots/apk/07-access-entrance-confirmed.png) | ![위치 카드](screenshots/apk/08-handoff-location-card.png) |

## 이전 APK 화면 (0.5.2 debug 프리뷰, 시험 입력 포함)

주행 중 차선 준비와 후보 없음 화면은 시험용 관측 입력이 필요해 위 영상에 없습니다. 0.5.2 프리뷰 때 촬영한 앱 창 원본(1682×1210)을 함께 둡니다. 원본은 [captures/android/0.5.2](../captures/android/0.5.2/)에 있고, 해시와 입력 출처는 [verification.json](../captures/android/0.5.2/verification.json)에 있습니다.

| 실제 플랫폼 연결 | 도착 안내 · 차선 준비 |
|---|---|
| ![플랫폼 연결](../captures/android/0.5.2/01-platform-connected.png) | ![차선 준비](../captures/android/0.5.2/07-lane-preparation.png) |

| 식사 · 견본 매장 | 주문 검토 |
|---|---|
| ![식사 매장](../captures/android/0.5.2/02-dining-stores.png) | ![주문 검토](../captures/android/0.5.2/03-dining-review.png) |

| 편안한 하차 · 후보 비교 | 하차 완료 확인 |
|---|---|
| ![하차 후보 비교](../captures/android/0.5.2/04-access-comparison.png) | ![하차 완료](../captures/android/0.5.2/05-access-completed.png) |

| A-2 이용 불가 → A-3 대안 | 조건에 맞는 후보 없음 |
|---|---|
| ![대체 베이](../captures/android/0.5.2/08-bay-alternative.png) | ![후보 없음](../captures/android/0.5.2/09-bay-no-match.png) |

![현장 인계 · 위치 카드](../captures/android/0.5.2/06-handoff.png)

차선·하차 대안 화면은 시험용 관측 입력을 사용합니다. 식사·시설은 견본 데이터이며 실제 주문·결제·시설 연결의 증거가 아닙니다.

## 웹 주행 시뮬레이션 화면 (개발용 검증 환경)

> [!IMPORTANT]
> **아래 화면은 CurbLink 앱이 아닙니다.** 앱의 규칙을 실제 도로 없이 시험하려고 팀이 별도로 만든 웹 시뮬레이션 환경입니다. 화면 구성과 디자인이 실제 앱 UI와 다릅니다. 앱과 같은 판단 규칙(차선 추천, 하차 후보 평가, 식사 시간 맞춤)을 쓰는지 확인하는 용도이며, 실제 앱 화면은 위의 **release APK 화면**을 봐 주세요.

영상과 같은 실행에서 촬영한 원본 PNG입니다.

| 1 골목 픽업 · 주행 | 2 주차 · R단 후진 |
|---|---|
| ![픽업 주행](screenshots/web/01-pickup-driving.png) | ![후진 주차](screenshots/web/02-parking-reverse.png) |

![3 잠시 정차 · 비상등](screenshots/web/03-short-stop-hazards.png)

| 4 식사 메뉴 · 혼자 운전 시 터치 주문 차단 | 4 음성 주문 확인 |
|---|---|
| ![식사 메뉴](screenshots/web/04-meal-menu-touch-blocked.png) | ![음성 주문 확인](screenshots/web/05-meal-voice-confirmed.png) |

![5 한강 합성 주변 시야와 위치 인계](screenshots/web/06-river-surround-handoff.png)

| 6 배리어프리 · 하차 후보 비교 | 6 P단 정차 후 안전 확인 |
|---|---|
| ![후보 비교](screenshots/web/07-access-bay-comparison.png) | ![안전 확인](screenshots/web/08-access-safety-checks.png) |

![6 입구 도착 완료](screenshots/web/09-access-entrance-arrived.png)
