package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class LaneRecommendationTest {
    @Test fun parkingReverseUsesWorldDistanceAndWithholdsMainRoadLanes() {
        val state=MobilityState(phase=DrivePhase.DRIVING,gear=VehicleGear.R,onMainRoad=false,remainingMeters=14)
        assertEquals(14,state.distance)
        assertNull(state.laneAdvice.lane)
    }
    @Test fun queueChangesTheAdviceActuallyConsumedByHud() {
        val state=MobilityState(phase=DrivePhase.DRIVING)
        assertEquals(2,state.laneAdvice.lane)
        val blocked=state.copy(laneTraffic=mapOf(2 to LaneTraffic(queue=20)))
        assertEquals(3,blocked.laneAdvice.lane)
        assertEquals(blocked.laneAdvice.lane,MobilityRules.lane(blocked).first)
        assertEquals(2,blocked.copy(laneTraffic=blocked.laneTraffic+(3 to LaneTraffic(closed=true))).laneAdvice.lane)
    }
    @Test fun staleAndClosedInputsWithholdRecommendation() {
        assertNull(MobilityState(phase=DrivePhase.DRIVING,laneDataValid=false).laneAdvice.lane)
        assertNull(LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,1000,(1..4).associateWith { LaneTraffic(closed=true) },true).lane)
    }
    @Test fun approachNeverRecommendsAnUnreachableLane() {
        val result=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,20,emptyMap(),true)
        assertEquals(2,result.lane)
        assertTrue(result.costs.first { it.lane==4 }.cost.isInfinite())
    }
}
