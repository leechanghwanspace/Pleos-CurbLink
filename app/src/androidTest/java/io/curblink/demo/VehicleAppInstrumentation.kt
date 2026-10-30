package io.curblink.demo

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** External test driver: simulation geometry/assets exist only in the test APK. */
class VehicleAppInstrumentation:Instrumentation(){
    private lateinit var activity:MainActivity
    private lateinit var vm:MobilityViewModel
    private var assertions=0
    private val completed=mutableListOf<String>()
    private var platformMode=false
    private fun verify(ok:Boolean,message:String){assertions++;check(ok){message}}
    private fun onMain(action:()->Unit){var failure:Throwable?=null;runOnMainSync{try{action()}catch(error:Throwable){failure=error}};failure?.let{throw it};waitForIdleSync()}
    private fun vehicle(gear:VehicleGear)=vm.observeVehicle(VehicleObservation(gear,if(gear==VehicleGear.P)0.0 else 18.0,SystemClock.elapsedRealtime(),VehicleSource.TEST,gear!=VehicleGear.P))
    private fun capture(name:String){
        SystemClock.sleep(180)
        val bitmap=Bitmap.createBitmap(activity.window.decorView.width,activity.window.decorView.height,Bitmap.Config.ARGB_8888)
        val latch=CountDownLatch(1)
        var result=-1
        PixelCopy.request(activity.window,bitmap,{result=it;latch.countDown()},Handler(Looper.getMainLooper()))
        check(latch.await(5,TimeUnit.SECONDS)&&result==PixelCopy.SUCCESS){"Could not capture application window: $result"}
        val folder=File(targetContext.filesDir,"native-journey-tests").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);platformMode=arguments?.getString("mode")=="platform";start()}
    override fun onStart(){try{
        if(platformMode){testPlatformConnection();return}
        // Dedicated test AVD only; this preference file contains our generated test ledger.
        targetContext.getSharedPreferences("curblink_drive",Context.MODE_PRIVATE).edit().clear().commit()
        activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        onMain{vm=ViewModelProvider(activity)[MobilityViewModel::class.java];vm.stopConnections()}
        verify(!vm.state.value.vehicleValid&&!vm.state.value.servicesReady,"App invented a vehicle/service connection")
        val initial=vm.state.value
        SystemClock.sleep(1100)
        verify(vm.state.value.elapsed==initial.elapsed&&vm.state.value.phase==initial.phase,"App is autonomously simulating a journey")
        capture("vehicle-app-disconnected")
        onMain{vehicle(VehicleGear.P);vm.observeTrip(TripObservation(900,3000,0,false))}
        SystemClock.sleep(6100);waitForIdleSync()
        verify(!vm.state.value.vehicleValid&&!vm.state.value.servicesReady&&!vm.state.value.routeReady,"Expired vehicle data remained active")
        verify(vm.state.value.elapsed==0&&vm.state.value.remaining==900,"Vehicle application advanced a virtual clock")
        val scenarios=ScenarioCatalog.load(context.assets)
        verify(scenarios.size==6,"External test fixture count")
        for(repeat in 1..3)for(id in listOf("PICKUP","PARKING","SHORT_STOP","MEAL","RIVER","ACCESS")){
            onMain{
                vehicle(VehicleGear.P);vm.reset()
                vm.destination(when(id){"ACCESS","PARKING"->MobilityCatalog.culture;"RIVER"->MobilityCatalog.river;else->MobilityCatalog.home})
                vm.page(when(id){"MEAL","RIVER"->1;"ACCESS"->2;else->0})
                vm.observeLocation("개발 검증용 위치",37.5,126.9)
                if(id=="ACCESS")vm.selectBay()
                vm.startTrip(id=="ACCESS")
            }
            verify(!vm.state.value.accessApproved&&!vm.state.value.locationShared&&vm.state.value.exitProgress==0,"Approval leaked between arrivals")
            var run=JourneyRun(JourneyEngine.forBay(scenarios.first{it.id==id},vm.state.value.bay))
            var elapsed=0
            val total=vm.state.value.total
            var obstacleTested=false
            var ordered=false
            val seen=mutableSetOf<String>()
            for(step in 0..240){
                onMain{
                    vehicle(VehicleGear.valueOf(run.gear))
                    val remaining=((run.scenario.stages.drop(run.stage+1).sumOf{it.duration}+(run.current.duration-run.stageTime))/run.scenario.duration*total).toInt()
                    val meters=((if(run.stopped)0.0 else run.current.length*(1-run.fraction))+run.scenario.stages.drop(run.stage+1).sumOf{it.length}).toInt()
                    vm.observeTrip(TripObservation(if(run.stopped)0 else remaining,meters,elapsed,run.stopped,run.current.id=="MAIN"))
                }
                if(!ordered&&(id=="MEAL"||id=="RIVER")){
                    onMain{vm.voice("메뉴 추천");vm.voice("주문하지 마")};verify(vm.state.value.order==null,"Negative voice created order")
                    onMain{vm.voice("메뉴 추천");vm.voice("주문 가격 알려줘")};verify(vm.state.value.order==null,"Question created order")
                    onMain{vm.voice("주문해 줘")};val order=vm.state.value.order;verify(order!=null,"Explicit confirmation failed")
                    onMain{vm.voice("주문해 줘")};verify(order?.id==vm.state.value.order?.id,"Duplicate confirmation created order");ordered=true
                }
                if(repeat==1&&seen.add(run.current.id))capture("${id.lowercase()}-${run.current.id.lowercase()}")
                if(run.done)break
                when(run.current.id){
                    "LOCATE"->{onMain{vm.page(3);vm.shareLocation()};verify(vm.state.value.canShareLocation,"Current location could not be confirmed");run=JourneyEngine.act(run,"SHARE",vm.state.value.canShareLocation)}
                    "ACCESS_WAIT"->{onMain{vm.markArrivedForCheck();vm.check("space",true);vm.check("ramp",true);vm.check("staff",true);vm.approveExit()};run=JourneyEngine.act(run,"APPROVE",vm.state.value.accessApproved)}
                    "ACCESS_EXIT"->if(!obstacleTested&&run.stageTime>0){
                        obstacleTested=true
                        onMain{vm.check("obstacle",true)};verify(!vm.state.value.accessApproved&&vm.state.value.exitHeld,"Live obstacle did not hold exit")
                        val held=vm.state.value.exitProgress;onMain{vm.observeAccessProgress(100)};verify(held==vm.state.value.exitProgress,"Held exit progress changed")
                        onMain{vm.check("obstacle",false);vm.check("space",true)};verify(!vm.state.value.accessApproved,"Recovery reapproved automatically")
                        onMain{vm.approveExit()}
                    }
                    "HANDOFF"->{onMain{vm.receive()};run=JourneyEngine.act(run,"RECEIVE",vm.state.value.received)}
                }
                run=JourneyEngine.advance(run,30.0*run.scenario.duration/total,vm.state.value.foodReady,vm.state.value.accessApproved&&MobilityRules.exitConditions(vm.state.value))
                if(run.current.id=="ACCESS_EXIT")onMain{vm.observeAccessProgress((run.fraction*100).toInt())}
                elapsed+=30
            }
            verify(run.done,"External $id fixture did not finish")
            if(id=="ACCESS")verify(obstacleTested&&vm.state.value.exitProgress==100,"Access flow incomplete")
            if(id=="MEAL"||id=="RIVER")verify(vm.state.value.received,"Receipt incomplete")
            onMain{vehicle(VehicleGear.P)}
            completed+="$id/$repeat"
        }
        // A restored order is verified in a separate real process restart after this run.
        onMain{vehicle(VehicleGear.P);vm.cancelOrder();vm.reset();vm.page(1);vm.observeLocation("개발 검증용 위치",37.5,126.9);vm.startTrip();vehicle(VehicleGear.D);vm.observeTrip(TripObservation(900,3000,0,false));vm.voice("메뉴 추천");vm.voice("주문해 줘")}
        capture("order-before-restart")
        finish(Activity.RESULT_OK,Bundle().apply{putString("result","PASS: ${completed.size} vehicle flows; $assertions assertions; external test driver only");putString("orderId",vm.state.value.order?.id);putString("flows",completed.joinToString(","))})
    }catch(error:Throwable){finish(Activity.RESULT_CANCELED,Bundle().apply{putString("failure",error.stackTraceToString());putString("completed",completed.joinToString(","))})}}
    private fun awaitConnected(){
        val deadline=SystemClock.elapsedRealtime()+15000
        while(SystemClock.elapsedRealtime()<deadline){
            val state=vm.state.value
            if(state.vehicleValid&&state.source==VehicleSource.VEHICLE&&state.navigationConnected)return
            SystemClock.sleep(100)
        }
        error("Platform connection timed out: vehicle=${vm.state.value.vehicleValid}, navi=${vm.state.value.navigationConnected}, note=${vm.state.value.note}")
    }
    private fun testPlatformConnection(){
        targetContext.getSharedPreferences("curblink_drive",Context.MODE_PRIVATE).edit().clear().commit()
        activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        onMain{vm=ViewModelProvider(activity)[MobilityViewModel::class.java]}
        awaitConnected()
        SystemClock.sleep(1500)
        val first=vm.state.value
        verify(first.gear!=VehicleGear.UNKNOWN,"Platform gear is unknown")
        verify(first.source==VehicleSource.VEHICLE,"Connection used a test observation")
        verify(first.servicesReady,"Mockup store & bay catalog not active when vehicle connected")
        onMain{vm.page(0)};capture("pleos-connected")
        // Verify 1~4 lane route exploration scenarios (3차선 직진 최적 & 4차선 목적지 진입)
        onMain{vm.observeLanes(mapOf(2 to LaneTraffic(queue=8,signalWait=12.0),3 to LaneTraffic(),4 to LaneTraffic()),LaneContext(),true)}
        verify(LaneRecommendation.rank(vm.state.value.currentLane,vm.state.value.purpose,600,vm.state.value.laneTraffic,true).lane==3,"Stage 1 cruise did not recommend lane 3")
        onMain{vm.observeLanes(mapOf(2 to LaneTraffic(queue=8,signalWait=12.0),3 to LaneTraffic(),4 to LaneTraffic()),LaneContext(),true)}
        verify(LaneRecommendation.rank(vm.state.value.currentLane,vm.state.value.purpose,480,vm.state.value.laneTraffic,true).lane==3,"Stage 2 left-turn bottleneck did not recommend lane 3")
        onMain{vm.observeLanes(mapOf(2 to LaneTraffic(queue=8,signalWait=12.0),3 to LaneTraffic(),4 to LaneTraffic()),LaneContext(),true)}
        verify(LaneRecommendation.rank(vm.state.value.currentLane,vm.state.value.purpose,120,vm.state.value.laneTraffic,true).lane==4,"Stage 3 destination approach did not recommend lane 4")
        capture("pleos-lane-approach")
        // Verify interactive Dining Store & Menu flow under live VEHICLE connection
        onMain{vm.page(1)};capture("pleos-dining-stores")
        onMain{vm.actualGps();vm.selectMenu(MobilityRules.recommendedMenu(vm.state.value.remaining).first())}
        verify(vm.state.value.selectedMenu!=null&&vm.state.value.mealStep==DiningStep.REVIEW,"Menu selection did not transition to REVIEW")
        capture("pleos-dining-review")
        onMain{vm.confirmMeal()}
        verify(vm.state.value.order!=null,"Interactive order confirmation failed under live vehicle connection")
        capture("pleos-dining-ordered")
        // Verify Barrier-Free Bay selection works EVEN WHEN an order is active (order != null), and transitions directly to safety check!
        onMain{vm.page(2);vm.needs(AccessPreferences(wheelchair=true,wide=false,gentle=true,staff=true))}
        capture("pleos-access-bays")
        onMain{vm.selectBay()}
        verify(vm.state.value.bay?.id=="A3"&&vm.state.value.accessTrip&&vm.state.value.phase!=DrivePhase.DRIVING,"Barrier-free bay selection (A3 with gentle slope) failed or did not transition to ARRIVED")
        onMain{vm.markArrivedForCheck();vm.check("space",true);vm.check("ramp",true);vm.check("staff",true);vm.approveExit()}
        verify(vm.state.value.accessApproved,"Interactive barrier-free approval failed")
        onMain{vm.completeAccess()}
        verify(vm.state.value.exitProgress==100,"Interactive barrier-free entrance arrival completion failed")
        capture("pleos-access-completed")
        onMain{vm.cancelOrder()}
        verify(vm.state.value.order==null,"Order cancel/reselect failed")
        // Verify interactive Handoff location confirmation under live VEHICLE connection
        onMain{vm.page(3);vm.actualGps();vm.shareLocation()}
        verify(vm.state.value.canShareLocation,"Interactive handoff location confirmation failed")
        capture("pleos-handoff-verified")
        // Verify permission denial / unavailable state immediately restricts vehicle controls
        onMain{vm.vehicleUnavailable("차량 속도 정보 접근 권한을 허용해 주세요.")}
        verify(!vm.state.value.vehicleValid&&vm.state.value.uxRestricted,"Permission denial kept vehicle valid")
        awaitConnected()
        // Verify lifecycle stop/start disconnection and reconnection
        onMain{vm.stopConnections()}
        SystemClock.sleep(300)
        verify(!vm.state.value.vehicleValid&&!vm.state.value.navigationConnected,"Stopped adapter still reports connected")
        onMain{vm.startConnections()}
        awaitConnected();SystemClock.sleep(1500)
        val restored=vm.state.value
        verify(restored.vehicleValid&&restored.navigationConnected,"Failed to reconnect after lifecycle restart")
        onMain{vm.cancelOrder();vm.reset();vm.page(0)}
        capture("pleos-reconnected")
        finish(Activity.RESULT_OK,Bundle().apply{
            putString("result","PASS: CarService + NaviHelper live connection, interactive store/bay/handoff actions, and stop/reconnect")
            putString("gear",restored.gear.name);putBoolean("routeReady",restored.routeReady)
            putInt("remainingSeconds",restored.remaining);putInt("remainingMeters",restored.distance)
            putString("source",restored.source.name);putString("destination",restored.destination.name)
            putInt("assertions",assertions)
        })
    }
}
