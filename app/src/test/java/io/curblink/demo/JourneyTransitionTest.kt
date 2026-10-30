package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class JourneyTransitionTest {
    @Test fun failuresRetainOrderAndPhotosButExpiredGpsCannotBeShared() {
        val order=DiningOrder(MobilityCatalog.menus.first(),0,120,0)
        val base=MobilityState(gear=VehicleGear.P,gpsReady=true,lat=37.5,lon=126.9,
            order=order,selectedMenu=order.menu,handoffPhotoUris=listOf("content://front"))
        val confirmed=base.copy(locationShared=true,confirmedLocation=base.locationKey)
        val expired=JourneyTransitions.locationUnavailable(confirmed)
        assertFalse(expired.gpsReady);assertFalse(expired.canShareLocation);assertNull(expired.lat)
        for(result in listOf(expired,JourneyTransitions.voiceUnavailable(confirmed),JourneyTransitions.shareUnavailable(confirmed))){
            assertEquals(order,result.order);assertEquals(base.handoffPhotoUris,result.handoffPhotoUris)
            assertEquals(base.selectedMenu,result.selectedMenu)
        }
        assertTrue(JourneyTransitions.shareUnavailable(confirmed).canShareLocation)
        assertFalse(JourneyTransitions.voiceUnavailable(confirmed.copy(gear=VehicleGear.D)).canEdit)
    }
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
            mealStep = DiningStep.TRACK, elapsed = 180, locationShared = true, handoffPhotoUris=listOf("content://private-photo"))
        val restored = JourneyStore.restore(JourneyStore.encode(s), MobilityState())
        assertEquals(s.tripId, restored.tripId); assertEquals(s.order, restored.order)
        assertEquals(s.elapsed, restored.elapsed); assertEquals(DiningStep.TRACK, restored.mealStep)
        assertTrue(restored.handoffPhotoUris.isEmpty()); assertTrue(restored.paused); assertFalse(restored.vehicleValid); assertFalse(restored.gpsReady)
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
    @Test fun appDestinationOwnershipSurvivesRestoreAndLegacyMigration() {
        val state=MobilityState(destination=MobilityCatalog.culture,appDestinationSelected=true)
        val encoded=JourneyStore.encode(state)
        assertTrue(JourneyStore.restore(encoded,MobilityState()).appDestinationSelected)
        assertEquals("CULTURE",JourneyStore.restore(encoded,MobilityState()).destination.id)
        val legacy=encoded.lines().filterNot{it.startsWith("appDestinationSelected=")}.joinToString("\n")
        assertTrue(JourneyStore.restore(legacy,MobilityState()).appDestinationSelected)
    }
    @Test fun gpsRefreshKeepsAttachmentsButRevokesLocationApproval() {
        val state=MobilityState(gear=VehicleGear.P)
        val attached=JourneyTransitions.attachPhotos(state,listOf("content://front","content://rear"),state.tripId)
        val confirmed=attached.copy(locationShared=true,confirmedLocation=attached.locationKey)
        val refreshed=JourneyTransitions.enforce(confirmed.copy(lat=37.51,lon=126.91,locationVersion=1))
        assertEquals(attached.handoffPhotoUris,refreshed.handoffPhotoUris)
        assertFalse(refreshed.canShareLocation)
    }
    @Test fun cancellingPhotoPickerPreservesAttachmentsAndConfirmation() {
        val state=MobilityState(gear=VehicleGear.P,handoffPhotoUris=listOf("content://front"))
        val confirmed=state.copy(locationShared=true,confirmedLocation=state.locationKey)
        val result=JourneyTransitions.attachPhotos(confirmed,emptyList(),state.tripId)
        assertEquals(state.handoffPhotoUris,result.handoffPhotoUris)
        assertTrue(result.canShareLocation)
    }
    @Test fun photoResultsFromOldTripOrWhileDrivingAreRejected() {
        val state=MobilityState(gear=VehicleGear.P,handoffPhotoUris=listOf("content://original"))
        assertEquals(state.handoffPhotoUris,JourneyTransitions.attachPhotos(state,listOf("content://new"),"previous-trip").handoffPhotoUris)
        assertEquals(state.handoffPhotoUris,JourneyTransitions.attachPhotos(state.copy(phase=DrivePhase.DRIVING),listOf("content://new"),state.tripId).handoffPhotoUris)
    }
    @Test fun replacingPhotosLimitsAndDeduplicatesAndRequiresReconfirmation() {
        val state=MobilityState(gear=VehicleGear.P)
        val confirmed=state.copy(locationShared=true,confirmedLocation=state.locationKey)
        val result=JourneyTransitions.attachPhotos(confirmed,listOf("a","a","b","c","d","e"),state.tripId)
        assertEquals(listOf("a","b","c","d"),result.handoffPhotoUris)
        assertFalse(result.canShareLocation)
        assertTrue(JourneyTransitions.newTrip(result).handoffPhotoUris.isEmpty())
    }
    @Test fun resetDoesNotSilentlyDiscardAnActiveOrder() {
        val order=DiningOrder(MobilityCatalog.menus.first(),0,120,0)
        val state=MobilityState(gear=VehicleGear.P,order=order,mealStep=DiningStep.TRACK)
        val result=JourneyTransitions.reset(state)
        assertEquals(state.tripId,result.tripId);assertEquals(order,result.order)
        assertNull(JourneyTransitions.reset(state.copy(received=true)).order)
    }
    @Test fun selectingBayPreservesRemainingTimeOrderAndObstacle() {
        val order=DiningOrder(MobilityCatalog.menus.first(),0,120,0)
        val state=MobilityState(gear=VehicleGear.P,remaining=120,total=1080,elapsed=960,remainingMeters=120,
            order=order,mealStep=DiningStep.TRACK,obstacle=true)
        val result=JourneyTransitions.selectBay(state,MobilityCatalog.bays[1])
        assertEquals(120,result.remaining);assertEquals(1080,result.total);assertEquals(960,result.elapsed)
        assertEquals(state.remainingMeters,result.remainingMeters);assertEquals(order,result.order)
        assertTrue(result.obstacle);assertFalse(result.accessApproved)
    }
    @Test fun unavailableBayRevokesApprovalAndPreservesJourneyData() {
        val order=DiningOrder(MobilityCatalog.menus.first(),0,120,0)
        val base=approved().copy(bay=MobilityCatalog.bays[1],remaining=120,elapsed=960,total=1080,
            remainingMeters=120,order=order,obstacle=true,handoffPhotoUris=listOf("content://front"))
        val blocked=JourneyTransitions.unavailableBay(base,"A2")
        assertNull(blocked.bay);assertFalse(blocked.accessApproved);assertFalse(blocked.spaceChecked)
        assertTrue(blocked.obstacle);assertEquals(order,blocked.order);assertEquals(120,blocked.remaining)
        assertEquals(base.handoffPhotoUris,blocked.handoffPhotoUris)
        assertEquals("A3",blocked.bayAssessments.first{it.eligible}.bay.id)
        val alternate=JourneyTransitions.selectBay(blocked,MobilityCatalog.bays[2])
        assertEquals("A3",alternate.bay?.id);assertFalse(MobilityRules.canExit(alternate))
        assertNull(JourneyTransitions.selectBay(blocked,MobilityCatalog.bays[1]).bay)
    }
    @Test fun unavailableBaysPersistWithinTripAndResetOnlyOnNewTrip() {
        val base=MobilityState(gear=VehicleGear.P,needs=AccessPreferences(wide=true))
        val noMatch=JourneyTransitions.unavailableBay(base,"A3")
        assertTrue(noMatch.bayAssessments.none{it.eligible})
        assertEquals(setOf("A3"),JourneyStore.restore(JourneyStore.encode(noMatch),MobilityState()).unavailableBayIds)
        assertTrue(JourneyTransitions.newTrip(noMatch).unavailableBayIds.isEmpty())
        assertTrue(JourneyTransitions.unavailableBay(base.copy(gear=VehicleGear.D),"A2").unavailableBayIds.isEmpty())
    }
    @Test fun unsupportedBayDoesNotChangeTheJourney() {
        val state=MobilityState(gear=VehicleGear.P,remaining=120)
        val result=JourneyTransitions.selectBay(state,MobilityCatalog.bays.first())
        assertNull(result.bay);assertEquals(state.remaining,result.remaining)
    }
}
