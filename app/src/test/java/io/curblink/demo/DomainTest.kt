package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val now = 10000L
    private val vehicle = VehicleSnapshot(capturedAt = now, oemControlAllowed = true)
    private val sensor = SensorSample(SensorVerdict.CLEAR_IN_VERIFIED_RANGE, now)
    private val order = Order(state = OrderState.READY)
    private val grant = StaffGrant(order.id, order.revision, now + 1000)
    private fun open(v: VehicleSnapshot = vehicle, s: SensorSample = sensor, g: StaffGrant? = grant,
                     o: Order = order, time: Long = now, zone: Boolean = true, approval: Boolean = true) =
        TailgateInterlockEvaluator.canRequest(v, s, g, o, time, zone, approval)

    @Test fun eightAcceptanceCases() { DemoAcceptance.run().forEach { assertTrue(it.first, it.second) } }
    @Test fun pAloneDoesNotOverrideUxRestrictions() { assertTrue(SafetyPolicy.locked(vehicle.copy(uxRestricted = true), now)); assertFalse(open(v = vehicle.copy(uxRestricted = true))) }
    @Test fun staleVehicleIsBlocked() { assertFalse(open(v = vehicle.copy(capturedAt = now - 1000))) }
    @Test fun futureSensorTimestampIsBlocked() { assertFalse(open(s = sensor.copy(capturedAt = now + 1))) }
    @Test fun sensorBoundaryIsExclusive() {
        assertTrue(open(s = sensor.copy(capturedAt = now - 2999)))
        assertFalse(open(s = sensor.copy(capturedAt = now - 3000)))
    }
    @Test fun invalidSpeedIsBlocked() { listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0.1f).forEach { assertFalse(open(v = vehicle.copy(speedKmh = it))) } }
    @Test fun unknownGearIsBlocked() { assertFalse(open(v = vehicle.copy(gear = Gear.UNKNOWN))) }
    @Test fun noApprovalNoCommand() { assertFalse(open(approval = false)); assertTrue(open()) }
    @Test fun noZoneNoCommand() { assertFalse(open(zone = false)) }
    @Test fun unknownTailgateNoCommand() { assertFalse(open(v = vehicle.copy(tailgate = Tailgate.UNKNOWN))) }
    @Test fun openRequestCannotBeRepeated() { assertFalse(open(o = order.copy(state = OrderState.OPEN_REQUESTED))) }
    @Test fun draftOrderCannotOpen() { assertFalse(open(o = order.copy(state = OrderState.DRAFT))) }
    @Test fun staffGrantBoundToOrderAndRevision() {
        assertFalse(open(g = grant.copy(orderId = "OTHER")))
        assertFalse(open(g = grant.copy(version = 2)))
        assertFalse(open(g = grant.copy(expiresAt = now)))
    }
    @Test fun paymentRetriesReuseResult() {
        val adapter = DemoPaymentAdapter()
        val first = adapter.pay(order, "key")
        assertEquals(first, adapter.pay(order, "key"))
        assertEquals(first, adapter.pay(order, "different-key-same-business-operation"))
        assertEquals(1, adapter.chargeCount)
    }
    @Test(expected = IllegalArgumentException::class) fun keyCannotBeReusedForDifferentAmount() {
        val adapter = DemoPaymentAdapter(); adapter.pay(order, "key")
        adapter.pay(order.copy(amountWon = 999), "key")
    }
    @Test fun remoteOpenEventsAreRejected() {
        val p = PartnerEventProcessor()
        assertFalse(p.accept(PartnerEvent("open", order.id, 1, 1, now + 1, "OPEN_TAILGATE"), order.copy(state = OrderState.PAID), now))
    }
    @Test fun wrongOrderExpiredAndOldEventsAreRejected() {
        val p = PartnerEventProcessor(); val paid = order.copy(state = OrderState.PAID)
        val e = PartnerEvent("e", order.id, 1, 2, now + 100, "READY")
        assertFalse(p.accept(e.copy(orderId = "OTHER"), paid, now))
        assertFalse(p.accept(e.copy(expiresAt = now), paid, now))
        assertFalse(p.accept(e.copy(revision = 2), paid, now))
        assertTrue(p.accept(e, paid, now))
        assertFalse(p.accept(e.copy(eventId = "old", version = 1), paid, now))
        assertFalse(p.accept(e, paid, now))
    }
}
