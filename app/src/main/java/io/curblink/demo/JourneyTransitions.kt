package io.curblink.demo

enum class MealVoiceIntent { CANCEL, QUERY, RECOMMEND, CONFIRM, HOME, UNKNOWN }

object JourneyTransitions {
    fun locationUnavailable(s: MobilityState) = s.copy(gpsReady=false,lat=null,lon=null,
        locationShared=false,confirmedLocation=null,
        note="현재 위치를 읽지 못했습니다. 위치 권한과 GPS 수신을 확인하고 안전한 곳에 정차 후 현재 위치를 다시 확인해 주세요.")

    fun voiceUnavailable(s: MobilityState) = s.copy(
        note="음성을 인식하지 못했습니다. 기존 선택은 유지합니다. 음성 명령을 다시 시도하거나 안전한 곳에 정차 후 확인해 주세요.")

    fun shareUnavailable(s: MobilityState) = s.copy(
        note="공유창을 열지 못했습니다. 사진과 위치 카드는 유지합니다. 정차 후 공유 앱을 확인하고 다시 시도해 주세요.")

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
        locationVersion = s.locationVersion + 1, confirmedLocation = null, locationShared = false, handoffPhotoUris = emptyList(),
        remaining = place.eta, total = place.eta, elapsed = 0, phase = DrivePhase.IDLE,
        paused = false, selectedMenu = null, mealStep = DiningStep.LOCATION, order = null,
        accessTrip = false, bay = null, unavailableBayIds = emptySet(), spaceChecked = false, rampReady = false,
        staffAccepted = false, obstacle = false, accessApproved = false, exitProgress = 0,
        exitHeld = false, received = false
    )

    fun reset(s: MobilityState): MobilityState = when {
        !s.canEdit -> s.copy(note="P단 정차 상태에서 초기화해 주세요.")
        s.order != null && !s.received -> s.copy(note="진행 중인 주문이 있습니다. 수령 또는 명시적 취소 후 새 여정을 시작해 주세요.")
        else -> newTrip(s).copy(page=0,note="새 도착 준비를 시작합니다.")
    }

    fun attachPhotos(s: MobilityState, uris: List<String>, tripId: String?): MobilityState = when {
        !s.canEdit || tripId != s.tripId -> s.copy(note="여정 또는 차량 상태가 바뀌었습니다. 정차 후 사진을 다시 선택해 주세요.")
        uris.isEmpty() -> s.copy(note="사진 선택을 취소했습니다. 기존 첨부는 유지합니다.")
        else -> s.copy(handoffPhotoUris=uris.distinct().take(4),locationShared=false,confirmedLocation=null,
            note="주변 사진을 첨부했습니다. 현재 위치와 사진을 확인한 뒤 공유해 주세요.")
    }

    fun selectBay(s: MobilityState, bay: AccessBay): MobilityState {
        if(!s.canEdit || !MobilityRules.assess(s.needs,listOf(bay),s.unavailableBayIds).first().eligible)
            return s.copy(note="하차 조건에 맞는 지점을 정차 후 선택해 주세요.")
        return s.copy(bay=bay,purpose=ArrivalPurpose.ACCESS,accessTrip=true,
            locationVersion=s.locationVersion+1,locationShared=false,confirmedLocation=null,
            spaceChecked=false,rampReady=false,staffAccepted=false,accessApproved=false,exitProgress=0,exitHeld=false,
            note="${bay.name} 선택 · 입구 ${bay.walk}m. 정차 후 주변 공간과 지원을 확인해 주세요.")
    }

    fun unavailableBay(s: MobilityState, id: String): MobilityState {
        if (!s.canEdit || MobilityCatalog.bays.none { it.id == id })
            return s.copy(note="안전한 곳에 P단 정차 후 이용 불가 지점을 확인해 주세요.")
        val next = s.copy(unavailableBayIds=s.unavailableBayIds+id)
        val revoked = if (s.bay?.id == id) next.copy(bay=null,accessTrip=false,accessApproved=false,
            spaceChecked=false,rampReady=false,staffAccepted=false,exitProgress=0,exitHeld=true,
            locationVersion=s.locationVersion+1,locationShared=false,confirmedLocation=null) else next
        val alternative = revoked.bayAssessments.firstOrNull { it.eligible }
        return revoked.copy(note=alternative?.let { "사용자 확인 · 이번 여정 이용 불가. 대안 ${it.bay.name} · 입구 ${it.bay.walk}m. 새 지점 선택 후 안전 조건을 다시 확인해 주세요." }
            ?: "조건에 맞는 하차 지점이 없습니다. 지원 조건을 확인하거나 현장 도움을 요청해 주세요.")
    }

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
