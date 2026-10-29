package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class JourneyEngineTest {
    private fun route(id: String, from: WorldPoint = WorldPoint(0.0,0.0), to: WorldPoint = WorldPoint(0.0,10.0)) = JourneyStage(id,id,listOf(from,to),1.0,0.0)
    private fun stop(id: String, wait: Double = 0.0) = JourneyStage(id,id,emptyList(),0.0,wait)
    private fun scenario(id: String, vararg stages: JourneyStage) = JourneyScenario(id,id,stages.toList())

    @Test fun alleyBoardReturnAndMergeKeepPassengerAndConsumeDelayedFrame() {
        val scenario=scenario("PICKUP",route("MAIN"),route("ALLEY"),stop("BOARD",7.0),route("RETURN"),route("MERGE"))
        val board=JourneyEngine.advance(JourneyRun(scenario),26.0,false,false)
        assertEquals("BOARD",board.current.id);assertEquals("P",board.gear);assertTrue(board.passenger)
        val done=JourneyEngine.advance(board,100.0,false,false)
        assertTrue(done.done);assertEquals("MERGE",done.current.id);assertTrue(done.passenger);assertEquals("D",done.gear)
    }
    @Test fun reverseParkingFacesOppositeTravelAndEndsInPark() {
        val s=scenario("PARKING",route("GATE"),route("REVERSE",WorldPoint(40.0,562.0),WorldPoint(40.0,576.0)),stop("PARKED",3.0))
        val reversing=JourneyEngine.advance(JourneyRun(s),17.0,false,false)
        assertEquals("R",reversing.gear);assertEquals(569.0,reversing.pose.first.z,.001)
        assertEquals(Math.PI,reversing.pose.second,.001)
        val done=JourneyEngine.advance(reversing,50.0,false,false)
        assertTrue(done.done);assertEquals("P",done.gear);assertEquals(WorldPoint(40.0,576.0),done.pose.first)
    }
    @Test fun shortStopEnablesHazardsOnlyWhileStopped() {
        val s=scenario("SHORT_STOP",route("PULLOVER"),stop("WAIT",12.0),route("MERGE"))
        val wait=JourneyEngine.advance(JourneyRun(s),15.0,false,false)
        assertEquals("P",wait.gear);assertTrue(wait.hazards)
        val merge=JourneyEngine.advance(wait,8.0,false,false)
        assertEquals("MERGE",merge.current.id);assertFalse(merge.hazards);assertEquals("D",merge.gear)
    }
    @Test fun mealWaitsForFoodAndExplicitReceipt() {
        val s=scenario("MEAL",route("HOME"),stop("COURIER",8.0),stop("HANDOFF"))
        val wait=JourneyEngine.advance(JourneyRun(s),100.0,false,false)
        assertEquals("COURIER",wait.current.id);assertFalse(wait.done)
        val handoff=JourneyEngine.advance(wait,1.0,true,false)
        assertEquals("HANDOFF",handoff.current.id);assertFalse(handoff.done)
        assertFalse(JourneyEngine.act(handoff,"RECEIVE",false).done)
        assertTrue(JourneyEngine.act(handoff,"RECEIVE",true).done)
    }
    @Test fun riverCannotAdvanceBeforeConfirmedShare() {
        val s=scenario("RIVER",route("RIVERSIDE"),stop("LOCATE"),stop("COURIER",8.0),stop("HANDOFF"))
        val locate=JourneyEngine.advance(JourneyRun(s),1000.0,true,false)
        assertEquals("LOCATE",locate.current.id)
        assertEquals(locate,JourneyEngine.act(locate,"SHARE",false))
        val handoff=JourneyEngine.advance(JourneyEngine.act(locate,"SHARE",true),9.0,true,false)
        assertEquals("HANDOFF",handoff.current.id);assertTrue(JourneyEngine.act(handoff,"RECEIVE",true).done)
    }
    @Test fun accessCannotMoveWithoutApprovalAndHoldsMidExit() {
        val s=scenario("ACCESS",route("ACCESS_APPROACH"),stop("ACCESS_WAIT"),stop("ACCESS_EXIT",12.0))
        val waiting=JourneyEngine.advance(JourneyRun(s),100.0,false,false)
        assertEquals("ACCESS_WAIT",waiting.current.id)
        assertEquals(waiting,JourneyEngine.act(waiting,"APPROVE",false))
        val exiting=JourneyEngine.advance(JourneyEngine.act(waiting,"APPROVE",true),5.0,false,true)
        assertEquals(5.0,exiting.stageTime,.001)
        assertEquals(exiting,JourneyEngine.advance(exiting,1000.0,false,false))
        assertTrue(JourneyEngine.advance(exiting,7.0,false,true).done)
    }
    @Test fun widerBayHasDifferentApproachAndWalkingDuration() {
        val s=scenario("ACCESS",route("ACCESS_APPROACH",WorldPoint(60.0,552.0),WorldPoint(70.0,610.0)),stop("ACCESS_WAIT"),stop("ACCESS_EXIT",12.0))
        val wider=JourneyEngine.forBay(s,MobilityCatalog.bays[2])
        assertEquals(WorldPoint(70.0,648.0),wider.stages.first().points.last())
        assertEquals(42.0,wider.stages.last().wait,.001)
    }
    @Test fun appSnapshotDoesNotContainSimulatorState() {
        val state=MobilityState(accessTrip=true,bay=MobilityCatalog.bays[1],phase=DrivePhase.ARRIVED,
            gear=VehicleGear.P,accessApproved=true,spaceChecked=true,rampReady=true,staffAccepted=true)
        val encoded=JourneyStore.encode(state)
        assertFalse(encoded.contains("scenario="));assertFalse(encoded.contains("stageTime="))
        val restored=JourneyStore.restore(encoded,MobilityState())
        assertFalse(restored.accessApproved)
        assertEquals(0,restored.exitProgress)
    }
}
