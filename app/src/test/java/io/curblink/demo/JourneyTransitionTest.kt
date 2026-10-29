package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class JourneyTransitionTest {
    private fun approved() = MobilityState(gear=VehicleGear.P, phase = DrivePhase.ARRIVED, accessTrip = true,
        bay = MobilityCatalog.bays[1], spaceChecked = true, rampReady = true, staffAccepted = true,
        accessApproved = true, exitProgress = 35)

    @Test fun negativeVoiceNeverConfirms() {
        listOf("주문하지 마", "주문 취소", "주문하지 말아줘", "주문 안 해", "주문 보류", "주문해줘 아니 주문하지마").forEach {
            assertEquals(it, MealVoiceIntent.CANCEL, JourneyTransitions.voiceIntent(it))
        }
    }
    @Test fun questionsAndAmbiguityNeverConfirm() {
        listOf("주문 금액 알려줘", "주문 가격", "주문해도 돼", "주문할까").forEach {
            assertEquals(it, MealVoiceIntent.QUERY, JourneyTransitions.voiceIntent(it))
        }
        listOf("주문", "네", "아마 주문해줘", "주문하면 안 되나").forEach {
            assertNotEquals(MealVoiceIntent.CONFIRM, JourneyTransitions.voiceIntent(it))
        }
        assertEquals(MealVoiceIntent.CONFIRM, JourneyTransitions.voiceIntent("주문해 줘"))
    }
    @Test fun secondTripClearsEveryApprovalButKeepsNeeds() {
        val old = approved().copy(locationShared = true, received = true, exitProgress = 100)
        val next = JourneyTransitions.newTrip(old, MobilityCatalog.river)
        assertNotEquals(old.tripId, next.tripId); assertEquals(old.needs, next.needs)
        assertFalse(next.accessApproved); assertFalse(next.spaceChecked); assertFalse(next.rampReady)
        assertFalse(next.staffAccepted); assertFalse(next.locationShared); assertFalse(next.received)
        assertEquals(0, next.exitProgress); assertNull(next.bay)
    }
    @Test fun obstacleAfterApprovalHoldsImmediatelyAndRecoveryNeedsNewApproval() {
        val held = JourneyTransitions.observe(approved(), "obstacle", true)
        assertTrue(held.exitHeld); assertFalse(held.accessApproved)
        assertEquals(35, SimulationDriver.advance(held, 60).exitProgress)
        val clear = JourneyTransitions.observe(held, "obstacle", false)
        assertFalse(clear.accessApproved); assertFalse(clear.spaceChecked)
        val checked = JourneyTransitions.observe(clear, "space", true)
        assertTrue(MobilityRules.canExit(checked))
        assertEquals(35, SimulationDriver.advance(checked, 30).exitProgress)
    }
    @Test fun equipmentAndVehicleLossInvalidateApproval() {
        assertFalse(JourneyTransitions.observe(approved(), "ramp", false).accessApproved)
        assertFalse(JourneyTransitions.observe(approved(), "staff", false).accessApproved)
        val held = JourneyTransitions.enforce(approved().copy(vehicleValid = false))
        assertFalse(held.accessApproved)
        assertFalse(MobilityRules.canExit(held.copy(vehicleValid = true)))
    }
    @Test fun destinationOrCardVersionChangesRevokeConfirmation() {
        val s = MobilityState(gear=VehicleGear.P)
        val confirmed = s.copy(locationShared = true, confirmedLocation = s.locationKey)
        assertTrue(confirmed.canShareLocation)
        assertFalse(confirmed.copy(destination = MobilityCatalog.river).canShareLocation)
        assertFalse(confirmed.copy(locationVersion = 1).canShareLocation)
        assertFalse(confirmed.copy(phase = DrivePhase.DRIVING).canShareLocation)
    }
    @Test fun processRestoreKeepsOrderButDropsSafetyAndLocationApproval() {
        val menu = MobilityCatalog.menus.first()
        val s = approved().copy(order = DiningOrder(menu, 0, 120, 0), selectedMenu = menu,
            mealStep = DiningStep.TRACK, elapsed = 180, locationShared = true)
        val restored = JourneyStore.restore(JourneyStore.encode(s), MobilityState())
        assertEquals(s.tripId, restored.tripId); assertEquals(s.order, restored.order)
        assertEquals(s.elapsed, restored.elapsed); assertEquals(DiningStep.TRACK, restored.mealStep)
        assertTrue(restored.paused); assertFalse(restored.vehicleValid); assertFalse(restored.gpsReady)
        assertFalse(restored.accessApproved); assertFalse(restored.spaceChecked)
        assertFalse(restored.rampReady); assertFalse(restored.staffAccepted); assertFalse(restored.locationShared)
    }
    @Test fun corruptOrUnsupportedSnapshotDoesNotRestorePartialState() {
        val base = MobilityState()
        for (raw in listOf("broken", "version=99", JourneyStore.encode(base).replace("remaining=1080", "remaining=-1"))) {
            val recovered = JourneyStore.restore(raw, base)
            assertNull(recovered.order); assertEquals(base.tripId, recovered.tripId)
            assertFalse(recovered.accessApproved)
        }
    }
    @Test fun roleCannotGrantMovingTouchAccess() {
        val s = MobilityState(phase = DrivePhase.DRIVING, role = CabinRole.PASSENGER)
        assertFalse(s.canEdit)
        assertFalse(s.copy(phase = DrivePhase.ARRIVED, vehicleValid = false).canEdit)
    }
    @Test fun monotonicClockAccountsForDelayPauseAndFraction() {
        val clock = DemoClock()
        assertEquals(0, clock.step(0, true)); assertEquals(60, clock.step(2000, true))
        assertEquals(900, clock.step(32000, true)); assertEquals(0, clock.step(34000, false))
        assertEquals(0, clock.step(64000, false)); assertEquals(15, clock.step(64500, true))
        assertEquals(0, clock.step(64501, true)); assertEquals(1, clock.step(64534, true))
    }
    @Test fun bigTimeJumpAndPauseRespectTheSameTransitionRules() {
        val s = MobilityState(phase = DrivePhase.DRIVING, remaining = 900)
        assertEquals(DrivePhase.ARRIVED, SimulationDriver.advance(s, 901).phase)
        assertEquals(s, SimulationDriver.advance(s.copy(paused = true), 900).copy(paused = false))
        assertEquals(100, SimulationDriver.advance(approved(), 900).exitProgress)
    }
}
