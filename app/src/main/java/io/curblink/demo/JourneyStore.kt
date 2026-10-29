package io.curblink.demo

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/** Local simulated provider ledger. Never persists GPS, voice, sensor checks or approvals. */
object JourneyStore {
    fun encode(s: MobilityState): String {
        val p = Properties()
        fun put(key: String, value: Any) { p.setProperty(key, value.toString()) }
        put("version", 1); put("trip", s.tripId); put("destination", s.destination.id)
        put("purpose", s.purpose.name); put("phase", s.phase.name); put("page", s.page)
        put("remaining", s.remaining); put("elapsed", s.elapsed); put("total", s.total)
        put("locationVersion", s.locationVersion); put("accessTrip", s.accessTrip)
        put("bay", s.bay?.id ?: ""); put("received", s.received)
        put("mealStep", s.mealStep.name); put("menu", s.selectedMenu?.id ?: "")
        s.order?.let { o -> put("orderId", o.id); put("orderMenu", o.menu.id); put("confirmedAt", o.confirmedAt); put("dispatchAt", o.dispatchAt); put("late", o.lateAtConfirmation) }
        return StringWriter().also { p.store(it, "CurbLink simulated journey v1") }.toString()
    }

    fun restore(raw: String?, base: MobilityState): MobilityState {
        if (raw == null) return base
        return try {
            require(raw.length < 16000)
            val p = Properties().apply { load(StringReader(raw)) }
            fun required(key: String) = requireNotNull(p.getProperty(key))
            fun number(key: String) = required(key).toInt().also { require(it in 0..604800) }
            fun flag(key: String) = required(key).toBooleanStrict()
            require(required("version") == "1")
            val trip = required("trip").also { java.util.UUID.fromString(it) }
            val destination = MobilityCatalog.places.firstOrNull { it.id == required("destination") }
                ?: MobilityCatalog.liveDestination.also { require(required("destination") == it.id) }
            val total = number("total").also { require(it > 0) }
            val remaining = number("remaining").also { require(it <= total) }
            val elapsed = number("elapsed")
            val selected = p.getProperty("menu")?.takeIf { it.isNotEmpty() }?.let { id -> MobilityCatalog.menus.first { it.id == id } }
            val order = p.getProperty("orderMenu")?.let { id ->
                val menu = MobilityCatalog.menus.first { it.id == id }
                val confirmed = number("confirmedAt"); val dispatch = number("dispatchAt")
                require(dispatch >= confirmed && confirmed <= elapsed)
                val orderId = required("orderId").also { java.util.UUID.fromString(it) }
                DiningOrder(menu, confirmed, dispatch, number("late"), orderId)
            }
            val step = DiningStep.valueOf(required("mealStep"))
            require(step != DiningStep.TRACK || order != null)
            require(step != DiningStep.REVIEW || selected != null)
            val access = flag("accessTrip")
            val bay = p.getProperty("bay")?.takeIf { it.isNotEmpty() }?.let { id -> MobilityCatalog.bays.first { it.id == id } }
            require(!access || bay != null)
            base.copy(tripId = trip, destination = destination, purpose = ArrivalPurpose.valueOf(required("purpose")),
                phase = DrivePhase.valueOf(required("phase")), page = number("page").also { require(it in 0..4) },
                remaining = remaining, elapsed = elapsed, total = total, paused = true, vehicleValid = false,
                gpsReady = false, role = CabinRole.DRIVER, locationVersion = number("locationVersion") + 1,
                accessTrip = access, bay = bay, selectedMenu = selected, mealStep = step, order = order,
                source = VehicleSource.UNAVAILABLE, gear = VehicleGear.UNKNOWN, servicesReady = false, routeReady = false,
                received = flag("received"), spaceChecked = false, rampReady = false, staffAccepted = false,
                accessApproved = false, exitProgress = 0, locationShared = false, confirmedLocation = null,
                note = "여정·주문 기록을 복구했습니다. 차량과 서비스 연결을 확인하고 있습니다.")
        } catch (_: Exception) {
            base.copy(note = "저장된 여정을 읽지 못했습니다. 차량과 서비스 연결 후 상태를 다시 확인해 주세요.")
        }
    }
}
