from pathlib import Path
import zipfile, shutil, hashlib, xml.etree.ElementTree as ET

root=Path('outputs/Pleos-CurbLink')
log=Path('work/build-apk.log').read_text(encoding='utf-8',errors='replace')
assert 'BUILD SUCCESSFUL' in log, 'Build not yet successful'
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
assert apk.is_file()
direct=Path('work/junit-direct.log').read_text(encoding='utf-8',errors='replace')
assert 'OK (17 tests)' in direct, 'JUnit direct execution did not pass'
stats={'tests':17,'failures':0,'errors':0,'skipped':0}
out=Path('outputs/Pleos-CurbLink-demo.apk')
shutil.copy2(apk,out)
(root/'apk').mkdir(exist_ok=True)
shutil.copy2(apk,root/'apk/Pleos-CurbLink-demo.apk')
sha=hashlib.sha256(out.read_bytes()).hexdigest()
report=f'''검증 기록 · Pleos CurbLink
기준일: 2026-10-25 (Asia/Seoul)

1. Android APK 빌드: 성공 (assembleDebug)
2. JUnitCore 직접 실행 시험: {stats['tests']}개, 실패 {stats['failures']}, 오류 {stats['errors']}, 건너뜀 {stats['skipped']}
3. APK: 디버그 서명 독립 시연 앱 / 실제 차량·결제 연동 없음
4. APK SHA-256: {sha}
5. 실제 Pleos AVD 실행·화면 검수·영상 녹화: 미실시
6. 실제 센서·차량·Gleo·CarPay 통합 시험: 미실시
7. OEM/NHTSA 적합성 인증 또는 실차 안전 검증 완료 주장: 없음

시험 범위: 주행/신호대기 잠금, UX 제한, 차량·센서 유효성, 비정상 차속,
기어 미확인, 명시 승인, 구역 확인, 테일게이트 상태, 중복 개방 요청,
주문별 직원 인증, 결제 키 충돌·멱등성, 원격 개방 이벤트 거부,
이벤트 만료·순서·다른 주문 차단.

8개 내장 자가검사는 도메인 로직의 빠른 확인용이다. 전체 UI 여정과 실차 시험을 대체하지 않는다.
실제 에뮬레이터에서 README 순서로 동작을 확인하고 제공 대본에 맞춰 촬영해야 한다.

빌드 환경: JDK17 / Gradle8.9 / AGP8.7.3 / Kotlin2.0.21 / SDK35.
APK 빌드는 IPv4 우선·no-daemon·Kotlin in-process로 실행했다.
Gradle testDebugUnitTest 실행기는 Windows 내부 loopback 오류로 실패했다.
동일하게 컴파일된 DomainTest를 JUnitCore로 직접 실행하여 17개 모두 통과했다.
APK 서명은 개발용이다. Play/Pleos 배포용 서명·등록·권한 심사는 별도다.
'''
(root/'docs/검증결과.txt').write_text(report,encoding='utf-8')
evidence=root/'docs/test-results'
evidence.mkdir(exist_ok=True)
shutil.copy2('work/junit-direct.log',evidence/'junit-direct.log')
with zipfile.ZipFile('outputs/Pleos-CurbLink-개발패키지.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(root.rglob('*')):
        rel=p.relative_to(root)
        if not p.is_file() or any(x in {'.gradle','.kotlin','build','.idea'} for x in rel.parts):continue
        if p.name=='local.properties' or p.suffix in {'.jks','.keystore'}:continue
        z.write(p,Path('Pleos-CurbLink')/rel)
print(stats)
print('APK bytes:',out.stat().st_size)
print('SHA256:',sha)
print('Package created')
