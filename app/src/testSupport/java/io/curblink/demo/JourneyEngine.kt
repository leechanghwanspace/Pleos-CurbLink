package io.curblink.demo

import kotlin.math.*

data class WorldPoint(val x: Double, val z: Double)
data class JourneyStage(val id: String, val label: String, val points: List<WorldPoint>, val speed: Double, val wait: Double) {
    val length: Double get() = points.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }.sum()
    val duration: Double get() = if (points.isEmpty()) wait else length / speed.coerceAtLeast(0.1)
}
data class JourneyScenario(val id: String, val label: String, val stages: List<JourneyStage>) {
    val duration: Double get() = stages.sumOf { it.duration }.coerceAtLeast(1.0)
}
data class JourneyRun(val scenario: JourneyScenario, val stage: Int = 0, val stageTime: Double = 0.0,
    val done: Boolean = false, val passenger: Boolean = false) {
    val current get() = scenario.stages[stage]
    val stopped get() = current.points.isEmpty()
    val gear get() = if (stopped) "P" else if (current.id == "REVERSE") "R" else "D"
    val hazards get() = current.id == "WAIT" && !done
    val speedKph get() = if (stopped || done) 0.0 else current.speed * 3.6
    val fraction get() = if (current.duration == 0.0) 0.0 else (stageTime / current.duration).coerceIn(0.0, 1.0)
    val pose: Pair<WorldPoint, Double> get() {
        val points = if (current.points.isNotEmpty()) current.points else scenario.stages.take(stage).lastOrNull { it.points.isNotEmpty() }?.points.orEmpty()
        if (points.size < 2) return WorldPoint(0.0, 0.0) to 0.0
        var travel = if (stopped) Double.MAX_VALUE else current.length * fraction
        for ((a, b) in points.zipWithNext()) {
            val length = hypot(b.x - a.x, b.z - a.z)
            if (travel <= length) {
                val f = (travel / length.coerceAtLeast(.0001)).coerceIn(0.0, 1.0)
                return WorldPoint(a.x + (b.x - a.x) * f, a.z + (b.z - a.z) * f) to (atan2(b.x - a.x, b.z - a.z) + if (gear == "R") PI else 0.0)
            }
            travel -= length
        }
        val a = points[points.lastIndex - 1]; val b = points.last()
        return b to (atan2(b.x - a.x, b.z - a.z) + if (current.id == "REVERSE" || (stopped && scenario.stages.take(stage).lastOrNull { it.points.isNotEmpty() }?.id == "REVERSE")) PI else 0.0)
    }
}

object JourneyEngine {
    fun forBay(scenario: JourneyScenario, bay: AccessBay?): JourneyScenario = if (scenario.id != "ACCESS" || bay?.id != "A3") scenario else scenario.copy(stages = scenario.stages.map {
        when (it.id) {
            "ACCESS_APPROACH" -> it.copy(points = it.points.dropLast(1) + WorldPoint(70.0, 648.0))
            "ACCESS_EXIT" -> it.copy(wait = 42.0)
            else -> it
        }
    })
    fun advance(run: JourneyRun, seconds: Double, foodReady: Boolean, exitAllowed: Boolean): JourneyRun {
        require(seconds.isFinite() && seconds >= 0)
        if (run.done || run.current.id in setOf("LOCATE", "HANDOFF", "ACCESS_WAIT") || (run.current.id == "ACCESS_EXIT" && !exitAllowed)) return run
        var next = run
        var left = seconds
        // Consume the full elapsed interval across stage boundaries, including delayed frames.
        while (left > 0 && !next.done) {
            val stage = next.current
            if (stage.id in setOf("LOCATE", "HANDOFF", "ACCESS_WAIT")) break
            if (stage.id == "ACCESS_EXIT" && !exitAllowed) break
            if (stage.id == "COURIER" && !foodReady) return next.copy(stageTime = min(stage.duration, next.stageTime + left))
            val spend = min(left, (stage.duration - next.stageTime).coerceAtLeast(0.0))
            next = next.copy(stageTime = next.stageTime + spend,
                passenger = next.passenger || (stage.id == "BOARD" && next.stageTime + spend >= 5))
            left -= spend
            if (next.stageTime >= stage.duration) next = if (next.stage == next.scenario.stages.lastIndex) next.copy(done = true) else next.copy(stage = next.stage + 1, stageTime = 0.0)
            else break
        }
        return next
    }
    fun act(run: JourneyRun, action: String, allowed: Boolean): JourneyRun {
        if (!allowed || !run.stopped || run.done) return run
        return when {
            run.current.id == "LOCATE" && action == "SHARE" -> run.copy(stage = run.stage + 1, stageTime = 0.0)
            run.current.id == "ACCESS_WAIT" && action == "APPROVE" -> run.copy(stage = run.stage + 1, stageTime = 0.0)
            run.current.id == "HANDOFF" && action == "RECEIVE" -> run.copy(done = true)
            else -> run
        }
    }
}
