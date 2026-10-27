import asyncio
import json
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from websockets.asyncio.client import connect
from websockets.exceptions import InvalidStatus

from server import Bridge, PROTOCOL, ProtocolError, decode, run


def message(bridge, kind, payload, source="simulator"):
    value = bridge.envelope(kind, payload)
    value["source"] = source
    return value


def valid_vehicle(sequence=0):
    return dict(injectionSequence=sequence, speedKph=0, gear="P", uxRestricted=False,
                doors=dict(driverDoor="CLOSED", passengerDoor="CLOSED", trunk="CLOSED"),
                rearObstacle=False, vehicleDataValid=True, sensorDataValid=True, oemControlAllowed=True)


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.bridge = Bridge()
        self.owner = object()

    def inject(self, payload):
        self.bridge.inject(message(self.bridge, "STATE_INJECTION", payload), self.owner)

    def command(self, request_id="one", action="OPEN"):
        return self.bridge.command(message(self.bridge, "COMMAND_REQUEST",
                    dict(requestId=request_id, target="trunk", action=action), "ivi-app"))

    def test_initial_unknown_and_invalid(self):
        state = self.bridge.snapshot()["payload"]
        self.assertFalse(state["vehicleDataValid"])
        self.assertEqual(state["doors"]["trunk"], "UNKNOWN")
        self.assertFalse(self.command()["payload"]["accepted"])

    def test_duplicate_success_does_not_reopen_after_close(self):
        self.inject(valid_vehicle())
        original = self.command()
        self.assertTrue(original["payload"]["accepted"])
        self.assertTrue(self.command("two", "CLOSE")["payload"]["accepted"])
        self.assertEqual(original, self.command())
        self.assertEqual("CLOSED", self.bridge.snapshot()["payload"]["doors"]["trunk"])

    def test_request_id_conflict(self):
        self.inject(valid_vehicle())
        self.command()
        with self.assertRaisesRegex(ProtocolError, "CONFLICT"):
            self.command(action="CLOSE")

    def test_each_vehicle_interlock_blocks(self):
        for key, value, reason in [("gear", "D", "GEAR_NOT_P"), ("speedKph", 5, "VEHICLE_MOVING"),
                                 ("uxRestricted", True, "UX_RESTRICTED"), ("rearObstacle", True, "REAR_OBSTACLE"),
                                 ("sensorDataValid", False, "SENSOR_DATA_INVALID"),
                                 ("vehicleDataValid", False, "VEHICLE_DATA_INVALID"),
                                 ("oemControlAllowed", False, "OEM_CONTROL_DENIED")]:
            with self.subTest(key=key):
                self.bridge = Bridge()
                payload = valid_vehicle()
                payload[key] = value
                self.inject(payload)
                result = self.command()["payload"]
                self.assertFalse(result["accepted"])
                self.assertIn(reason, result["reasonCodes"])

    def test_invalid_input_is_atomic(self):
        before = self.bridge.snapshot()["payload"]
        for update in ({"speedKph": float("nan")}, {"speedKph": True}, {"uxRestricted": 0},
                       {"doors": {"trunk": "MAYBE"}}, {"unknown": 1}):
            with self.subTest(update=update), self.assertRaises(ProtocolError):
                self.inject(dict(injectionSequence=0, **update))
        after = self.bridge.snapshot()["payload"]
        before.pop("sequence"); after.pop("sequence")
        self.assertEqual(before, after)

    def test_wrong_session_and_source_rejected(self):
        value = message(self.bridge, "COMMAND_REQUEST", dict(requestId="one", target="trunk", action="OPEN"), "ivi-app")
        value["sessionId"] = "old-session"
        with self.assertRaisesRegex(ProtocolError, "SESSION"):
            self.bridge.command(value)
        value["sessionId"] = self.bridge.session
        value["source"] = "simulator"
        with self.assertRaisesRegex(ProtocolError, "NOT_ALLOWED"):
            self.bridge.command(value)

    def test_injection_sequence_and_owner(self):
        self.inject(valid_vehicle())
        with self.assertRaisesRegex(ProtocolError, "STALE"):
            self.inject(valid_vehicle())
        with self.assertRaisesRegex(ProtocolError, "CONTROLLER"):
            self.bridge.inject(message(self.bridge, "STATE_INJECTION", valid_vehicle(1)), object())

    def test_disconnect_invalidates_and_resync_required(self):
        self.inject(valid_vehicle())
        self.bridge.disconnect(self.owner)
        self.assertFalse(self.command()["payload"]["accepted"])
        with self.assertRaisesRegex(ProtocolError, "INCOMPLETE"):
            self.inject(dict(injectionSequence=1, vehicleDataValid=True))
        self.inject(valid_vehicle(1))
        self.assertTrue(self.command("new")["payload"]["accepted"])

    def test_heartbeat_expiry_does_not_auto_restore_validity(self):
        self.inject(valid_vehicle())
        self.bridge.controller_at -= 4
        self.bridge.heartbeat(message(self.bridge, "HEARTBEAT", {}), self.owner)
        self.assertFalse(self.bridge.snapshot()["payload"]["vehicleDataValid"])

    def test_scenario_cannot_control_vehicle(self):
        self.bridge.set_scenario(message(self.bridge, "SCENARIO_STATE", {"orderState": "READY", "doors": {"trunk": "OPEN"}}, "ivi-app"))
        self.assertEqual("UNKNOWN", self.bridge.snapshot()["payload"]["doors"]["trunk"])

    def test_invalid_json_and_message_size(self):
        for raw in ('{"a":NaN}', '[]', '{', ' ' * 16385):
            with self.assertRaises(ProtocolError):
                decode(raw)

    def test_gemini_vehicle_patch_and_scenario_authority(self):
        patch = valid_vehicle()
        patch.pop("injectionSequence")
        value = message(self.bridge, "EVALUATOR_INJECT", dict(label="initialize", vehiclePatch=patch, scenarioPatch=None))
        self.bridge.inject_legacy(value, self.owner)
        self.assertTrue(self.bridge.snapshot()["payload"]["vehicleDataValid"])
        value["payload"] = dict(label="bad", vehiclePatch={"rearObstacle": True}, scenarioPatch={"customerReadyConfirmed": True})
        with self.assertRaisesRegex(ProtocolError, "APK_SCENARIO_AUTHORITY"):
            self.bridge.inject_legacy(value, self.owner)
        self.assertFalse(self.bridge.snapshot()["payload"]["rearObstacle"])
        value["payload"]["scenarioPatch"] = None
        value["timestamp"] -= 1
        with self.assertRaisesRegex(ProtocolError, "STALE_INJECTION_TIMESTAMP"):
            self.bridge.inject_legacy(value, self.owner)

    def test_gemini_scenario_aliases_are_app_derived(self):
        payload = dict(orderState="READY", zoneConfirmed=True, staffAuthenticated=True,
                       care=dict(remainInVehicle=True, trunkLoadingHelp=True, extraPreparationTime=False,
                                 response="ACCEPTED", customerReady=True))
        self.bridge.set_scenario(message(self.bridge, "SCENARIO_STATE", payload, "ivi-app"))
        result = self.bridge.scenario["payload"]
        self.assertTrue(result["orderReady"] and result["pickupZoneConfirmed"] and result["otpVerified"])
        self.assertEqual(result["carePrefs"], dict(waitInVehicle=True, loadingHelp=True, extraPrepTime=False))
        self.assertTrue(result["customerReadyConfirmed"])


class TransportTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.stop = threading.Event()
        ready = asyncio.get_running_loop().create_future()
        self.task = asyncio.create_task(run(0, 0, ready=lambda *values: ready.set_result(values), stop=self.stop))
        self.bridge, self.ws_port, self.http_port = await asyncio.wait_for(ready, 5)

    async def asyncTearDown(self):
        self.stop.set()
        await asyncio.wait_for(self.task, 5)

    async def http(self, path, value=None, origin=None):
        def request():
            headers = {"Content-Type": "application/json"}
            if origin:
                headers["Origin"] = origin
            req = Request(f"http://127.0.0.1:{self.http_port}{path}",
                          data=None if value is None else json.dumps(value).encode(), headers=headers)
            with urlopen(req, timeout=3) as response:
                return json.load(response)
        return await asyncio.to_thread(request)

    async def test_websocket_to_http_command_roundtrip(self):
        async with connect(f"ws://127.0.0.1:{self.ws_port}", origin="http://localhost:8000", proxy=None) as socket:
            hello = json.loads(await socket.recv())
            self.assertEqual(hello["type"], "HELLO")
            await socket.send(json.dumps(message(self.bridge, "STATE_INJECTION", valid_vehicle())))
            for _ in range(20):
                state = await self.http("/state")
                if state["vehicle"]["payload"]["vehicleDataValid"]:
                    break
                await asyncio.sleep(.02)
            self.assertTrue(state["vehicle"]["payload"]["vehicleDataValid"])
            result = await self.http("/command", message(self.bridge, "COMMAND_REQUEST",
                                    dict(requestId="roundtrip", target="trunk", action="OPEN"), "ivi-app"))
            self.assertTrue(result["payload"]["accepted"])
            state = await self.http("/state")
            self.assertEqual(state["vehicle"]["payload"]["doors"]["trunk"], "OPEN")
            self.assertEqual(state["results"][-1], result)
            await self.http("/scenario", message(self.bridge, "SCENARIO_STATE", {"orderState": "OPEN"}, "ivi-app"))
            received = []
            for _ in range(20):
                received.append(json.loads(await asyncio.wait_for(socket.recv(), 2)))
                if any(e["type"] == "SCENARIO_STATE" for e in received):
                    break
            self.assertTrue(any(e["type"] == "COMMAND_RESULT" for e in received))
            self.assertTrue(any(e["type"] == "SCENARIO_STATE" for e in received))
        await asyncio.sleep(.05)
        self.assertFalse((await self.http("/state"))["vehicle"]["payload"]["vehicleDataValid"])

    async def test_origin_and_browser_command_rejection(self):
        with self.assertRaises(InvalidStatus):
            async with connect(f"ws://127.0.0.1:{self.ws_port}", origin="https://untrusted.example", proxy=None):
                pass
        value = message(self.bridge, "COMMAND_REQUEST", dict(requestId="x", target="trunk", action="OPEN"), "ivi-app")
        with self.assertRaises(HTTPError):
            await self.http("/command", value, "http://localhost:8000")
        async with connect(f"ws://127.0.0.1:{self.ws_port}", origin="http://localhost:8000", proxy=None) as socket:
            await socket.send(json.dumps(value))
            while True:
                response = json.loads(await asyncio.wait_for(socket.recv(), 2))
                if response["type"] == "ERROR":
                    self.assertEqual(response["payload"]["code"], "BROWSER_MESSAGE_NOT_ALLOWED")
                    break

    async def test_gemini_ping_pong_and_duplicate_message(self):
        async with connect(f"ws://127.0.0.1:{self.ws_port}", origin="http://localhost:8080", proxy=None) as socket:
            patch = valid_vehicle()
            patch.pop("injectionSequence")
            value = message(self.bridge, "EVALUATOR_INJECT", dict(label="initialize", vehiclePatch=patch, scenarioPatch=None))
            await socket.send(json.dumps(value))
            await socket.send(json.dumps(value))
            while True:
                response = json.loads(await asyncio.wait_for(socket.recv(), 2))
                if response["type"] == "ERROR":
                    self.assertEqual(response["payload"]["code"], "DUPLICATE_MESSAGE")
                    break
            # No application HEARTBEAT: confirmed protocol pong keeps only legacy controller alive.
            await asyncio.sleep(3.2)
            self.assertTrue((await self.http("/state"))["vehicle"]["payload"]["vehicleDataValid"])


if __name__ == "__main__":
    unittest.main()
