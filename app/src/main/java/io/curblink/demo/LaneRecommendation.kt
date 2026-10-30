package io.curblink.demo

import kotlin.math.*

data class LaneTraffic(val queue: Int = 0, val signalWait: Double = 0.0, val blockProbability: Double = 0.0,
    val extraWait: Double = 0.0, val closed: Boolean = false, val uncertainty: Double = 0.0)
data class LaneCost(val lane: Int, val cost: Double)
data class LaneAdvice(val lane: Int?, val reason: String, val costs: List<LaneCost>, val alternatives: List<Int> = emptyList(),
    val nextAction: String = "현재 경로를 확인하세요", val nextLane: Int? = null)
data class LaneContext(val simultaneousGreen: Boolean = false, val exitMeters: Int? = null,
    val exitLane: Int = 4, val laneChangeAllowed: Boolean = true)

/** Port of simulator/js/lane-engine.js. Demo cost model, never a driving controller. */
object LaneRecommendation {
    fun rank(current: Int, purpose: ArrivalPurpose, distance: Int, traffic: Map<Int,LaneTraffic>, valid: Boolean,
        context: LaneContext = LaneContext()): LaneAdvice {
        if (!valid) return LaneAdvice(null,"정보 만료 · 차선 추천 보류",emptyList(),nextAction="차선 정보가 갱신될 때까지 현재 경로를 확인하세요")
        if (traffic.values.any { !it.signalWait.isFinite() || !it.blockProbability.isFinite() || !it.extraWait.isFinite() || !it.uncertainty.isFinite() } || current !in 1..4 || distance < 0 || context.exitLane !in 1..4 || context.exitMeters?.let { it < 0 } == true)
            return LaneAdvice(null,"경로 정보 확인 필요",emptyList())
        val exit = context.exitMeters?.let { it <= 1500 } == true
        if (exit && (!context.laneChangeAllowed || abs(current-context.exitLane)*150 > context.exitMeters!!))
            return LaneAdvice(current,"분기점 진입 보류 · 재탐색 필요",emptyList(),nextAction="현재 차선 유지 · 경로 제공자의 다음 안내 확인")
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
        val cheapest=costs.minBy { it.cost }
        // Small cost changes do not justify repeated lane changes in this demo model.
        val best=costs.firstOrNull { !approach && it.lane==current && it.cost.isFinite() && it.cost<=cheapest.cost+3 } ?: cheapest
        val lane=best.lane.takeIf { best.cost.isFinite() }
        val reason=when {
            lane==null -> "도달 가능한 차선 없음 · 추천 보류"
            exit -> "${context.exitMeters}m 앞 분기점 · ${context.exitLane}차선까지 한 차선씩 미리 준비"
            approach -> "선택한 정차·주차 진입 차선으로 순차 접근"
            context.simultaneousGreen && lane in 2..3 -> "직좌 동시 신호 · 2·3차선 비용 비교"
            lane==3 && traffic[2]?.let { it.queue>0 || it.signalWait>0 || it.blockProbability*it.extraWait>0 } == true -> "2차선 대기 영향 · 직진 전용 추천"
            lane!=4 && traffic[4]?.let { it.queue>0 || it.signalWait>0 || it.blockProbability*it.extraWait>0 } == true -> "4차선 대기 영향 · 차선별 비용 비교"
            lane==current -> "변경 비용을 고려해 현재 차선 유지"
            else -> "차선별 예상 비용 비교 · 직진 흐름 유지"
        }
        val alternatives=if(context.simultaneousGreen && !approach && lane!=null)costs.filter{it.lane in 2..3 && it.cost.isFinite() && it.cost<=best.cost+6}.map{it.lane} else listOfNotNull(lane)
        val nextLane=lane?.takeIf { it!=current }?.let { current+(it-current).sign }
        val action=when {
            lane==null -> "현재 경로 확인 · 안전한 곳에서 안내 재확인"
            lane==current -> "현재 ${current}차선 유지"
            else -> "${nextLane}차선 방향 변경 준비 · 표지와 안전 간격 확인"
        }
        return LaneAdvice(lane,reason,costs,alternatives,action,nextLane)
    }
}
