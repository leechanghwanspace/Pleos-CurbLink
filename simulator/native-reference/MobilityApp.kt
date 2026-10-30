package io.curblink.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Mint = Color(0xFF85E5C7)
private val Ink = Color(0xFF0B141E)
private val Panel = Color(0xFF142330)
private val Muted = Color(0xFFA6BCC9)
private val Amber = Color(0xFFF1CA85)
private val tabs = listOf("홈", "경로 안내", "도착 식사", "배리어프리", "현장 인계")

@Composable fun MobilityApp(vm: MobilityViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { result -> if (result == TextToSpeech.SUCCESS) { speechReady = engine?.setLanguage(Locale.KOREAN)?.let { it >= 0 } == true } }
        tts = engine
        onDispose { engine?.stop(); engine?.shutdown() }
    }
    fun speak(text: String) { if (speechReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "curblink-guidance") else vm.note("이 기기에 한국어 음성 엔진이 없습니다. 안내 문장과 모의 음성 입력을 사용할 수 있습니다.") }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.voice(it) }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (intent.resolveActivity(context.packageManager) != null) voice.launch(intent) else vm.note("음성 인식 서비스가 없는 기기입니다. 발표 설정의 모의 음성 입력으로 진행하세요.")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) vm.actualGps() else vm.note("위치 권한 없이 모의 GPS로 시연할 수 있습니다.")
    }
    fun gps() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) vm.actualGps()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Amber, background = Ink, surface = Panel, onPrimary = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val wide = maxWidth >= 950.dp
                Column(Modifier.fillMaxSize().padding(if (wide) 24.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(46.dp).background(Mint, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("C", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Text("CurbLink Drive", fontSize = 25.sp, fontWeight = FontWeight.SemiBold); Text("목적지, 그 다음까지", color = Muted, fontSize = 13.sp) }
                        Status(if (!s.vehicleValid) "상태 미확인" else if (s.journey != null) "${s.journey?.gear} · 모의 여정" else if (s.moving) "D · 주행 시연" else "P · 정차", if (!s.vehicleValid) Amber else Mint)
                        OutlinedButton(onClick = vm::settings, modifier = Modifier.heightIn(min = 52.dp)) { Text("발표 설정") }
                    }
                    Text("공모전 프로토타입 · 차량 상태/경로/주문/카메라 모의 · 실제 Pleos·결제·차량 제어 미연결", fontSize = 11.sp, color = Muted)
                    if (s.settings) SettingsPanel(s, vm)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.forEachIndexed { i, name -> FilterChip(selected = s.page == i, onClick = { vm.page(i) }, label = { Text(name, fontSize = 17.sp) }, modifier = Modifier.heightIn(min = 56.dp)) }
                    }
                    s.journey?.let { run ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${run.gear} · ${if (run.done) "시연 완료" else run.current.label}", color = Mint, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            if (s.paused) OutlinedButton(onClick = vm::pause, enabled = s.vehicleValid) { Text("시연 재개") }
                            if (run.current.id == "LOCATE") OutlinedButton(onClick = { vm.page(4) }) { Text("위치 확인·인계") }
                            if (run.current.id == "ACCESS_WAIT" || run.current.id == "ACCESS_EXIT") OutlinedButton(onClick = { vm.page(3) }) { Text("하차 지원") }
                        }
                    }
                    if (!s.vehicleValid) Text("차량 상태가 유효하지 않아 조작과 하차를 보류합니다.", color = Amber, fontSize = 18.sp)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (s.page) {
                            0 -> HomePage(s, vm, ::gps)
                            1 -> RoutePage(s, vm, wide)
                            2 -> DiningPage(s, vm, wide, ::listen, ::speak)
                            3 -> AccessPage(s, vm, wide)
                            else -> HandoffPage(s, vm)
                        }
                    }
                    Text(s.note, color = Mint, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF102D2C), RoundedCornerShape(10.dp)).padding(12.dp))
                }
            }
        }
    }
}
@Composable private fun Status(text: String, color: Color = Mint) { Text(text, color = color, fontSize = 12.sp, modifier = Modifier.border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(8.dp)).background(color.copy(alpha = .08f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) }
@Composable private fun Block(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Panel, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold); content()
    }
}
@Composable private fun Action(text: String, enabled: Boolean = true, click: () -> Unit) { Button(onClick = click, enabled = enabled, modifier = Modifier.heightIn(min = 58.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(text, fontSize = 17.sp) } }
@Composable private fun Hint(text: String) { Text(text, color = Muted, fontSize = 14.sp, lineHeight = 21.sp) }
@Composable private fun Split(wide: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) { left() }
        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { right() }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { left(); right() }
}
@Composable private fun HomePage(s: MobilityState, vm: MobilityViewModel, gps: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Block("APK 내부 오프라인 여정", Modifier.fillMaxWidth()) {
            Hint("실제 주문·결제·차량 제어 없이 로컬 공간과 단계로 실행합니다. 식사는 메뉴 선택과 명확한 확인이 필요합니다.")
            vm.scenarios.forEach { scenario -> Action(scenario.label, s.order == null || s.received) { vm.beginScenario(scenario.id) } }
        }
        Text("어디로, 어떤 목적으로 가시나요?", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
        Hint("같은 목적지여도 승하차, 주차, 필요한 지원에 따라 마지막 경로가 달라집니다.")
        OutlinedTextField(query, { query = it }, label = { Text("목적지 검색 · 집 / 문화센터 / 한강") }, enabled = s.canEdit && !s.moving && s.order == null, singleLine = true, modifier = Modifier.fillMaxWidth())
        MobilityCatalog.places.filter { query.isBlank() || it.name.contains(query) || it.subtitle.contains(query) }.forEach { place ->
            Block(place.name, Modifier.fillMaxWidth().clickable(enabled = s.canEdit && !s.moving && s.order == null) { vm.destination(place) }) {
                Hint(place.subtitle); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("예상 ${place.eta / 60}분", color = Mint); Text(if (s.destination.id == place.id) "선택됨" else "선택 →", color = Mint) }
            }
        }
        Block("도착 목적", Modifier.fillMaxWidth()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ArrivalPurpose.entries.forEach { FilterChip(selected = s.purpose == it, onClick = { vm.purpose(it) }, enabled = s.canEdit && !s.moving, label = { Text(it.label) }) } }
            Hint(s.locationLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = vm::mockGps, enabled = s.phase == DrivePhase.IDLE) { Text("모의 GPS 확인") }; OutlinedButton(onClick = gps, enabled = s.phase == DrivePhase.IDLE) { Text("Android 위치 확인") } }
            Action("${s.destination.name} · 경로 보기") { vm.page(if (s.purpose == ArrivalPurpose.ACCESS) 3 else 1) }
        }
    }
}
@Composable private fun RoutePage(s: MobilityState, vm: MobilityViewModel, wide: Boolean) {
    Split(wide, left = { JourneyMap(s, vm) }, right = {
        Block("${s.destination.name}", Modifier.fillMaxWidth()) {
            Hint(s.destination.subtitle); Text(MobilityRules.formatTime(s.remaining), fontSize = 36.sp, color = Mint)
            Text("${"%.1f".format(s.distance / 1000f)} km 남음 · ${s.purpose.label}", color = Muted)
            LaneHud(s)
            when {
                s.journey?.done == true -> Action("홈에서 다음 여정 선택") { vm.page(0) }
                s.journey?.current?.id in listOf("BOARD", "WAIT") -> Hint("정차 시연 후 자동으로 본선 복귀를 진행합니다.")
                else -> when (s.phase) {
                DrivePhase.IDLE -> { if (!s.gpsReady) Action("모의 GPS 위치 확인", click = vm::mockGps); Action("주행 시연 시작", s.gpsReady && s.vehicleValid) { vm.startTrip(s.accessTrip) } }
                DrivePhase.DRIVING -> Action(if (s.paused) "시연 이어서 재생" else "시연 일시 정지", click = vm::pause)
                DrivePhase.ARRIVED -> { Status("목적지 도착 · P단 정차"); Action(if (s.accessTrip) "하차 지원 확인" else if (s.order != null) "음식 수령 확인" else "현장 인계 보기") { vm.page(if (s.accessTrip) 3 else 4) } }
                }
            }
            Hint("30배속 가상 주행 · 본 지도는 실도로 내비게이션이 아닌 오프라인 시연 지도입니다.")
        }
    })
}
@Composable private fun LaneHud(s: MobilityState) {
    val (recommended, reason) = MobilityRules.lane(s)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        (1..4).forEach { lane -> Column(Modifier.weight(1f).background(if (lane == recommended) Color(0xFF22554B) else Ink, RoundedCornerShape(10.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(listOf("↰", "↑↰", "↑", "↱")[lane - 1], fontSize = 25.sp, color = if (lane == recommended) Mint else Muted); Text("${lane}차선", fontSize = 11.sp); if (lane == recommended) Text("추천", color = Mint, fontSize = 11.sp)
        } }
    }
    Hint(reason)
}
@Composable private fun DiningPage(s: MobilityState, vm: MobilityViewModel, wide: Boolean, listen: () -> Unit, speak: (String) -> Unit) {
    Split(wide, left = { JourneyMap(s, vm) }, right = {
        Block("도착에 맞춰 준비되는 한 끼", Modifier.fillMaxWidth()) {
            Hint("운전자 화면 · 주행 중 터치는 역할 선택과 관계없이 제한됩니다.")
            Hint("위치 → 집까지 시간 → 메뉴 추천 → 주문 확인 → 도착 동기화")
            when (s.mealStep) {
                DiningStep.LOCATION -> { Text("차량 위치부터 확인할까요?", fontSize = 24.sp); Hint("위치와 경로 시간으로 조리·배달 일정을 계산합니다."); Action("모의 GPS 확인", !s.moving, vm::mockGps) }
                DiningStep.DESTINATION -> {
                    Text("${s.destination.name}까지 ${s.remaining / 60}분", fontSize = 27.sp, color = Mint)
                    Hint("저장된 집: 리버빌라 101 · 시간은 가상 경로 기준입니다.")
                    Action("집으로 출발 · 메뉴 추천", s.phase == DrivePhase.IDLE && s.vehicleValid) { vm.destination(MobilityCatalog.home); vm.startTrip() }
                }
                DiningStep.MENU -> {
                    Text("${s.destination.name} · ${MobilityRules.formatTime(s.remaining)}", color = Mint, fontSize = 20.sp)
                    MobilityRules.recommendedMenu(s.remaining).forEachIndexed { index, menu ->
                        Block("${if (index == 0) "추천 · " else ""}${menu.name}", Modifier.fillMaxWidth().border(1.dp, if (index == 0) Mint.copy(alpha = .5f) else Color(0xFF2D424F), RoundedCornerShape(16.dp))) {
                            Hint("${menu.price}원 · 조리 ${menu.prep / 60}분 + 배달 ${menu.delivery / 60}분")
                            Text(if (menu.lead <= s.remaining) "${(s.remaining - menu.lead) / 60}분 뒤 주문 전송으로 도착에 맞춤" else "도착 후 약 ${(menu.lead - s.remaining) / 60}분 대기", color = if (menu.lead <= s.remaining) Mint else Amber, fontSize = 13.sp)
                            Action("${menu.name} 선택", s.canEdit) { vm.selectMenu(menu) }
                        }
                    }
                    if (!s.canEdit) Hint("운전자 주행 중 메뉴 터치는 잠깁니다. 음성으로 추천을 요청하세요.")
                }
                DiningStep.REVIEW -> {
                    val menu = s.selectedMenu!!
                    Text(menu.name, fontSize = 29.sp, color = Mint); Hint("${menu.price}원 · 1개 · ${s.destination.name}에서 수령")
                    Hint("조리·배달 ${menu.lead / 60}분 / 차량 도착까지 ${s.remaining / 60}분")
                    Action("예약 주문 확인", s.canEdit) { vm.confirmMeal() }
                }
                DiningStep.TRACK -> {
                    Text(s.mealStatus, color = Mint, fontSize = 28.sp); Hint("모의 주문 #${s.order?.id?.take(8)} · ${s.order?.menu?.name}")
                    Text("차량  ${if (s.remaining == 0) "도착" else MobilityRules.formatTime(s.remaining)}", fontSize = 22.sp)
                    val order = s.order!!
                    Text("음식  ${if (s.foodReady) "도착 완료" else if (s.elapsed < order.dispatchAt) "예약 대기" else MobilityRules.formatTime(order.dispatchAt + order.menu.lead - s.elapsed)}", fontSize = 22.sp)
                    Hint("조리·배달 ${order.menu.lead / 60}분을 역산해 주문을 전송합니다. 실제 결제·주문은 없습니다.")
                    if (order.lateAtConfirmation > 0) Text("주문 확인 시 예상 추가 대기 ${order.lateAtConfirmation / 60}분", color = Amber)
                    if (s.received) Action("새 식사 여정 시작", click = vm::reset)
                    if (s.phase == DrivePhase.ARRIVED) Action(if (s.received) "음식 수령 완료" else "음식 확인 · 수령 완료", s.foodReady && !s.received && s.vehicleValid, vm::receive)
                }
            }
            HorizontalDivider(color = Color(0xFF34505B))
            Hint(if (s.role == CabinRole.DRIVER) "운전자 음성 안내" else "동승자 터치 조작 · 실제 탑승 감지 아님")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = listen) { Text("음성 입력") }; OutlinedButton(onClick = { speak("${s.destination.name}까지 ${s.remaining / 60}분 남았습니다. ${s.note}") }) { Text("안내 듣기") } }
            Hint("음성 서비스가 없는 기기는 발표 설정의 모의 음성 입력으로 시연합니다.")
        }
    })
}
@Composable private fun Need(label: String, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled) { change(!selected) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, onCheckedChange = change, enabled = enabled); Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
    }
}
@Composable private fun AccessPage(s: MobilityState, vm: MobilityViewModel, wide: Boolean) {
    Split(wide, left = { JourneyMap(s, vm) }, right = {
        Block(if (s.exitProgress == 100) "무단차 입구 도착" else "내리기 편한 마지막 위치", Modifier.fillMaxWidth()) {
            Hint("진단명이나 증명 대신 필요한 공간과 도움만 선택하세요.")
            if (!s.accessTrip || s.phase == DrivePhase.IDLE) {
                Need("휠체어 옆문 공간 필요", s.needs.wheelchair, s.canEdit) { vm.needs(s.needs.copy(wheelchair = it)) }
                Need("더 넓은 옆문 공간", s.needs.wide, s.canEdit) { vm.needs(s.needs.copy(wide = it)) }
                Need("완만한 경사 우선", s.needs.gentle, s.canEdit) { vm.needs(s.needs.copy(gentle = it)) }
                Need("직원 승하차 도움 요청", s.needs.staff, s.canEdit) { vm.needs(s.needs.copy(staff = it)) }
                MobilityRules.assess(s.needs).forEach { row ->
                    Column(Modifier.fillMaxWidth().background(if (row.eligible) Color(0xFF193A35) else Ink, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.bay.name, color = if (row.eligible) Mint else Amber, fontSize = 18.sp)
                        Hint("입구까지 ${row.bay.walk}m · 경사 ${row.bay.slope}% · 옆문 ${row.bay.side}m")
                        Hint(if (row.eligible) "무단차 연결 · 차도 횡단 없음" else "제외: ${row.reasons.joinToString(" / ")}")
                    }
                }
                Action("조건에 맞는 하차 베이 선택", s.canEdit, vm::selectBay)
                if (s.bay != null) Action("${s.bay.id} 하차 지점으로 출발", s.vehicleValid) { vm.startTrip(true) }
            } else {
                Text(s.bay?.name ?: "하차 베이", fontSize = 24.sp, color = Mint)
                Hint("이동 ${s.bay?.walk}m · 경사 ${s.bay?.slope}% · 옆문 ${s.bay?.side}m")
                if (s.moving) { Hint("정문 앞 계단과 차도 횡단을 피해 무단차 베이로 접근합니다."); Action("경로 보기") { vm.page(1) } }
                if (s.phase == DrivePhase.ARRIVED && !s.accessApproved) {
                    Status("P단 정차 · 하차 전 조건 확인")
                    Need("옆문과 보행로 공간 확인", s.spaceChecked, s.vehicleValid) { vm.check("space", it) }
                    if (s.needs.wheelchair) Need("차량 램프·리프트 준비 확인 (모의)", s.rampReady, s.vehicleValid) { vm.check("ramp", it) }
                    if (s.needs.staff) Need("직원 지원 수락 확인", s.staffAccepted, s.vehicleValid) { vm.check("staff", it) }
                    Need("시연용 측면 장애물 주입", s.obstacle, s.vehicleValid) { vm.check("obstacle", it) }
                    Action("주변 확인 · 하차 안내 시작", MobilityRules.canExit(s), vm::approveExit)
                }
                if (s.exitHeld) Text("보류 · 조건을 다시 확인하고 재승인해 주세요.", color = Amber, fontSize = 18.sp)
                if (s.accessApproved) { LinearProgressIndicator(progress = { s.exitProgress / 100f }, modifier = Modifier.fillMaxWidth()); Text(if (s.exitProgress == 100) "입구 도착 · 차량 정차 유지" else "${if (s.needs.wheelchair) "휠체어" else "보행 지원"} · 무단차 입구로 이동 중", color = Mint) }
            }
            Hint("접근성 수치는 시연용 가정값이며 인증이 아닙니다. 실제 도어·리프트를 제어하지 않습니다.")
        }
    })
}
@Composable private fun HandoffPage(s: MobilityState, vm: MobilityViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Block("주소보다 정확한 현장 인계", Modifier.fillMaxWidth()) {
            Hint("한강처럼 주소로 설명하기 어려운 곳에서는 정차 베이와 주변 기준점을 함께 확인합니다.")
            Text(if (s.destination.id == "RIVER") "B-12 표지판 · 강변 진입로 3번" else s.destination.name, color = Mint, fontSize = 26.sp)
            if (s.journey != null) JourneyViewport(s, vm::sceneView) else Hint("홈의 ‘한강 위치 확인’ 여정을 시작하면 APK 내부 가상 공간의 전후좌우 시야를 볼 수 있습니다.")
            Hint(s.locationLabel + if (s.lat != null) "\n${s.lat}, ${s.lon}" else "\n가상 지점 ${s.destination.name} · 실제 GPS 좌표 아님")
            Action("인계 위치 카드 확인", !s.moving && s.vehicleValid, vm::shareLocation)
            if (s.journey?.current?.id == "LOCATE") Action("모의 배달원에게 확인 위치 전달", s.canShareLocation, vm::shareInScene)
            Action("위치 카드 공유창 열기", s.canShareLocation) {
                val current = vm.currentShareState()
                if (current == null) { vm.note("위치 카드를 다시 확인해 주세요."); return@Action }
                val text = "CurbLink 인계 위치\n${current.destination.name}\n${if (current.destination.id == "RIVER") "B-12 표지판 · 강변 진입로 3번" else "지정된 정차 지점"}\n${if (current.lat != null) "Android 위치: ${current.lat}, ${current.lon} (${current.locationLabel})" else "모의 위치 카드 · 실제 위치 아님"}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "인계 위치 공유"))
            }
            if (s.order != null) Action(if (s.received) "인계 완료" else "음식 확인 · 수령 완료", s.phase == DrivePhase.ARRIVED && s.foodReady && !s.received && s.vehicleValid, vm::receive)
            Hint("주변 카메라·배달 서비스는 연동되어 있지 않습니다. 외부 공유는 사용자가 공유창에서 직접 선택합니다.")
        }
    }
}
@Composable private fun SettingsPanel(s: MobilityState, vm: MobilityViewModel) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF243333), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("발표용 모의 입력 · 운전자 제품 조작과 구분", color = Amber, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.voice("메뉴 추천") }) { Text("모의 음성: 추천해 줘") }
            OutlinedButton(onClick = { vm.voice("주문해 줘") }) { Text("모의 음성: 주문해 줘") }
            OutlinedButton(onClick = vm::validity) { Text(if (s.vehicleValid) "차량 상태 무효 주입" else "정상 상태 복구") }
            if (s.accessApproved) OutlinedButton(onClick = { vm.check("obstacle", true) }) { Text("하차 중 장애물 주입") }
            OutlinedButton(onClick = vm::reset) { Text("시연 초기화") }
            OutlinedButton(onClick = vm::laneQueue) { Text("2차선 대기열 전환") }
            OutlinedButton(onClick = vm::laneValidity) { Text(if(s.laneDataValid) "차선 정보 만료 주입" else "차선 정보 복구") }
            OutlinedButton(onClick = { vm.advanceDemo() }) { Text("모의 시간 30초 진행") }
        }
    }
}
@Composable private fun JourneyMap(s: MobilityState, vm: MobilityViewModel) {
    if (s.journey != null) { JourneyViewport(s, vm::sceneView); return }
    Column(Modifier.fillMaxWidth().background(Color(0xFF10212D), RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${s.destination.name}", color = Mint, fontSize = 18.sp); Status("오프라인 모의 지도", Muted) }
        Canvas(Modifier.fillMaxWidth().height(390.dp).semantics { contentDescription = "현재 위치와 목적지, 선택된 하차 베이를 표시한 가상 경로 지도" }) {
            val w = size.width; val h = size.height
            fun point(x: Float, y: Float) = Offset(x / 100 * w, y / 100 * h)
            drawRoundRect(Color(0xFF162D36), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f))
            drawRect(Color(0xFF204C60), topLeft = point(80f, 0f), size = Size(w * .2f, h))
            for (x in listOf(5f, 28f, 51f)) for (y in listOf(8f, 34f, 60f)) drawRoundRect(Color(0xFF263D45), topLeft = point(x, y), size = Size(w * .14f, h * .16f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
            for (x in listOf(23f, 47f, 73f)) drawLine(Color(0xFF50616A), point(x, 0f), point(x, 100f), 14f)
            for (y in listOf(27f, 55f, 84f)) drawLine(Color(0xFF50616A), point(0f, y), point(80f, y), 14f)
            val end = when (s.destination.id) { "HOME" -> MapPoint(58f, 18f); "CULTURE" -> MapPoint(66f, if (s.bay?.id == "A3") 15f else 30f); else -> MapPoint(76f, 52f) }
            val pts = listOf(MapPoint(23f, 91f), MapPoint(23f, 55f), MapPoint(47f, 55f), MapPoint(47f, end.y), end)
            val path = Path().apply { pts.forEachIndexed { i, p -> val o = point(p.x, p.y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, Mint.copy(alpha = .15f), style = Stroke(20f, cap = StrokeCap.Round)); drawPath(path, Mint, style = Stroke(5f, cap = StrokeCap.Round))
            val lengths = pts.zipWithNext { a, b -> kotlin.math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat() }
            var traveled = lengths.sum() * s.progress
            var position = pts.first()
            for (i in lengths.indices) { if (traveled <= lengths[i] || i == lengths.lastIndex) { val f = (traveled / lengths[i]).coerceIn(0f, 1f); position = MapPoint(pts[i].x + (pts[i + 1].x - pts[i].x) * f, pts[i].y + (pts[i + 1].y - pts[i].y) * f); break }; traveled -= lengths[i] }
            drawCircle(Mint.copy(alpha = .16f), 20f, point(position.x, position.y)); drawCircle(Mint, 9f, point(position.x, position.y)); drawCircle(Color.White, 3f, point(position.x, position.y))
            drawCircle(Amber, 10f, point(end.x, end.y)); drawCircle(Ink, 4f, point(end.x, end.y))
            if (s.accessTrip) { drawLine(Color(0xFF85C79C), point(end.x, end.y), point(76f, end.y), 10f); val fraction = s.exitProgress / 100f; if (s.accessApproved) drawCircle(Color.White, 7f, point(end.x + (76f - end.x) * fraction, end.y)) }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(184, 211, 218); textSize = 12.dp.toPx() }
            drawContext.canvas.nativeCanvas.drawText("중앙로", w * .25f, h * .9f, paint)
            drawContext.canvas.nativeCanvas.drawText(if (s.destination.id == "RIVER") "B-12" else s.bay?.id ?: "도착", point(end.x, end.y).x - 15, point(end.x, end.y).y - 18, paint)
        }
        Text(if (s.phase == DrivePhase.ARRIVED) "도착 · 정차 후 다음 행동을 확인하세요" else "남은 거리 ${"%.1f".format(s.distance / 1000f)} km · ${MobilityRules.formatTime(s.remaining)}", color = Muted, fontSize = 14.sp)
    }
}
