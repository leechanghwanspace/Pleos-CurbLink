CurbLink Drive 0.2 — 2026 HMG 차량용 앱 개발 공모전 프로토타입

1. 설치
- CurbLink-Drive-0.2-demo.apk: 일반 Android/차량용 Android에서 설치 가능한 debug 서명 APK입니다.
- 패키지 io.curblink.demo / versionCode 2 / minSdk 29 / targetSdk 35 / Kotlin 2.0.21 / Compose.
- adb install -r "CurbLink-Drive-0.2-demo.apk"
- adb shell am start -n io.curblink.demo/.MainActivity
- 950dp 이상은 지도와 상세 화면을 좌우로 배치하고, 작은 화면은 세로 배치합니다.
- 기존 0.1 APK와 서명이 다르다는 오류가 나면 기존 설치본을 임의로 삭제하지 말고 설치 상태와 인증서를 먼저 확인하세요.

2. 권장 시연 순서
가. 홈에서 집 선택 → 도착 식사 → 모의 GPS → 집으로 출발.
나. 운전자 혼자는 메뉴 터치가 잠깁니다. 실제 음성 서비스가 있으면 '음성 입력'을 사용합니다.
   서비스가 없는 기기는 '발표 설정' → '모의 음성: 추천해 줘' → '모의 음성: 주문해 줘'.
   동승자 조작은 메뉴 선택 → 예약 주문 확인. 조리·배달 시간을 역산해 예약됩니다.
다. 배리어프리 → 필요한 공간/지원 선택 → 하차 베이 선택 → 출발.
   좁고 경사가 있는 정문은 제외됩니다. 넓은 공간/완만한 경사 선택 시 A3가 추천됩니다.
   도착 후 공간·장비·직원 확인을 모두 마치면 하차 안내가 시작됩니다.
   '측면 장애물 주입'이나 '차량 상태 무효'에서는 하차를 승인할 수 없습니다.
라. 현장 인계 → 정차 후 인계 위치 카드 확인. 외부 공유는 사용자가 공유창에서 직접 선택합니다.
마. '발표 설정' → '시연 초기화'로 다음 여정을 시작합니다.
- 주행 시간은 발표용 30배속입니다. 실제 차량을 움직이지 않습니다.

3. 구현과 연동 경계
구현: 네이티브 목적지/도착 목적 UI, 모의 경로·차선 추천, ETA 기반 메뉴 정렬·예약 주문,
      운전자/동승자 역할에 따른 조작 제한, 접근성 후보 필터·선택, 하차 재평가, 위치 공유창,
      지원되는 기기에서 Android 최근 위치(30초 이내), 플랫폼 음성 인식 호출과 한국어 TTS.
미연결: Pleos Vehicle/NaviHelper/FusedLocation/TTS/STT SDK, 실제 도로망·교통·가맹점 주문·결제,
        서라운드 카메라·테일게이트·차량 도어/리프트. 카메라 영역은 명시적인 모의 카드입니다.
Android 위치를 읽어도 오프라인 지도와 ETA는 실제 도로 내비게이션으로 바뀌지 않습니다.
접근성 수치는 데모 데이터이며 법적·시설 접근성 인증이 아닙니다.
Manifest는 parked activity이며 distractionOptimized/차량 제어 권한을 주장하지 않습니다.
지원 선호만 기기 내 저장하며 진단명이나 장애 증명을 받지 않습니다.
모의 주문·인계는 메모리 세션이며 앱 프로세스 재시작 시 유지되지 않습니다.

4. Pleos SDK 고도화 계획 (아직 구현·검증한 기능이 아님)
- Vehicle State: MobilityState의 모의 차량 상태를 실제 상태/UX 제한·유효시간과 연결.
- NaviHelper: 목적지·경유지와 실제 남은 시간/거리 이벤트를 MobilityViewModel에 연결.
- FusedLocation: 제공 상태·권한 확인 후 Android 위치 대체 또는 보완.
- TTS/STT: 제공 상태·클라이언트 등록 확인 후 플랫폼 음성 어댑터 대체.
- 카메라/차량 제어는 지원 API·차종·권한 승인 확인 후 별도 검증.
공식 문서의 기능 제공 상태와 권한을 확인해야 하며 이 APK를 Pleos SDK 연동 완료로 제출하지 마세요.

5. 공모전 제출 확인 (2026-10-29 확인)
- 접수 기간: 2026-10-21 ~ 2026-11-15.
- 필수 제출물은 접수양식(기획서)입니다. APK만으로 접수가 완료되지 않습니다.
- 소스 코드와 APK를 함께 GitHub 저장소에 올린 링크, 시연 영상은 선택 개발 산출물입니다.
- 2차 PT 대상은 실제 동작 가능한 앱을 PT 전까지 완료해야 합니다.
- 이 작업에서는 이메일 제출·GitHub 업로드·SDK 사용 신청을 수행하지 않았습니다.
- 심사 항목: 차량 특화성 / 창의성 / 기술 활용성 / 사업성.
  차량 특화성: 주행/정차와 운전자·동승자 맥락, 도착 ETA 활용.
  창의성: 도착 후의 식사·무단차 하차·현장 인계를 하나의 여정으로 연결.
  기술 활용성: Android 네이티브 구현은 완료, Pleos SDK 실연동 증빙은 후속 과제.
  사업성: 배달/주차·시설 운영 연계의 타깃/수익 모델/이용 지표는 기획서에 구체화 필요.

공식 근거:
https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001
https://appdevchallenge.auto-hmg.io/guide/development
https://appdevchallenge.auto-hmg.io/guide/judging-awards
https://appdevchallenge.auto-hmg.io/faq
https://document.pleos.ai/docs/pleos-only/create-my-project
https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro

6. 재빌드
JDK17/Gradle8.9/SDK35, AndroidX/Compose 의존성을 사용합니다.
JAVA_HOME / ANDROID_HOME / 선택적으로 CURBLINK_GRADLE을 지정한 후:
  powershell -ExecutionPolicy Bypass -File scripts/build-drive.ps1
이 스크립트는 assembleDebug + compileDebugUnitTestKotlin + lintDebug 후 직접 JUnit을 실행합니다.
호스트의 Gradle test worker pipe 문제 때문에 순수 도메인 테스트는 JUnitCore로 실행합니다.

7. 검증 범위
- BUILD-VERIFICATION.txt의 실제 빌드·테스트·서명 결과를 확인하세요.
- 이 PC에는 Android Emulator 하드웨어 가속 드라이버가 없습니다.
  소프트웨어 모드 AVD 부팅은 device offline 상태로 진행되지 않았습니다.
  따라서 Android/AAOS/Pleos 실기기 설치·전체 UI 시연 검증 완료로 표시하지 않습니다.
- 실제 시연 기기에서 설치·글꼴·음성 서비스·화면 크기·주행 UX 제한을 추가 확인해야 합니다.

[2026-10-29 실기기 캡처 추가]
배포 APK를 Samsung SM-N976N(Android 12)에 설치·실행하여 2560×1380px / 240dpi 원본 PNG 6장을 촬영했습니다. captures/android 및 captures/README.md 참고. 기존 실기기 설치 미검증 기록은 이 촬영 범위에 한해 갱신하며, AAOS/Pleos/실차 및 전체 기능 종단 검증은 미완료입니다.
