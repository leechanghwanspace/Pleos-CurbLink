> **integration — 앱과 시뮬레이션을 합쳐 검증하던 통합 브랜치** · 공모전 제출 기준은 [`main`](../../tree/main)입니다.

# CurbLink Drive — integration

주행 중 차량 상태를 읽어, 도착 이후의 식사·편안한 하차·현장 인계를 안전하게 미리 준비하는 Pleos 차량용 앱입니다. 앱 소개, 제출 정보, 화면, 검증 결과는 [main README](../../blob/main/README.md)에 정리되어 있습니다.

## 이 브랜치의 역할

차량 앱(`app/`)과 웹 주행 시뮬레이터(`simulator/`)가 **같은 판단 규칙**(차선 추천, 하차 후보 평가, 식사 시간 맞춤)을 쓰는지 함께 맞추고 검증하던 브랜치입니다. 0.5 이후의 개발 변경은 모두 이 브랜치에 먼저 모은 뒤 `main`에 병합했습니다.

| 날짜 | 이 브랜치에서 한 일 | `main` 반영 |
|---|---|---|
| 10-30 | 0.5 브랜치 체계 수립, 0.5.1 수정(사진·여정 상태 유지, 앱-웹 견본 데이터 일치) | — |
| 10-30 ~ 10-31 | 0.5.2 도착 복구 기능(차선 준비 안내, 하차 대안·새 승인, 실패 후 다음 행동), 검증 캡처 | `692d492` 병합 |
| 11-15 | release APK, 방향별 사진 첨부, 시연 영상·스크린샷, 제출 정보 | 병합 (PR #1) |

2026-11-15부터 최종 작업은 `main`에서 진행합니다. 이 브랜치는 `main`과 같은 소스를 두고 README만 다릅니다.

## 앱과 시뮬레이션 함께 실행

```powershell
. ./scripts/use-home-env.ps1
# Pleos Connect v2.1.0 에뮬레이터를 먼저 부팅
./scripts/start-experience.ps1 -Mode Integrated -Serial emulator-5554 -OpenBrowser
```

앱과 웹 시뮬레이터를 **동시에 띄우는 것**이며, 두 화면이 실시간으로 상태를 주고받지는 않습니다.

## 규칙 일치 검증

```powershell
# 앱 규칙 (Windows에서는 Gradle 테스트 대신 JUnitCore로 실행)
./gradlew.bat :app:compileDebugUnitTestKotlin
./run-domain-tests.ps1
# 웹 규칙
node simulator/js/lane-engine.test.cjs
node simulator/js/access-planner.test.cjs
node simulator/js/meal-planner.test.cjs
node simulator/js/mission-engine.test.cjs
node simulator/js/destination.test.cjs
# 앱-웹 견본 데이터 일치
python scripts/export-studio-catalog.py --check
```

## 다른 브랜치

| 브랜치 | 역할 |
|---|---|
| [`main`](../../tree/main) | 공모전 제출 기준, 최종 작업 |
| [`simulation`](../../tree/simulation) | 웹 주행 시뮬레이션 안내 |
| [`apk`](../../tree/apk) | 차량 앱 설치·실행 안내 |

전체 브랜치 흐름과 보존한 기능 브랜치는 [main README의 브랜치 구성과 개발 이력](../../blob/main/README.md#브랜치-구성과-개발-이력)에 있습니다.
