package io.curblink.demo

enum class MealVoiceIntent { CANCEL, QUERY, RECOMMEND, CONFIRM, HOME, UNKNOWN }

object JourneyTransitions {
    fun voiceIntent(text: String): MealVoiceIntent {
        val words = text.trim().replace(Regex("[\\s.!?]+"), "")
        // A positive substring is never enough to authorize an order.
        if (listOf("취소", "하지마", "하지말", "안할", "안해", "싫", "말아", "주문안", "주문말", "보류").any { it in words }) return MealVoiceIntent.CANCEL
        if (listOf("가격", "금액", "얼마", "알려", "조회", "언제", "상태", "할까", "해도", "뭐").any { it in words }) return MealVoiceIntent.QUERY
        return when (words) {
            "주문해줘", "주문해", "주문확정", "이메뉴로주문해줘", "예약주문확인" -> MealVoiceIntent.CONFIRM
            "메뉴추천", "메뉴추천해줘", "추천해줘", "추천" -> MealVoiceIntent.RECOMMEND
            "집", "집으로", "집으로가자" -> MealVoiceIntent.HOME
            else -> MealVoiceIntent.UNKNOWN
        }
    }

    fun newTrip(s: MobilityState, place: DemoPlace = s.destination): MobilityState = s.copy(
        tripId = java.util.UUID.randomUUID().toString(), destination = place, routeReady = false, remainingMeters = null,
        locationVersion = s.locationVersion + 1, confirmedLocation = null, locationShared = false,
        remaining = place.eta, total = place.eta, elapsed = 0, phase = DrivePhase.IDLE,
        paused = false, selectedMenu = null, mealStep = DiningStep.LOCATION, order = null,
        accessTrip = false, bay = null, spaceChecked = false, rampReady = false,
        staffAccepted = false, obstacle = false, accessApproved = false, exitProgress = 0,
        exitHeld = false, received = false
    )

    fun enforce(s: MobilityState): MobilityState {
        var next = s
        if (next.accessApproved && !MobilityRules.exitConditions(next)) next = next.copy(
            accessApproved = false, exitHeld = true, spaceChecked = false,
            note = "하차 보류 · 조건 변경으로 승인을 폐기했습니다. 주변 확인 후 다시 승인해 주세요."
        )
        if (next.confirmedLocation != next.locationKey && next.locationShared) next = next.copy(locationShared = false, confirmedLocation = null)
        return next
    }

    fun observe(s: MobilityState, name: String, value: Boolean): MobilityState {
        if (s.phase != DrivePhase.ARRIVED) return s
        return enforce(when (name) {
            "space" -> s.copy(spaceChecked = value)
            "ramp" -> s.copy(rampReady = value)
            "staff" -> s.copy(staffAccepted = value)
            "obstacle" -> s.copy(obstacle = value, spaceChecked = false)
            else -> s
        })
    }

}
