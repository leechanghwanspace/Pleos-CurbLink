package io.curblink.demo

import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobilityViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("curblink_drive", Context.MODE_PRIVATE)
    private val initial = MobilityState(vehicleValid=false, uxRestricted=true, appDestinationSelected=true, remaining=0, total=1,
        locationLabel="위치 확인 전", note="차량과 서비스 연결을 기다리고 있습니다.",
        needs=AccessPreferences(prefs.getBoolean("wheelchair",true),prefs.getBoolean("wide",false),prefs.getBoolean("gentle",false),prefs.getBoolean("staff",true)))
    private val mutable = MutableStateFlow(JourneyStore.restore(prefs.getString("journey_v1",null),initial))
    val state = mutable.asStateFlow()
    private val connections = PlatformConnections(app.applicationContext, this)
    fun startConnections() = connections.start()
    fun stopConnections() = connections.stop()
    override fun onCleared() { connections.stop(); super.onCleared() }
    private var lastVehicle: VehicleObservation? = null
    private var lastLocationAt=0L
    private var lastLaneAt=0L
    private fun update(change: (MobilityState)->MobilityState) {
        var next=JourneyTransitions.enforce(change(mutable.value))
        if(next.lat!=mutable.value.lat||next.lon!=mutable.value.lon||next.locationLabel!=mutable.value.locationLabel) next=next.copy(locationVersion=next.locationVersion+1,locationShared=false,confirmedLocation=null)
        if(next==mutable.value)return
        val durable=next.order!=mutable.value.order||next.received!=mutable.value.received
        val editor=prefs.edit().putString("journey_v1",JourneyStore.encode(next))
        if(durable&&!editor.commit()){mutable.value=mutable.value.copy(note="주문 상태를 저장하지 못했습니다. 저장 공간과 서비스 상태를 확인해 주세요.");return}
        if(!durable)editor.apply()
        mutable.value=next
    }
    // Only connection freshness is monitored. No virtual driving, clock acceleration or actors.
    init {
        viewModelScope.launch {
            while(true){
                delay(500)
                val now=SystemClock.elapsedRealtime()
                if(lastLocationAt!=0L&&now-lastLocationAt>30000&&mutable.value.gpsReady) locationUnavailable()
                if(lastLaneAt!=0L&&now-lastLaneAt>5000&&mutable.value.laneDataValid) update{it.copy(laneDataValid=false)}
                if(lastVehicle?.validAt(now)!=true&&mutable.value.vehicleValid) disconnected()
            }
        }
    }
    private fun disconnected()=update{it.copy(vehicleValid=false,uxRestricted=true,source=VehicleSource.UNAVAILABLE,gear=VehicleGear.UNKNOWN,
        servicesReady=false,routeReady=false,spaceChecked=false,locationShared=false,confirmedLocation=null,note="차량 정보를 확인할 수 없습니다. 연결 후 상태를 다시 확인해 주세요.")}
    fun vehicleUnavailable(message:String) { lastVehicle=null; disconnected(); note(message) }
    fun navigationStatus(connected:Boolean,message:String?=null)=update{it.copy(navigationConnected=connected,note=message?:it.note)}
    fun clearNavigationRoute()=update{if(!it.routeReady)it else it.copy(routeReady=false,locationVersion=it.locationVersion+1,
        locationShared=false,confirmedLocation=null,accessApproved=false,exitHeld=it.accessApproved||it.exitHeld,spaceChecked=false)}
    fun acceptsNavigation(name:String):Boolean = !mutable.value.appDestinationSelected || name == mutable.value.destination.name
    fun observeNavigationDestination(name:String,seconds:Int,meters:Int)=update{s->
        if (!acceptsNavigation(name)) return@update s
        val changed=!s.appDestinationSelected && (s.destination.id!="PLEOS_CURRENT"||s.destination.name!=name)
        s.copy(destination=if(s.appDestinationSelected)s.destination else DemoPlace("PLEOS_CURRENT",name.ifBlank{"내비게이션 목적지"},"Pleos 내비게이션 안내",seconds,meters),
            locationVersion=if(changed)s.locationVersion+1 else s.locationVersion,
            locationShared=if(changed)false else s.locationShared,confirmedLocation=if(changed)null else s.confirmedLocation,
            accessApproved=if(changed)false else s.accessApproved,spaceChecked=if(changed)false else s.spaceChecked,
            accessTrip=if(changed)false else s.accessTrip,bay=if(changed)null else s.bay,exitProgress=if(changed)0 else s.exitProgress)
    }
    fun observeVehicle(value: VehicleObservation) {
        if(value.observedAtMs<(lastVehicle?.observedAtMs?:Long.MIN_VALUE))return
        lastVehicle=value
        if(!value.validAt(SystemClock.elapsedRealtime())){disconnected();return}
        update{it.copy(vehicleValid=true,source=value.source,gear=value.gear,uxRestricted=value.uxRestricted,
            phase=if(value.moving)DrivePhase.DRIVING else if(it.phase==DrivePhase.ARRIVED)DrivePhase.ARRIVED else DrivePhase.IDLE,
            servicesReady=value.source!=VehicleSource.UNAVAILABLE,
            laneDataValid=value.source==VehicleSource.TEST || lastLaneAt!=0L&&SystemClock.elapsedRealtime()-lastLaneAt<=5000,
            note=if(it.vehicleValid)it.note else if(value.source==VehicleSource.TEST)"개발 검증용 데이터 · 실제 주문·차량 제어 미연결" else "차량 정보가 연결되었습니다.")}
    }
    fun observeTrip(value: TripObservation) {
        if(value.remainingSeconds<0||value.remainingMeters<0||value.elapsedSeconds<0)return
        update{if(!it.vehicleValid)it else it.copy(routeReady=true,remaining=value.remainingSeconds,remainingMeters=value.remainingMeters,
            elapsed=value.elapsedSeconds,total=maxOf(it.total,value.remainingSeconds+value.elapsedSeconds),currentLane=value.currentLane.coerceIn(1,4),onMainRoad=value.onMainRoad,
            phase=if(value.arrived&&it.gear==VehicleGear.P)DrivePhase.ARRIVED else if(it.gear!=VehicleGear.P)DrivePhase.DRIVING else DrivePhase.IDLE)}
    }
    fun observeAccessProgress(value: Int)=update{if(it.accessApproved&&MobilityRules.exitConditions(it))it.copy(exitProgress=value.coerceIn(0,100))else it}
    fun page(value:Int)=update{it.copy(page=value.coerceIn(0,3))}
    fun note(text:String)=update{it.copy(note=text)}
    fun destination(place:DemoPlace)=update{s->
        if(!s.canEdit)s.copy(note="P단 정차 상태에서 목적지를 변경해 주세요.")
        else if(s.order!=null&&!s.received)s.copy(note="진행 중인 주문의 수령 목적지를 먼저 확인해 주세요.")
        else JourneyTransitions.newTrip(s,place).copy(
            order=if(s.received)null else s.order,
            selectedMenu=if(s.received)null else s.selectedMenu,
            mealStep=if(s.order!=null&&!s.received)s.mealStep else DiningStep.LOCATION,
            appDestinationSelected=true,routeReady=s.source==VehicleSource.TEST,remaining=place.eta,total=place.eta,remainingMeters=place.meters,
            note="${place.name}(으)로 목적지를 설정했습니다. 1~4차선 최적 경로와 도착 준비를 안내합니다."
        )
    }
    fun purpose(value:ArrivalPurpose)=update{if(!it.canEdit||it.purpose==value)it else it.copy(purpose=value,note="도착 목적을 '${value.label}'(으)로 변경했습니다. 목적지 진입 차선과 정차 구역을 재탐색합니다.")}
    fun observeLanes(traffic:Map<Int,LaneTraffic>, context:LaneContext, valid:Boolean){
        lastLaneAt=SystemClock.elapsedRealtime()
        update{it.copy(laneTraffic=traffic,laneContext=context,laneDataValid=valid&&it.vehicleValid)}
    }
    fun reset()=update(JourneyTransitions::reset)
    fun attachPhotos(uris:List<String>,tripId:String?)=update{JourneyTransitions.attachPhotos(it,uris,tripId)}
    fun clearPhotos()=update{if(it.canEdit)it.copy(handoffPhotoUris=emptyList(),locationShared=false,confirmedLocation=null,note="첨부 사진을 모두 제거했습니다.")else it}
    fun observeLocation(label:String,latitude:Double?=null,longitude:Double?=null)=update{
        if(latitude==null||longitude==null||!latitude.isFinite()||!longitude.isFinite()||latitude !in -90.0..90.0||longitude !in -180.0..180.0) it
        else { lastLocationAt=SystemClock.elapsedRealtime(); it.copy(gpsReady=true,locationLabel=label,lat=latitude,lon=longitude,
            mealStep=if(it.mealStep==DiningStep.LOCATION)DiningStep.DESTINATION else it.mealStep) }
    }
    fun role(value:CabinRole)=update{if(it.canEdit)it.copy(role=value)else it}

    private fun locationUnavailable()=update{it.copy(gpsReady=false,lat=null,lon=null,locationShared=false,confirmedLocation=null,note="현재 위치를 읽지 못했습니다. GPS 수신과 위치 권한을 확인해 주세요.")}
    @Suppress("MissingPermission") fun actualGps(){
        try{
            val manager=getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location=manager.getProviders(true).mapNotNull{manager.getLastKnownLocation(it)}.filter{SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0L..30_000_000_000L}.minByOrNull{it.accuracy}
            if(location==null){
                locationUnavailable()
                return
            }
            observeLocation("현재 위치 · 오차 약 ${location.accuracy.toInt()}m",location.latitude,location.longitude)
        }catch(_:SecurityException){
            locationUnavailable()
        }
    }
    fun startTrip(access:Boolean=false)=update{s->
        if(!s.canEdit||!s.gpsReady)s.copy(note="정차 상태와 현재 위치를 먼저 확인해 주세요.")
        else if(access&&s.bay==null)s.copy(note="하차 지점을 먼저 선택해 주세요.")
        else s.copy(accessTrip=access,mealStep=if(s.page==1)DiningStep.MENU else s.mealStep,
            note="목적지와 도착 준비를 저장했습니다. 이동 상태는 차량·내비게이션 정보로 갱신됩니다.")
    }
    fun selectMenu(menu:DiningMenu,voice:Boolean=false)=update{s->
        if(!s.servicesReady)s.copy(note="주문 서비스를 연결한 뒤 메뉴를 선택할 수 있습니다.")
        else if(!s.gpsReady)s.copy(note="현재 위치를 먼저 확인해 주세요.")
        else if(!s.vehicleValid||s.order!=null||(!s.canEdit&&!voice))s.copy(note="주행 중에는 음성으로 메뉴를 확인해 주세요.")
        else s.copy(selectedMenu=menu,mealStep=DiningStep.REVIEW,note="[${menu.storeName}] ${menu.name} · ${menu.price}원 · ${s.destination.name} 도착 맞춤 픽업")
    }
    fun confirmMeal(voice:Boolean=false)=update{s->
        if(!s.servicesReady||!s.vehicleValid||!s.gpsReady)s.copy(note="현재 위치·차량 및 주문 서비스 상태를 확인해 주세요.")
        else MobilityRules.confirmOrder(s,voice)?.let{s.copy(order=it,mealStep=DiningStep.TRACK,note="[${it.menu.storeName}] 도착 시간 맞춤 주문을 접수했습니다 (모의 매장 연동).")}?:s.copy(note="메뉴·금액·수령 위치와 차량 상태를 다시 확인해 주세요.")
    }
    fun cancelOrder()=update{s->
        if(!s.canEdit)s.copy(note="정차 중에만 주문을 다시 선택할 수 있습니다.")
        else s.copy(order=null,selectedMenu=null,received=false,mealStep=DiningStep.MENU,note="주문을 취소하고 매장 목록으로 돌아왔습니다.")
    }
    fun voice(text:String){
        val s=mutable.value
        when(JourneyTransitions.voiceIntent(text)){
            MealVoiceIntent.CANCEL->update{if(it.order==null)it.copy(selectedMenu=null,mealStep=DiningStep.MENU,note="주문 확인을 취소했습니다.")else it.copy(note="주문 서비스에서 취소 가능 상태를 확인해야 합니다.")}
            MealVoiceIntent.QUERY->note(s.selectedMenu?.let{"[${it.storeName}] ${it.name} · ${it.price}원 · ${s.destination.name}"}?:"메뉴를 먼저 확인해 주세요.")
            MealVoiceIntent.RECOMMEND->selectMenu(MobilityRules.recommendedMenu(s.remaining).first(),true)
            MealVoiceIntent.CONFIRM->confirmMeal(true)
            MealVoiceIntent.HOME->if(!s.moving&&s.order==null){destination(MobilityCatalog.home);page(1)}
            MealVoiceIntent.UNKNOWN->note("메뉴와 금액을 확인하고 명확하게 주문 의사를 말씀해 주세요.")
        }
    }
    fun needs(value:AccessPreferences){
        update{if(!it.canEdit)it else it.copy(needs=value,bay=null,accessTrip=false,accessApproved=false,exitProgress=0,exitHeld=false,spaceChecked=false,rampReady=false,staffAccepted=false,note="필요한 하차 지원 조건을 변경했습니다. 조건에 맞는 지점을 선택해 주세요.")}
        val accepted=mutable.value.needs
        prefs.edit().putBoolean("wheelchair",accepted.wheelchair).putBoolean("wide",accepted.wide).putBoolean("gentle",accepted.gentle).putBoolean("staff",accepted.staff).apply()
    }
    fun selectBay()=update{s->
        if(!s.canEdit||!s.servicesReady)s.copy(note="P단 정차 및 차량 연결 상태에서 하차 지점을 선택해 주세요.")
        else MobilityRules.assess(s.needs).firstOrNull{it.eligible}?.bay?.let{JourneyTransitions.selectBay(s,it)}
            ?:s.copy(note="조건에 맞는 하차 지점이 없습니다. 지원 조건을 확인하거나 현장 도움을 요청해 주세요.")
    }
    fun chooseBay(bay:AccessBay)=update{JourneyTransitions.selectBay(it,bay)}
    fun resetAccessSelection()=update{s->
        if(!s.canEdit)s.copy(note="P단 정차 상태에서 변경해 주세요.")
        else s.copy(accessTrip=false,bay=null,spaceChecked=false,rampReady=false,staffAccepted=false,obstacle=false,accessApproved=false,exitProgress=0,exitHeld=false,note="하차 지원 조건과 후보 베이를 다시 선택합니다.")
    }
    fun check(name:String,value:Boolean)=update{s->
        val readyState=s
        JourneyTransitions.observe(readyState,name,value)
    }
    fun approveExit()=update{if(!MobilityRules.canExit(it))it.copy(note="정차·주변 공간·요청한 지원을 다시 확인해 주세요.")else it.copy(accessApproved=true,exitHeld=false,note="하차 준비를 확인했습니다. 주변 상황이 바뀌면 다시 확인해야 합니다.")}
    fun completeAccess()=update{if(it.accessApproved&&MobilityRules.exitConditions(it))it.copy(exitProgress=100,note="입구 도착을 확인했습니다.")else it}
    fun shareLocation()=update{if(!it.canEdit||!it.gpsReady)it.copy(note="정차 후 현재 위치를 확인해 주세요.")else it.copy(locationShared=true,confirmedLocation=it.locationKey,note="현재 인계 위치를 확인했습니다.")}
    fun currentShareState():MobilityState?=mutable.value.takeIf{it.canShareLocation}
    fun invalidateHandoff()=update{it.copy(locationShared=false,confirmedLocation=null)}
    fun receive()=update{if(it.received||it.phase!=DrivePhase.ARRIVED||!it.vehicleValid||!it.foodReady||(it.destination.id=="RIVER"&&!it.canShareLocation))it.copy(note="정차·음식 도착·인계 위치를 확인해 주세요.")else it.copy(received=true,note="음식 수령을 확인했습니다.")}
    fun markArrivedForCheck()=update{s->
        if(!s.canEdit)s.copy(note="P단 정차 후 도착을 확인해 주세요.")
        else s.copy(phase=DrivePhase.ARRIVED,remaining=0,remainingMeters=0,
            note="사용자가 도착을 확인했습니다. 음식·지원 준비는 별도로 확인하세요.")
    }
}
