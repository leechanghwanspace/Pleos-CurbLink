package io.curblink.demo

import kotlin.math.abs

enum class ArrivalPurpose(val label: String) { PICKUP("승하차·픽업"), SHORT_STOP("잠시 정차"), PARKING("일반 주차"), ACCESS("배리어프리") }
enum class CabinRole(val label: String) { DRIVER("운전자 혼자"), PASSENGER("동승자 조작") }
enum class DrivePhase { IDLE, DRIVING, ARRIVED }
enum class DiningStep { LOCATION, DESTINATION, MENU, REVIEW, TRACK }
data class DemoPlace(val id: String, val name: String, val subtitle: String, val eta: Int, val meters: Int)
data class MapPoint(val x: Float, val y: Float)
data class AccessPreferences(val wheelchair: Boolean = true, val wide: Boolean = false, val gentle: Boolean = false, val staff: Boolean = true)
data class AccessBay(val id: String, val name: String, val walk: Int, val slope: Double, val side: Double,
    val stepFree: Boolean, val crossing: Boolean, val curb: Int, val staff: Boolean, val available: Boolean = true)
data class BayAssessment(val bay: AccessBay, val reasons: List<String>, val cost: Double) { val eligible get() = reasons.isEmpty() }

/** Mockup store & menu modeled after Korean delivery/pickup APIs (Yogiyo / Baemin / Coupang Eats style). */
data class DiningMenu(
    val id: String,
    val name: String,
    val detail: String,
    val price: Int,
    val prep: Int,
    val delivery: Int,
    val storeName: String = "온기키친 목적지점",
    val category: String = "한식·덮밥",
    val rating: Double = 4.9,
    val distanceMeters: Int = 280
) { val lead get() = prep + delivery }

data class DiningOrder(val menu: DiningMenu, val confirmedAt: Int, val dispatchAt: Int, val lateAtConfirmation: Int,
    val id: String = java.util.UUID.randomUUID().toString())
data class MobilityState(
    val source: VehicleSource = VehicleSource.UNAVAILABLE, val gear: VehicleGear = VehicleGear.UNKNOWN,
    val uxRestricted: Boolean = false, val routeReady: Boolean = false, val currentLane: Int = 2,
    val onMainRoad: Boolean = true, val remainingMeters: Int? = null,
    val servicesReady: Boolean = false,
    val navigationConnected: Boolean = false, val appDestinationSelected: Boolean = false,
    val laneTraffic: Map<Int, LaneTraffic> = emptyMap(), val laneDataValid: Boolean = true,
    val laneContext: LaneContext = LaneContext(),
    val tripId: String = java.util.UUID.randomUUID().toString(), val locationVersion: Int = 0,
    val confirmedLocation: String? = null, val exitHeld: Boolean = false,
    val page: Int = 0, val destination: DemoPlace = MobilityCatalog.home, val purpose: ArrivalPurpose = ArrivalPurpose.PICKUP,
    val role: CabinRole = CabinRole.DRIVER, val phase: DrivePhase = DrivePhase.IDLE,
    val remaining: Int = 1080, val elapsed: Int = 0, val total: Int = 1080, val paused: Boolean = false,
    val mealStep: DiningStep = DiningStep.LOCATION, val selectedMenu: DiningMenu? = null, val order: DiningOrder? = null,
    val gpsReady: Boolean = false, val locationLabel: String = "모의 GPS · 중앙로 출발", val lat: Double? = null, val lon: Double? = null,
    val needs: AccessPreferences = AccessPreferences(), val bay: AccessBay? = null, val accessTrip: Boolean = false,
    val spaceChecked: Boolean = false, val rampReady: Boolean = false, val staffAccepted: Boolean = false,
    val obstacle: Boolean = false, val accessApproved: Boolean = false, val exitProgress: Int = 0,
    val handoffPhotoUris: List<String> = emptyList(),
    val locationShared: Boolean = false, val received: Boolean = false, val vehicleValid: Boolean = true,
    val settings: Boolean = false, val note: String = "목적지를 선택하고 도착 이후의 경험까지 준비하세요."
) {
    val moving get() = phase == DrivePhase.DRIVING
    // One driver display: the demo role selector cannot grant seat/window privileges.
    val canEdit get() = vehicleValid && !moving && !uxRestricted && gear == VehicleGear.P
    val locationKey get() = "$tripId:${destination.id}:$locationVersion"
    val canShareLocation get() = canEdit && locationShared && confirmedLocation == locationKey
    val laneAdvice get() = if (!moving) LaneAdvice(null, "정차 지점 · 주변을 확인하세요", emptyList())
        else if (!onMainRoad) LaneAdvice(null, "본선 차선 안내 종료 · 현재 접근 안내를 따르세요", emptyList())
        else LaneRecommendation.rank(
        currentLane,
        purpose, distance, laneTraffic, laneDataValid && vehicleValid, laneContext)
    val progress get() = if (phase == DrivePhase.IDLE) 0f else (1f - remaining.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    val distance get() = remainingMeters ?: (destination.meters * (1f - progress)).toInt()
    val mealStatus: String get() {
        val current = order ?: return "주문 확인 전"
        val age = elapsed - current.dispatchAt
        return when { received -> "수령 완료"; age < 0 -> "도착 맞춤 예약"; age < 10 -> "주문 접수"; age < current.menu.prep -> "조리 중"; age < current.menu.lead -> "배달 중"; else -> "음식 도착" }
    }
    val foodReady get() = order?.let { elapsed >= it.dispatchAt + it.menu.lead } ?: false
}
object MobilityCatalog {
    val liveDestination = DemoPlace("PLEOS_CURRENT", "목적지 확인 중", "내비게이션에서 최신 목적지를 확인합니다", 0, 0)
    val home = DemoPlace("HOME", "집 · 리버빌라 101", "중앙로 → 주택가 골목", 1080, 7800)
    val culture = DemoPlace("CULTURE", "문화센터", "무단차 하차 베이 · 보행 연결", 960, 6500)
    val river = DemoPlace("RIVER", "한강 인계 베이 B-12", "강변 진입로 3번 · 산책로 옆", 1200, 9100)
    val places = listOf(home, culture, river)
    val menus = listOf(
        DiningMenu("BOWL", "닭구이 덮밥", "숯불 직화구이와 따뜻한 한 그릇", 10900, 480, 360, "온기키친 목적지점", "한식·직화덮밥", 4.9, 240),
        DiningMenu("SALAD", "연어 포케 샐러드", "신선한 생연어와 아보카도 볼", 11900, 300, 300, "그린보울 샐러드랩", "포케·다이어트", 4.8, 180),
        DiningMenu("BURGER", "더블 트러플 수제버거 세트", "갓 구운 브리오슈 번과 감자튀김", 12900, 420, 300, "브루클린 버거웍스", "수제버거·픽업", 4.9, 310),
        DiningMenu("COFFEE", "시그니처 바닐라 라떼 & 샌드위치", "도착 즉시 픽업하는 브런치 세트", 9800, 180, 240, "블루보틀 드라이브점", "카페·베이커리", 4.9, 120),
        DiningMenu("PASTA", "생모짜렐라 토마토 파스타", "주문 즉시 조리하는 화덕 파스타", 13900, 900, 600, "트라토리아 파스타", "이탈리안·화덕", 4.7, 420)
    )
    val bays = listOf(AccessBay("DOOR", "정문 바로 앞", 8, 9.0, 0.8, false, true, 15, false),
        AccessBay("A2", "동측 무단차 베이 A-2", 32, 2.5, 1.8, true, false, 0, true),
        AccessBay("A3", "북측 넓은 베이 A-3", 55, 1.2, 2.4, true, false, 0, true))
}
object MobilityRules {
    fun assess(needs: AccessPreferences, bays: List<AccessBay> = MobilityCatalog.bays): List<BayAssessment> = bays.map { b ->
        val reasons = buildList {
            if (!b.available) add("이용 불가·미확인")
            if (!b.stepFree) add("계단·단차 있음")
            if (b.crossing) add("차도 횡단 필요")
            if (b.curb > 2) add("연석 높이 초과")
            if (b.slope > if (needs.gentle) 2 else 5) add("경사 조건 초과")
            if (b.side < if (needs.wide) 2.0 else if (needs.wheelchair) 1.5 else 1.0) add("옆문 공간 부족")
            if (needs.staff && !b.staff) add("직원 지원 미배치")
        }
        BayAssessment(b, reasons, if (reasons.isEmpty()) b.walk + b.slope * if (needs.gentle) 8 else 2 else Double.POSITIVE_INFINITY)
    }.sortedWith(compareBy<BayAssessment> { it.cost }.thenBy { it.bay.walk })
    fun recommendedMenu(eta: Int): List<DiningMenu> = MobilityCatalog.menus.sortedWith(compareBy<DiningMenu> { (it.lead - eta).coerceAtLeast(0) }.thenBy { abs(eta - it.lead) })
    fun confirmOrder(s: MobilityState, voice: Boolean): DiningOrder? {
        val menu = s.selectedMenu ?: return null
        if (!s.vehicleValid || s.gear == VehicleGear.UNKNOWN || s.order != null || s.mealStep != DiningStep.REVIEW) return null
        if (!s.canEdit && !voice) return null
        return DiningOrder(menu, s.elapsed, s.elapsed + (s.remaining - menu.lead).coerceAtLeast(0), (menu.lead - s.remaining).coerceAtLeast(0))
    }
    fun exitConditions(s: MobilityState): Boolean = s.canEdit && s.phase == DrivePhase.ARRIVED && s.accessTrip && !s.obstacle &&
        s.bay?.let { b -> assess(s.needs, listOf(b)).first().eligible } == true && s.spaceChecked &&
        (!s.needs.wheelchair || s.rampReady) && (!s.needs.staff || s.staffAccepted)
    fun canExit(s: MobilityState): Boolean = exitConditions(s) && !s.accessApproved && s.exitProgress < 100
    fun lane(s: MobilityState): Pair<Int, String> = s.laneAdvice.let { (it.lane ?: 0) to it.reason }
    fun formatTime(seconds: Int): String = "${seconds.coerceAtLeast(0) / 60}분 ${seconds.coerceAtLeast(0) % 60}초"
}
