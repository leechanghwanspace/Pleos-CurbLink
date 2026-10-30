package io.curblink.demo

enum class VehicleGear { UNKNOWN, P, D, R, N }
enum class VehicleSource { UNAVAILABLE, VEHICLE, TEST }

/** Adapter input contract; no simulator or SDK implementation is embedded here. */
data class VehicleObservation(val gear: VehicleGear, val speedKph: Double, val observedAtMs: Long,
    val source: VehicleSource, val uxRestricted: Boolean) {
    fun validAt(nowMs: Long): Boolean = source != VehicleSource.UNAVAILABLE && gear != VehicleGear.UNKNOWN &&
        speedKph.isFinite() && speedKph in 0.0..250.0 && nowMs - observedAtMs in 0..5000
    val moving get() = speedKph > .1 || gear != VehicleGear.P
}

data class TripObservation(val remainingSeconds: Int, val remainingMeters: Int, val elapsedSeconds: Int,
    val arrived: Boolean, val onMainRoad: Boolean = true, val currentLane: Int = 2)
