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
    private val mutable = MutableStateFlow(MobilityState(role = if (prefs.getBoolean("passenger", false)) CabinRole.PASSENGER else CabinRole.DRIVER,
        needs = AccessPreferences(prefs.getBoolean("wheelchair", true), prefs.getBoolean("wide", false), prefs.getBoolean("gentle", false), prefs.getBoolean("staff", true))))
    val state = mutable.asStateFlow()
    private fun update(change: (MobilityState) -> MobilityState) { mutable.value = change(mutable.value) }
    init { viewModelScope.launch { while (true) { delay(500); tick() } } }
    fun page(value: Int) = update { s ->
        when {
            value == 3 && s.phase == DrivePhase.IDLE && s.order == null -> s.copy(page = value, settings = false, destination = MobilityCatalog.culture, purpose = ArrivalPurpose.ACCESS, remaining = MobilityCatalog.culture.eta, total = MobilityCatalog.culture.eta)
            value == 2 && s.phase == DrivePhase.IDLE && s.order == null -> s.copy(page = value, settings = false, destination = MobilityCatalog.home, purpose = ArrivalPurpose.PICKUP, remaining = MobilityCatalog.home.eta, total = MobilityCatalog.home.eta, accessTrip = false, bay = null)
            else -> s.copy(page = value, settings = false)
        }
    }
    fun settings() = update { it.copy(settings = !it.settings) }
    fun note(text: String) = update { it.copy(note = text) }
    fun destination(place: DemoPlace) = update { s -> if (!s.canEdit || s.moving || s.order != null) s.copy(note = "여정·주문을 종료한 뒤 목적지를 바꿔 주세요.") else s.copy(destination = place, remaining = place.eta, total = place.eta, gpsReady = true, bay = null, received = false, accessTrip = false, phase = DrivePhase.IDLE, note = "${place.name}까지 ${MobilityRules.formatTime(place.eta)} 예상") }
    fun purpose(value: ArrivalPurpose) = update { if (!it.canEdit || it.moving) it else it.copy(purpose = value) }
    fun role(value: CabinRole) { update { if (it.order != null) it else it.copy(role = value) }; prefs.edit().putBoolean("passenger", value == CabinRole.PASSENGER).apply() }
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
        else s.copy(phase = DrivePhase.DRIVING, paused = false, elapsed = 0, remaining = s.total,
            accessTrip = access, page = if (access) 3 else s.page, mealStep = if (s.page == 2) DiningStep.MENU else s.mealStep,
            note = "30배속 주행 시연 · 실제 차량·도로 안내 아님")
    }
    fun pause() = update { it.copy(paused = !it.paused) }
    fun reset() = update { MobilityState(page = it.page, role = it.role, needs = it.needs, note = "시연을 초기화했습니다. 주문은 모의 기록이며 실제 결제는 없습니다.") }
    fun validity() = update { it.copy(vehicleValid = !it.vehicleValid, paused = true, spaceChecked = false, note = "차량 상태 변경 · 확인과 하차 승인을 다시 평가합니다.") }
    private fun tick() = update { s ->
        if (s.paused || !s.vehicleValid) s else {
            val remaining = if (s.moving) (s.remaining - 15).coerceAtLeast(0) else s.remaining
            val elapsed = if (s.moving || (s.order != null && !s.received)) s.elapsed + 15 else s.elapsed
            val exit = if (s.accessApproved && !s.obstacle) (s.exitProgress + 5).coerceAtMost(100) else s.exitProgress
            s.copy(remaining = remaining, elapsed = elapsed, exitProgress = exit,
                phase = if (s.moving && remaining == 0) DrivePhase.ARRIVED else s.phase,
                note = if (s.moving && remaining == 0) "목적지에 도착했습니다. P단 정차 후 인계를 확인하세요." else s.note)
        }
    }
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
            "주문" in text && s.mealStep == DiningStep.REVIEW -> confirmMeal(true)
            "추천" in text -> selectMenu(MobilityRules.recommendedMenu(s.remaining).first(), true)
            "집" in text && !s.moving && s.order == null -> { destination(MobilityCatalog.home); mockGps(); page(2) }
            else -> note("음성 의도: ‘집’, ‘메뉴 추천’, ‘주문해 줘’를 사용할 수 있습니다.")
        }
    }
    fun needs(value: AccessPreferences) {
        update { if (!it.canEdit || it.moving || it.accessApproved) it else it.copy(needs = value, bay = null, spaceChecked = false, rampReady = false, staffAccepted = false) }
        prefs.edit().putBoolean("wheelchair", value.wheelchair).putBoolean("wide", value.wide).putBoolean("gentle", value.gentle).putBoolean("staff", value.staff).apply()
    }
    fun selectBay() = update { s ->
        if (!s.canEdit || s.moving || s.order != null) s.copy(note = "현재 여정을 종료한 뒤 하차 위치를 변경해 주세요.") else {
            val best = MobilityRules.assess(s.needs).firstOrNull { it.eligible }?.bay
            s.copy(bay = best, destination = MobilityCatalog.culture, purpose = ArrivalPurpose.ACCESS,
                remaining = MobilityCatalog.culture.eta, total = MobilityCatalog.culture.eta, gpsReady = true,
                phase = DrivePhase.IDLE, accessTrip = true, note = best?.let { "${it.name} 선택 · 입구까지 ${it.walk}m" } ?: "이용 가능한 하차 지점이 없습니다.")
        }
    }
    fun check(name: String, value: Boolean) = update { s ->
        if (s.phase != DrivePhase.ARRIVED || s.accessApproved) s else when (name) {
            "space" -> s.copy(spaceChecked = value)
            "ramp" -> s.copy(rampReady = value)
            "staff" -> s.copy(staffAccepted = value)
            "obstacle" -> s.copy(obstacle = value, spaceChecked = false)
            else -> s
        }
    }
    fun approveExit() = update { s -> if (!MobilityRules.canExit(s)) s.copy(note = "하차 보류 · 현재 상태와 지원 조건을 확인하세요.") else s.copy(accessApproved = true, exitProgress = 0, note = "하차 안내 시작 · 실제 도어·리프트를 제어하지 않습니다.") }
    fun shareLocation() = update { s -> if (s.moving || !s.vehicleValid) s.copy(note = "위치 카드는 정차 후 확인해 주세요.") else s.copy(locationShared = true, note = "인계 위치 카드를 확인했습니다. 외부 전송은 공유창에서 직접 선택합니다.") }
    fun receive() = update { s -> if (s.phase != DrivePhase.ARRIVED || !s.vehicleValid || !s.foodReady || (s.destination.id == "RIVER" && !s.locationShared)) s.copy(note = "정차·음식 도착·인계 위치를 확인해 주세요.") else s.copy(received = true, note = "주문 #024 음식 인계 완료 · 맛있게 드세요.") }
}
