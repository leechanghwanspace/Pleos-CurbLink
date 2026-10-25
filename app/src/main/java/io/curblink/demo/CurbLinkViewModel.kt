package io.curblink.demo

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom

class CurbLinkViewModel : ViewModel() {
    private fun clock() = SystemClock.elapsedRealtime()
    private var payment = DemoPaymentAdapter()
    private var events = PartnerEventProcessor()
    private val mutable = MutableStateFlow(DemoState())
    val state = mutable.asStateFlow()
    init {
        reset()
        viewModelScope.launch {
            while (true) {
                delay(100)
                val now = clock()
                val s = mutable.value
                val timedOut = s.commandAt != null && now - s.commandAt >= 5000
                mutable.value = s.copy(now = now,
                    care = s.care.invalidate(s.vehicle, now),
                    vehicle = s.vehicle.copy(capturedAt = now),
                    sensor = if (s.sensorStreaming) s.sensor.copy(capturedAt = now) else s.sensor,
                    order = if (timedOut) s.order.copy(state = OrderState.READY) else s.order,
                    commandAt = if (timedOut) null else s.commandAt,
                    staff = if (timedOut) null else s.staff,
                    lastMessage = if (timedOut) "응답 시간 초과 · 자동 재시도 없음 · 재인증 필요" else s.lastMessage)
            }
        }
    }
    private fun note(message: String) {
        val s = mutable.value
        mutable.value = s.copy(lastMessage = message, logs = (listOf(message) + s.logs).take(12))
    }
    fun reset() {
        payment = DemoPaymentAdapter(); events = PartnerEventProcessor()
        val now = clock()
        mutable.value = DemoState(now = now,
            vehicle = VehicleSnapshot(capturedAt = now, oemControlAllowed = true),
            sensor = SensorSample(SensorVerdict.CLEAR_IN_VERIFIED_RANGE, now, "모의 스트림"))
    }
    fun gear(gear: Gear, speed: Float) {
        val s = mutable.value
        mutable.value = s.copy(vehicle = s.vehicle.copy(gear = gear, speedKmh = speed, capturedAt = clock()),
            staff = null, issuedOtp = null,
            care = s.care.invalidate(s.vehicle.copy(gear = gear, speedKmh = speed), clock()))
        note("차량 상태 변경 · 직원 인증 초기화")
    }
    fun restriction() { val s = mutable.value; mutable.value = s.copy(vehicle = s.vehicle.copy(uxRestricted = !s.vehicle.uxRestricted), staff = null, care = s.care.copy(customerReady = false)) }
    fun permission() { val s = mutable.value; mutable.value = s.copy(vehicle = s.vehicle.copy(oemControlAllowed = !s.vehicle.oemControlAllowed), staff = null) }
    fun sensor(verdict: SensorVerdict) {
        val s = mutable.value
        mutable.value = s.copy(sensor = SensorSample(verdict, clock(), "시연 입력"), sensorStreaming = true,
            staff = if (verdict != SensorVerdict.CLEAR_IN_VERIFIED_RANGE) null else s.staff)
        note("모의 센서: $verdict")
    }
    fun freezeSensor() { mutable.value = mutable.value.copy(sensorStreaming = false); note("센서 스트림 중단 · 3초 후 확인 불가") }
    fun confirmZone() {
        val s = mutable.value
        mutable.value = s.copy(zoneConfirmed = !s.zoneConfirmed, staff = null, issuedOtp = null,
            care = if (s.zoneConfirmed) s.care.copy(customerReady = false) else s.care)
        note("구역 번호 A-3 수동 확인 · UWB 실측 아님")
    }
    private fun canEditCare(s: DemoState): Boolean =
        !SafetyPolicy.locked(s.vehicle, clock()) &&
            s.order.state in listOf(OrderState.DRAFT, OrderState.PAID, OrderState.READY)

    fun serviceNeeds(needs: ServiceNeeds) {
        val s = mutable.value
        if (!canEditCare(s)) return
        mutable.value = s.copy(care = s.care.changeNeeds(needs))
        note("서비스 요청 변경 · 매장 응답과 고객 준비 재확인 필요")
    }
    fun assistanceResponse(response: AssistanceResponse) {
        val s = mutable.value
        if (s.order.state == OrderState.COMPLETE || s.order.state == OrderState.OPEN) return
        mutable.value = s.copy(care = s.care.merchantResponse(response))
        note("모의 매장 배려 요청 응답: $response")
    }
    fun customerReady() {
        val s = mutable.value
        if (!canEditCare(s)) return
        mutable.value = s.copy(care = s.care.confirmReady(s.vehicle, clock()))
    }
    fun pay() {
        val s = mutable.value
        if (SafetyPolicy.locked(s.vehicle, clock())) { note("정차 및 UX 제한 해제 후 결제 가능"); return }
        if (s.order.state != OrderState.DRAFT) { note("이미 결제됨 · 신규 결제 차단"); return }
        payment.pay(s.order, "${s.order.id}:${s.order.revision}:payment")
        mutable.value = s.copy(order = s.order.copy(state = OrderState.PAID), chargeCount = payment.chargeCount)
        note("모의 결제 완료 · 실제 CarPay 인증/출금 없음")
    }
    fun repeatPayment() {
        val s = mutable.value
        if (s.order.state == OrderState.DRAFT) { note("먼저 모의 결제를 완료하세요"); return }
        repeat(3) { payment.pay(s.order, "${s.order.id}:${s.order.revision}:payment") }
        mutable.value = s.copy(chargeCount = payment.chargeCount)
        note("동일 결제 3회 재전송 · 모의 청구 ${payment.chargeCount}회")
    }
    fun ready(duplicate: Boolean = false) {
        val s = mutable.value
        val event = PartnerEvent("ready-${s.order.revision}", s.order.id, s.order.revision, 1, clock() + 60000, "READY")
        val accepted = events.accept(event, s.order, clock())
        if (accepted) mutable.value = s.copy(order = s.order.copy(state = OrderState.READY))
        if (duplicate) events.accept(event, mutable.value.order, clock())
        note(if (accepted) "매장 준비 완료 이벤트 수신${if (duplicate) " · 중복 폐기" else ""}" else "중복 또는 현재 상태에 맞지 않는 이벤트 폐기")
    }
    fun voiceRequest() {
        val s = mutable.value
        if (s.order.state == OrderState.COMPLETE || s.order.state == OrderState.OPEN || s.order.state == OrderState.OPEN_REQUESTED) return
        mutable.value = s.copy(pendingMilk = true)
        note("음성 의도 주입(실제 Gleo 아님): 우유 추가 요청 · 정차 후 별도 주문 안내")
    }
    fun resolveModification() {
        val s = mutable.value
        if (SafetyPolicy.locked(s.vehicle, clock())) return
        mutable.value = s.copy(pendingMilk = false)
        note("추가 요청 취소 · 기존 결제/주문 유지 (변경 결제는 후속 범위)")
    }
    fun issueOtp() {
        val s = mutable.value
        if (s.order.state != OrderState.READY) { note("준비 완료 주문에서 코드 발급 가능"); return }
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        mutable.value = s.copy(issuedOtp = code, otpExpiresAt = clock() + 60000, otpAttempts = 0, otpInput = "", staff = null)
        note("매장 패널에서 60초 유효 코드 발급 · 5회 실패 시 재발급")
    }
    fun otpInput(text: String) { mutable.value = mutable.value.copy(otpInput = text.filter { it.isDigit() }.take(6)) }
    fun verifyOtp() {
        val s = mutable.value; val now = clock()
        if (SafetyPolicy.locked(s.vehicle, now)) return
        val ok = s.order.state == OrderState.READY && s.zoneConfirmed && s.issuedOtp != null &&
            s.otpInput == s.issuedOtp && s.otpExpiresAt > now && s.otpAttempts < 5
        mutable.value = s.copy(staff = if (ok) StaffGrant(s.order.id, s.order.revision, s.otpExpiresAt) else null,
            issuedOtp = if (ok) null else s.issuedOtp, otpAttempts = s.otpAttempts + 1, otpInput = "")
        note(if (ok) "주문별 직원 코드 확인 완료" else "코드/구역/유효시간 확인 실패")
    }
    fun approveOpen() {
        val s = mutable.value; val now = clock()
        // Re-evaluate live conditions at the click, not a cached enabled flag.
        if (!TailgateInterlockEvaluator.canRequest(s.vehicle, s.sensor, s.staff, s.order, now, s.zoneConfirmed, true, s.care)) {
            note("개방 요청 거부 · 조건을 다시 확인하세요"); return
        }
        mutable.value = s.copy(order = s.order.copy(state = OrderState.OPEN_REQUESTED), commandAt = now,
            commandCount = s.commandCount + 1, staff = null)
        note("⑦ 이번 요청 승인 완료 · 모의 개방 요청 전송 · 열림 응답 대기")
    }
    fun acknowledgeOpen() {
        val s = mutable.value
        if (s.order.state != OrderState.OPEN_REQUESTED) { note("진행 중인 개방 요청 없음"); return }
        val live = TailgateInterlockEvaluator.evaluate(s.vehicle, s.sensor,
            StaffGrant(s.order.id, s.order.revision, clock() + 1), s.order, clock())
        if (!live.prerequisitesMet || !s.zoneConfirmed || !s.care.permitsHandoff) { note("모의 차량 제어기 동작 거부 · 조건 변경"); return }
        mutable.value = s.copy(vehicle = s.vehicle.copy(tailgate = Tailgate.OPEN), order = s.order.copy(state = OrderState.OPEN), commandAt = null)
        note("모의 차량 열림 상태 수신 · 상품 적재 대기")
    }
    fun closeAndReceive() {
        val s = mutable.value
        if (s.order.state != OrderState.OPEN || SafetyPolicy.locked(s.vehicle, clock())) return
        mutable.value = s.copy(vehicle = s.vehicle.copy(tailgate = Tailgate.CLOSED), order = s.order.copy(state = OrderState.COMPLETE))
        note("모의 닫힘 상태 + 수령 확인 · 인계 완료")
    }
    fun runTests() { mutable.value = mutable.value.copy(testResults = DemoAcceptance.run()); note("순수 로직 8개 자가검사 · 실차/규정 인증 아님") }
}
