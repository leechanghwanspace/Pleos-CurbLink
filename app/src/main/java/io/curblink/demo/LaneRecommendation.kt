package io.curblink.demo

import kotlin.math.*

data class LaneTraffic(val queue: Int = 0, val signalWait: Double = 0.0, val blockProbability: Double = 0.0,
    val extraWait: Double = 0.0, val closed: Boolean = false, val uncertainty: Double = 0.0)
data class LaneCost(val lane: Int, val cost: Double)
data class LaneAdvice(val lane: Int?, val reason: String, val costs: List<LaneCost>)

/** Port of simulator/js/lane-engine.js. Demo cost model, never a driving controller. */
object LaneRecommendation {
    fun rank(current: Int, purpose: ArrivalPurpose, distance: Int, traffic: Map<Int,LaneTraffic>, valid: Boolean): LaneAdvice {
        if (!valid) return LaneAdvice(null,"정보 만료 · 차선 추천 보류",emptyList())
        val start=if(purpose==ArrivalPurpose.PARKING)180.0 else 450.0
        val approach=distance<=start
        val goal=if(approach)4 else 3
        val urgency=if(approach)8+16*(1-distance.coerceAtLeast(0)/start) else 0.0
        val costs=(1..4).map { lane ->
            val t=traffic[lane]?:LaneTraffic()
            val changes=abs(lane-current)
            val allowed= !t.closed && (if(approach)lane in min(current,goal)..max(current,goal) else lane==2||lane==3)
            val reachable=changes*50<=distance
            val queue=t.queue.coerceAtLeast(0)*2+t.signalWait.coerceAtLeast(0.0)
            val mixed=if(lane==2)t.blockProbability.coerceIn(0.0,1.0)*t.extraWait.coerceAtLeast(0.0) else 0.0
            LaneCost(lane,if(allowed&&reachable)12+queue+mixed+changes*3+abs(lane-goal)*urgency+t.uncertainty.coerceIn(0.0,1.0)*10 else Double.POSITIVE_INFINITY)
        }
        val best=costs.minBy { it.cost }
        val lane=best.lane.takeIf { best.cost.isFinite() }
        val reason=when {
            lane==null -> "도달 가능한 차선 없음 · 추천 보류"
            approach -> "선택한 정차·주차 진입 차선으로 순차 접근"
            lane==3 -> "좌회전 대기 영향 회피 · 직진 전용 추천"
            else -> "변경 비용을 고려해 현재 차선 유지"
        }
        return LaneAdvice(lane,reason,costs)
    }
}
