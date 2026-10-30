> 현재 개발 업데이트: **0.5.1**. 사진·GPS·하차 ETA·주문 초기화 수정은 [UPDATE_0_5_1.md](UPDATE_0_5_1.md)를 확인하세요. main 제품 승격은 아직 진행하지 않습니다.

# 브랜치 사용 안내

2026-10-30부터 현재 사용하는 브랜치는 다음 네 개입니다. 기본 브랜치 `main`은 검증을 마친 최종본, 개발 변경의 합류점은 `integration`입니다.

| 브랜치 | 목적 | 주요 위치 | 실행 |
|---|---|---|---|
| `main` | 최종 검증을 통과한 제출·배포 기준 | README의 최종 APK·증거 | 해당 버전 설치 안내 |
| `simulation` | 도로·주행·차량 내부·주변 시야 웹 시뮬레이션 | `simulator/`, `captures/web/` | `scripts/start-experience.ps1 -Mode Simulation` |
| `integration` | 시뮬레이션과 네이티브 APK 통합 검증 | `simulator/` + `app/` + `release/` | `scripts/start-experience.ps1 -Mode Integrated -Serial SERIAL` |
| `apk` | 실제 차량 앱 UI·로직·APK 단독 실행 | `app/`, `release/` | `scripts/start-experience.ps1 -Mode Apk -Serial SERIAL` |

`start-experience.ps1`의 `-Check`는 설치·서버 실행 없이 브랜치와 실행 전제조건을 확인합니다. `SERIAL`은 `adb devices`에서 확인합니다. 실제 실행 때만 `-OpenBrowser`를 붙이면 브라우저를 엽니다. 에뮬레이터는 자동 부팅하지 않습니다.

## 코드가 서로 달라지지 않게 하는 규칙

- 브랜치마다 다른 제품 복사본을 만들지 않습니다. 공통 모델·테스트·소스는 보존하고 **담당 영역과 실행 흐름**을 구분합니다. `apk`에도 회귀시험용 시뮬레이터 소스가 남지만 APK에는 포함되지 않습니다.
- 공통 차선·ETA·주문·하차 규칙은 통합 브랜치에서 정리합니다. 시뮬레이션 전용/앱 전용 변경은 해당 브랜치에서 작업한 뒤 통합 브랜치로 합류시켜 검증합니다.
- 통합한 변경은 두 작업 브랜치에도 반영합니다. 공통 변경을 세 브랜치에서 따로 재구현하거나, 같은 커밋을 각자 cherry-pick하지 않습니다.
- `main`은 통합 브랜치의 빌드·규칙 시험뿐 아니라 대상 Pleos에서 실제 UI·음성·전체 여정·캡처/영상 검증까지 끝낸 버전을 명시적으로 승격합니다. 자동 병합·강제 푸시는 하지 않습니다.
- 릴리스 태그는 실제 검증 후 `v0.x.y`로 붙입니다. 미검증 0.5에 최종 릴리스 태그를 붙이지 않습니다.

## 현재 기준

- `main`: 기존 0.2 최종 기준선 유지. 이번 정리는 안내 문서만 추가합니다.
- 세 작업 브랜치: 동일한 0.5 공통 기준선에서 출발하며, 시뮬레이션/앱별로 달라진 부분은 앞으로 통합 검증합니다. 같은 커밋에서 시작한다는 것은 실제 시뮬레이션-APK 실시간 연동이 완료되었다는 뜻이 아닙니다.
- 0.5: 네이티브 APK/시험 APK 빌드, JVM 69개 시험, lint 오류0, APK 서명·정렬·분리 검사 통과. 실제 새 APK의 Pleos 실행·캡처·영상은 집 PC에서 검증할 항목입니다.
- `release/studio/`와 단일 HTML 목업은 **웹 UI 디자인 목업**입니다. README의 도로·차량·주변 시야 시뮬레이터를 대체하지 않습니다.
- 시뮬레이션 개발의 기준은 `captures/web/driver-view.png`, `meal-cabin-*.png`, `access-*.png`, `river-surround-handoff.png`에 담긴 공간과 여정입니다. 해당 장면은 `simulator/`에서 유지·개선하며, GPU 시뮬레이션 실행은 집 PC에서 진행합니다.

## 기존 브랜치 대응표

| 기존 브랜치 | 지금 볼 위치 | 처리 |
|---|---|---|
| `automation/collaboration-setup` | `main` 이력 | 이미 main의 조상. 과거 이력 보존 |
| `automation/care-pickup` | `main` 이력 | 이미 main의 조상. 과거 이력 보존 |
| `feature/simulator-bridge` | `main` 이력 / 통합 브랜치 | 이미 main의 조상. 과거 브리지 개발 이력 보존 |
| `codex/simulation-app-redesign` | `integration` | 0.4 이전 기준선 보존. 신규 작업은 통합 브랜치 사용 |
| 로컬 `codex/contest-experience` | `integration` | 미커밋 0.5를 보존한 뒤 현재 이름으로 정리 |

기존 브랜치·원격 기록은 삭제하거나 덮어쓰지 않습니다. 새 작업은 위 네 개 중 목적에 맞는 곳에서 시작하면 됩니다.

## 집 PC에서 받기

```powershell
git fetch origin
git switch integration
# 아직 로컬 브랜치가 없다면:
# git switch --track origin/integration
```

미커밋 변경이 있는 PC에서는 먼저 해당 변경을 보존하고 전환합니다. `reset --hard`, `clean`, 자동 stash는 사용하지 않습니다.

```powershell
# 웹 시뮬레이션만
./scripts/start-experience.ps1 -Mode Simulation -OpenBrowser

# APK만: 연결된 Pleos 장치가 필요
./scripts/start-experience.ps1 -Mode Apk -Serial emulator-5554

# 함께 실행: APK는 Pleos에서, 웹 시뮬레이션은 브라우저에서
./scripts/start-experience.ps1 -Mode Integrated -Serial emulator-5554 -OpenBrowser
```

함께 실행한다는 것은 **동시 실행**입니다. 실제 앱-시뮬레이션 상태 동기화·실시간 브리지 제공은 별도 개발·시험 대상이며, 웹 목업을 실제 APK 실행 화면이라고 표시하지 않습니다.
