# 캡처 출처와 촬영 규격

## 실제 Android APK — android/

배포 APK `release/CurbLink-Drive-0.2-demo.apk`를 Samsung Galaxy Note10+ 5G(SM-N976N)에 설치하여 촬영했습니다. 실제 AAOS/Pleos/차량 디스플레이에서 촬영한 것은 아닙니다.

- Pleos 공식 이미지 규격 중 **2560×1380px**를 적용했습니다. 이미지당 8MB 이하 PNG입니다.
- 기기 논리 디스플레이를 가로 2560×1380px / 240dpi로 설정해 앱이 해당 크기로 직접 렌더링하도록 했습니다.
- `adb exec-out screencap -p` 결과를 그대로 저장했습니다. 리사이즈·선명화·합성·JPEG 변환을 하지 않았으며 Android 시스템 바를 포함합니다.
- 개별 JSON에는 기기 모델, Android 버전, APK SHA256, PNG SHA256과 촬영 시각을 기록합니다. 기기 시리얼과 개인 GPS는 게시하지 않습니다.
- 촬영 후 원래 해상도·밀도·회전 설정을 복원합니다. 앱 APK와 모의 시연 데이터는 기기에 남습니다.

| 파일 | 실제 실행 화면 |
|---|---|
| 01-home.png | 목적지 선택 홈 |
| 02-dining-location.png | 식사 주문 위치 확인 단계 |
| 03-dining-menu.png | ETA·조리·배달 소요 시간 비교와 메뉴 추천 |
| 04-dining-order.png | 모의 주문 후 추적. 늦게 주문한 경우 추가 대기 표시 포함 |
| 05-access-candidates.png | 지원 조건·하차 후보 비교. 아래 후보는 스크롤로 확인 |
| 06-handoff.png | 모의 주변 상황과 위치 카드. 실제 카메라 미연결 |

[공식 스크린샷 규격](https://document.pleos.ai/en/docs/pleos-only/distribute/app-review-guide/review-app-registration-info)은 2560×1380 / 1700×1212 / 840×1212px를 허용합니다. 이 규격에 맞는 이미지가 실차 검증이나 앱 마켓 심사 통과를 의미하지 않습니다.

재촬영 도구: `scripts/capture-drive.py`. 연결 기기의 시리얼은 `adb devices`로 확인하고 `--serial`에 지정합니다. **prepare 후 반드시 restore를 실행**하세요. adb와 Python Pillow가 필요합니다.

## 웹 참고 시연 — web/

현재 소스의 화면을 Chromium에서 새로 촬영했습니다. 브라우저 뷰포트 1920×1200, 배율 1, PNG 원본으로 UI 요소 영역을 촬영하며 크기는 각 요소에 따라 다릅니다. 차량용 APK 이미지 제출 규격을 적용한 자료가 아닙니다.

- driver-view.png: 운전자 시점·차선 HUD
- meal-cabin-gps.png / meal-cabin-menu.png: 위치 탐색·식사 메뉴 추천
- access-comparison.png / access-arrival.png: 후보 비교·정차 후 하차 전 안전 확인
- river-surround-handoff.png: 합성 주변 시야·현장 인계 카드

`python scripts/capture-web.py`로 재현합니다. Playwright와 Chromium 설치 후 루트 HTTP 서버(8000)를 실행하세요.

## 이전 자료

루트 JPG, 01~06 트렁크 PNG, bridge/와 캡처설명.txt는 이전 기록입니다. 현재 README에서는 사용하지 않습니다. 웹 시연은 APK 실행 증거가 아니며 모든 지도·주문·카메라·차량 제어는 모의입니다.
