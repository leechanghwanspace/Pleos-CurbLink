package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class LaneRecommendationTest {
    @Test fun adviceSeparatesTargetFromAdjacentPreparationAndUsesOnlyEvidence() {
        val early=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,emptyMap(),true,LaneContext(exitMeters=1200))
        assertEquals(4,early.lane);assertEquals(3,early.nextLane);assertTrue(early.nextAction.contains("준비"))
        val unknown=LaneRecommendation.rank(3,ArrivalPurpose.PICKUP,5000,emptyMap(),true)
        assertFalse(unknown.reason.contains("대기 영향"))
        val small=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,mapOf(2 to LaneTraffic(queue=2)),true)
        assertEquals(2,small.lane);assertNull(small.nextLane)
        val expired=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,emptyMap(),false)
        assertNull(expired.lane);assertNull(expired.nextLane);assertTrue(expired.nextAction.contains("갱신"))
    }
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
        assertEquals(4,blocked.copy(laneTraffic=blocked.laneTraffic+(3 to LaneTraffic(closed=true))).laneAdvice.lane)
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
    @Test fun simultaneousGreenKeepsBothStraightChoices() {
        val result=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,emptyMap(),true,LaneContext(simultaneousGreen=true))
        assertEquals(listOf(2,3),result.alternatives)
    }
    @Test fun highwayExitIsPreparedEarlyAndLateMergeIsRefused() {
        val early=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,emptyMap(),true,LaneContext(exitMeters=1200))
        assertEquals(4,early.lane)
        val late=LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,emptyMap(),true,LaneContext(exitMeters=100))
        assertEquals(2,late.lane);assertTrue(late.reason.contains("재탐색"))
        assertTrue(LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,emptyMap(),true,LaneContext(exitMeters=1200,laneChangeAllowed=false)).reason.contains("재탐색"))
    }
    @Test fun rightTurnQueueAndInvalidNumbersCannotCreateFalseAdvice() {
        val traffic=mapOf(2 to LaneTraffic(queue=8),3 to LaneTraffic(queue=1),4 to LaneTraffic(queue=8,blockProbability=1.0,extraWait=40.0))
        assertEquals(3,LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,traffic,true).lane)
        assertNull(LaneRecommendation.rank(2,ArrivalPurpose.PICKUP,5000,mapOf(2 to LaneTraffic(signalWait=Double.NaN)),true).lane)
    }
}
