package io.curblink.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CurbLinkMainScreen(vm: CurbLinkViewModel) {
    val s by vm.state.collectAsState()
    val locked = SafetyPolicy.locked(s.vehicle, s.now)
    val interlock = TailgateInterlockEvaluator.evaluate(s.vehicle, s.sensor, s.staff, s.order, s.now)
    val canApprove = interlock.prerequisitesMet && s.zoneConfirmed && s.order.state == OrderState.READY && s.vehicle.tailgate == Tailgate.CLOSED
    var evaluator by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CurbLink", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
            Text("차량에서 이어지는 픽업  |  독립 시연 APK · 모든 외부 연동은 모의", fontSize = 14.sp)
            Text("${s.vehicle.gear}  ·  ${s.vehicle.speedKmh.toInt()} km/h  ·  ${if (locked) "운전자 조작 잠금" else "정차 모드"}  ·  ${s.order.state}")
            if (locked) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                    Text("운전에 집중해 주세요", fontSize = 24.sp)
                    Text("상품 탐색·결제·직원 코드·개방 조작이 잠겼습니다.")
                    Text("승인된 Gleo 연동은 후속 범위입니다. 아래 운영자 패널에서 음성 의도만 주입할 수 있습니다.")
                } }
            } else {
                BoxWithConstraints {
                    val wide = maxWidth >= 900.dp
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Panel("01  주문", Modifier.weight(1f)) { OrderPanel(s, vm) }
                        Panel("02  픽업 공간", Modifier.weight(1f)) { SpacePanel(s) }
                        Panel("03  인계", Modifier.weight(1.2f)) { HandoffPanel(s, vm, interlock, canApprove) }
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Panel("01  주문") { OrderPanel(s, vm) }
                        Panel("02  픽업 공간") { SpacePanel(s) }
                        Panel("03  인계") { HandoffPanel(s, vm, interlock, canApprove) }
                    }
                }
            }
            Text(s.lastMessage, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = { evaluator = !evaluator }) { Text(if (evaluator) "운영자 패널 닫기" else "시연 운영자 패널 열기") }
            if (evaluator) Panel("시험 장치 · 운전자용 제품 화면 아님") {
                Text("아래 조작은 가상 차량/매장 입력입니다. 실제 주행 중 사용하는 기능이 아닙니다.", color = Color(0xFFFFCB88))
                Controls {
                    OutlinedButton(onClick = { vm.gear(Gear.P, 0f) }) { Text("P · 0") }
                    OutlinedButton(onClick = { vm.gear(Gear.D, 40f) }) { Text("D · 40") }
                    OutlinedButton(onClick = { vm.gear(Gear.D, 0f) }) { Text("D · 0") }
                    OutlinedButton(onClick = { vm.gear(Gear.R, 0f) }) { Text("R · 0") }
                    OutlinedButton(onClick = vm::restriction) { Text("UX 제한 전환") }
                    OutlinedButton(onClick = vm::permission) { Text("OEM 허용 전환") }
                }
                Controls {
                    OutlinedButton(onClick = { vm.sensor(SensorVerdict.CLEAR_IN_VERIFIED_RANGE) }) { Text("미검출 스트림") }
                    OutlinedButton(onClick = { vm.sensor(SensorVerdict.OBSTACLE_DETECTED) }) { Text("장애물") }
                    OutlinedButton(onClick = { vm.sensor(SensorVerdict.UNVERIFIED) }) { Text("확인 불가") }
                    OutlinedButton(onClick = vm::freezeSensor) { Text("스트림 중단") }
                    OutlinedButton(onClick = vm::voiceRequest) { Text("음성 의도: 우유 추가") }
                }
                Controls {
                    OutlinedButton(onClick = vm::confirmZone) { Text("구역 확인 전환") }
                    OutlinedButton(onClick = { vm.ready() }) { Text("매장 준비 완료") }
                    OutlinedButton(onClick = { vm.ready(true) }) { Text("중복 이벤트") }
                    OutlinedButton(onClick = vm::repeatPayment) { Text("중복 결제 재시도") }
                    OutlinedButton(onClick = vm::issueOtp) { Text("직원 코드 발급") }
                }
                Text("직원 단말 모의 코드: ${s.issuedOtp ?: "미발급/소비됨"}  ·  모의 청구 ${s.chargeCount}회  ·  개방 요청 ${s.commandCount}회")
                Controls {
                    OutlinedButton(onClick = vm::acknowledgeOpen) { Text("차량 열림 응답") }
                    OutlinedButton(onClick = vm::closeAndReceive) { Text("닫힘 + 수령 확인") }
                    OutlinedButton(onClick = vm::runTests) { Text("8개 로직 자가검사") }
                    OutlinedButton(onClick = vm::reset) { Text("시연 초기화") }
                }
                s.testResults.forEach { (name, pass) -> Text("${if (pass) "PASS" else "FAIL"}  $name") }
                Text("최근 이벤트", color = MaterialTheme.colorScheme.primary)
                s.logs.take(6).forEach { Text(it, fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary); content()
    } }
}
@Composable private fun Controls(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
@Composable private fun OrderPanel(s: DemoState, vm: CurbLinkViewModel) {
    Text("퇴근길 장보기", fontSize = 22.sp)
    Text("제휴 마트 DEMO · 픽업 구역 A-3")
    Text("우유 · 채소 · 식료품\n총 24,000원")
    Text("주문 ${s.order.id}")
    Button(onClick = vm::pay, enabled = s.order.state == OrderState.DRAFT) { Text("모의 결제 승인") }
    if (s.pendingMilk) {
        Text("음성 변경 요청 보류: 우유 추가\nMVP에서는 변경 결제를 실행하지 않습니다.")
        OutlinedButton(onClick = vm::resolveModification) { Text("추가 요청 취소 · 기존 주문 유지") }
    }
    if (s.order.state == OrderState.COMPLETE) Text("수령 완료\nDEMO 영수증 · 실제 출금 없음", color = MaterialTheme.colorScheme.primary)
}
@Composable private fun SpacePanel(s: DemoState) {
    val verdict = SafetyPolicy.effectiveSensor(s.sensor, s.now)
    Text(if (s.zoneConfirmed) "A-3 구역 확인됨" else "지정 구역 확인 대기")
    Text("측위: 구역 번호 수동 확인\nGPS/UWB 실측 없음", fontSize = 13.sp)
    Box(Modifier.fillMaxWidth().height(90.dp).background(Color(0xFF101A27)).padding(12.dp)) {
        Text("차량 후방 작업 공간\n센서 시뮬레이션 · 카메라 영상 아님")
    }
    Text(when (verdict) {
        SensorVerdict.CLEAR_IN_VERIFIED_RANGE -> "확인 범위 내 장애물 미검출"
        SensorVerdict.OBSTACLE_DETECTED -> "장애물 감지 · 개방 보류"
        SensorVerdict.UNVERIFIED -> "확인 불가 · 개방 보류"
    }, color = if (verdict == SensorVerdict.CLEAR_IN_VERIFIED_RANGE) MaterialTheme.colorScheme.primary else Color(0xFFFFCB88))
    Text("데이터 경과: ${((s.now - s.sensor.capturedAt).coerceAtLeast(0) / 100) / 10f}초\n3초는 시연용 만료 설정입니다.", fontSize = 12.sp)
}
@Composable private fun HandoffPanel(s: DemoState, vm: CurbLinkViewModel, checks: InterlockStatus, enabled: Boolean) {
    Text("후방을 직접 확인한 후 승인하세요.")
    OutlinedTextField(value = s.otpInput, onValueChange = vm::otpInput, label = { Text("직원 일회용 코드") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = vm::verifyOtp) { Text("코드 확인") }
    checks.checks.forEach { Text("${if (it.passed) "✓" else "○"} ${it.label}", fontSize = 13.sp) }
    Text("⑦ 이번 개방 요청에 대한 사용자 승인", fontSize = 13.sp)
    Button(onClick = vm::approveOpen, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("확인 후 개방 요청 승인") }
    Text("테일게이트: ${s.vehicle.tailgate}\n요청 전송은 실제 열림 확인과 다릅니다.", fontSize = 12.sp)
}
