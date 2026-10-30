package io.curblink.demo

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Paper=Color(0xFFF1F4F4)
private val Ink=Color(0xFF152E35)
private val Muted=Color(0xFF536970)
private val Teal=Color(0xFF007F73)
private val Mint=Color(0xFFE2F4ED)
private val Line=Color(0xFFD5E1E0)
private val Amber=Color(0xFF985700)
private val Red=Color(0xFFB93F3F)
private val Shape=RoundedCornerShape(24.dp)

@Composable fun MobilityApp(vm:MobilityViewModel){
    val s by vm.state.collectAsState()
    val context=LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var spokenNote by remember { mutableStateOf<String?>(null) }
    var speechSequence by remember { mutableIntStateOf(0) }
    DisposableEffect(context){
        tts=TextToSpeech(context){ttsReady=it==TextToSpeech.SUCCESS}
        onDispose{tts?.stop();tts?.shutdown()}
    }
    LaunchedEffect(spokenNote,ttsReady,speechSequence){if(ttsReady)spokenNote?.let{tts?.language=Locale.KOREAN;tts?.speak(it,TextToSpeech.QUEUE_FLUSH,null,"curblink")}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){vm.actualGps()}
    fun gps(){if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)vm.actualGps()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
    val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        val transcript=result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if(transcript.isNullOrBlank()){vm.voiceUnavailable();spokenNote=vm.state.value.note;speechSequence++}
        else {vm.voice(transcript);spokenNote=vm.state.value.note;speechSequence++}
    }
    fun listen(){
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try{voice.launch(intent)}catch(_:Exception){vm.note("음성 서비스를 사용할 수 없습니다. 안전한 곳에 정차 후 화면을 조작해 주세요.")}
    }
    val photos=s.handoffPhotoUris.map(Uri::parse)
    var photoPickerTrip by remember { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        vm.attachPhotos(uris.map(Uri::toString),photoPickerTrip)
        photoPickerTrip=null
    }
    fun share(){
        val current=vm.currentShareState() ?: return
        val text="CurbLink 현장 인계\n${current.destination.name}\n${current.locationLabel}\n${current.lat}, ${current.lon}\n위치 카드 ${current.locationVersion} · 첨부 ${photos.size}장"
        val intent=if(photos.isEmpty())Intent(Intent.ACTION_SEND).setType("text/plain") else
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(photos)).apply{
                clipData=ClipData.newUri(context.contentResolver,"현장 사진",photos.first()).also{clip->photos.drop(1).forEach{clip.addItem(ClipData.Item(it))}}
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        intent.putExtra(Intent.EXTRA_TEXT,text)
        if(intent.resolveActivity(context.packageManager)==null){vm.note("공유할 수 있는 앱이 없습니다. 사진과 위치 카드는 유지합니다. 공유 앱 설치 후 정차 상태에서 다시 시도해 주세요.");return}
        try{context.startActivity(Intent.createChooser(intent,"확인한 위치와 사진 공유"));vm.note("공유창을 열었습니다. 전달 완료 여부는 선택한 앱에서 확인해 주세요.")}catch(_:Exception){vm.shareUnavailable()}
    }
    MaterialTheme(colorScheme=lightColorScheme(primary=Teal,onPrimary=Color.White,background=Paper,surface=Color.White,onSurface=Ink)){
        Surface(Modifier.fillMaxSize(),color=Paper){BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()){
            val wide=maxWidth>=840.dp
            val compact=maxHeight<620.dp
            Column(Modifier.fillMaxSize().padding(if(compact)12.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                    Box(Modifier.size(44.dp).background(Ink,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Text("C",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)}
                    Column(Modifier.weight(1f)){Text("CurbLink",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Ink);if(!compact)Text("도착 이후까지, 하나의 여정",color=Muted,fontSize=14.sp)}
                    Badge(if(s.vehicleValid)"${s.gear} · 차량 연결" else "차량 연결 대기",if(s.vehicleValid)Teal else Amber)
                    Button(onClick=::listen,shape=RoundedCornerShape(16.dp)){Text("음성 명령",fontSize=16.sp)}
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("01  도착 안내","02  도착 맞춤 식사","03  편안한 하차","04  현장 인계").forEachIndexed{i,label->
                        FilterChip(selected=s.page==i,onClick={vm.page(i)},label={Text(label,fontSize=16.sp,fontWeight=FontWeight.SemiBold)},modifier=Modifier.heightIn(min=48.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())){
                    when(s.page){
                        0->Arrival(s,vm,wide)
                        1->Dining(s,vm,wide,::gps,::listen)
                        2->Access(s,vm,wide)
                        else->Handoff(s,vm,wide,photos.size,::gps,{photoPickerTrip=s.tripId;try{picker.launch(arrayOf("image/*"))}catch(_:Exception){photoPickerTrip=null;vm.note("사진 선택기를 열지 못했습니다. 기존 첨부는 유지합니다. 정차 후 사진 앱을 확인하고 다시 시도해 주세요.")}},::share)
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(18.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Column(Modifier.weight(1f)){Text(s.note,color=Ink,fontSize=15.sp);if(!compact)Text(if(s.source==VehicleSource.TEST)"외부 시험 입력 · 실거래·실카메라 아님" else "주문·시설·카메라: 제공자 미연결 · 견본 데이터",color=Muted,fontSize=12.sp)}
                    if(s.page==0)OutlinedButton(onClick=vm::markArrivedForCheck,enabled=s.canEdit){Text("정차 후 도착 확인")}
                }
            }
        }}
    }
}

@Composable private fun Badge(text:String,color:Color=Teal){Text(text,color=color,fontSize=13.sp,fontWeight=FontWeight.Bold,modifier=Modifier.background(color.copy(alpha=.09f),RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=8.dp))}
@Composable private fun Copy(text:String,muted:Boolean=true){Text(text,fontSize=16.sp,color=if(muted)Muted else Ink)}
@Composable private fun Panel(title:String,eyebrow:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Column(modifier.fillMaxWidth().background(Color.White,Shape).border(1.dp,Line,Shape).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        if(eyebrow.isNotEmpty())Text(eyebrow,color=Teal,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
        Text(title,color=Ink,fontSize=24.sp,fontWeight=FontWeight.Bold)
        content()
    }
}
@Composable private fun Split(wide:Boolean,left:@Composable ColumnScope.()->Unit,right:@Composable ColumnScope.()->Unit){
    if(wide)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
        Column(Modifier.weight(1.12f),verticalArrangement=Arrangement.spacedBy(16.dp),content=left)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp),content=right)
    }else Column(verticalArrangement=Arrangement.spacedBy(16.dp)){left();right()}
}
@Composable private fun Primary(text:String,enabled:Boolean=true,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)}}
@Composable private fun Choice(title:String,detail:String,selected:Boolean,enabled:Boolean=true,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if(selected)Mint else Paper).border(if(selected)2.dp else 1.dp,if(selected)Teal else Line,RoundedCornerShape(16.dp)).clickable(enabled=enabled,onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f)){Text(title,color=Ink,fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text(detail,color=Muted,fontSize=14.sp)}
        Text(if(selected)"●" else "○",color=if(selected)Teal else Muted,fontSize=22.sp)
    }
}
@Composable private fun Trip(s:MobilityState){
    Column(Modifier.fillMaxWidth().background(Ink,Shape).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("YOUR NEXT ARRIVAL",color=Color(0xFF96D8CA),fontSize=12.sp,letterSpacing=3.sp)
        Text(s.destination.name,color=Color.White,fontSize=30.sp,fontWeight=FontWeight.Bold)
        Text(if(s.routeReady)"경로 관측 연결 · ${s.purpose.label}" else "앱에서 선택한 목적지 · 경로 제공자 확인 대기",color=Color(0xFFBCD0D3),fontSize=14.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column{Text("${(s.remaining+59)/60}분",color=Color.White,fontSize=42.sp,fontWeight=FontWeight.Bold);Text(if(s.routeReady)"도착까지" else "견본 예상 시간",color=Color(0xFFBCD0D3),fontSize=14.sp)}
            Column(horizontalAlignment=Alignment.End){Text("%.1f km".format(s.distance/1000.0),color=Color.White,fontSize=26.sp,fontWeight=FontWeight.SemiBold);Text("마지막 500m까지 준비",color=Color(0xFFBCD0D3),fontSize=14.sp)}
        }
        LinearProgressIndicator(progress={s.progress},modifier=Modifier.fillMaxWidth().height(5.dp),color=Color(0xFF67D8BA),trackColor=Color(0xFF385158))
    }
}
@Composable private fun Arrival(s:MobilityState,vm:MobilityViewModel,wide:Boolean){Split(wide,{
    Trip(s)
    Panel("다음 차선을, 미리", "LANE INTELLIGENCE"){
        val advice=s.laneAdvice
        Text(advice.nextAction,color=Ink,fontSize=22.sp,fontWeight=FontWeight.Bold)
        if(s.moving && s.routeReady)Badge("목적지까지 ${s.distance}m")
        Badge(advice.lane?.let{"${it}차선 추천"}?:"차선 관측 대기",if(advice.lane!=null)Teal else Amber)
        LaneRoad(advice.lane,s.currentLane.takeIf{s.laneDataValid})
        Copy(advice.reason,false)
        if(advice.nextLane!=null)Copy("목표 ${advice.lane}차선 · 다음 인접 ${advice.nextLane}차선. 변경 가능 여부는 현장에서 확인합니다.")
        if(s.laneContext.exitMeters!=null)Badge("분기점 ${s.laneContext.exitMeters}m · 급한 끼어들기 금지",Amber)
        if(!s.laneDataValid)Copy("실제 차선·신호·대기열 제공자 미연결. 임의 추천을 표시하지 않습니다.")
    }
},{
    Panel("어디로 가시나요?","DESTINATION, IN THIS APP"){
        Copy("목적지를 먼저 고르세요. 도착할 때 필요한 서비스까지 함께 준비합니다.")
        MobilityCatalog.places.forEach{place->Choice(place.name,"${place.subtitle} · 견본 ${place.eta/60}분",s.destination.id==place.id,s.canEdit){vm.destination(place)}}
        if(!s.canEdit)Copy("P단 정차와 차량 상태 확인 후 목적지를 변경할 수 있습니다.")
    }
    Panel("도착 목적","THE LAST 500 METERS"){
        ArrivalPurpose.entries.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach{purpose->FilterChip(selected=s.purpose==purpose,onClick={vm.purpose(purpose)},enabled=s.canEdit,label={Text(purpose.label)},modifier=Modifier.weight(1f))}}}
        val zone=when(s.purpose){ArrivalPurpose.PICKUP->"동문 승하차 A-3 · 보행 15m";ArrivalPurpose.SHORT_STOP->"단기 정차 B-1 · 최대 5분";ArrivalPurpose.PARKING->"정식 주차장 P1 · 출입구 진입";ArrivalPurpose.ACCESS->"무단차 베이 · 경사·문 개방 공간 비교"}
        Badge("추천 후보 · $zone")
        Copy("시설 데이터는 견본입니다. 버스 정류장·정차 금지 구역은 제외합니다.")
        Primary(when(s.purpose){ArrivalPurpose.ACCESS->"편안한 하차 지점 비교";else->"도착 시간에 맞춰 식사 준비"},true){vm.page(if(s.purpose==ArrivalPurpose.ACCESS)2 else 1)}
    }
})}

@Composable private fun LaneRoad(recommended:Int?,current:Int?){
    Column(Modifier.fillMaxWidth().background(Color(0xFFEAF0F0),RoundedCornerShape(18.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics{contentDescription="4개 차선. ${current?.let{"현재 ${it}차선"}?:"현재 차선 미확인"}, ${recommended?.let{"추천 ${it}차선"}?:"추천 대기"}"}){
            val w=size.width;val h=size.height
            drawRect(Ink,topLeft=Offset(w*.08f,0f),size=androidx.compose.ui.geometry.Size(w*.84f,h))
            (1..3).forEach{i->val x=w*(.08f+.21f*i);var y=0f;while(y<h){drawLine(Color(0xFF749096),Offset(x,y),Offset(x,y+12),2f);y+=24}}
            recommended?.let{lane->val x=w*(.08f+.21f*(lane-.5f));drawLine(Color(0xFF68DBBC),Offset(x,h),Offset(x,18f),w*.11f,StrokeCap.Round)}
            (1..4).forEach{i->val x=w*(.08f+.21f*(i-.5f));val y=h*.28f
                if(i!=1){
                    drawLine(Color.White,Offset(x,y+25),Offset(x,y),3f,StrokeCap.Round)
                    drawLine(Color.White,Offset(x,y),Offset(x-7,y+8),3f);drawLine(Color.White,Offset(x,y),Offset(x+7,y+8),3f)
                }
                if(i!=3){
                    val direction=if(i==4)1 else -1
                    drawLine(Color.White,Offset(x,y+25),Offset(x,y+12),3f)
                    drawLine(Color.White,Offset(x,y+12),Offset(x+direction*13,y+12),3f)
                    drawLine(Color.White,Offset(x+direction*13,y+12),Offset(x+direction*7,y+6),3f)
                }
            }
            current?.let{lane->
                val x=w*(.08f+.21f*(lane-.5f))
                drawRoundRect(Color.White,Offset(x-12,h*.7f),androidx.compose.ui.geometry.Size(24f,40f),androidx.compose.ui.geometry.CornerRadius(6f))
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){listOf("1  ↰","2  ↑↰","3  ↑","4  ↑↱").forEach{Text(it,color=Ink,fontSize=17.sp,fontWeight=FontWeight.Bold)}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){listOf("좌회전","직좌 혼용","직진 전용","직우 혼용").forEach{Text(it,color=Muted,fontSize=12.sp)}}
    }
}

@Composable private fun Dining(s:MobilityState,vm:MobilityViewModel,wide:Boolean,gps:()->Unit,listen:()->Unit){Split(wide,{
    Trip(s)
    Panel("당신과 음식이, 같은 시간에","ARRIVAL DINING"){
        Copy("현재 위치를 확인하고 목적지 근처 매장 중 조리·배달 시간이 도착 시간에 맞는 메뉴를 추천합니다.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){CabinRole.entries.forEach{role->FilterChip(selected=s.role==role,onClick={vm.role(role)},enabled=s.canEdit,label={Text(role.label)})}}
        Copy(if(s.role==CabinRole.DRIVER)"혼자 주행 중에는 음성으로 추천·금액 확인·명시적 주문 확인을 진행합니다." else "정차 중 동승자가 조작할 수 있습니다. 주행 중에는 좌석별 공식 권한이 필요합니다.")
        Primary(if(s.gpsReady)"현재 위치 다시 확인" else "현재 위치 확인",s.canEdit,gps)
        Copy(s.locationLabel)
        Primary("음성으로 메뉴 추천받기",s.vehicleValid,listen)
        Copy("“메뉴 추천해줘” → 금액과 수령지 확인 → “주문해줘”")
    }
},{
    if(s.order!=null){val order=s.order
        Panel("도착을 함께 기다리는 중","ORDER ${order.id.take(8)}"){
            Badge(s.mealStatus)
            Text(order.menu.name,color=Ink,fontSize=26.sp,fontWeight=FontWeight.Bold)
            Copy("${order.menu.storeName} · %,d원".format(order.menu.price))
            Copy("조리 ${order.menu.prep/60}분 + 배달 ${order.menu.delivery/60}분 = ${order.menu.lead/60}분")
            Copy("예약 대기 ${((order.dispatchAt-s.elapsed).coerceAtLeast(0)+59)/60}분 · 도착 지연 ${order.lateAtConfirmation/60}분")
            Copy("견본 주문입니다. 실제 매장에 전송하거나 결제하지 않습니다.")
            Primary("음식 수령 확인",s.canEdit&&s.foodReady&&s.phase==DrivePhase.ARRIVED&&!s.received,vm::receive)
            OutlinedButton(onClick=vm::cancelOrder,enabled=s.canEdit){Text("견본 주문 취소")}
        }
    }else if(s.mealStep==DiningStep.REVIEW&&s.selectedMenu!=null){val menu=s.selectedMenu
        Panel("주문 전, 한 번 더 확인","REVIEW YOUR ORDER"){
            Text(menu.name,color=Ink,fontSize=28.sp,fontWeight=FontWeight.Bold);Copy(menu.storeName)
            Text("%,d원".format(menu.price),color=Teal,fontSize=32.sp,fontWeight=FontWeight.Bold)
            Copy("수령지 · ${s.destination.name}");Copy("조리 ${menu.prep/60}분 + 배달 ${menu.delivery/60}분")
            Badge(if(menu.lead<=s.remaining)"도착 시간에 맞춰 준비" else "도착보다 ${(menu.lead-s.remaining+59)/60}분 늦음",if(menu.lead<=s.remaining)Teal else Amber)
            Primary("확인 · 견본 주문 예약",s.canEdit){vm.confirmMeal()}
            OutlinedButton(onClick=vm::cancelOrder,enabled=s.canEdit){Text("다른 메뉴 보기")}
        }
    }else Panel("도착 시간에 맞는 한 끼","NEAR YOUR DESTINATION · 견본"){
        MobilityRules.recommendedMenu(s.remaining).forEachIndexed{i,menu->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if(i==0)Mint else Paper).clickable(enabled=s.canEdit&&s.gpsReady){vm.selectMenu(menu)}.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(menu.storeName,color=Muted,fontSize=13.sp);Text(if(menu.lead<=s.remaining)"시간 맞춤" else "지연 예상",color=if(menu.lead<=s.remaining)Teal else Amber,fontSize=13.sp)}
                Text(menu.name,color=Ink,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                Copy("조리 ${menu.prep/60} + 배달 ${menu.delivery/60} = ${menu.lead/60}분 · %,d원".format(menu.price))
            }
        }
    }
})}

@Composable private fun Toggle(label:String,checked:Boolean,enabled:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=17.sp,color=Ink);Switch(checked=checked,onCheckedChange=change,enabled=enabled)}
}
@Composable private fun Access(s:MobilityState,vm:MobilityViewModel,wide:Boolean){Split(wide,{
    Panel("가까운 입구보다, 편안한 하차","BARRIER-FREE ARRIVAL"){
        Copy("${s.destination.name} · 문을 열 공간부터 입구까지의 단차와 경사를 함께 비교합니다.")
        Toggle("휠체어 옆문 공간",s.needs.wheelchair,s.canEdit){vm.needs(s.needs.copy(wheelchair=it))}
        Toggle("문을 넓게 열 공간 · 2m 이상",s.needs.wide,s.canEdit){vm.needs(s.needs.copy(wide=it))}
        Toggle("더 완만한 경사 · 2% 이하",s.needs.gentle,s.canEdit){vm.needs(s.needs.copy(gentle=it))}
        Toggle("현장 직원 지원",s.needs.staff,s.canEdit){vm.needs(s.needs.copy(staff=it))}
        Copy("필요한 지원만 선택하세요. 시설 수치는 견본이며 현장 확인이 필요합니다.")
    }
    if(s.bay==null)Panel("차에서 입구까지, 끊김 없이","STEP-FREE CONNECTION"){
        Canvas(Modifier.fillMaxWidth().height(165.dp).semantics{contentDescription="무단차 베이에서 보행로를 따라 입구로 연결되는 개념도. 실제 시설 지도 아님"}){
            val w=size.width;val h=size.height
            drawRoundRect(Paper,Offset.Zero,size,androidx.compose.ui.geometry.CornerRadius(20f))
            drawRect(Line,Offset(w*.05f,h*.65f),androidx.compose.ui.geometry.Size(w*.9f,h*.23f))
            drawRoundRect(Ink,Offset(w*.12f,h*.59f),androidx.compose.ui.geometry.Size(w*.18f,h*.35f),androidx.compose.ui.geometry.CornerRadius(10f))
            val route=Path().apply{moveTo(w*.3f,h*.65f);lineTo(w*.48f,h*.65f);lineTo(w*.48f,h*.27f);lineTo(w*.78f,h*.27f)}
            drawPath(route,Teal,style=Stroke(width=10f,cap=StrokeCap.Round))
            drawRoundRect(Teal,Offset(w*.72f,h*.12f),androidx.compose.ui.geometry.Size(w*.16f,h*.28f),androidx.compose.ui.geometry.CornerRadius(8f))
        }
        Copy("승하차 공간 → 무단차 보행로 → 시설 입구",false)
        Copy("개념도 · 시설 지도 미연결. 단차·경사·옆문 공간을 현장에서 다시 확인합니다.")
    }
    if(s.bay!=null)Panel("하차 전 안전 확인","CHECK BEFORE EXIT"){
        Badge(if(s.exitHeld)"조건 변경 · 재확인 필요" else if(s.accessApproved)"사용자 승인 완료" else "정차 후 확인",if(s.exitHeld)Amber else Teal)
        Copy(s.bay.name,false)
        Toggle("문 개방 공간 확인",s.spaceChecked,s.canEdit&&s.phase==DrivePhase.ARRIVED){vm.check("space",it)}
        if(s.needs.wheelchair)Toggle("램프·리프트 준비 확인",s.rampReady,s.canEdit&&s.phase==DrivePhase.ARRIVED){vm.check("ramp",it)}
        if(s.needs.staff)Toggle("직원 지원 확인",s.staffAccepted,s.canEdit&&s.phase==DrivePhase.ARRIVED){vm.check("staff",it)}
        Primary(if(s.accessApproved)"입구 도착 확인" else "주변 확인 · 하차 안내 승인",if(s.accessApproved)MobilityRules.exitConditions(s) else MobilityRules.canExit(s)){if(s.accessApproved)vm.completeAccess()else vm.approveExit()}
        if(s.phase!=DrivePhase.ARRIVED)Primary("정차 후 도착 확인",s.canEdit,vm::markArrivedForCheck)
        if(s.exitProgress==100)Badge("입구 도착 확인 완료")
    }
},{
    Panel("내게 맞는 하차 지점","COMPARE THE WHOLE PATH"){
        if(s.bayAssessments.none{it.eligible})Copy("조건에 맞는 후보가 없습니다. 지원 조건을 확인하거나 현장 도움을 요청해 주세요.",false)
        if(s.unavailableBayIds.isNotEmpty())Copy("이용 불가는 사용자 확인입니다. 이번 여정에만 적용하며 시설 실시간 정보가 아닙니다.")
        s.bayAssessments.forEach{assessment->val bay=assessment.bay
            Column(Modifier.fillMaxWidth().background(if(assessment.eligible)Mint else Paper,RoundedCornerShape(18.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(bay.name,color=Ink,fontSize=19.sp,fontWeight=FontWeight.Bold);Badge(if(assessment.eligible)"조건 충족" else "제외",if(assessment.eligible)Teal else Red)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Copy("입구 ${bay.walk}m");Copy("경사 ${bay.slope}%");Copy("옆문 ${bay.side}m")}
                Copy(if(assessment.eligible)"무단차 연결 · 차도 횡단 없음" else assessment.reasons.joinToString(" · "))
                if(assessment.eligible)Primary(if(s.bay?.id==bay.id)"선택한 하차 지점" else "이 지점으로 안내",s.canEdit){vm.chooseBay(bay)}
                if(bay.id !in s.unavailableBayIds)OutlinedButton(onClick={vm.unavailableBay(bay.id)},enabled=s.canEdit){Text("${bay.id} · 이 지점 이용 불가")}
            }
        }
    }
})}

@Composable private fun Handoff(s:MobilityState,vm:MobilityViewModel,wide:Boolean,count:Int,gps:()->Unit,attach:()->Unit,share:()->Unit){Split(wide,{
    Panel("주소 대신, 눈에 보이는 위치","VISUAL HANDOFF"){
        Copy("큰 건물이나 정확한 주소가 없어도 현재 GPS와 주변 사진을 함께 공유합니다.")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("전방","후방").forEach{CameraSlot(it,Modifier.weight(1f))}}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("좌측","우측").forEach{CameraSlot(it,Modifier.weight(1f))}}
        Copy("차량 어라운드뷰 제공자 미연결. 실제 촬영 이미지는 사진 선택으로 첨부할 수 있습니다.")
        Primary("주변 사진 선택 · 최대 4장 ($count/4)",s.canEdit,attach)
        if(count>0)OutlinedButton(onClick=vm::clearPhotos,enabled=s.canEdit){Text("첨부 사진 모두 제거")}
    }
},{
    Panel("찾아오기 쉬운 인계 카드","LOCATION CARD ${s.locationVersion}"){
        Text(s.destination.name,color=Ink,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Copy(s.locationLabel)
        if(s.lat!=null&&s.lon!=null)Text("%.5f, %.5f".format(s.lat,s.lon),color=Teal,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        Badge("GPS ${if(s.gpsReady)"확인" else "대기"} · 사진 ${count}장")
        Copy("사진 순서: 전방 → 후방 → 좌측 → 우측. 공유 전에 번호판·주변 사람과 촬영 방향을 확인해 주세요.")
        Primary("현재 GPS 다시 확인",s.canEdit,gps)
        Primary("이 위치와 사진을 확인했어요",s.canEdit&&s.gpsReady,vm::shareLocation)
        Primary("위치 카드 · 사진 공유창 열기",s.canShareLocation,share)
        Copy("공유창에서 받는 사람을 직접 선택합니다. 위치가 바뀌면 다시 확인해야 합니다.")
    }
})}
@Composable private fun CameraSlot(label:String,modifier:Modifier){
    Column(modifier.background(Paper,RoundedCornerShape(16.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(label,color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(64.dp)){
            val path=Path().apply{moveTo(size.width*.2f,size.height);lineTo(size.width*.43f,0f);lineTo(size.width*.57f,0f);lineTo(size.width*.8f,size.height)}
            drawPath(path,Line,style=Stroke(width=3f));drawLine(Line,Offset(size.width*.5f,0f),Offset(size.width*.5f,size.height),2f)
        }
        Text("카메라 연결 대기",color=Muted,fontSize=12.sp)
    }
}
