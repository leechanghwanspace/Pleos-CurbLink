package io.curblink.demo

import android.app.Application
import android.location.LocationManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobilityViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("curblink_drive", Context.MODE_PRIVATE)
    val scenarios = ScenarioCatalog.load(app.assets)
    private val mutable = MutableStateFlow(JourneyStore.restore(prefs.getString("journey_v1", null), MobilityState(
        needs = AccessPreferences(prefs.getBoolean("wheelchair", true), prefs.getBoolean("wide", false), prefs.getBoolean("gentle", false), prefs.getBoolean("staff", true))), scenarios))
    val state = mutable.asStateFlow()
    private val clock = DemoClock()
    private fun update(change: (MobilityState) -> MobilityState) {
        var next = JourneyTransitions.enforce(change(mutable.value))
        if (next.lat != mutable.value.lat || next.lon != mutable.value.lon || next.locationLabel != mutable.value.locationLabel) {
            next = next.copy(locationVersion = next.locationVersion + 1, locationShared = false, confirmedLocation = null)
        }
        if (next != mutable.value) {
            val durable = next.order != mutable.value.order || next.received != mutable.value.received
            val editor = prefs.edit().putString("journey_v1", JourneyStore.encode(next))
            if (durable && !editor.commit()) {
                mutable.value = mutable.value.copy(note = "모의 주문 상태를 저장하지 못했습니다. 주문을 확정하지 않았습니다. 저장 공간을 확인해 주세요.")
                return
            }
            if (!durable) editor.apply()
            mutable.value = next
        }
    }
    init { viewModelScope.launch { while (true) { delay(500); tick() } } }
    fun page(value: Int) = update { s ->
        when {
            value == 3 && s.phase == DrivePhase.IDLE && s.order == null && s.destination.id != "CULTURE" -> JourneyTransitions.newTrip(s, MobilityCatalog.culture).copy(page = value, settings = false, purpose = ArrivalPurpose.ACCESS)
            value == 2 && s.phase == DrivePhase.IDLE && s.order == null && s.destination.id != "HOME" -> JourneyTransitions.newTrip(s, MobilityCatalog.home).copy(page = value, settings = false, purpose = ArrivalPurpose.PICKUP)
            else -> s.copy(page = value, settings = false)
        }
    }
    fun settings() = update { it.copy(settings = !it.settings) }
    fun sceneView(value: Int) = update { it.copy(sceneView = value.coerceIn(0, 4)) }
    fun laneQueue() = update { s -> s.copy(laneTraffic = if (s.laneTraffic[2]?.queue == 0 || s.laneTraffic.isEmpty()) mapOf(2 to LaneTraffic(queue = 20)) else emptyMap(), note = "모의 차선 입력 변경 · 같은 추천 계산 결과를 HUD에 표시합니다.") }
    fun laneValidity() = update { it.copy(laneDataValid = !it.laneDataValid) }
    fun advanceDemo(seconds: Int = 30) = update { s ->
        if (!s.settings || seconds !in 1..300) s else JourneyTransitions.advance(s.copy(paused = false), seconds).copy(paused = s.paused)
    }
    fun beginScenario(id: String) {
        val previous = mutable.value
        if (previous.order != null && !previous.received) { note("모의 주문을 수령하거나 발표 설정에서 시연을 초기화한 뒤 새 여정을 선택하세요."); return }
        val place = when (id) { "ACCESS", "PARKING" -> MobilityCatalog.culture; "RIVER" -> MobilityCatalog.river; else -> MobilityCatalog.home }
        update { JourneyTransitions.newTrip(it, place).copy(gpsReady = true,
            purpose = when (id) { "ACCESS" -> ArrivalPurpose.ACCESS; "PARKING" -> ArrivalPurpose.PARKING; "SHORT_STOP" -> ArrivalPurpose.SHORT_STOP; else -> ArrivalPurpose.PICKUP },
            page = when (id) { "ACCESS" -> 3; "MEAL", "RIVER" -> 2; else -> 1 },
            mealStep = if (id == "MEAL" || id == "RIVER") DiningStep.MENU else DiningStep.LOCATION,
            note = "APK 내부 오프라인 모의 시연 · 실제 차량·주문·카메라 미연결") }
        if (id != "ACCESS") {
            update { it.copy(phase = DrivePhase.DRIVING, journey = JourneyRun(scenarios.first { scenario -> scenario.id == id })) }
        }
    }
    fun note(text: String) = update { it.copy(note = text) }
    fun destination(place: DemoPlace) = update { s -> if (!s.canEdit || s.order != null) s.copy(note = "여정·주문을 종료한 뒤 목적지를 바꿔 주세요.") else JourneyTransitions.newTrip(s, place).copy(gpsReady = true, note = "${place.name}까지 ${MobilityRules.formatTime(place.eta)} 예상") }
    fun purpose(value: ArrivalPurpose) = update { if (!it.canEdit || it.order != null || it.purpose == value) it else JourneyTransitions.newTrip(it).copy(purpose = value) }
    fun role(value: CabinRole) = update { if (it.moving || it.order != null) it else it.copy(role = value) }
    fun mockGps() = update { if (it.moving) it else it.copy(gpsReady = true, locationLabel = "모의 GPS · 중앙로 출발", lat = null, lon = null, mealStep = DiningStep.DESTINATION, note = "위치를 확인했습니다. 목적지와 시간을 확인해 주세요.") }
    @Suppress("MissingPermission")
    fun actualGps() {
        try {
            val manager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.filter { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0L..30_000_000_000L }.minByOrNull { it.accuracy }
            if (location == null) { note("30초 이내의 위치가 없습니다. 모의 GPS로 계속 시연할 수 있습니다."); return }
            update { it.copy(gpsReady = true, lat = location.latitude, lon = location.longitude,
                locationLabel = "Android 위치 · 오차 약 ${location.accuracy.toInt()}m", mealStep = DiningStep.DESTINATION,
                note = "위치 확인 완료. 지도와 주행 경로는 공모전 시연용 모의 데이터입니다.") }
        } catch (_: SecurityException) { note("위치 권한이 없습니다. 모의 GPS를 사용할 수 있습니다.") }
    }
    fun startTrip(access: Boolean = false) = update { s ->
        if (!s.vehicleValid || s.phase != DrivePhase.IDLE || !s.gpsReady) s.copy(note = "위치와 차량 상태를 먼저 확인해 주세요.")
        else if (access && s.bay == null) s.copy(note = "조건에 맞는 하차 지점을 먼저 선택해 주세요.")
        else JourneyTransitions.newTrip(s).copy(bay = s.bay, gpsReady = s.gpsReady, phase = DrivePhase.DRIVING, paused = false, elapsed = 0, remaining = s.total,
            journey = JourneyRun(JourneyEngine.forBay(scenarios.first { it.id == when { access -> "ACCESS"; s.page == 2 -> "MEAL"; s.destination.id == "RIVER" -> "RIVER"; s.purpose == ArrivalPurpose.PARKING -> "PARKING"; s.purpose == ArrivalPurpose.SHORT_STOP -> "SHORT_STOP"; else -> "PICKUP" } }, s.bay)),
            accessTrip = access, page = if (access) 3 else s.page, mealStep = if (s.page == 2) DiningStep.MENU else s.mealStep,
            note = "30배속 주행 시연 · 실제 차량·도로 안내 아님")
    }
    fun pause() = update { it.copy(paused = !it.paused) }
    fun reset() = update { MobilityState(page = it.page, role = it.role, needs = it.needs, note = "시연을 초기화했습니다. 주문은 모의 기록이며 실제 결제는 없습니다.") }
    fun validity() = update { it.copy(vehicleValid = !it.vehicleValid, paused = true, spaceChecked = false, note = "차량 상태 변경 · 확인과 하차 승인을 다시 평가합니다.") }
    private fun tick() = update { s -> JourneyTransitions.advance(s,
        clock.step(SystemClock.elapsedRealtime(), !s.paused && s.vehicleValid)) }
    fun selectMenu(menu: DiningMenu, voice: Boolean = false) = update { s ->
        if (!s.vehicleValid || s.order != null || (!s.canEdit && !voice)) s.copy(note = "운전자 주행 중 메뉴 터치가 잠겨 있습니다. 음성 모드를 사용하세요.")
        else s.copy(selectedMenu = menu, mealStep = DiningStep.REVIEW, note = "${menu.name} · 조리 ${menu.prep / 60}분 + 배달 ${menu.delivery / 60}분")
    }
    fun confirmMeal(voice: Boolean = false) = update { s ->
        val order = MobilityRules.confirmOrder(s, voice)
        if (order == null) s.copy(note = "주문 조건을 다시 확인해 주세요.") else s.copy(order = order, mealStep = DiningStep.TRACK,
            note = if (order.lateAtConfirmation == 0) "집 도착에 맞춰 모의 주문을 예약했습니다." else "도착 후 약 ${order.lateAtConfirmation / 60}분 추가 대기가 예상됩니다.")
    }
    fun voice(text: String) {
        val s = mutable.value
        when {
            JourneyTransitions.voiceIntent(text) == MealVoiceIntent.CANCEL -> update {
                if (it.order == null) it.copy(selectedMenu = null, mealStep = DiningStep.MENU, note = "주문 확인을 취소했습니다. 주문을 만들지 않았습니다.")
                else it.copy(note = "이미 확정된 모의 주문입니다. 취소 기능은 아직 지원하지 않으며 새 주문은 만들지 않았습니다.")
            }
            JourneyTransitions.voiceIntent(text) == MealVoiceIntent.QUERY -> note(s.selectedMenu?.let { "${it.name} · ${it.price}원 · ${s.destination.name} · 주문 확정 전에는 ‘주문해 줘’로 확인하세요." } ?: "주문할 메뉴를 먼저 확인해 주세요.")
            JourneyTransitions.voiceIntent(text) == MealVoiceIntent.CONFIRM -> confirmMeal(true)
            JourneyTransitions.voiceIntent(text) == MealVoiceIntent.RECOMMEND -> selectMenu(MobilityRules.recommendedMenu(s.remaining).first(), true)
            JourneyTransitions.voiceIntent(text) == MealVoiceIntent.HOME && !s.moving && s.order == null -> { destination(MobilityCatalog.home); mockGps(); page(2) }
            else -> note("명확한 주문 확인을 듣지 못했습니다. 메뉴·금액·목적지를 검토하고 ‘주문해 줘’로 확인해 주세요.")
        }
    }
    fun needs(value: AccessPreferences) {
        update { if (!it.canEdit || it.moving || it.accessApproved) it else it.copy(needs = value, bay = null, spaceChecked = false, rampReady = false, staffAccepted = false) }
        val accepted = mutable.value.needs
        prefs.edit().putBoolean("wheelchair", accepted.wheelchair).putBoolean("wide", accepted.wide).putBoolean("gentle", accepted.gentle).putBoolean("staff", accepted.staff).apply()
    }
    fun selectBay() = update { s ->
        if (!s.canEdit || s.moving || s.order != null) s.copy(note = "현재 여정을 종료한 뒤 하차 위치를 변경해 주세요.") else {
            val best = MobilityRules.assess(s.needs).firstOrNull { it.eligible }?.bay
            JourneyTransitions.newTrip(s, MobilityCatalog.culture).copy(bay = best, purpose = ArrivalPurpose.ACCESS,
                remaining = MobilityCatalog.culture.eta, total = MobilityCatalog.culture.eta, gpsReady = true,
                phase = DrivePhase.IDLE, accessTrip = true, note = best?.let { "${it.name} 선택 · 입구까지 ${it.walk}m" } ?: "이용 가능한 하차 지점이 없습니다.")
        }
    }
    fun check(name: String, value: Boolean) = update { JourneyTransitions.observe(it, name, value) }
    fun approveExit() = update { s -> if (!MobilityRules.canExit(s)) s.copy(note = "하차 보류 · 현재 상태와 지원 조건을 확인하세요.") else s.copy(accessApproved = true, exitHeld = false,
        journey = s.journey?.let { JourneyEngine.act(it, "APPROVE", true) }, note = "하차 안내 시작 · 실제 도어·리프트를 제어하지 않습니다.") }
    fun shareLocation() = update { s -> if (s.moving || !s.vehicleValid) s.copy(note = "위치 카드는 정차 후 확인해 주세요.") else s.copy(locationShared = true, confirmedLocation = s.locationKey, note = "인계 위치 카드를 확인했습니다. 외부 전송은 공유창에서 직접 선택합니다.") }
    fun currentShareState(): MobilityState? = mutable.value.takeIf { it.canShareLocation }
    fun shareInScene() = update { s -> if (!s.canShareLocation) s.copy(note = "현재 인계 위치 카드를 먼저 확인하세요.") else s.copy(
        journey = s.journey?.let { JourneyEngine.act(it, "SHARE", true) }, note = "모의 배달원에게 확인된 위치를 전달했습니다. 실제 외부 전송은 없습니다.") }
    fun receive() = update { s -> if (s.received || s.phase != DrivePhase.ARRIVED || !s.vehicleValid || !s.foodReady || (s.destination.id == "RIVER" && !s.canShareLocation) || (s.journey != null && s.journey.current.id != "HANDOFF")) s.copy(note = "정차·음식 도착·인계 위치를 확인해 주세요.") else s.copy(received = true,
        journey = s.journey?.let { JourneyEngine.act(it, "RECEIVE", true) }, note = "모의 주문 #${s.order?.id?.take(8)} 음식 인계 완료 · 맛있게 드세요.") }
}
