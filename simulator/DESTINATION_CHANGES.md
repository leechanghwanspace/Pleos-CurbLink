# 목적지 의도 구현: 변경·추가 코드

프로젝트 루트 기준 상대 경로. 신규 파일은 전체가 추가 코드입니다. 기존 index.html은 연결부만 수록합니다.

## simulator/js/lane-engine.js — 신규 정의·함수

```javascript
  const DestinationMode = Object.freeze({ PICKUP: 'PICKUP', SHORT_STOP: 'SHORT_STOP', PARKING: 'PARKING' });
  const demoDestination = {
    id: 'central-mall', requestedStopSeconds: 180,
    nodes: [
      { id: 'A3', label: '동문 안전 승하차 A-3', modes: ['PICKUP'], status: 'GREEN', allowed: true, available: true, entryLane: 4, walkMeters: 15, detourSeconds: 3, waitSeconds: 0, maxStopSeconds: 120, pedestrianAccess: true },
      { id: 'B1', label: '단기 정차 B-1 · 최대 5분', modes: ['SHORT_STOP'], status: 'YELLOW', allowed: true, available: true, entryLane: 4, walkMeters: 30, detourSeconds: 5, waitSeconds: 0, maxStopSeconds: 300 },
      { id: 'P1', label: '정식 주차장 P1 진입로', modes: ['PARKING'], status: 'GREEN', allowed: true, available: true, entryLane: 4, walkMeters: 50, detourSeconds: 8, waitSeconds: 0 },
      { id: 'R1', label: '버스 정류장 · 정차 금지', modes: [], status: 'RED', allowed: false, available: true, entryLane: 4 }
    ]
  };
  function findOptimalCurbZone(destination, mode) {
    if (!Object.values(DestinationMode).includes(mode)) return null;
    const candidates = (destination?.nodes || []).filter(n =>
      n.allowed === true && n.available === true && n.status !== 'RED' &&
      n.modes.includes(mode) && Number.isInteger(n.entryLane) && n.entryLane >= 1 && n.entryLane <= 4 &&
      (mode !== DestinationMode.PICKUP || n.pedestrianAccess === true) &&
      (mode !== DestinationMode.SHORT_STOP || (destination.requestedStopSeconds > 0 && destination.requestedStopSeconds <= Math.min(300, n.maxStopSeconds || 0)))
    ).map(n => ({ ...n, suitabilityCost: (n.detourSeconds || 0) + (n.waitSeconds || 0) + (n.walkMeters || 0) / 1.2 }));
    return candidates.sort((a, b) => a.suitabilityCost - b.suitabilityCost || a.id.localeCompare(b.id))[0] || null;
  }
  function destinationProfile(mode, distance, zone) {
    const parking = mode === DestinationMode.PARKING;
    const start = parking ? 180 : 450;
    const approaching = Boolean(zone) && distance <= start;
    return { approaching, targetLane: zone?.entryLane ?? null,
      approachWeight: approaching ? 8 + 16 * (1 - Math.max(0, distance) / start) : 0,
      // Simulation comfort profile, not a real vehicle braking controller.
      speedKph: Math.min(36, Math.sqrt(2 * (parking ? 1.2 : 1.5) * Math.max(0, distance)) * 3.6),
      warning: approaching && distance < 80 ? '진입 전 보행자·우측 사각지대 확인' : '' };
  }

```

## simulator/js/lane-engine.js — 변경된 rank 및 내보내기

```javascript
  function rank(input) {
    const { currentLane = 2, phase = 'CRUISE', distance = 300,
      traffic = {}, valid = true } = input;
    if (!valid) return { lane: null, reason: '정보 만료 · 추천 보류', costs: [] };
    const profile = input.destinationMode ? destinationProfile(input.destinationMode, distance, input.zone) : null;
    const approaching = profile ? profile.approaching : phase === 'APPROACH';
    const goal = approaching ? (profile?.targetLane ?? 4) : 3;
    const urgency = profile ? profile.approachWeight : approaching ? 4 + 12 * (1 - Math.min(1, Math.max(0, distance) / 300)) : 0;
    const costs = lanes.map(lane => {
      const t = traffic[lane.id] || {};
      const allowed = !t.closed && (approaching ? lane.id >= Math.min(currentLane, goal) && lane.id <= Math.max(currentLane, goal) : lane.turns.includes('STRAIGHT'));
      const changes = Math.abs(lane.id - currentLane);
      // 50 m per change is a demo feasibility assumption, not a road safety standard.
      const reachable = changes * 50 <= distance;
      const travel = 12;
      const queue = Math.max(0, t.queue || 0) * 2 + Math.max(0, t.signalWait || 0);
      const mixed = lane.turns.length > 1 ? Math.max(0, Math.min(1, t.blockProbability || 0)) * Math.max(0, t.extraWait || 0) : 0;
      const change = changes * 3;
      const approach = Math.abs(lane.id - goal) * urgency;
      const uncertainty = Math.max(0, Math.min(1, t.uncertainty || 0)) * 10;
      return { ...lane, travel, queue, mixed, change, approach, uncertainty,
        cost: allowed && reachable ? travel + queue + mixed + change + approach + uncertainty : Infinity };
    });
    const best = costs.reduce((a, b) => b.cost < a.cost ? b : a);
    return { lane: Number.isFinite(best.cost) ? best.id : null, costs,
      reason: approaching ? '선택한 정차·주차 진입 차선으로 순차 접근' : phase === 'REJOIN' ? '인접 본선 합류 후 직진 차선 추천' : best.id === 3 ? '좌회전 대기 영향 회피 · 직진 전용 추천' : '변경 비용을 고려해 현재 차선 유지' };
  }

```
```javascript
  const api = { lanes, rank, nextChange, DestinationMode, demoDestination, findOptimalCurbZone, destinationProfile };

```

## simulator/js/destination-simulation.js — 신규

```javascript
(function(root) {
  function initial(mode, zone) {
    return { mode, zone, distance: 600, lane: 2, lanePosition: 2, speedKph: 36, elapsed: 0, stopped: false };
  }
  function advance(state, dt) {
    if (state.stopped) return state;
    const profile = CurbLane.destinationProfile(state.mode, state.distance, state.zone);
    const recommendation = CurbLane.rank({ currentLane: state.lane, distance: state.distance,
      destinationMode: state.mode, zone: state.zone, traffic: { 4: { queue: 3 } } });
    const target = recommendation.lane ?? state.lane;
    const next = CurbLane.nextChange(state.lane, target, true); // Fixed demo road has clear gaps.
    const laneTarget = next.allowed ? next.lane : state.lane;
    const lanePosition = state.lanePosition + Math.sign(laneTarget - state.lanePosition) * Math.min(Math.abs(laneTarget - state.lanePosition), dt / 3);
    const lane = Math.abs(lanePosition - laneTarget) < .001 ? laneTarget : state.lane;
    const speedKph = Math.max(0, Math.min(profile.speedKph, state.speedKph + dt * 3.6));
    // Advance the same longitudinal position used by the map and the vehicle snapshot.
    const distance = Math.max(0, state.distance - speedKph / 3.6 * dt);
    const stopped = distance < .15 && lane === state.zone.entryLane;
    return { ...state, lane, lanePosition, distance: stopped ? 0 : distance,
      speedKph: stopped ? 0 : speedKph, elapsed: state.elapsed + dt, stopped };
  }
  root.DestinationSimulation = { initial, advance };
  if (typeof module !== 'undefined') module.exports = root.DestinationSimulation;
})(typeof globalThis !== 'undefined' ? globalThis : window);

```

## simulator/js/destination-demo.jsx — 신규

```jsx
function DestinationDemo({ enabled, safe, onFrame, onActive, onStart }) {
  const [state, setState] = React.useState(null);
  const [playing, setPlaying] = React.useState(false);
  const stateRef = React.useRef(null);
  const callbacks = React.useRef({ onFrame, onActive, safe, enabled });
  callbacks.current = { onFrame, onActive, safe, enabled };
  const start = mode => {
    if (!enabled || !safe) return;
    const zone = CurbLane.findOptimalCurbZone(CurbLane.demoDestination, mode);
    if (!zone) return;
    const next = DestinationSimulation.initial(mode, zone);
    onStart(); stateRef.current = next; setState(next); setPlaying(true); onActive(true); onFrame(next);
  };
  React.useEffect(() => {
    if (!playing) return;
    let previous = performance.now();
    const timer = setInterval(() => {
      const now = performance.now();
      const dt = Math.min((now - previous) / 1000, .2) * 5;
      previous = now;
      const cb = callbacks.current;
      if (!cb.enabled || !cb.safe) { setPlaying(false); return; }
      const next = DestinationSimulation.advance(stateRef.current, dt);
      stateRef.current = next; setState(next); cb.onFrame(next);
      if (next.stopped) setPlaying(false);
    }, 50);
    return () => clearInterval(timer);
  }, [playing]);
  const profile = state && CurbLane.destinationProfile(state.mode, state.distance, state.zone);
  const rec = state && CurbLane.rank({ currentLane: state.lane, distance: state.distance, destinationMode: state.mode, zone: state.zone, traffic: { 4: { queue: 3 } } });
  const progress = state ? 1 - state.distance / 600 : 0;
  const entry = state ? Math.max(0, 1 - state.distance / 30) : 0;
  const zoneY = state ? 92 + CurbLane.demoDestination.nodes.findIndex(z => z.id === state.zone.id) * 68 : 92;
  const x = (state ? 95 + (state.lanePosition - 1) * 100 : 195) * (1 - entry) + 510 * entry;
  const y = (65 + progress * 230) * (1 - entry) + zoneY * entry;
  return <section className="w-full max-w-[1840px] mx-auto mb-3 p-4 rounded-xl bg-slate-900 border border-teal-500" aria-label="목적지 의도 시연">
    <div className="flex flex-wrap gap-2 items-center">
      <strong>센트럴몰 · 목적지 의도</strong>
      {Object.values(CurbLane.DestinationMode).map((mode, i) => <button key={mode} disabled={!enabled || !safe} aria-pressed={state?.mode === mode} className="px-4 py-2 border rounded disabled:opacity-40" onClick={() => start(mode)}>{['픽업 시나리오', '잠시 정차 시나리오', '주차 시나리오'][i]}</button>)}
      <button disabled={!state || state.stopped || !enabled || !safe} className="px-3 py-2 border rounded" onClick={() => setPlaying(v => !v)}>{playing ? '일시 정지' : '이어서 재생'}</button>
      <button disabled={playing} className="px-3 py-2 border rounded" onClick={() => { setState(null); stateRef.current = null; onActive(false); }}>시연 지도 닫기</button>
    </div>
    {!safe && <p role="alert">차량·센서·연결·도어 상태를 확인하세요. 자동 주행이 중지됩니다.</p>}
    {state && <div className="grid lg:grid-cols-2 gap-4 mt-3">
      <svg viewBox="0 0 660 390" role="img" aria-label="차선 접근 및 정차를 보여주는 가상 지도" className="w-full bg-slate-950 rounded">
        {[1,2,3,4].map(lane => <g key={lane}>
          <rect x={45+(lane-1)*100} y="25" width="95" height="320" fill={rec.lane === lane ? '#164e63' : '#1e293b'} stroke="#64748b" strokeDasharray="6 6" />
          <text x={65+(lane-1)*100} y="48" fill="white">{lane}차선 {rec.lane === lane ? '★' : ''}</text>
        </g>)}
        {CurbLane.demoDestination.nodes.map((zone, i) => <g key={zone.id}>
          <rect x="470" y={65+i*68} width="175" height="55" rx="6" fill={zone.status === 'RED' ? '#7f1d1d' : zone.status === 'YELLOW' ? '#713f12' : '#064e3b'} stroke={state.zone.id === zone.id ? '#fff' : '#64748b'} strokeWidth={state.zone.id === zone.id ? 4 : 1} />
          <text x="480" y={87+i*68} fill="white">{zone.id} {zone.status === 'RED' ? '× 금지' : zone.status === 'YELLOW' ? '⏱ 단기' : '✓ 허용'}</text>
          <text x="480" y={106+i*68} fill="white" fontSize="11">{zone.label}</text>
        </g>)}
        <path d={`M ${x} ${y} L 440 320 L 470 ${92+CurbLane.demoDestination.nodes.findIndex(z => z.id === state.zone.id)*68}`} stroke="#5eead4" strokeWidth="3" fill="none" strokeDasharray="7 5" />
        <g transform={`translate(${x},${y})`}>
          <rect x="-12" y="-20" width="24" height="40" rx="7" fill="#5eead4" stroke="white" />
          <text x="17" y="5" fill="white" fontSize="12">내 차</text>
        </g>
        <text x="45" y="375" fill="#cbd5e1">가상 규제 데이터 · 초록 허용 / 노랑 단기 / 빨강 금지</text>
      </svg>
      <div role="status" className="space-y-3 text-lg">
        <p>최종 안내: {state.zone.label}</p>
        <p>{state.stopped ? '정차 완료 · P단' : `${Math.ceil(state.distance)}m · ${state.speedKph.toFixed(0)}km/h · ${playing ? '시연 중' : '일시 정지'}`}</p>
        <p>현재 {state.lanePosition.toFixed(1)}차선 → 권장 {rec.lane ?? '보류'}차선</p>
        <p>{profile.approaching ? '진입 차선으로 순차 접근' : '연석 병목 회피 · 본선 유지'}</p>
        <p className="text-amber-300">{profile.warning}</p>
        {state.stopped && <p>{state.mode === 'PARKING' ? '주차장 진입점 도착' : '정차 구역 도착 · 트렁크는 IVI에서 직접 승인'}</p>}
      </div>
    </div>}
    <p className="text-xs text-slate-400 mt-2">5배속 가상 지도 시연 · 안전 간격·보행자 충돌 없는 고정 경로 · 실차 제어 없음</p>
  </section>;
}

```

## simulator/js/destination.test.cjs — 신규

```javascript
const assert = require('node:assert/strict');
global.CurbLane = require('./lane-engine.js');
const sim = require('./destination-simulation.js');
const {DestinationMode, demoDestination, findOptimalCurbZone, destinationProfile, rank} = CurbLane;
for (const [mode,id] of [['PICKUP','A3'],['SHORT_STOP','B1'],['PARKING','P1']]) {
 const zone = findOptimalCurbZone(demoDestination,mode);
 assert.equal(zone.id,id);
 let state = sim.initial(mode,zone), previous = state;
 for (let i=0;i<6000 && !state.stopped;i++) {
  state=sim.advance(state,.05);
  assert.ok(state.distance <= previous.distance);
  assert.ok(Math.abs(state.lanePosition-previous.lanePosition)<=.05/3+.00001);
  assert.ok(state.speedKph>=0);
  previous=state;
 }
 assert.ok(state.stopped,mode+' must arrive');
 assert.equal(state.lane,4); assert.equal(state.speedKph,0);
 console.log('PASS trajectory:',mode,state.elapsed.toFixed(1)+' simulated seconds');
}
assert.equal(findOptimalCurbZone({...demoDestination,requestedStopSeconds:301},'SHORT_STOP'),null);
assert.equal(findOptimalCurbZone({...demoDestination,nodes:demoDestination.nodes.map(n=>({...n,available:false}))},'PICKUP'),null);
assert.equal(findOptimalCurbZone(demoDestination,'INVALID'),null);
assert.equal(destinationProfile('PARKING',300,{entryLane:4}).approaching,false);
assert.equal(destinationProfile('PICKUP',400,{entryLane:4}).approaching,true);
assert.equal(rank({destinationMode:'PICKUP',zone:{entryLane:4},distance:400}).lane,4);
console.log('PASS mode eligibility, timing and lane cost integration');

```

## simulator/index.html — App 이벤트·상태 연결


```jsx
  <script src="js/destination-simulation.js"></script>
  <script type="text/babel" src="js/destination-demo.jsx"></script>
```


```jsx
      const [destinationActive, setDestinationActive] = useState(false);
```


```jsx
      // DestinationDemo is the sole vehicle input owner while a preset is running.
      const applyDestinationFrame = useCallback(frame => {
        if (stateRef.current.mode !== 'WEB_DEMO') return;
        setVehicleState(prev => ({ ...prev, speedKph: frame.speedKph,
          gear: frame.stopped ? 'P' : 'D', uxRestricted: !frame.stopped }));
        setScenarioState(prev => ({ ...prev, pickupZoneConfirmed:
          frame.stopped && frame.mode !== CurbLane.DestinationMode.PARKING }));
      }, []);
      const beginDestinationDemo = useCallback(() => {
        setAutoPlayState(prev => ({ ...prev, playing: false, narration: '', simulatedInputBanner: false }));
        setScenarioState(prev => ({ ...prev, handoffCompleted: false,
          customerReadyConfirmed: false, pickupZoneConfirmed: false }));
      }, []);
```


```jsx
          {presentationMode && <DestinationDemo
            enabled={mode === 'WEB_DEMO' && !commandStatus.pendingRequestId && !autoPlayState.playing}
            safe={connectionState.connected && connectionState.snapshotReceived && vehicleState.vehicleDataValid && vehicleState.sensorDataValid && vehicleState.oemControlAllowed && !vehicleState.rearObstacle &&
              nowMs - vehicleState.lastVehicleRxAt < ttlConfig.vehicleTtlMs && nowMs - vehicleState.lastSensorRxAt < ttlConfig.sensorTtlMs &&
              Object.values(vehicleState.doors).every(value => value === 'CLOSED')}
            onFrame={applyDestinationFrame} onStart={beginDestinationDemo} onActive={setDestinationActive} />}
          {destinationActive && presentationMode && <button className="p-2 border rounded" onClick={() => { setDestinationActive(false); setPresentationMode(false); }}>시연 종료 · 고급 설정</button>}
          {presentationMode ? (!destinationActive && <LanePresentation mode={mode} vehicleState={vehicleState}
            scenarioState={scenarioState} connectionState={connectionState} nowMs={nowMs}
            pending={Boolean(commandStatus.pendingRequestId)} onScenario={runLaneScenario}
            onClose={() => setPresentationMode(false)} />) :
            <button className="p-3 border rounded mb-2" onClick={() => {
              setAutoPlayState(prev => ({ ...prev, playing: false, narration: '', simulatedInputBanner: false }));
              setPresentationMode(true);
            }}>발표 모드</button>}
```


```jsx
          if (presentationMode || destinationActive) return;
          // 기존 카메라 단축키 처리 유지
      }, [presentationMode, destinationActive]);
```
