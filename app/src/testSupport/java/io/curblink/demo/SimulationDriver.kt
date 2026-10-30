package io.curblink.demo

/** Test-only time advancement. Never compiled into the vehicle application. */
object SimulationDriver {
    fun advance(state: MobilityState, seconds: Int): MobilityState {
        val s = JourneyTransitions.enforce(state)
        if (seconds <= 0 || s.paused || !s.vehicleValid) return s
        val remaining = if(s.moving)(s.remaining-seconds).coerceAtLeast(0) else s.remaining
        return s.copy(remaining=remaining,elapsed=if(s.moving||s.order!=null)s.elapsed+seconds else s.elapsed,
            exitProgress=if(s.accessApproved&&MobilityRules.exitConditions(s))(s.exitProgress+seconds/3).coerceAtMost(100)else s.exitProgress,
            phase=if(s.moving&&remaining==0)DrivePhase.ARRIVED else s.phase)
    }
}
