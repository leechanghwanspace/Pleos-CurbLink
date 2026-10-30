package io.curblink.demo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import java.io.File

/** Runs the real ViewModel, bundled JSON and Compose UI on Android; no new test dependency. */
class NativeJourneyInstrumentation : Instrumentation() {
    private lateinit var vm: MobilityViewModel
    private var assertions = 0
    private val completed = mutableListOf<String>()
    private fun verify(condition: Boolean, message: String) { assertions++; check(condition) { message } }
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        waitForIdleSync()
    }
    private fun capture(name: String) {
        onMain { if (vm.state.value.settings) vm.settings() }
        SystemClock.sleep(200)
        val bitmap = uiAutomation.takeScreenshot() ?: error("No Android screenshot for $name")
        val directory = File(targetContext.filesDir, "native-journey-tests").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        onMain { if (!vm.state.value.settings) vm.settings() }
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            onMain { vm = ViewModelProvider(activity)[MobilityViewModel::class.java] }
            verify(vm.scenarios.size == 6, "Six bundled scenarios must load on Android")
            for (repeat in 1..3) for (id in listOf("PICKUP", "PARKING", "SHORT_STOP", "MEAL", "RIVER", "ACCESS")) {
                onMain {
                    vm.reset(); vm.beginScenario(id)
                    if (id == "ACCESS") { vm.selectBay(); vm.startTrip(true) }
                    vm.pause(); vm.settings()
                }
                verify(!vm.state.value.accessApproved && !vm.state.value.locationShared && vm.state.value.exitProgress == 0, "$id reset leaked approval")
                verify(vm.state.value.journey?.scenario?.id == id, "$id did not start")
                if (id == "MEAL" || id == "RIVER") {
                    onMain { vm.voice("메뉴 추천"); vm.voice("주문하지 마") }
                    verify(vm.state.value.order == null, "Negative voice created an order")
                    onMain { vm.voice("메뉴 추천"); vm.voice("주문 가격 알려줘") }
                    verify(vm.state.value.order == null, "Price question created an order")
                    onMain { vm.voice("주문해 줘") }
                    val order = vm.state.value.order
                    verify(order != null, "Explicit simulated order was not confirmed")
                    onMain { vm.voice("주문해 줘") }
                    verify(vm.state.value.order?.id == order?.id, "Duplicate confirmation created a new order")
                }
                var obstacleTested = false
                val observed = mutableSetOf<String>()
                for (step in 0..240) {
                    val s = vm.state.value
                    val run = s.journey ?: error("$id lost journey")
                    if (repeat == 1 && observed.add(run.current.id)) capture("${id.lowercase()}-${run.current.id.lowercase()}")
                    if (run.done) break
                    when (run.current.id) {
                        "LOCATE" -> {
                            verify(!s.canShareLocation, "River shared without current card confirmation")
                            onMain { vm.shareLocation() }
                            verify(vm.state.value.canShareLocation, "Location card could not be confirmed")
                            onMain { vm.shareInScene() }
                        }
                        "ACCESS_WAIT" -> onMain { vm.check("space", true); vm.check("ramp", true); vm.check("staff", true); vm.approveExit() }
                        "ACCESS_EXIT" -> if (!obstacleTested && s.exitProgress > 0) {
                            obstacleTested = true
                            onMain { vm.check("obstacle", true) }
                            verify(!vm.state.value.accessApproved && vm.state.value.exitHeld, "Obstacle did not revoke approval")
                            val progress = vm.state.value.exitProgress
                            onMain { vm.advanceDemo(60) }
                            verify(vm.state.value.exitProgress == progress, "Held exit kept moving")
                            onMain { vm.check("obstacle", false); vm.check("space", true) }
                            verify(!vm.state.value.accessApproved, "Recovery silently reapproved exit")
                            onMain { vm.approveExit() }
                        }
                        "HANDOFF" -> onMain { vm.receive() }
                    }
                    onMain { vm.advanceDemo(30) }
                }
                val final = vm.state.value
                verify(final.journey?.done == true, "$id did not complete within bounded simulated time")
                if (id == "ACCESS") verify(final.exitProgress == 100 && obstacleTested, "Access exit or hold test incomplete")
                if (id == "MEAL" || id == "RIVER") verify(final.received, "$id receipt not completed")
                completed += "$id/$repeat"
            }
            // Leave a known durable mock order for a separate real force-stop/restart check.
            onMain { vm.reset(); vm.beginScenario("MEAL"); vm.pause(); vm.voice("메뉴 추천"); vm.voice("주문해 줘") }
            capture("order-before-restart")
            val result = Bundle().apply {
                putString("result", "PASS: ${completed.size} native journeys; $assertions assertions")
                putString("orderId", vm.state.value.order?.id)
                putString("journeys", completed.joinToString(","))
            }
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", failure.stackTraceToString()); putString("completed", completed.joinToString(",")) })
        }
    }
}
