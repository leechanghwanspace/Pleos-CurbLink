package io.curblink.demo

import android.os.Bundle
import android.car.Car
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[MobilityViewModel::class.java] }
    private val vehiclePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.startConnections() else model.note("차량 속도 정보 권한을 허용해야 현재 차량 상태를 확인할 수 있습니다.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobilityApp(model) }
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) vehiclePermission.launch(Car.PERMISSION_SPEED)
    }
    override fun onStart() { super.onStart(); model.startConnections() }
    override fun onStop() { model.stopConnections(); super.onStop() }
}
