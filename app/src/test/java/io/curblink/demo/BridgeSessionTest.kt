package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class BridgeSessionTest {
    @Test fun rejectsDuplicateAndOutOfOrderFrames() {
        val s = BridgeSession()
        assertTrue(s.acceptFrame("a", 3))
        assertFalse(s.acceptFrame("a", 3))
        assertFalse(s.acceptFrame("a", 2))
        assertTrue(s.acceptFrame("a", 4))
    }
    @Test fun onlyMatchingResultThenFreshObservationCompletes() {
        val s = BridgeSession()
        s.acceptFrame("a", 1)
        assertTrue(s.begin("r", "OPEN", 10))
        assertFalse(s.begin("duplicate", "OPEN", 10))
        assertNull(s.observe(Tailgate.OPEN, true))
        assertFalse(s.result("other", "r", true))
        assertFalse(s.result("a", "wrong", true))
        assertTrue(s.result("a", "r", true))
        assertNull(s.observe(Tailgate.OPEN, false))
        assertNull(s.observe(Tailgate.CLOSED, true))
        assertEquals("OPEN", s.observe(Tailgate.OPEN, true))
        assertNull(s.observe(Tailgate.OPEN, true))
    }
    @Test fun timeoutDoesNotAuthorizeRetryAndLateStateCanReconcile() {
        val s = BridgeSession()
        s.acceptFrame("a", 1)
        s.begin("r", "CLOSE", 10)
        assertFalse(s.timedOut(5009))
        assertTrue(s.timedOut(5010))
        assertFalse(s.begin("retry", "CLOSE", 6000))
        s.result("a", "r", true)
        assertEquals("CLOSE", s.observe(Tailgate.CLOSED, true))
    }
    @Test fun newServerSessionDiscardsOldPendingRequest() {
        val s = BridgeSession()
        s.acceptFrame("a", 90)
        s.begin("r", "OPEN", 10)
        assertTrue(s.acceptFrame("b", 0))
        assertNull(s.pending)
        assertFalse(s.result("a", "r", true))
    }
    @Test fun rejectionClearsPendingButUnrelatedRejectionDoesNot() {
        val s = BridgeSession()
        assertFalse(s.begin("r", "OPEN", 0))
        s.acceptFrame("a", 1)
        assertFalse(s.begin("r", "invalid", 0))
        s.begin("r", "OPEN", 0)
        assertFalse(s.result("a", "wrong", false))
        assertNotNull(s.pending)
        assertTrue(s.result("a", "r", false))
        assertNull(s.pending)
    }
}
