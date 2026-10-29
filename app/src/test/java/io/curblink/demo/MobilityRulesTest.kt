package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class MobilityRulesTest {
    @Test fun nearestSuitableBayIsA2() { assertEquals("A2", MobilityRules.assess(AccessPreferences()).first { it.eligible }.bay.id) }
    @Test fun widerOrGentlerNeedsSelectA3() {
        assertEquals("A3", MobilityRules.assess(AccessPreferences(wide = true)).first { it.eligible }.bay.id)
        assertEquals("A3", MobilityRules.assess(AccessPreferences(gentle = true)).first { it.eligible }.bay.id)
    }
    @Test fun nearestDoorIsExcludedAndUnknownAvailabilityIsNotRecommended() {
        assertFalse(MobilityRules.assess(AccessPreferences()).first { it.bay.id == "DOOR" }.eligible)
        assertTrue(MobilityRules.assess(AccessPreferences(), MobilityCatalog.bays.map { it.copy(available = false) }).none { it.eligible })
    }
    private fun review() = MobilityState(phase = DrivePhase.DRIVING, remaining = 900, elapsed = 180, selectedMenu = MobilityCatalog.menus.first(), mealStep = DiningStep.REVIEW)
    @Test fun drivingDriverCannotConfirmWithTouch() { assertNull(MobilityRules.confirmOrder(review(), false)); assertNotNull(MobilityRules.confirmOrder(review(), true)) }
    @Test fun passengerCanConfirmWhileDrivingAndLateMenuIsExplicit() {
        val s = review().copy(role = CabinRole.PASSENGER, selectedMenu = MobilityCatalog.menus.last())
        val order = MobilityRules.confirmOrder(s, false)!!
        assertEquals(600, order.lateAtConfirmation); assertEquals(s.elapsed, order.dispatchAt)
    }
    @Test fun orderDispatchMatchesArrivalAndDuplicateIsRejected() {
        val s = review(); val order = MobilityRules.confirmOrder(s, true)!!
        assertEquals(s.elapsed + s.remaining, order.dispatchAt + order.menu.lead)
        assertNull(MobilityRules.confirmOrder(s.copy(order = order), true))
    }
    @Test fun invalidVehicleBlocksAllOrderChannels() { assertNull(MobilityRules.confirmOrder(review().copy(vehicleValid = false), true)) }
    private fun exit() = MobilityState(phase = DrivePhase.ARRIVED, accessTrip = true, bay = MobilityCatalog.bays[1], spaceChecked = true, rampReady = true, staffAccepted = true)
    @Test fun exitRequiresCurrentConditionsAndCannotRepeat() {
        assertTrue(MobilityRules.canExit(exit()))
        assertFalse(MobilityRules.canExit(exit().copy(obstacle = true)))
        assertFalse(MobilityRules.canExit(exit().copy(vehicleValid = false)))
        assertFalse(MobilityRules.canExit(exit().copy(phase = DrivePhase.DRIVING)))
        assertFalse(MobilityRules.canExit(exit().copy(staffAccepted = false)))
        assertFalse(MobilityRules.canExit(exit().copy(accessApproved = true)))
    }
    @Test fun walkingSupportDoesNotRequireUnrequestedEquipment() {
        assertTrue(MobilityRules.canExit(exit().copy(needs = AccessPreferences(wheelchair = false, staff = false), rampReady = false, staffAccepted = false)))
    }
    @Test fun foodReadinessUsesActualSelectedLeadTime() {
        val order = DiningOrder(MobilityCatalog.menus[1], 0, 120, 0)
        assertFalse(MobilityState(order = order, elapsed = 719).foodReady)
        assertTrue(MobilityState(order = order, elapsed = 720).foodReady)
    }
    @Test fun curbAndParkingApproachesUseDifferentDistances() {
        val s = MobilityState(phase = DrivePhase.DRIVING, remaining = 50, total = 1080)
        assertEquals(4, MobilityRules.lane(s).first)
        assertEquals(2, MobilityRules.lane(s.copy(purpose = ArrivalPurpose.PARKING)).first)
        assertEquals(4, MobilityRules.lane(s.copy(purpose = ArrivalPurpose.PARKING, remaining = 20)).first)
    }
}
