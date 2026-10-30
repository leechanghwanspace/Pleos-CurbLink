package io.curblink.demo

import kotlin.math.*

data class LaneTraffic(val queue: Int = 0, val signalWait: Double = 0.0, val blockProbability: Double = 0.0,
    val extraWait: Double = 0.0, val closed: Boolean = false, val uncertainty: Double = 0.0)
data class LaneCost(val lane: Int, val cost: Double)
data class LaneAdvice(val lane: Int?, val reason: String, val costs: List<LaneCost>, val alternatives: List<Int> = emptyList())
data class LaneContext(val simultaneousGreen: Boolean = false, val exitMeters: Int? = null,
    val exitLane: Int = 4, val laneChangeAllowed: Boolean = true)

/** Port of simulator/js/lane-engine.js. Demo cost model, never a driving controller. */
object LaneRecommendation {
    fun rank(current: Int, purpose: ArrivalPurpose, distance: Int, traffic: Map<Int,LaneTraffic>, valid: Boolean,
        context: LaneContext = LaneContext()): LaneAdvice {
        if (!valid) return LaneAdvice(null,"정보 만료 · 차선 추천 보류",emptyList())
        if (traffic.values.any { !it.signalWait.isFinite() || !it.blockProbability.isFinite() || !it.extraWait.isFinite() || !it.uncertainty.isFinite() } || current !in 1..4 || distance < 0 || context.exitLane !in 1..4 || context.exitMeters?.let { it < 0 } == true)
            return LaneAdvice(null,"경로 정보 확인 필요",emptyList())
        val exit = context.exitMeters?.let { it <= 1500 } == true
        if (exit && (!context.laneChangeAllowed || abs(current-context.exitLane)*150 > context.exitMeters!!))
            return LaneAdvice(current,"분기점 진입 보류 · 급한 변경 없이 다음 경로로 재탐색",emptyList())
        val start=if(purpose==ArrivalPurpose.PARKING)180.0 else 450.0
        val approach=distance<=start || exit
        val goal=if(exit)context.exitLane else if(approach)4 else 3
        val urgency=if(approach)8+16*(1-distance.coerceAtLeast(0)/start) else 0.0
        val costs=(1..4).map { lane ->
            val t=traffic[lane]?:LaneTraffic()
            val changes=abs(lane-current)
            val allowed= !t.closed && (context.laneChangeAllowed || lane==current) && (if(approach)lane in min(current,goal)..max(current,goal) else lane in 2..4)
            val reachable=changes*50<=distance
            val queue=t.queue.coerceAtLeast(0)*2+t.signalWait.coerceAtLeast(0.0)
            val mixed=if(lane==2 && !context.simultaneousGreen || lane==4)t.blockProbability.coerceIn(0.0,1.0)*t.extraWait.coerceAtLeast(0.0) else 0.0
            LaneCost(lane,if(allowed&&reachable)12+queue+mixed+changes*3+(if(!approach&&lane==4)8 else 0)+abs(lane-goal)*(if(exit)24.0 else urgency)+t.uncertainty.coerceIn(0.0,1.0)*10 else Double.POSITIVE_INFINITY)
        }
        val best=costs.minBy { it.cost }
        val lane=best.lane.takeIf { best.cost.isFinite() }
        val reason=when {
            lane==null -> "도달 가능한 차선 없음 · 추천 보류"
            exit -> "${context.exitMeters}m 앞 분기점 · ${context.exitLane}차선까지 한 차선씩 미리 준비"
            approach -> "선택한 정차·주차 진입 차선으로 순차 접근"
            context.simultaneousGreen && lane in 2..3 -> "직좌 동시 신호 · 2·3차선 비교 후 원활한 차선 유지"
            lane==3 -> "좌회전 대기 영향 회피 · 직진 전용 추천"
            else -> "변경 비용을 고려해 현재 차선 유지"
        }
        val alternatives=if(context.simultaneousGreen && !approach && lane!=null)costs.filter{it.lane in 2..3 && it.cost.isFinite() && it.cost<=best.cost+6}.map{it.lane} else listOfNotNull(lane)
        return LaneAdvice(lane,reason,costs,alternatives)
    }
}
