package io.curblink.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// All-New TMAP / Naver Map / Kakao Navi Design System — 150% In-Vehicle Large Scale Edition
private val CoolGrayBg = Color(0xFFF2F4F8)
private val CoolGraySurface = Color(0xFFE5EAF2)
private val PureWhite = Color(0xFFFFFFFF)
private val BorderCoolGray = Color(0xFFD5DCE8)
private val TmapBlue = Color(0xFF2563EB)
private val TmapIndigo = Color(0xFF4F46E5)
private val TmapBlueSoft = Color(0xFFEFF6FF)
private val RoadSignGreen = Color(0xFF00875A)
private val RoadSignGreenDark = Color(0xFF006644)
private val NaverGreen = Color(0xFF03C75A)
private val GreenSoft = Color(0xFFECFDF5)
private val TrafficAmber = Color(0xFFD97706)
private val AmberSoft = Color(0xFFFEF3C7)
private val TrafficRed = Color(0xFFDC2626)
private val RedSoft = Color(0xFFFEE2E2)
private val InkStrong = Color(0xFF111827)
private val InkBody = Color(0xFF374151)
private val InkCaption = Color(0xFF6B7280)

private fun formatWon(price: Int): String = "%,d원".format(price)

@Composable
fun MobilityApp(vm: MobilityViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) vm.actualGps() else vm.actualGps()
    }
    fun gps() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) vm.actualGps()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::voice)
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (intent.resolveActivity(context.packageManager) != null) voice.launch(intent)
        else vm.note("음성 대신 화면의 매장·메뉴 버튼을 눌러 바로 진행하실 수 있습니다.")
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TmapBlue,
            onPrimary = PureWhite,
            primaryContainer = TmapBlueSoft,
            onPrimaryContainer = TmapBlue,
            secondary = RoadSignGreen,
            background = CoolGrayBg,
            surface = PureWhite,
            onSurface = InkStrong
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = CoolGrayBg) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= 900.dp
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TmapHeroHeader(s, vm, wide, ::listen)

                    if (s.source == VehicleSource.TEST) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(AmberSoft, RoundedCornerShape(16.dp))
                                .border(1.dp, TrafficAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(12.dp).background(TrafficAmber, CircleShape))
                                Text("개발 검증용 데이터 · 실제 거래·차량 제어 미연결", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrafficAmber)
                            }
                            Text("TEST FIXTURE", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TrafficAmber)
                        }
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (s.page) {
                            0 -> ArrivalScreen(s, vm, wide)
                            1 -> MealScreen(s, vm, wide, ::listen, ::gps)
                            2 -> AccessScreen(s, vm, wide, ::gps)
                            else -> HandoffScreen(s, vm, wide, ::gps) {
                                val current = vm.currentShareState()
                                if (current == null) vm.note("현재 인계 위치를 다시 확인해 주세요.") else {
                                    val text = "${if (current.source == VehicleSource.TEST) "개발 검증용 위치 카드" else "CurbLink 인계 위치"}\n${current.destination.name}\n${current.locationLabel}${if (current.lat != null) "\n${current.lat}, ${current.lon}" else ""}"
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                            "인계 위치 공유"
                                        )
                                    )
                                }
                            }
                        }
                    }

                    BottomRouteBar(s, vm, ::listen)
                }
            }
        }
    }
}

/**
 * 150% Scaled All-New TMAP Gradient Header + 3D Asset Launcher Tiles
 */
@Composable
private fun TmapHeroHeader(s: MobilityState, vm: MobilityViewModel, wide: Boolean, listen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E3A8A), TmapBlue, TmapIndigo)
                )
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(PureWhite, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = TmapBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("CurbLink", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    Text("ALL-NEW DRIVE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PureWhite.copy(alpha = 0.85f))
                }
            }

            // Large Search / Destination Bar
            Row(
                Modifier
                    .weight(1f)
                    .height(62.dp)
                    .clip(RoundedCornerShape(31.dp))
                    .background(PureWhite)
                    .clickable { vm.page(0) }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (s.routeReady) NaverGreen else TrafficAmber, CircleShape)
                    )
                    Text(
                        if (s.routeReady || s.source == VehicleSource.TEST) "목적지 · ${s.destination.name}"
                        else "어디로 갈까요? 목적지를 선택해 주세요",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(TmapBlueSoft)
                        .clickable { listen() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(10.dp).background(TmapBlue, CircleShape))
                    Text("음성인식", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                }
            }

            // Large Gear Badge
            Row(
                Modifier
                    .height(62.dp)
                    .background(PureWhite.copy(alpha = 0.18f), RoundedCornerShape(31.dp))
                    .border(2.dp, PureWhite.copy(alpha = 0.35f), RoundedCornerShape(31.dp))
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(
                            if (!s.vehicleValid) TrafficAmber else if (s.moving) NaverGreen else PureWhite,
                            CircleShape
                        )
                )
                Text(
                    if (!s.vehicleValid) "차량 연결 대기" else if (s.moving) "${s.gear} · 주행 중" else "P · 정차",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            }
        }

        // 150% Scaled 3D-Asset Category Tabs
        val categories = listOf(
            Triple("도착 안내", "내비 · 주차 · 픽업", 0),
            Triple("식사 주문", "도착시간 맞춤 매장", 1),
            Triple("하차 지원", "배리어프리 베이", 2),
            Triple("현장 인계", "정밀 위치 카드", 3)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            categories.forEach { (label, desc, idx) ->
                val selected = s.page == idx
                Row(
                    Modifier
                        .weight(1f, fill = wide)
                        .heightIn(min = 88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) PureWhite else PureWhite.copy(alpha = 0.15f))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) PureWhite else PureWhite.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { vm.page(idx) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ThreeDAssetIcon(index = idx, selected = selected)
                    Column {
                        Text(
                            label,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) InkStrong else PureWhite
                        )
                        if (wide) {
                            Text(
                                desc,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) TmapBlue else PureWhite.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeDAssetIcon(index: Int, selected: Boolean) {
    Canvas(Modifier.size(58.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = when (index) {
                    0 -> listOf(Color(0xFFDBEAFE), Color(0xFF93C5FD))
                    1 -> listOf(Color(0xFFFEF3C7), Color(0xFFFCD34D))
                    2 -> listOf(Color(0xFFD1FAE5), Color(0xFF6EE7B7))
                    else -> listOf(Color(0xFFEDE9FE), Color(0xFFC4B5FD))
                }
            ),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = if (selected) 0.75f else 0.45f),
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.84f, h * 0.35f),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        when (index) {
            0 -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.20f)
                    lineTo(w * 0.78f, h * 0.76f)
                    lineTo(w * 0.5f, h * 0.63f)
                    lineTo(w * 0.22f, h * 0.76f)
                    close()
                }
                drawPath(path, color = Color(0xFF1D4ED8))
            }
            1 -> {
                drawArc(
                    color = Color(0xFFD97706),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.20f, h * 0.30f),
                    size = Size(w * 0.60f, h * 0.52f)
                )
                drawRoundRect(
                    color = Color(0xFF92400E),
                    topLeft = Offset(w * 0.16f, h * 0.58f),
                    size = Size(w * 0.68f, h * 0.15f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            2 -> {
                drawCircle(
                    color = Color(0xFF047857),
                    radius = w * 0.12f,
                    center = Offset(w * 0.48f, h * 0.30f)
                )
                drawArc(
                    color = Color(0xFF059669),
                    startAngle = 20f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(w * 0.26f, h * 0.40f),
                    size = Size(w * 0.42f, h * 0.38f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            else -> {
                drawCircle(
                    color = Color(0xFF6D28D9),
                    radius = w * 0.22f,
                    center = Offset(w * 0.5f, h * 0.44f)
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.09f,
                    center = Offset(w * 0.5f, h * 0.44f)
                )
            }
        }
    }
}

/**
 * 150% Scaled Korean Highway Road-Sign Header + Route Segment Progress Bar
 */
@Composable
private fun TripSummary(s: MobilityState) {
    val hasRoute = s.routeReady || s.source == VehicleSource.TEST
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PureWhite)
            .border(1.dp, BorderCoolGray, RoundedCornerShape(24.dp))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(RoadSignGreenDark, RoadSignGreen)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .background(PureWhite, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("도착 안내", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoadSignGreenDark)
                    }
                    Text(
                        s.purpose.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
                Box(
                    Modifier
                        .background(PureWhite.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .border(1.dp, PureWhite.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (s.moving) "▶ ${s.purpose.label} 진입 준비"
                        else if (s.phase == DrivePhase.ARRIVED) "★ 목적지 도착 완료"
                        else "● 정차 확인",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
            }

            Text(
                if (hasRoute) s.destination.name else "목적지를 기다리고 있습니다",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite
            )
            if (hasRoute && s.destination.subtitle.isNotBlank()) {
                Text(s.destination.subtitle, fontSize = 19.sp, fontWeight = FontWeight.Normal, color = PureWhite.copy(alpha = 0.9f))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (s.routeReady) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("도착까지 남은 시간 (ETA)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkCaption)
                        Text(
                            MobilityRules.formatTime(s.remaining),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = TmapBlue
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("남은 거리", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkCaption)
                        Text(
                            if (s.distance < 1000) "${s.distance}m" else "%.1f km".format(s.distance / 1000f),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkStrong
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(CoolGraySurface),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.weight(0.55f).fillMaxHeight().background(NaverGreen))
                        Box(Modifier.weight(0.25f).fillMaxHeight().background(TmapBlue))
                        Box(Modifier.weight(0.20f).fillMaxHeight().background(RoadSignGreen))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("출발 · 본선 원활", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NaverGreen)
                        Text("접근 차선 변경", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                        Text("마지막 500m · ${s.purpose.label}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoadSignGreen)
                    }
                }
            } else {
                Text(
                    "내비게이션에서 목적지와 도착 예정 시간을 받으면 안내가 표시됩니다.",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = InkBody
                )
            }

            if (s.moving && s.routeReady) {
                val advice = s.laneAdvice
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(TmapBlueSoft, RoundedCornerShape(18.dp))
                        .border(2.dp, TmapBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("실시간 권장 차선 안내", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                        StatusPill(advice.lane?.let { "$it 차선 권장" } ?: "차선 안내 대기", TmapBlue, PureWhite)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (1..4).forEach { laneNum ->
                            val isRec = advice.lane == laneNum
                            val isCur = s.currentLane == laneNum
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .background(
                                        if (isRec) TmapBlue else if (isCur) PureWhite else CoolGrayBg,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        2.dp,
                                        if (isRec) TmapBlue else if (isCur) TmapBlue else BorderCoolGray,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${laneNum}차선",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRec) PureWhite else if (isCur) TmapBlue else InkCaption
                                )
                            }
                        }
                    }
                    Text(
                        if (s.laneDataValid) advice.reason else "차선 정보를 연결하면 권장 차선을 표시합니다.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = InkBody
                    )
                }
            }

            if (!s.vehicleValid) {
                Text(
                    "차량 정보를 확인할 수 없어 주행 중 조작을 제한하고 있습니다.",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TrafficAmber
                )
            }
        }
    }
}

@Composable
private fun UnifiedCard(
    title: String,
    badge: String? = null,
    badgeColor: Color = TmapBlue,
    bottomActionText: String? = null,
    bottomActionEnabled: Boolean = true,
    onBottomAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PureWhite)
            .border(1.dp, BorderCoolGray, RoundedCornerShape(24.dp))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                if (badge != null) {
                    StatusPill(badge, badgeColor, badgeColor.copy(alpha = 0.12f))
                }
            }
            content()
        }

        if (bottomActionText != null && onBottomAction != null) {
            val bg = if (bottomActionEnabled) TmapBlue else CoolGraySurface
            val fg = if (bottomActionEnabled) PureWhite else InkCaption
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(bg)
                    .clickable(enabled = bottomActionEnabled, onClick = onBottomAction),
                contentAlignment = Alignment.Center
            ) {
                Text(bottomActionText, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = fg)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color = TmapBlue, bg: Color = TmapBlueSoft) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Hint(text: String, color: Color = InkBody) {
    Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Normal, lineHeight = 29.sp)
}

@Composable
private fun ActionButton(text: String, enabled: Boolean = true, primary: Boolean = true, click: () -> Unit) {
    Button(
        onClick = click,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) TmapBlue else CoolGrayBg,
            contentColor = if (primary) PureWhite else InkStrong,
            disabledContainerColor = CoolGraySurface,
            disabledContentColor = InkCaption
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (primary) Arrangement.Center else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!primary) {
                Text("›", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
            }
        }
    }
}

@Composable
private fun Panels(wide: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(22.dp)) { left() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) { right() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) { left(); right() }
    }
}

@Composable
private fun Connection(title: String, description: String) {
    UnifiedCard(title, badge = "연결 대기", badgeColor = TrafficAmber) {
        Hint(description)
    }
}

@Composable
private fun ArrivalScreen(s: MobilityState, vm: MobilityViewModel, wide: Boolean) {
    Panels(
        wide = wide,
        left = {
            TripSummary(s)
            LaneExplorationCard(s, vm)
        },
        right = {
            CurbZoneExplorerCard(s, vm)
            if (s.servicesReady) {
                UnifiedCard("목적지 설정 · 시나리오 프리셋", badge = "클릭하여 경로 탐색") {
                    Hint("목적지를 선택하면 예상 도착 시간(ETA)과 1~4차선 최적 진입 경로, 주변 식당·하차 베이를 즉시 탐색합니다.")
                    MobilityCatalog.places.forEach { place ->
                        val isCurrent = s.destination.id == place.id
                        ActionButton(
                            text = "${place.name} (${place.subtitle}) · 약 ${place.eta / 60}분",
                            enabled = s.canEdit,
                            primary = isCurrent
                        ) { vm.destination(place) }
                    }
                }
            }
            UnifiedCard("도착 후 연계 서비스", badge = "빠른 이동") {
                ActionButton("도착 시간에 맞춰 주변 매장 식사 주문", primary = false) { vm.page(1) }
                ActionButton("배리어프리 무단차 하차 지점 및 지원 확인", primary = false) { vm.page(2) }
                ActionButton("배달원·동승자 현장 인계 위치 카드", primary = false) { vm.page(3) }
            }
        }
    )
}

@Composable
private fun LaneExplorationCard(s: MobilityState, vm: MobilityViewModel) {
    val defaultTraffic = remember {
        mapOf(
            1 to LaneTraffic(queue = 6, signalWait = 18.0),
            2 to LaneTraffic(queue = 5, signalWait = 10.0, blockProbability = 0.8, extraWait = 20.0),
            3 to LaneTraffic(queue = 0, signalWait = 0.0),
            4 to LaneTraffic(queue = 3, signalWait = 8.0, blockProbability = 0.5, extraWait = 12.0)
        )
    }
    val activeTraffic = s.laneTraffic.ifEmpty { defaultTraffic }
    val evalDistance = if (s.remainingMeters != null && s.remainingMeters in 1..1000) s.remainingMeters else 600
    val advice = LaneRecommendation.rank(
        current = s.currentLane,
        purpose = s.purpose,
        distance = evalDistance,
        traffic = activeTraffic,
        valid = s.laneDataValid && s.vehicleValid
    )
    val startThreshold = if (s.purpose == ArrivalPurpose.PARKING) 180 else 450
    val isApproaching = evalDistance <= startThreshold

    UnifiedCard(
        "1~4차선 신호·병목 분석 및 최적 경로 탐색",
        badge = advice.lane?.let { "★ ${it}차선 최적 추천" } ?: "차선 탐색 대기",
        badgeColor = if (advice.lane == 3) RoadSignGreen else TmapBlue
    ) {
        // Explicit simulation explanation banner
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (advice.lane == 3) GreenSoft else TmapBlueSoft, RoundedCornerShape(16.dp))
                .border(2.dp, if (advice.lane == 3) RoadSignGreen else TmapBlue, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (!s.laneDataValid || !s.vehicleValid) "차량·차선 데이터 확인 대기 중"
                else if (isApproaching) "▶ 목적지 전방 ${evalDistance}m 접근 구간 · 4차선(정차·주차 진입 차선) 순차 변경 추천"
                else "★ 본선 직진 최적 경로: 3차선 (직진 전용 차선) 주행 추천",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = if (advice.lane == 3) RoadSignGreenDark else TmapBlue
            )
            Text(
                if (isApproaching)
                    "본선 3차선으로 빠르게 직진한 뒤, 목적지 전방(${startThreshold}m 이내)에서 선택한 '${s.purpose.label}' 구역 진입을 위해 4차선으로 순차 진입합니다."
                else
                    "1·2차선은 좌회전 신호 대기 차량이 섞여 병목이 발생하고, 4차선은 직진 및 우회전 신호·정차 합류로 혼잡하므로 직진 주행 시 유일한 직진 전용인 3차선이 가장 빠르고 안 막힙니다.",
                fontSize = 19.sp,
                fontWeight = FontWeight.Normal,
                color = InkStrong,
                lineHeight = 27.sp
            )
        }

        // 4-Lane Visual Comparison Grid (modeled after simulator/js/lane-engine.js & presentation-ui.jsx)
        val laneMeta = listOf(
            Triple(1, "↰", "좌회전 전용"),
            Triple(2, "↑↰", "직진·좌회전 혼용"),
            Triple(3, "↑", "직진 전용 (최적)"),
            Triple(4, "↑↱", "직진·우회전/진입")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            laneMeta.forEach { (laneId, arrow, turnRole) ->
                val isRec = advice.lane == laneId
                val isCur = s.currentLane == laneId
                val costObj = advice.costs.find { it.lane == laneId }
                val costFinite = costObj != null && costObj.cost.isFinite()
                val bg = when {
                    isRec && laneId == 3 -> GreenSoft
                    isRec -> TmapBlueSoft
                    laneId == 2 -> AmberSoft
                    else -> CoolGrayBg
                }
                val borderCol = when {
                    isRec && laneId == 3 -> RoadSignGreen
                    isRec -> TmapBlue
                    isCur -> InkStrong
                    laneId == 2 -> TrafficAmber
                    else -> BorderCoolGray
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .border(if (isRec || isCur) 3.dp else 1.dp, borderCol, RoundedCornerShape(16.dp))
                        .clickable { vm.selectLane(laneId) }
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        arrow,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRec && laneId == 3) RoadSignGreenDark else if (isRec) TmapBlue else if (laneId == 2) TrafficAmber else InkStrong
                    )
                    Text(
                        "${laneId}차선",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkStrong
                    )
                    Text(
                        turnRole,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRec && laneId == 3) RoadSignGreenDark else if (laneId == 2) TrafficAmber else InkBody
                    )
                    Box(
                        Modifier
                            .background(
                                if (isRec && laneId == 3) RoadSignGreen else if (isRec) TmapBlue else if (laneId == 2) TrafficAmber else CoolGraySurface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            when {
                                isRec -> "★ 최적 추천"
                                laneId == 1 -> "좌회전 제외"
                                laneId == 2 -> "좌회전 병목"
                                laneId == 4 && !isApproaching -> "우회전 혼잡"
                                else -> "대안 경로"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRec || laneId == 2) PureWhite else InkCaption
                        )
                    }
                    Text(
                        if (costFinite) "예상 ${costObj!!.cost.toInt()}초 비용"
                        else if (laneId == 1) "직진 불가"
                        else "본선 비추천",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRec) InkStrong else InkCaption
                    )
                    if (isCur) {
                        Text("● 내 차량 위치", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                    }
                }
            }
        }

        // Interactive Route Stage Explorer Buttons (from simulator/js/presentation-ui.jsx)
        Text("경로 구간별 차선 탐색 시뮬레이션 (클릭하여 구간 전환)", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = InkStrong)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                1 to "1. 본선 직진 (3차선 최적)",
                2 to "2. 2차선 좌회전 병목 회피",
                3 to "3. 목적지 접근 (4차선 진입)"
            ).forEach { (stageNum, label) ->
                val activeStage = when {
                    isApproaching -> stageNum == 3
                    (activeTraffic[2]?.queue ?: 0) >= 7 -> stageNum == 2
                    else -> stageNum == 1
                }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (activeStage) TmapBlue else CoolGrayBg)
                        .border(1.dp, if (activeStage) TmapBlue else BorderCoolGray, RoundedCornerShape(14.dp))
                        .clickable { vm.applyLaneScenario(stageNum) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeStage) PureWhite else InkStrong
                    )
                }
            }
        }
    }
}

@Composable
private fun CurbZoneExplorerCard(s: MobilityState, vm: MobilityViewModel) {
    UnifiedCard("도착 목적 및 최적 정차·주차 구역 탐색", badge = s.purpose.label) {
        Hint("도착 목적을 선택하면 목적지 주변 연석·주차장 구역 중 진입 가능한 최적 지점을 탐색합니다.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ArrivalPurpose.entries.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { purpose ->
                        val selected = s.purpose == purpose
                        val enabled = s.canEdit
                        Row(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) TmapBlueSoft else CoolGrayBg)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) TmapBlue else BorderCoolGray,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(enabled = enabled) { vm.purpose(purpose) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                purpose.label,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!enabled) InkCaption else if (selected) TmapBlue else InkStrong
                            )
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(if (selected) TmapBlue else BorderCoolGray, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // Simulation Curb Zones (from simulator/js/lane-engine.js demoDestination.nodes)
        val zones = listOf(
            Triple("동문 안전 승하차 A-3", "4차선 진입 · 보행 15m · 우회 +3초 · 최대 2분 정차 · 보행로 연결", ArrivalPurpose.PICKUP),
            Triple("단기 정차 베이 B-1", "4차선 진입 · 보행 30m · 우회 +5초 · 최대 5분 비상등 정차", ArrivalPurpose.SHORT_STOP),
            Triple("정식 주차장 P1 (P-07면)", "4차선 진입 · 보행 50m · 차단기 통과 → 통로 → 후진 주차 정렬", ArrivalPurpose.PARKING),
            Triple("무단차 하차 베이 A-2 / A-3", "4차선 진입 · 계단·단차 없음 · 옆문 여유 공간 및 직원 지원 연결", ArrivalPurpose.ACCESS)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            zones.forEach { (zoneName, zoneDesc, matchPurpose) ->
                val matched = s.purpose == matchPurpose
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (matched) GreenSoft else CoolGrayBg)
                        .border(if (matched) 2.dp else 1.dp, if (matched) RoadSignGreen else BorderCoolGray, RoundedCornerShape(14.dp))
                        .clickable(enabled = s.canEdit) {
                            vm.purpose(matchPurpose)
                            if (matchPurpose == ArrivalPurpose.ACCESS) vm.page(2)
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (matched) "★ 최적 구역 · $zoneName" else zoneName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (matched) RoadSignGreenDark else InkStrong
                        )
                        StatusPill(
                            if (matched) "현재 선택됨" else matchPurpose.label,
                            if (matched) RoadSignGreenDark else InkCaption,
                            PureWhite
                        )
                    }
                    Text(zoneDesc, fontSize = 17.sp, color = InkBody)
                }
            }
            // Excluded Red Zone from simulator/js/lane-engine.js
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(RedSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("버스 정류장 R-1 · 정차 금지 구역", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrafficRed)
                Text("RED · 경로 자동 제외", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TrafficRed)
            }
        }
    }
}

@Composable
private fun MealScreen(s: MobilityState, vm: MobilityViewModel, wide: Boolean, listen: () -> Unit, gps: () -> Unit) {
    Panels(
        wide = wide,
        left = {
            TripSummary(s)
            UnifiedCard(
                "도착에 맞춘 준비",
                badge = "조리+배달 동기화",
                bottomActionText = if (s.vehicleValid) "음성으로 확인" else null,
                bottomActionEnabled = s.vehicleValid,
                onBottomAction = if (s.vehicleValid) listen else null
            ) {
                Hint("목적지 근처 식당의 조리 시간과 배달·픽업 시간을 합산해 차량 도착 시각에 딱 맞는 매장을 추천합니다.")
                Hint("메뉴와 금액, 수령 위치를 확인한 뒤에만 주문합니다.")
            }
        },
        right = {
            if (s.order != null) {
                val order = s.order
                val canReceiveNow = s.vehicleValid && s.phase == DrivePhase.ARRIVED && s.foodReady && !s.received
                UnifiedCard(
                    "주문 확인 · ${order.menu.storeName}",
                    badge = if (s.servicesReady) s.mealStatus else "상태 확인 대기",
                    badgeColor = if (s.received) RoadSignGreen else TmapBlue,
                    bottomActionText = if (s.servicesReady) (if (s.received) "✓ 음식 수령 완료" else "음식 확인 · 수령 완료") else null,
                    bottomActionEnabled = canReceiveNow,
                    onBottomAction = if (s.servicesReady) vm::receive else null
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(CoolGrayBg, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (s.servicesReady) s.mealStatus else "주문 상태 확인 대기",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (s.received || s.foodReady) RoadSignGreenDark else TmapBlue
                        )
                        Text(
                            "#${order.id.take(8)} · ${order.menu.name}",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkStrong,
                            fontFamily = FontFamily.Monospace
                        )
                        Hint("${order.menu.storeName} (${order.menu.category}) · ★ ${order.menu.rating}")
                        Hint(
                            if (s.servicesReady) "${formatWon(order.menu.price)} · ${s.destination.name}에서 수령 (조리 ${order.menu.prep / 60}분 + 배달 ${order.menu.delivery / 60}분)"
                            else "${formatWon(order.menu.price)} · 수령 위치는 주문 서비스에서 재확인합니다."
                        )

                        // 4-Stage Order Track from simulator/js/meal-cabin.jsx
                        val stages = listOf("주문 접수", "매장 조리", "배달 이동", "음식 도착")
                        val activeIdx = when (s.mealStatus) {
                            "수령 완료", "음식 도착" -> 3
                            "배달 중" -> 2
                            "조리 중" -> 1
                            else -> 0
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            stages.forEachIndexed { idx, label ->
                                val lit = idx <= activeIdx
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .background(if (lit) TmapBlue else PureWhite, RoundedCornerShape(10.dp))
                                        .border(1.dp, if (lit) TmapBlue else BorderCoolGray, RoundedCornerShape(10.dp))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (lit) PureWhite else InkCaption
                                    )
                                }
                            }
                        }

                        // Vehicle vs Food Arrival Sync Comparison from simulator/js/meal-cabin.jsx
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .background(PureWhite, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text("차량 도착 예정", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkCaption)
                                Text(
                                    if (s.phase == DrivePhase.ARRIVED || s.remaining == 0) "목적지 도착 완료" else MobilityRules.formatTime(s.remaining),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TmapBlue
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .background(PureWhite, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text("음식 도착 상태", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkCaption)
                                Text(
                                    if (s.received) "수령 완료 ✓" else if (s.foodReady) "도착 완료 (수령 가능)" else "도착 시각 맞춤 준비 중",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.foodReady || s.received) RoadSignGreenDark else InkStrong
                                )
                            }
                        }
                    }
                    if (!s.servicesReady) {
                        Hint("저장된 주문 기록입니다. 서비스 연결 후 최신 상태를 확인합니다.", TrafficAmber)
                    } else {
                        if (!canReceiveNow && !s.received && s.canEdit) {
                            ActionButton("목적지 정차 및 음식 도착 완료 상태로 전환 (시연 확인)", enabled = true, primary = true) {
                                vm.markArrivedForCheck()
                            }
                        }
                        if (s.canEdit) {
                            ActionButton("다른 매장·메뉴 다시 선택 (주문 취소)", enabled = true, primary = false) {
                                vm.cancelOrder()
                            }
                        }
                    }
                }
            } else if (!s.servicesReady) {
                Connection("식사 서비스 연결", "이용 가능한 음식점과 메뉴를 연결하면 도착 시간에 맞춘 추천을 받을 수 있습니다.")
            } else {
                // Interactive step indicator + direct store list
                if (s.mealStep == DiningStep.LOCATION || s.mealStep == DiningStep.DESTINATION) {
                    UnifiedCard(
                        if (s.mealStep == DiningStep.LOCATION) "현재 위치 확인" else "도착 준비 시작",
                        badge = "수령지 동기화",
                        bottomActionText = if (s.mealStep == DiningStep.LOCATION) "현재 위치 확인" else "도착 정보로 메뉴 확인",
                        bottomActionEnabled = s.canEdit,
                        onBottomAction = {
                            if (s.mealStep == DiningStep.LOCATION) gps() else vm.startTrip()
                        }
                    ) {
                        Hint("목적지(${s.destination.name}) 도착 예정 시간(${MobilityRules.formatTime(s.remaining)})에 맞춰 주변 가맹점의 조리+배달 시간을 계산합니다.")
                    }
                }

                if (s.mealStep == DiningStep.REVIEW && s.selectedMenu != null) {
                    val menu = s.selectedMenu
                    val diffMin = (s.remaining - menu.lead) / 60
                    UnifiedCard(
                        "주문 전 확인 · ${menu.storeName}",
                        badge = "최종 확인",
                        bottomActionText = "내용 확인 · 주문",
                        bottomActionEnabled = s.canEdit,
                        onBottomAction = { vm.confirmMeal() }
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(TmapBlueSoft, RoundedCornerShape(18.dp))
                                .border(2.dp, TmapBlue, RoundedCornerShape(18.dp))
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("${menu.storeName} · ${menu.category}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                            Text(menu.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                            Text("1개 · ${formatWon(menu.price)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                            HorizontalDivider(color = BorderCoolGray)
                            Hint("수령 위치 · ${s.destination.name} (매장 거리 ${menu.distanceMeters}m)")
                            Hint("예상 준비·배달 ${menu.lead / 60}분 (조리 ${menu.prep / 60}분 + 배달 ${menu.delivery / 60}분)")
                            Text(
                                if (diffMin >= 0) "✓ 차량 도착 약 ${diffMin}분 전에 조리·배달이 완료되도록 자동 발주합니다."
                                else "⚠ 차량 도착 후 약 ${abs(diffMin)}분 뒤 도착 예정입니다.",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (diffMin >= 0) RoadSignGreenDark else TrafficAmber
                            )
                        }
                        ActionButton("다른 매장 메뉴 보기", enabled = s.canEdit, primary = false) {
                            vm.voice("주문 취소")
                        }
                        if (s.moving) Hint("메뉴·금액·수령 위치를 확인하고 음성으로 주문 의사를 말씀해 주세요.", TrafficAmber)
                    }
                } else {
                    // Always display the nearby stores & menus sorted by ETA match so user can immediately see and tap!
                    val etaSec = if (s.remaining > 0) s.remaining else s.destination.eta.coerceAtLeast(900)
                    UnifiedCard(
                        "시간에 맞는 메뉴 · 목적지 인근 매장",
                        badge = "ETA ${etaSec / 60}분 기준 추천"
                    ) {
                        Hint("목적지(${s.destination.name}) 근처 배달·픽업 가맹점입니다. 조리시간+배달시간을 합산해 도착 즉시 수령 가능한 순서로 정렬했습니다.")
                        MobilityRules.recommendedMenu(etaSec).forEach { menu ->
                            val leadMin = menu.lead / 60
                            val etaMin = etaSec / 60
                            val fitText = if (leadMin <= etaMin) "도착 즉시 수령 (여유 ${etaMin - leadMin}분)" else "도착 후 ${leadMin - etaMin}분 대기"
                            val fitColor = if (leadMin <= etaMin) RoadSignGreenDark else TrafficAmber
                            val fitBg = if (leadMin <= etaMin) GreenSoft else AmberSoft
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(CoolGrayBg)
                                    .border(2.dp, BorderCoolGray, RoundedCornerShape(18.dp))
                                    .clickable(enabled = s.canEdit) {
                                        if (!s.gpsReady) vm.observeLocation("Pleos 내비게이션 연동 위치", 37.5042, 126.7235)
                                        vm.selectMenu(menu)
                                    }
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            Modifier
                                                .background(TmapBlue, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(menu.category, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                        }
                                        Text(menu.storeName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                                    }
                                    StatusPill(fitText, fitColor, fitBg)
                                }

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(menu.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TmapBlue)
                                        Text(menu.detail, fontSize = 18.sp, color = InkBody)
                                    }
                                    Text(formatWon(menu.price), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                                }

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(PureWhite, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "조리 ${menu.prep / 60}분 + 배달 ${menu.delivery / 60}분 = 총 ${leadMin}분 소요",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkStrong
                                    )
                                    Text(
                                        "★ ${menu.rating} · ${menu.distanceMeters}m",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkCaption
                                    )
                                }

                                ActionButton("메뉴 확인", s.canEdit) {
                                    if (!s.gpsReady) vm.observeLocation("Pleos 내비게이션 연동 위치", 37.5042, 126.7235)
                                    vm.selectMenu(menu)
                                }
                            }
                        }
                        if (s.moving) Hint("주행 중에는 음성으로 추천과 주문 내용을 확인해 주세요.", TrafficAmber)
                    }
                }
            }
        }
    )
}

@Composable
private fun NeedRow(text: String, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TmapBlueSoft else CoolGrayBg)
            .border(if (selected) 2.dp else 1.dp, if (selected) TmapBlue else BorderCoolGray, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { change(!selected) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = change,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
            colors = CheckboxDefaults.colors(checkedColor = TmapBlue, checkmarkColor = PureWhite)
        )
        Text(
            text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) InkStrong else InkCaption,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CheckStatus(label: String, ready: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CoolGrayBg, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkStrong)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).background(if (ready) NaverGreen else TrafficAmber, CircleShape))
            Text(
                if (ready) "확인됨" else "확인 대기",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (ready) RoadSignGreen else TrafficAmber
            )
        }
    }
}

@Composable
private fun AccessScreen(s: MobilityState, vm: MobilityViewModel, wide: Boolean, gps: () -> Unit) {
    Panels(
        wide = wide,
        left = {
            UnifiedCard("내리기 편한 도착", badge = "배리어프리", badgeColor = RoadSignGreen) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (s.bay != null) TmapBlueSoft else GreenSoft, RoundedCornerShape(18.dp))
                        .border(2.dp, if (s.bay != null) TmapBlue else RoadSignGreen.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        s.bay?.let { "★ 선택된 하차 지점 · ${it.name}" } ?: "필요한 도움을 먼저 알려주세요",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (s.bay != null) TmapBlue else RoadSignGreenDark
                    )
                    s.bay?.let {
                        Hint("입구까지 보행 ${it.walk}m · 경사 ${it.slope}% · 옆문 여유 ${it.side}m · 정문 앞 계단 및 차도 횡단을 피하는 무단차 연결 경로", InkStrong)
                    }
                }
                Hint("필요한 공간과 도움만 선택합니다. 진단명이나 장애 증명을 요구하지 않습니다.")
            }
            UnifiedCard("지원 선호 (필요한 조건 선택)", badge = "맞춤 조건") {
                NeedRow("휠체어 옆문 공간 (1.5m 이상)", s.needs.wheelchair, s.canEdit) { vm.needs(s.needs.copy(wheelchair = it)) }
                NeedRow("더 넓은 옆문 공간 (2.0m 이상)", s.needs.wide, s.canEdit) { vm.needs(s.needs.copy(wide = it)) }
                NeedRow("완만한 경사 우선 (2% 이하)", s.needs.gentle, s.canEdit) { vm.needs(s.needs.copy(gentle = it)) }
                NeedRow("직원 승하차 도움 요청", s.needs.staff, s.canEdit) { vm.needs(s.needs.copy(staff = it)) }
            }
        },
        right = {
            if (!s.servicesReady) {
                Connection("하차 지점 확인", "시설의 입구·무단차 경로·지원 정보를 연결하면 조건에 맞는 지점을 안내합니다.")
            } else {
                // As soon as a bay is selected (via "조건에 맞는 지점 선택" or tapping a bay), show the Safety Check & Exit Approval card right at the top of the right panel!
                if (s.bay != null && s.accessTrip) {
                    if (s.moving) {
                        UnifiedCard("하차 준비 · ${s.bay.name} 접근 중", badge = "정차 후 확인", badgeColor = TrafficAmber) {
                            Hint("선택한 [${s.bay.name}] 위치로 4차선 진입 안내 중입니다. 정차 후 주변 공간과 지원 상태를 확인합니다.")
                        }
                    } else {
                        UnifiedCard(
                            if (s.exitProgress == 100) "✓ 무단차 입구 도착 완료" else "하차 전 안전 확인 · ${s.bay.name}",
                            badge = if (s.exitHeld) "보류 · 다시 확인" else if (s.accessApproved) "하차 안내 진행 중" else "체크 후 승인",
                            badgeColor = if (s.exitHeld) TrafficAmber else RoadSignGreen,
                            bottomActionText = if (s.exitProgress == 100) "다른 하차 지점 다시 선택" else if (s.accessApproved) "✓ 계단 없는 입구 도착 완료 확인" else "주변 확인 · 하차 안내 시작",
                            bottomActionEnabled = if (s.exitProgress == 100) s.canEdit else if (s.accessApproved) s.canEdit else MobilityRules.canExit(
                                if (s.source == VehicleSource.VEHICLE && s.canEdit) s.copy(phase = DrivePhase.ARRIVED) else s
                            ),
                            onBottomAction = {
                                when {
                                    s.exitProgress == 100 -> vm.resetAccessSelection()
                                    s.accessApproved -> vm.completeAccess()
                                    else -> {
                                        if (s.phase != DrivePhase.ARRIVED && s.canEdit) vm.markArrivedForCheck()
                                        vm.approveExit()
                                    }
                                }
                            }
                        ) {
                            Hint("하차 전 아래 안전 조건을 직접 체크하면 하단 [주변 확인 · 하차 안내 시작] 버튼이 활성화됩니다.")
                            NeedRow("1. 옆문과 보행로 여유 공간 확인", s.spaceChecked, s.canEdit && !s.accessApproved) { vm.check("space", it) }
                            if (s.needs.wheelchair) {
                                NeedRow("2. 차량 램프·리프트 준비 확인 (모의)", s.rampReady, s.canEdit && !s.accessApproved) { vm.check("ramp", it) }
                            }
                            if (s.needs.staff) {
                                NeedRow("3. 요청한 현장 직원 지원 수락 확인", s.staffAccepted, s.canEdit && !s.accessApproved) { vm.check("staff", it) }
                            }
                            NeedRow("시연용 측면 장애물 감지 주입 (안전 보류 테스트)", s.obstacle, s.canEdit) { vm.check("obstacle", it) }

                            if (s.obstacle) {
                                Hint("⚠ 측면 장애물이 감지되어 하차 안내를 즉시 보류했습니다. 장애물 해제 후 공간 확인을 다시 체크해 주세요.", TrafficRed)
                            }
                            if (s.exitHeld && !s.obstacle) {
                                Hint("⚠ 상태 변경으로 이전 승인이 보류되었습니다. 공간 확인 체크 후 다시 승인해 주세요.", TrafficAmber)
                            }
                            if (s.accessApproved && s.exitProgress < 100) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(GreenSoft, RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${if (s.needs.wheelchair) "휠체어 이동 공간 확보" else "보행 지원"} · [${s.bay.name}]에서 계단 없는 입구로 이동 중",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoadSignGreenDark
                                    )
                                    Hint("하단 [✓ 계단 없는 입구 도착 완료 확인] 버튼을 누르면 입구 도착이 완료됩니다.")
                                }
                            }
                            if (s.exitProgress == 100) {
                                StatusPill("✓ 문화센터 무단차 입구 도착 완료 · 차량은 P단 정차 유지", RoadSignGreen, GreenSoft)
                            }
                            ActionButton("다른 하차 지점·조건 다시 선택", s.canEdit, primary = false, click = vm::resetAccessSelection)
                            Hint("수치·구역은 시연용 가정값이며 도어·리프트를 직접 제어하지 않습니다.")
                        }
                    }
                }

                // Candidate Bays card
                UnifiedCard(
                    "하차 지점 · 후보 베이 비교",
                    badge = if (s.bay != null) "${s.bay.id} 선택됨 ✓" else "클릭하여 선택",
                    badgeColor = if (s.bay != null) RoadSignGreen else TmapBlue,
                    bottomActionText = if (s.bay == null) "조건에 맞는 지점 선택" else "조건에 맞는 최적 지점 다시 선택",
                    bottomActionEnabled = s.canEdit,
                    onBottomAction = {
                        vm.selectBay()
                    }
                ) {
                    MobilityRules.assess(s.needs).forEach { row ->
                        val isSelected = s.bay?.id == row.bay.id
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) TmapBlueSoft else if (row.eligible) GreenSoft else CoolGrayBg)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) TmapBlue else if (row.eligible) RoadSignGreen else BorderCoolGray,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(enabled = s.canEdit && row.eligible) { vm.chooseBay(row.bay) }
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isSelected) "★ ${row.bay.name}" else row.bay.name,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TmapBlue else if (row.eligible) RoadSignGreenDark else InkCaption
                                )
                                StatusPill(
                                    if (isSelected) "선택 완료 ✓" else if (row.eligible) "추천 적합 (클릭)" else "조건 미충족",
                                    if (isSelected) TmapBlue else if (row.eligible) RoadSignGreenDark else TrafficAmber,
                                    PureWhite
                                )
                            }
                            Hint(
                                if (row.eligible) "보행 이동 ${row.bay.walk}m · 경사 ${row.bay.slope}% · 옆문 ${row.bay.side}m · 차도 횡단 없음"
                                else "제외 사유: ${row.reasons.joinToString(" · ")}"
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun HandoffScreen(s: MobilityState, vm: MobilityViewModel, wide: Boolean, gps: () -> Unit, share: () -> Unit) {
    var selectedLandmark by remember { mutableStateOf("B-12 표지판 · 강변 진입로 3번") }
    val landmarks = listOf(
        "B-12 표지판 · 강변 진입로 3번",
        "강변 산책로 옆 지정 정차 베이 · B-12",
        "${s.destination.name} 정문 픽업 존"
    )
    Panels(
        wide = wide,
        left = {
            UnifiedCard(
                "주소 대신 차량 주변 기준점으로 만나기",
                badge = "서라운드 기준점",
                bottomActionText = "현재 위치 및 기준점 확인",
                bottomActionEnabled = s.canEdit,
                onBottomAction = {
                    gps()
                    vm.shareLocation()
                }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(CoolGrayBg, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (s.routeReady) s.destination.name else "인계 위치를 확인해 주세요",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = TmapBlue
                    )
                    Text("선택 기준점: $selectedLandmark", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RoadSignGreenDark)
                    Hint(if (s.gpsReady) s.locationLabel else "현재 위치 확인 전 (아래 버튼을 눌러 한 번에 확인)")
                    s.lat?.let {
                        Text("GPS 좌표 · %.5f, %.5f".format(it, s.lon), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                    }
                }

                // 4-Directional Surround Landmarks from simulator/js/destination-demo.jsx
                Text("차량 전·후·좌·우 현장 기준점 확인", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "전방 시야 · B-12 표지판 및 정차 베이 확인" to "우측 시야 · 강변 산책로 보행 연결로",
                        "후방 시야 · 강변 진입로 3번 표지" to "좌측 시야 · 정차 구역 안전 여유 공간"
                    ).forEach { (leftLabel, rightLabel) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(leftLabel, rightLabel).forEach { item ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .background(TmapBlueSoft, RoundedCornerShape(12.dp))
                                        .border(1.dp, TmapBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(item, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                                }
                            }
                        }
                    }
                }
                Hint("한강공원·대형 단지처럼 도로명 주소만으로 만나기 어려운 곳에서 정차 베이와 주변 기준점을 함께 확인합니다.")
            }
        },
        right = {
            UnifiedCard(
                "인계 위치 카드 · 배달원/동승자 전달",
                badge = if (s.canShareLocation) "위치 확인됨 · v${s.locationVersion}" else "확인 대기",
                badgeColor = if (s.canShareLocation) RoadSignGreen else TrafficAmber,
                bottomActionText = "인계 위치 공유창 열기",
                bottomActionEnabled = s.canShareLocation,
                onBottomAction = share
            ) {
                Text("배달원·동승자가 확인할 기준점 선택", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkStrong)
                landmarks.forEach { lm ->
                    val isSel = selectedLandmark == lm
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSel) GreenSoft else CoolGrayBg)
                            .border(if (isSel) 2.dp else 1.dp, if (isSel) RoadSignGreen else BorderCoolGray, RoundedCornerShape(14.dp))
                            .clickable { selectedLandmark = lm }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lm, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (isSel) RoadSignGreenDark else InkStrong)
                        if (isSel) Text("선택됨 ✓", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RoadSignGreenDark)
                    }
                }

                ActionButton("이 위치 확인 · 배달원 전달 준비 완료", s.canEdit) {
                    if (!s.gpsReady) gps()
                    vm.shareLocation()
                }
                if (s.order != null) {
                    val canReceive = s.vehicleValid && s.phase == DrivePhase.ARRIVED && s.foodReady && !s.received && (s.destination.id != "RIVER" || s.canShareLocation)
                    if (!canReceive && !s.received && s.canEdit) {
                        ActionButton("배달원 도착 · 음식 도착 상태 전환 (시연 확인)", primary = false) {
                            if (!s.gpsReady) gps()
                            vm.markArrivedForCheck()
                            vm.shareLocation()
                        }
                    }
                    ActionButton(
                        if (s.received) "✓ 음식 수령 및 인계 완료" else "음식 확인 · 수령 완료",
                        canReceive
                    ) { vm.receive() }
                }
            }
        }
    )
}

@Composable
private fun BottomRouteBar(s: MobilityState, vm: MobilityViewModel, listen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PureWhite, RoundedCornerShape(18.dp))
            .border(1.dp, BorderCoolGray, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(if (s.vehicleValid) RoadSignGreen else TrafficAmber, CircleShape)
            )
            Text(
                s.note,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = InkStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.canEdit && s.phase != DrivePhase.ARRIVED) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(GreenSoft)
                        .border(1.dp, RoadSignGreen, RoundedCornerShape(14.dp))
                        .clickable { vm.markArrivedForCheck() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓ 도착 완료 확인", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RoadSignGreenDark)
                }
            }
            if (s.vehicleValid) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(TmapBlue)
                        .clickable { listen() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("● 음성 명령", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                }
            }
        }
    }
}
