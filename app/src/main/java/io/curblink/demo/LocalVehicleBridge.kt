package io.curblink.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class BridgeSnapshot(
    val sessionId: String,
    val sequence: Long,
    val vehicle: VehicleSnapshot,
    val sensor: SensorSample,
    val vehicleDataValid: Boolean,
    val sensorDataValid: Boolean,
    val results: List<BridgeCommandResult>
)

data class BridgeCommandResult(
    val sessionId: String,
    val requestId: String,
    val accepted: Boolean,
    val reasonCodes: List<String>
)

/** Local simulation transport only. HTTP is allowed for these hosts in debug builds only. */
class LocalVehicleBridge(private val baseUrl: String = "http://10.0.2.2:8766") {
    init {
        require(baseUrl in setOf("http://10.0.2.2:8766", "http://127.0.0.1:8766"))
    }

    suspend fun poll(): BridgeSnapshot = withContext(Dispatchers.IO) {
        decodeSnapshot(request("/state"))
    }

    suspend fun command(sessionId: String, requestId: String, action: String): BridgeCommandResult =
        withContext(Dispatchers.IO) {
            require(action == "OPEN" || action == "CLOSE")
            require(requestId.isNotBlank() && requestId.length <= 128)
            val response = decodeResult(request("/command", envelope(sessionId, "COMMAND_REQUEST",
                JSONObject().put("requestId", requestId).put("target", "trunk").put("action", action))))
            if (response.sessionId != sessionId || response.requestId != requestId) {
                throw IOException("Command response correlation mismatch")
            }
            response
        }

    suspend fun scenario(sessionId: String, payload: JSONObject): Unit = withContext(Dispatchers.IO) {
        val response = request("/scenario", envelope(sessionId, "SCENARIO_STATE", payload))
        if (!strictBoolean(response, "ok")) throw IOException("Scenario update rejected")
    }

    private fun envelope(sessionId: String, type: String, payload: JSONObject): JSONObject {
        require(sessionId.isNotBlank() && sessionId.length <= 128)
        return JSONObject().put("protocol", PROTOCOL).put("messageId", UUID.randomUUID().toString())
            .put("source", "ivi-app").put("sessionId", sessionId).put("type", type)
            .put("timestamp", System.currentTimeMillis()).put("payload", payload)
    }

    private fun request(path: String, body: JSONObject? = null): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 700
            connection.readTimeout = 700
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                if (bytes.size > MAX_BODY) throw IOException("Request too large")
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            if (connection.responseCode !in 200..299) throw IOException("Bridge HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > MAX_BODY) throw IOException("Response too large")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count == -1) break
                    if (output.size() + count > MAX_BODY) throw IOException("Response too large")
                    output.write(chunk, 0, count)
                }
            }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
            return JSONObject(text)
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Invalid bridge response", error)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PROTOCOL = "curblink.vehicle.v1"
        private const val MAX_BODY = 65536

        internal fun decodeSnapshot(root: JSONObject): BridgeSnapshot {
            val vehicleEnvelope = strictObject(root, "vehicle")
            val session = validateEnvelope(vehicleEnvelope, "VEHICLE_STATE")
            val payload = strictObject(vehicleEnvelope, "payload")
            val sequence = strictInteger(payload, "sequence")
            val speed = strictNumber(payload, "speedKph")
            if (speed !in 0.0..200.0) throw IOException("Invalid speed")
            val gear = enumValue<Gear>(strictString(payload, "gear"))
            val doors = strictObject(payload, "doors")
            enumValue<Tailgate>(strictString(doors, "driverDoor"))
            enumValue<Tailgate>(strictString(doors, "passengerDoor"))
            val trunk = enumValue<Tailgate>(strictString(doors, "trunk"))
            val vehicleValid = strictBoolean(payload, "vehicleDataValid")
            val sensorValid = strictBoolean(payload, "sensorDataValid")
            val obstacle = strictBoolean(payload, "rearObstacle")
            val vehicle = VehicleSnapshot(gear = gear, speedKmh = speed.toFloat(), capturedAt = 0,
                uxRestricted = strictBoolean(payload, "uxRestricted"),
                oemControlAllowed = strictBoolean(payload, "oemControlAllowed"), tailgate = trunk)
            val sensor = SensorSample(when {
                !sensorValid -> SensorVerdict.UNVERIFIED
                obstacle -> SensorVerdict.OBSTACLE_DETECTED
                else -> SensorVerdict.CLEAR_IN_VERIFIED_RANGE
            }, 0, "외부 시뮬레이터 · 실차 센서 아님")
            val resultArray = root.opt("results") as? JSONArray ?: throw IOException("Missing results")
            if (resultArray.length() > 128) throw IOException("Too many command results")
            val results = (0 until resultArray.length()).map { index ->
                val result = decodeResult(resultArray.opt(index) as? JSONObject ?: throw IOException("Invalid result"))
                if (result.sessionId != session) throw IOException("Mixed sessions")
                result
            }
            return BridgeSnapshot(session, sequence, vehicle, sensor, vehicleValid, sensorValid, results)
        }

        internal fun decodeResult(root: JSONObject): BridgeCommandResult {
            val session = validateEnvelope(root, "COMMAND_RESULT")
            val payload = strictObject(root, "payload")
            val requestId = strictString(payload, "requestId")
            val accepted = strictBoolean(payload, "accepted")
            val reasons = payload.opt("reasonCodes") as? JSONArray ?: throw IOException("Missing reason codes")
            if (reasons.length() > 32) throw IOException("Too many reason codes")
            val codes = (0 until reasons.length()).map { index ->
                val code = reasons.opt(index) as? String ?: throw IOException("Invalid reason code")
                if (code.isBlank() || code.length > 128) throw IOException("Invalid reason code")
                code
            }
            if (accepted && codes.isNotEmpty()) throw IOException("Contradictory result")
            return BridgeCommandResult(session, requestId, accepted, codes)
        }

        private fun validateEnvelope(root: JSONObject, type: String): String {
            if (strictString(root, "protocol") != PROTOCOL || strictString(root, "source") != "android-bridge" ||
                strictString(root, "type") != type) throw IOException("Unexpected bridge envelope")
            strictString(root, "messageId")
            strictInteger(root, "timestamp")
            return strictString(root, "sessionId")
        }

        private fun strictObject(root: JSONObject, key: String): JSONObject =
            root.opt(key) as? JSONObject ?: throw IOException("Invalid object: $key")

        private fun strictString(root: JSONObject, key: String): String {
            val value = root.opt(key) as? String ?: throw IOException("Invalid string: $key")
            if (value.isBlank() || value.length > 128) throw IOException("Invalid string: $key")
            return value
        }

        private fun strictBoolean(root: JSONObject, key: String): Boolean =
            root.opt(key) as? Boolean ?: throw IOException("Invalid boolean: $key")

        private fun strictNumber(root: JSONObject, key: String): Double {
            val value = (root.opt(key) as? Number)?.toDouble() ?: throw IOException("Invalid number: $key")
            if (!value.isFinite()) throw IOException("Invalid number: $key")
            return value
        }

        private fun strictInteger(root: JSONObject, key: String): Long {
            val value = strictNumber(root, key)
            if (value < 0 || value > 9007199254740991.0 || value % 1.0 != 0.0) throw IOException("Invalid integer: $key")
            return value.toLong()
        }

        private inline fun <reified T : Enum<T>> enumValue(value: String): T =
            enumValues<T>().firstOrNull { it.name == value } ?: throw IOException("Invalid enum")
    }
}
