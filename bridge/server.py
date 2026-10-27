"""Loopback-only demonstration vehicle bridge. Never connects to a real vehicle."""
from __future__ import annotations

import argparse
import asyncio
from collections import OrderedDict, deque
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import threading
import time
import uuid

from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed

PROTOCOL = "curblink.vehicle.v1"
MAX_BODY = 16_384
DEFAULT_ORIGINS = ("http://127.0.0.1:8000", "http://localhost:8000",
                   "http://127.0.0.1:8080", "http://localhost:8080")


class ProtocolError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ProtocolError(message)


def decode(raw):
    require(isinstance(raw, (str, bytes)) and len(raw) <= MAX_BODY, "MESSAGE_TOO_LARGE")
    try:
        value = json.loads(raw, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except (ValueError, UnicodeError):
        raise ProtocolError("INVALID_JSON") from None
    require(isinstance(value, dict), "EXPECTED_OBJECT")
    return value


def validate_envelope(value, kind, source, session=None):
    require(value.get("protocol") == PROTOCOL, "PROTOCOL_MISMATCH")
    require(value.get("type") == kind and value.get("source") == source, "MESSAGE_NOT_ALLOWED")
    for key in ("messageId", "sessionId"):
        require(isinstance(value.get(key), str) and 0 < len(value[key]) <= 128, "INVALID_" + key)
    require(type(value.get("timestamp")) is int and value["timestamp"] >= 0, "INVALID_TIMESTAMP")
    require(isinstance(value.get("payload"), dict), "INVALID_PAYLOAD")
    if session is not None:
        require(value["sessionId"] == session, "SESSION_MISMATCH")
    return value["payload"]


class Bridge:
    def __init__(self):
        self.session = str(uuid.uuid4())
        self.lock = threading.RLock()
        self.sequence = 0
        self.vehicle = dict(speedKph=0, gear="UNKNOWN", uxRestricted=True,
                            doors=dict(driverDoor="UNKNOWN", passengerDoor="UNKNOWN", trunk="UNKNOWN"),
                            rearObstacle=False, vehicleDataValid=False, sensorDataValid=False,
                            oemControlAllowed=False)
        self.controller = None
        self.controller_at = 0.0
        self.injection_sequence = -1
        self.legacy_controller = False
        self.legacy_timestamp = -1
        self.commands = OrderedDict()
        self.results = deque(maxlen=32)
        self.scenario = None
        self.outbox = deque(maxlen=128)

    def envelope(self, kind, payload):
        return dict(protocol=PROTOCOL, messageId=str(uuid.uuid4()), source="android-bridge",
                    sessionId=self.session, type=kind, timestamp=int(time.time() * 1000), payload=payload)

    def invalidate(self):
        self.vehicle.update(vehicleDataValid=False, sensorDataValid=False, oemControlAllowed=False)
        self.vehicle["doors"] = dict.fromkeys(self.vehicle["doors"], "UNKNOWN")

    def expire(self):
        if self.controller is not None and time.monotonic() - self.controller_at >= 3:
            self.invalidate()
            self.controller = None

    def disconnect(self, owner):
        with self.lock:
            if self.controller is owner:
                self.invalidate()
                self.controller = None

    def snapshot(self):
        with self.lock:
            self.expire()
            self.sequence += 1
            return self.envelope("VEHICLE_STATE", dict(sequence=self.sequence, **deepcopy(self.vehicle)))

    def state(self):
        with self.lock:
            return dict(vehicle=self.snapshot(), results=list(self.results))

    def inject(self, message, owner):
        with self.lock:
            payload = validate_envelope(message, "STATE_INJECTION", "simulator", self.session)
            allowed = set(self.vehicle) | {"injectionSequence"}
            require(set(payload) <= allowed, "UNKNOWN_VEHICLE_FIELD")
            seq = payload.get("injectionSequence")
            require(type(seq) is int and seq >= 0, "INVALID_INJECTION_SEQUENCE")
            self.expire()
            require(self.controller is None or self.controller is owner, "CONTROLLER_ALREADY_CONNECTED")
            if self.controller is owner:
                require(seq > self.injection_sequence, "STALE_INJECTION")
            candidate = deepcopy(self.vehicle)
            for key, value in payload.items():
                if key == "injectionSequence":
                    continue
                if key == "speedKph":
                    require(type(value) in (int, float) and math.isfinite(value) and 0 <= value <= 200,
                            "INVALID_SPEED")
                elif key == "gear":
                    require(value in ("P", "D", "R", "UNKNOWN"), "INVALID_GEAR")
                elif key == "doors":
                    require(isinstance(value, dict) and set(value) <= set(candidate["doors"]), "INVALID_DOORS")
                    require(all(v in ("OPEN", "CLOSED", "UNKNOWN") for v in value.values()), "INVALID_DOOR_STATE")
                    candidate["doors"].update(value)
                    continue
                else:
                    require(type(value) is bool, "INVALID_BOOLEAN")
                candidate[key] = value
            # First sync must not silently turn UNKNOWN state into a usable snapshot.
            if candidate["vehicleDataValid"]:
                require(candidate["gear"] != "UNKNOWN" and all(v != "UNKNOWN" for v in candidate["doors"].values()),
                        "INCOMPLETE_VEHICLE_SNAPSHOT")
            self.vehicle = candidate
            self.controller, self.controller_at, self.injection_sequence = owner, time.monotonic(), seq
            self.legacy_controller = False

    def inject_legacy(self, message, owner):
        """Gemini shell compatibility; app-owned scenario writes remain forbidden."""
        with self.lock:
            payload = validate_envelope(message, "EVALUATOR_INJECT", "simulator", self.session)
            require(set(payload) <= {"label", "vehiclePatch", "scenarioPatch"}, "INVALID_EVALUATOR_FIELDS")
            require(isinstance(payload.get("label"), str) and len(payload["label"]) <= 200, "INVALID_LABEL")
            require(payload.get("scenarioPatch") in (None, {}), "APK_SCENARIO_AUTHORITY")
            patch = payload.get("vehiclePatch")
            require(isinstance(patch, dict) and bool(patch), "VEHICLE_PATCH_REQUIRED")
            self.expire()
            if self.controller is owner and self.legacy_controller:
                require(message["timestamp"] >= self.legacy_timestamp, "STALE_INJECTION_TIMESTAMP")
            translated = dict(message, type="STATE_INJECTION", payload=dict(patch,
                              injectionSequence=self.injection_sequence + 1 if self.controller is owner else 0))
            self.inject(translated, owner)
            self.legacy_controller = True
            self.legacy_timestamp = message["timestamp"]

    def pong_received(self, owner):
        with self.lock:
            self.expire()
            if self.controller is owner and self.legacy_controller:
                self.controller_at = time.monotonic()

    def heartbeat(self, message, owner):
        with self.lock:
            validate_envelope(message, "HEARTBEAT", "simulator", self.session)
            self.expire()
            if self.controller is owner:
                self.controller_at = time.monotonic()

    def command(self, message):
        with self.lock:
            payload = validate_envelope(message, "COMMAND_REQUEST", "ivi-app", self.session)
            require(set(payload) == {"requestId", "target", "action"}, "INVALID_COMMAND_FIELDS")
            request_id, target, action = (payload.get(k) for k in ("requestId", "target", "action"))
            require(isinstance(request_id, str) and 0 < len(request_id) <= 128, "INVALID_REQUEST_ID")
            require(target == "trunk" and action in ("OPEN", "CLOSE"), "UNSUPPORTED_COMMAND")
            if request_id in self.commands:
                original, result = self.commands[request_id]
                require(original == payload, "REQUEST_ID_CONFLICT")
                return deepcopy(result)
            # Do not evict old IDs and accidentally execute a replay in the same session.
            require(len(self.commands) < 1024, "SESSION_COMMAND_LIMIT_RESTART_REQUIRED")
            self.expire()
            v = self.vehicle
            reasons = []
            for ok, reason in ((v["vehicleDataValid"], "VEHICLE_DATA_INVALID"),
                               (v["speedKph"] == 0, "VEHICLE_MOVING"), (v["gear"] == "P", "GEAR_NOT_P"),
                               (not v["uxRestricted"], "UX_RESTRICTED"),
                               (v["sensorDataValid"], "SENSOR_DATA_INVALID"),
                               (not v["rearObstacle"], "REAR_OBSTACLE"),
                               (v["oemControlAllowed"], "OEM_CONTROL_DENIED"),
                               (v["doors"]["trunk"] == ("CLOSED" if action == "OPEN" else "OPEN"), "TRUNK_STATE_MISMATCH")):
                if not ok:
                    reasons.append(reason)
            result = self.envelope("COMMAND_RESULT", dict(requestId=request_id, accepted=not reasons, reasonCodes=reasons))
            self.commands[request_id] = (deepcopy(payload), result)
            self.results.append(result)
            self.outbox.append(result)
            if not reasons:
                # Simulated controller transition, not an OEM command or hardware acknowledgement.
                v["doors"]["trunk"] = "OPEN" if action == "OPEN" else "CLOSED"
            return deepcopy(result)

    def set_scenario(self, message):
        with self.lock:
            payload = validate_envelope(message, "SCENARIO_STATE", "ivi-app", self.session)
            # App owns this informational snapshot. It cannot alter the vehicle controller.
            display = deepcopy(payload)
            care = payload.get("care", {})
            require(isinstance(care, dict), "INVALID_CARE")
            display.update(orderReady=payload.get("orderState") in ("READY", "OPEN_REQUESTED", "OPEN"),
                           pickupZoneConfirmed=payload.get("zoneConfirmed") is True,
                           otpVerified=payload.get("staffAuthenticated") is True,
                           carePrefs=dict(waitInVehicle=care.get("remainInVehicle") is True,
                                          loadingHelp=care.get("trunkLoadingHelp") is True,
                                          extraPrepTime=care.get("extraPreparationTime") is True),
                           merchantResponse=care.get("response", "PENDING"),
                           customerReadyConfirmed=care.get("customerReady") is True,
                           handoffCompleted=payload.get("orderState") == "COMPLETE")
            self.scenario = self.envelope("SCENARIO_STATE", display)
            self.outbox.append(self.scenario)


def make_handler(bridge, origins):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def setup(self):
            super().setup()
            self.connection.settimeout(3)

        def respond(self, status, value):
            raw = json.dumps(value, allow_nan=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            origin = self.headers.get("Origin")
            if origin in origins:
                self.send_header("Access-Control-Allow-Origin", origin)
                self.send_header("Vary", "Origin")
            self.end_headers()
            self.wfile.write(raw)

        def check_origin(self, mutation=False):
            origin = self.headers.get("Origin")
            require(origin is None or origin in origins, "ORIGIN_NOT_ALLOWED")
            # Only native app HTTP may mutate scenario/command. Browser uses constrained WS injections.
            require(not mutation or origin is None, "BROWSER_HTTP_MUTATION_FORBIDDEN")
            host = self.headers.get("Host", "").split(":")[0]
            require(host in ("127.0.0.1", "localhost", "10.0.2.2"), "HOST_NOT_ALLOWED")

        def do_GET(self):
            try:
                self.check_origin()
                if self.path == "/state":
                    self.respond(200, bridge.state())
                elif self.path == "/health":
                    self.respond(200, dict(ok=True, protocol=PROTOCOL, sessionId=bridge.session, simulation=True))
                else:
                    self.respond(404, dict(error="NOT_FOUND"))
            except ProtocolError as exc:
                self.respond(403, dict(error=str(exc)))

        def do_POST(self):
            try:
                self.check_origin(mutation=True)
                require(self.headers.get("Content-Type", "").split(";")[0].strip() == "application/json", "JSON_REQUIRED")
                length = self.headers.get("Content-Length", "")
                require(length.isdigit() and 0 < int(length) <= MAX_BODY, "INVALID_CONTENT_LENGTH")
                raw = self.rfile.read(int(length))
                require(len(raw) == int(length), "INCOMPLETE_BODY")
                message = decode(raw)
                if self.path == "/command":
                    self.respond(200, bridge.command(message))
                elif self.path == "/scenario":
                    bridge.set_scenario(message)
                    self.respond(200, dict(ok=True))
                else:
                    self.respond(404, dict(error="NOT_FOUND"))
            except (ProtocolError, TimeoutError) as exc:
                self.respond(400, dict(error=str(exc)))

        def do_OPTIONS(self):
            self.respond(403, dict(error="BROWSER_HTTP_MUTATION_FORBIDDEN"))

    return Handler


async def run(ws_port=8765, http_port=8766, origins=DEFAULT_ORIGINS, ready=None, stop=None):
    bridge = Bridge()
    clients = set()

    async def client(socket):
        clients.add(socket)
        seen = OrderedDict()

        async def legacy_liveness():
            while True:
                await asyncio.sleep(1)
                with bridge.lock:
                    probe = bridge.controller is socket and bridge.legacy_controller
                if probe:
                    try:
                        pong = await socket.ping()
                        await asyncio.wait_for(pong, timeout=1)
                        bridge.pong_received(socket)
                    except (TimeoutError, ConnectionClosed):
                        bridge.disconnect(socket)
                        await socket.close()
                        return

        liveness_task = asyncio.create_task(legacy_liveness())
        try:
            await socket.send(json.dumps(bridge.envelope("HELLO", dict(simulation=True, heartbeatIntervalMs=1000))))
            await socket.send(json.dumps(bridge.snapshot()))
            with bridge.lock:
                scenario = bridge.scenario
            if scenario:
                await socket.send(json.dumps(scenario))
            async for raw in socket:
                try:
                    message = decode(raw)
                    message_id = message.get("messageId")
                    require(isinstance(message_id, str) and 0 < len(message_id) <= 128, "INVALID_messageId")
                    require(message_id not in seen, "DUPLICATE_MESSAGE")
                    seen[message_id] = None
                    if len(seen) > 1024:
                        seen.popitem(last=False)
                    kind = message.get("type")
                    if kind == "STATE_INJECTION":
                        bridge.inject(message, socket)
                    elif kind == "EVALUATOR_INJECT":
                        bridge.inject_legacy(message, socket)
                    elif kind == "HEARTBEAT":
                        bridge.heartbeat(message, socket)
                    elif kind == "HELLO":
                        validate_envelope(message, "HELLO", "simulator")
                        await socket.send(json.dumps(bridge.envelope("HELLO", dict(simulation=True, heartbeatIntervalMs=1000))))
                    else:
                        raise ProtocolError("BROWSER_MESSAGE_NOT_ALLOWED")
                except ProtocolError as exc:
                    await socket.send(json.dumps(bridge.envelope("ERROR", dict(code=str(exc)))))
        except ConnectionClosed:
            pass
        finally:
            liveness_task.cancel()
            await asyncio.gather(liveness_task, return_exceptions=True)
            clients.discard(socket)
            bridge.disconnect(socket)

    http = ThreadingHTTPServer(("127.0.0.1", http_port), make_handler(bridge, origins))
    http.daemon_threads = True
    thread = threading.Thread(target=http.serve_forever, daemon=True)
    try:
        async with serve(client, "127.0.0.1", ws_port, origins=list(origins), max_size=MAX_BODY,
                         max_queue=16, compression=None, close_timeout=1) as websocket_server:
            thread.start()
            if ready:
                ready(bridge, websocket_server.sockets[0].getsockname()[1], http.server_port)
            print(f"SIMULATION ONLY | WS 127.0.0.1:{websocket_server.sockets[0].getsockname()[1]} | HTTP 127.0.0.1:{http.server_port}", flush=True)
            while stop is None or not stop.is_set():
                snapshot = bridge.snapshot()
                with bridge.lock:
                    queued = list(bridge.outbox)
                    bridge.outbox.clear()
                for message in [*queued, snapshot]:
                    if clients:
                        # Timeout/close a slow viewer rather than letting it freeze every viewer.
                        async def send_one(socket):
                            try:
                                await asyncio.wait_for(socket.send(json.dumps(message)), timeout=0.5)
                            except (TimeoutError, ConnectionClosed):
                                await socket.close()
                        await asyncio.gather(*(send_one(s) for s in list(clients)))
                await asyncio.sleep(0.25)
    finally:
        if thread.is_alive():
            await asyncio.to_thread(http.shutdown)
            thread.join(timeout=2)
        http.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ws-port", type=int, default=8765)
    parser.add_argument("--http-port", type=int, default=8766)
    parser.add_argument("--origin", action="append", help="Exact browser origin allowlist; repeatable. Defaults to localhost:8000.")
    args = parser.parse_args()
    try:
        asyncio.run(run(args.ws_port, args.http_port, tuple(args.origin or DEFAULT_ORIGINS)))
    except KeyboardInterrupt:
        pass
