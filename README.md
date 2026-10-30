# CurbLink · 차량 앱 APK

> **현재 브랜치: `apk` — 네이티브 차량 앱 개발·APK 단독 실행**
>
> [최종 main](https://github.com/leechanghwanspace/Pleos-CurbLink/tree/main) · [웹 시뮬레이션](https://github.com/leechanghwanspace/Pleos-CurbLink/tree/simulation) · [통합 개발](https://github.com/leechanghwanspace/Pleos-CurbLink/tree/integration) · **APK 단독**
>
> [브랜치 역할과 개발 규칙](docs/BRANCH_GUIDE.md)

Kotlin / Jetpack Compose 기반 차량 앱입니다. 기본 실행은 **APK만** 설치·실행하며 웹 서버나 시뮬레이터를 시작하지 않습니다.

## 설치 파일

**[0.5-arrival-studio APK 다운로드](release/CurbLink-Drive-0.5-arrival-studio.apk)** · [SHA-256](release/CurbLink-Drive-0.5-arrival-studio.apk.sha256) · [검증 기록](release/VERIFICATION-0.5.json)

Android 10(API29) 이상, target35, versionCode6, 패키지 `io.curblink.demo`, 자동차 기능 required, debug 서명입니다. 일반 스마트폰용 APK로 제공하지 않습니다. 기존 0.2/0.3/0.4 배포물은 과거 기록으로 보존합니다.

## 실행

집 PC에서 Pleos를 먼저 실행하고 `adb devices`로 장치를 확인합니다.

```powershell
./scripts/start-experience.ps1 -Mode Apk -Serial emulator-5554
# 설치 없이 전제조건만 확인:
./scripts/start-experience.ps1 -Mode Apk -Check
```

Android 사용자는 기본 10이며 다르면 `-AndroidUser ID`를 지정합니다. APK 서명이 달라 설치가 실패하더라도 자동 삭제·데이터 초기화를 하지 않습니다.

## 앱 화면 구성

| 화면 | 기능 |
|---|---|
| 도착 안내 | 앱 내 목적지 선택, 도착 목적, 차선·분기점 규칙과 관측 상태 |
| 도착 맞춤 식사 | 현재 위치 확인, 조리+배달 시간 정렬, 음성 안내와 명시적 견본 주문 |
| 편안한 하차 | 경사·단차·문 개방 공간·지원 비교, 안전 확인과 재승인 |
| 현장 인계 | 현재 GPS, 사용자 선택 사진 최대 4장, 위치 확인 후 공유창 |

목적지 3곳·메뉴·시설은 견본입니다. 실제 POI 검색·경로 생성·차선/교통·배달 주문·SVM 카메라 제공자는 미연결입니다. 확인되지 않은 현재 차선이나 GPS를 임의로 생성하지 않습니다. 동승자 선택만으로 공식 UX 제한을 해제하지 않습니다.

## 실제 APK 화면 증거

아래는 **이전 0.4 버전**의 Pleos Connect 2.1.0 앱 창 PixelCopy 원본(1682×1210)입니다. 새 0.5의 실행 화면이 아닙니다.

![이전 0.4 APK 실제 실행 원본](captures/android/vehicle-app-preview/01-pleos-connected.png)

새 0.5 UI의 실제 Pleos 캡처·시연 영상은 집 PC에서 촬영할 항목입니다. 웹 디자인 목업을 실제 APK 캡처 대신 표시하지 않습니다.

## 빌드·현재 검증

JDK17 / SDK35 / Gradle8.9 환경에서 실행합니다.

```powershell
./scripts/build-drive.ps1
```

제품 APK·시험 APK 컴파일, JVM 테스트 **69개**, lint **오류0 / 기존 의존성 경고3**, APK v2 서명·16KB 정렬·시뮬레이션 분리 검사가 통과했습니다. 새 버전의 기기 실행·음성·접근성·공식 폼팩터·전체 여정 검증은 아직 수행하지 않았습니다.

핵심 소스: `app/src/main/java/io/curblink/demo/`. APK에 시뮬레이션 엔진·시나리오·발표 UI를 넣지 않습니다. 저장소의 웹 소스는 회귀검증과 공통 이력을 위해 보존합니다.

[집 PC 작업 인계](docs/CONTEST_0_5_HANDOFF.md) · [웹과 함께 검증하려면 integration](https://github.com/leechanghwanspace/Pleos-CurbLink/tree/integration)
