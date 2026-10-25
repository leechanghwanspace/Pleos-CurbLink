package io.curblink.demo

object SafetyPolicy {
    // Demonstration thresholds, not validated vehicle safety limits.
    const val SENSOR_TTL = 3000L
    const val VEHICLE_TTL = 1000L
    fun fresh(now: Long, captured: Long, ttl: Long) = now >= captured && now - captured < ttl
    fun locked(v: VehicleSnapshot, now: Long): Boolean =
        !fresh(now, v.capturedAt, VEHICLE_TTL) || v.uxRestricted ||
            v.gear != Gear.P || !v.speedKmh.isFinite() || v.speedKmh != 0f
    fun effectiveSensor(s: SensorSample, now: Long): SensorVerdict =
        if (fresh(now, s.capturedAt, SENSOR_TTL)) s.verdict else SensorVerdict.UNVERIFIED
}

object TailgateInterlockEvaluator {
    fun evaluate(v: VehicleSnapshot, s: SensorSample, grant: StaffGrant?, order: Order, now: Long) =
        InterlockStatus(listOf(
            Check("① 차속 0 · 최신 차량 상태", v.speedKmh.isFinite() && v.speedKmh == 0f && SafetyPolicy.fresh(now, v.capturedAt, SafetyPolicy.VEHICLE_TTL)),
            Check("② P단 · UX 제한 해제", v.gear == Gear.P && !v.uxRestricted),
            Check("③ 센서 데이터 유효", SafetyPolicy.fresh(now, s.capturedAt, SafetyPolicy.SENSOR_TTL)),
            Check("④ 확인 범위 내 장애물 미검출", SafetyPolicy.effectiveSensor(s, now) == SensorVerdict.CLEAR_IN_VERIFIED_RANGE),
            Check("⑤ 주문별 직원 인증 유효", grant != null && grant.orderId == order.id && grant.version == order.revision && now < grant.expiresAt),
            Check("⑥ OEM 제어 허용", v.oemControlAllowed)
        ))
    fun canRequest(v: VehicleSnapshot, s: SensorSample, grant: StaffGrant?, order: Order,
                   now: Long, zoneConfirmed: Boolean, explicitApproval: Boolean): Boolean =
        explicitApproval && zoneConfirmed && order.state == OrderState.READY && v.tailgate == Tailgate.CLOSED &&
            evaluate(v, s, grant, order, now).prerequisitesMet
}

// Order status transport simulation, not a real MQTT client. Remote events cannot open a tailgate.
class PartnerEventProcessor {
    private val seen = mutableSetOf<String>()
    private val versions = mutableMapOf<String, Int>()
    fun accept(e: PartnerEvent, order: Order, now: Long): Boolean {
        if (e.orderId != order.id || e.revision != order.revision || e.expiresAt <= now ||
            e.type != "READY" || order.state != OrderState.PAID || e.eventId in seen) return false
        val stream = "${e.orderId}:${e.revision}"
        if (e.version <= (versions[stream] ?: 0)) return false
        seen.add(e.eventId)
        versions[stream] = e.version
        return true
    }
}

class DemoPaymentAdapter : PaymentAdapter {
    private val receipts = mutableMapOf<String, PaymentReceipt>()
    private val paidRevisions = mutableMapOf<String, PaymentReceipt>()
    var chargeCount = 0
        private set
    override fun pay(order: Order, key: String): PaymentReceipt {
        require(order.amountWon > 0)
        receipts[key]?.let {
            require(it.orderId == order.id && it.revision == order.revision && it.amountWon == order.amountWon) { "동일 키의 금액/주문 충돌" }
            return it
        }
        val businessKey = "${order.id}:${order.revision}"
        paidRevisions[businessKey]?.let {
            require(it.amountWon == order.amountWon) { "결제 확정 버전의 금액 변경" }
            return it
        }
        chargeCount++
        return PaymentReceipt(key, order.id, order.revision, order.amountWon, "SIM-PAY-$chargeCount").also {
            receipts[key] = it
            paidRevisions[businessKey] = it
        }
    }
}

object DemoAcceptance {
    fun run(): List<Pair<String, Boolean>> {
        val now = 10000L
        val v = VehicleSnapshot(capturedAt = now, oemControlAllowed = true)
        val s = SensorSample(SensorVerdict.CLEAR_IN_VERIFIED_RANGE, now)
        val order = Order(state = OrderState.READY)
        val grant = StaffGrant(order.id, order.revision, now + 10000)
        fun allowed(vehicle: VehicleSnapshot = v, sensor: SensorSample = s, at: Long = now, approval: Boolean = true) =
            TailgateInterlockEvaluator.canRequest(vehicle, sensor, grant, order, at, true, approval)
        val pay = DemoPaymentAdapter()
        repeat(3) { pay.pay(order, "stable-key") }
        val events = PartnerEventProcessor()
        val e = PartnerEvent("e1", order.id, order.revision, 1, now + 10000, "READY")
        val paid = order.copy(state = OrderState.PAID)
        val eventOk = events.accept(e, paid, now) && !events.accept(e, paid, now)
        return listOf(
            "01 주행 중 잠금" to SafetyPolicy.locked(v.copy(gear = Gear.D, speedKmh = 40f), now),
            "02 D단·0km/h 잠금 유지" to SafetyPolicy.locked(v.copy(gear = Gear.D), now),
            "03 P단 해제 후 개방 거부" to !allowed(v.copy(gear = Gear.R)),
            "04 센서 3초 경계 만료" to !allowed(v.copy(capturedAt = now + 3000), at = now + 3000),
            "05 장애물 진입 시 거부" to !allowed(sensor = s.copy(verdict = SensorVerdict.OBSTACLE_DETECTED)),
            "06 OEM 권한 거부" to !allowed(v.copy(oemControlAllowed = false)),
            "07 중복 결제·이벤트 제거" to (pay.chargeCount == 1 && eventOk),
            "08 승인 전 거부·승인 후 요청 허용" to (!allowed(approval = false) && allowed())
        )
    }
}
