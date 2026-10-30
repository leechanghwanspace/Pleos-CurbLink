package io.curblink.demo

import org.junit.Assert.*
import org.junit.Test

class VehicleContextTest {
    @Test fun unknownOrExpiredVehicleNeverGrantsAccess() {
        val observation=VehicleObservation(VehicleGear.P,0.0,1000,VehicleSource.VEHICLE,false)
        assertTrue(observation.validAt(2000));assertFalse(observation.validAt(6001))
        assertFalse(observation.validAt(999));assertFalse(observation.copy(gear=VehicleGear.UNKNOWN).validAt(1000))
        assertFalse(observation.copy(speedKph=Double.NaN).validAt(1000))
        assertFalse(MobilityState(vehicleValid=true,gear=VehicleGear.UNKNOWN).canEdit)
    }
    @Test fun driveOrOemRestrictionCannotBecomeParkedTouchAccess() {
        assertFalse(VehicleObservation(VehicleGear.P,0.0,1000,VehicleSource.TEST,false).moving)
        assertTrue(VehicleObservation(VehicleGear.R,0.0,1000,VehicleSource.TEST,false).moving)
        assertFalse(MobilityState(gear=VehicleGear.P,uxRestricted=true).canEdit)
        assertFalse(MobilityState(gear=VehicleGear.D,phase=DrivePhase.DRIVING,role=CabinRole.PASSENGER).canEdit)
    }
}
