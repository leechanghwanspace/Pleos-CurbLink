package io.curblink.demo

import android.car.Car
import android.car.VehicleGear as CarGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import kotlin.math.abs

/** Read-only AAOS/Pleos service adapters. No virtual vehicle state or route generator. */
class PlatformConnections(private val context: Context, private val model: MobilityViewModel) {
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var navi: NaviHelper? = null
    private var naviReady = false
    private var routeStarted = SystemClock.elapsedRealtime()
    private var arrived = false
    private var routeKey: String? = null
    private var destination: DestinationInfo? = null
    private var lastNavigationReply = 0L
    private var sequence = 0
    private var lastCarLog: VehicleGear? = null
    private var generation = 0
    private var registeredListener: NaviHelperEventListener? = null
    private fun dispatch(token: Int, action: () -> Unit) { main.post { if (active && token == generation) action() } }

    private fun createListener(token: Int) = object : NaviHelperEventListener {
        override fun onDestinationInfo(info: DestinationInfo) = dispatch(token) {
            if (info.remainTime < 0 || info.remainDistance < 0) return@dispatch
            if (info.name.isBlank() && info.remainTime == 0 && info.remainDistance == 0) {
                destination = null; routeKey = null; arrived = false
                model.clearNavigationRoute()
                return@dispatch
            }
            val key = "${info.poiId}:${info.latitude}:${info.longitude}:${info.name}"
            if (routeKey != key) { routeKey = key; routeStarted = SystemClock.elapsedRealtime(); arrived = false }
            destination = info
            navigationReply()
            model.observeNavigationDestination(info.name, info.remainTime, info.remainDistance)
            publishTrip(info.remainTime, info.remainDistance)
        }
        override fun onDrivingInfo(info: DrivingInfo) = dispatch(token) {
            navigationReply()
            publishTrip(info.destination.duration, info.destination.distance)
        }
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = dispatch(token) {
            if (info.latitude.isFinite() && info.longitude.isFinite() && info.latitude in -90.0..90.0 && info.longitude in -180.0..180.0)
                model.observeLocation("Pleos 내비게이션 현재 위치", info.latitude, info.longitude)
        }
        override fun onRouteStateInfo(info: RouteStateInfo) = dispatch(token) {
            navigationReply()
            if (!info.termsAgreement) model.navigationStatus(true, "내비게이션 이용 동의를 확인해 주세요.")
        }
        override fun onDestinationChanged() = dispatch(token) {
            destination = null; routeKey = null; arrived = false
            model.clearNavigationRoute()
            navi?.getDestinationInfo()
        }
        override fun onRouteCancelled() = dispatch(token) { destination = null; arrived = false; model.clearNavigationRoute() }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = dispatch(token) {
            navigationReply(); arrived = true; publishTrip(0, 0)
        }
        override fun onError(errorCode: NaviErrorCode) = dispatch(token) {
            Log.w("CurbLinkPlatform", "Navi API result=${errorCode.name}")
            model.navigationStatus(naviReady, "내비게이션 정보를 다시 확인하고 있습니다.")
        }
    }
    private fun navigationReply() {
        if (lastNavigationReply == 0L) Log.i("CurbLinkPlatform", "NAVI_DATA_RECEIVED")
        lastNavigationReply = SystemClock.elapsedRealtime()
        model.navigationStatus(true)
    }
    private fun publishTrip(seconds: Int, meters: Int) {
        if (seconds < 0 || meters < 0 || destination == null || !model.acceptsNavigation(destination!!.name)) return
        model.observeTrip(TripObservation(seconds, meters, ((SystemClock.elapsedRealtime()-routeStarted)/1000).toInt(), arrived))
    }
    fun start() {
        if (active) return
        val token = ++generation
        active = true
        try {
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready -> dispatch(token) {
                if (ready) {
                    try {
                        properties = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                        restrictions = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        restrictions?.registerListener { dispatch(token) { readVehicle() } }
                        readVehicle()
                    } catch (error: Exception) { vehicleFailure(error) }
                } else { properties = null; restrictions = null; model.vehicleUnavailable("차량 서비스 연결을 기다리고 있습니다.") }
            } }
        } catch (error: Exception) { vehicleFailure(error) }
        try {
            val listener = createListener(token)
            registeredListener = listener
            navi = NaviHelper(context).also { helper -> helper.initialize(
                onConnected = { dispatch(token) {
                    naviReady = true
                    helper.addListener(listener)
                    model.navigationStatus(true)
                    Log.i("CurbLinkPlatform", "NAVI_SERVICE_CONNECTED")
                    pollNavigation()
                } },
                onFailed = { error -> dispatch(token) {
                    naviReady = false
                    model.navigationStatus(false, "내비게이션 서비스에 연결하지 못했습니다.")
                    Log.w("CurbLinkPlatform", "Navi connect failed: ${error.javaClass.simpleName}")
                } }
            ) }
        } catch (error: Exception) { model.navigationStatus(false, "내비게이션 서비스를 사용할 수 없습니다.") }
        main.post(tick)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            readVehicle()
            if (sequence++ % 3 == 0) pollNavigation()
            if (lastNavigationReply != 0L && SystemClock.elapsedRealtime()-lastNavigationReply > 10000) {
                model.clearNavigationRoute(); model.navigationStatus(false, "내비게이션 응답을 기다리고 있습니다.")
            }
            main.postDelayed(this, 1000)
        }
    }
    @Suppress("MissingPermission", "DEPRECATION")
    private fun readVehicle() {
        val manager = properties ?: return
        try {
            if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
                model.vehicleUnavailable("차량 속도 정보 접근 권한을 허용해 주세요."); return
            }
            val gear = manager.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
            val speed = manager.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
            require(gear.status == CarPropertyValue.STATUS_AVAILABLE && speed.status == CarPropertyValue.STATUS_AVAILABLE)
            val ux = requireNotNull(restrictions).currentCarUxRestrictions
            val mapped = when (gear.value) {
                CarGear.GEAR_PARK -> VehicleGear.P
                CarGear.GEAR_DRIVE -> VehicleGear.D
                CarGear.GEAR_REVERSE -> VehicleGear.R
                CarGear.GEAR_NEUTRAL -> VehicleGear.N
                else -> VehicleGear.UNKNOWN
            }
            model.observeVehicle(VehicleObservation(mapped, abs(speed.value.toDouble())*3.6, SystemClock.elapsedRealtime(), VehicleSource.VEHICLE,
                ux.isRequiresDistractionOptimization || ux.activeRestrictions != 0))
            if (mapped != lastCarLog) {
                Log.i("CurbLinkPlatform", "CAR_DATA_RECEIVED gear=$mapped uxRestricted=${ux.isRequiresDistractionOptimization}")
                lastCarLog = mapped
            }
        } catch (error: Exception) { vehicleFailure(error) }
    }
    private fun vehicleFailure(error: Exception) {
        model.vehicleUnavailable("차량 상태를 확인할 수 없습니다. 연결과 접근 권한을 확인해 주세요.")
        Log.w("CurbLinkPlatform", "Car read failed: ${error.javaClass.simpleName}")
    }
    private fun pollNavigation() {
        if (!naviReady) return
        try { navi?.getRouteStateInfo(); navi?.getDestinationInfo(); navi?.getCurrentLocationInfo() }
        catch (error: Exception) { model.navigationStatus(false, "내비게이션 연결을 다시 확인하고 있습니다.") }
    }
    fun stop() {
        active = false
        generation++
        main.removeCallbacks(tick)
        try { restrictions?.unregisterListener() } catch (_: Exception) {}
        try { car?.disconnect() } catch (_: Exception) {}
        try { registeredListener?.let { navi?.removeListener(it) }; navi?.release() } catch (_: Exception) {}
        registeredListener = null
        car = null; properties = null; restrictions = null; navi = null; naviReady = false
        model.vehicleUnavailable("차량 연결을 확인하고 있습니다.")
        model.navigationStatus(false)
    }
}
