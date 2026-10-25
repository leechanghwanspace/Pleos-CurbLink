package io.curblink.demo

enum class Gear { P, D, R, UNKNOWN }
enum class SensorVerdict { CLEAR_IN_VERIFIED_RANGE, OBSTACLE_DETECTED, UNVERIFIED }
enum class PositioningMode { GPS_GEOFENCE, UWB_VERIFIED_ZONE, MANUAL_BAY_NUMBER }
enum class OrderState { DRAFT, PAID, READY, OPEN_REQUESTED, OPEN, COMPLETE }
enum class Tailgate { CLOSED, OPEN, UNKNOWN }

// All times supplied to the domain are monotonic milliseconds, never wall-clock time.
data class VehicleSnapshot(
    val gear: Gear = Gear.P,
    val speedKmh: Float = 0f,
    val capturedAt: Long = 0,
    val uxRestricted: Boolean = false,
    val oemControlAllowed: Boolean = false,
    val tailgate: Tailgate = Tailgate.CLOSED
)
data class SensorSample(
    val verdict: SensorVerdict = SensorVerdict.UNVERIFIED,
    val capturedAt: Long = 0,
    val reason: String = "센서 미확인"
)
data class StaffGrant(val orderId: String, val version: Int, val expiresAt: Long)
data class Order(val id: String = "DEMO-001", val revision: Int = 1,
                 val amountWon: Int = 24000, val state: OrderState = OrderState.DRAFT)
data class Check(val label: String, val passed: Boolean)
data class InterlockStatus(val checks: List<Check>) {
    val prerequisitesMet: Boolean get() = checks.all { it.passed }
    val reasons: List<String> get() = checks.filterNot { it.passed }.map { it.label }
}
data class PartnerEvent(val eventId: String, val orderId: String, val revision: Int,
                        val version: Int, val expiresAt: Long, val type: String)
data class PaymentReceipt(val key: String, val orderId: String, val revision: Int,
                          val amountWon: Int, val demoReference: String)

interface VehicleAdapter {
    fun snapshot(now: Long): VehicleSnapshot
    fun requestOpen(commandId: String): Boolean
}
interface PaymentAdapter { fun pay(order: Order, key: String): PaymentReceipt }

data class DemoState(
    val now: Long = 0,
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val sensor: SensorSample = SensorSample(),
    val order: Order = Order(),
    val staff: StaffGrant? = null,
    val position: PositioningMode = PositioningMode.MANUAL_BAY_NUMBER,
    val zoneConfirmed: Boolean = false,
    val pendingMilk: Boolean = false,
    val sensorStreaming: Boolean = true,
    val otpInput: String = "",
    val issuedOtp: String? = null,
    val otpExpiresAt: Long = 0,
    val otpAttempts: Int = 0,
    val commandAt: Long? = null,
    val commandCount: Int = 0,
    val chargeCount: Int = 0,
    val lastMessage: String = "시연 모드 · 실제 결제/차량 제어 없음",
    val logs: List<String> = emptyList(),
    val testResults: List<Pair<String, Boolean>> = emptyList()
)
