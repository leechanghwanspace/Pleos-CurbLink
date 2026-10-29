# 파일별 변경 코드

신규 파일은 전체가 추가 코드입니다. index.html은 변경 구간만 수록했습니다.

## simulator/js/lane-engine.js (신규)

```javascript
(function (root) {
  'use strict';
  const lanes = [
    { id: 1, turns: ['LEFT'] }, { id: 2, turns: ['STRAIGHT', 'LEFT'] },
    { id: 3, turns: ['STRAIGHT'] }, { id: 4, turns: ['RIGHT'], curb: true }
  ];
  function rank(input) {
    const { currentLane = 2, phase = 'CRUISE', distance = 300,
      traffic = {}, valid = true } = input;
    if (!valid) return { lane: null, reason: '정보 만료 · 추천 보류', costs: [] };
    const approaching = phase === 'APPROACH';
    const goal = approaching ? 4 : 3;
    const urgency = approaching ? 4 + 12 * (1 - Math.min(1, Math.max(0, distance) / 300)) : 0;
    const costs = lanes.map(lane => {
      const t = traffic[lane.id] || {};
      const allowed = !t.closed && (approaching ? lane.id >= currentLane : lane.turns.includes('STRAIGHT'));
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
      reason: approaching ? '픽업 베이 연결 차선으로 순차 접근' : phase === 'REJOIN' ? '인접 본선 합류 후 직진 차선 추천' : best.id === 3 ? '좌회전 대기 영향 회피 · 직진 전용 추천' : '변경 비용을 고려해 현재 차선 유지' };
  }
  function nextChange(current, target, gapSafe, legal = true) {
    if (target == null || current === target) return { lane: current, allowed: false, reason: '현재 차선 유지' };
    return { lane: current + Math.sign(target - current), allowed: Boolean(gapSafe && legal),
      reason: gapSafe && legal ? '인접 차선으로 변경 가능' : '안전 간격 또는 변경 허용 구간 대기' };
  }
  const api = { lanes, rank, nextChange };
  root.CurbLane = api;
  if (typeof module !== 'undefined') module.exports = api;
})(typeof globalThis !== 'undefined' ? globalThis : window);

```

## simulator/js/presentation-ui.jsx (신규)

```jsx
function LanePresentation({ mode, vehicleState, scenarioState, connectionState, nowMs, onScenario, onClose, pending }) {
  const [step, setStep] = React.useState(1);
  const [phase, setPhase] = React.useState('CRUISE');
  const [currentLane, setCurrentLane] = React.useState(2);
  const [gapSafe, setGapSafe] = React.useState(false);
  const [notice, setNotice] = React.useState('');
  const disabled = mode !== 'WEB_DEMO' || pending;
  const select = React.useCallback((n) => {
    if (disabled || vehicleState.doors.trunk !== 'CLOSED') {
      setNotice('외부 연결·명령 대기·트렁크 상태를 확인하세요.'); return;
    }
    setStep(n); setPhase(n === 3 ? 'APPROACH' : 'CRUISE');
    setCurrentLane(2); setGapSafe(false); setNotice(''); onScenario(n);
  }, [disabled, vehicleState.doors.trunk, onScenario]);
  React.useEffect(() => {
    const key = e => {
      if (e.repeat || e.ctrlKey || e.altKey || e.metaKey || /INPUT|TEXTAREA|SELECT|BUTTON/.test(e.target.tagName) || e.target.isContentEditable) return;
      if (['1', '2', '3'].includes(e.key)) { e.preventDefault(); select(Number(e.key)); }
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, [select]);
  const valid = connectionState.connected && connectionState.snapshotReceived && vehicleState.vehicleDataValid && nowMs - vehicleState.lastVehicleRxAt < 1000;
  const result = CurbLane.rank({ currentLane, phase, distance: 300, valid,
    traffic: step === 2 ? { 2: { queue: 1, blockProbability: .7, extraWait: 20 }, 3: { queue: 1 } } : {} });
  const change = CurbLane.nextChange(currentLane, result.lane, gapSafe);
  const complete = scenarioState.handoffCompleted && Object.values(vehicleState.doors).every(value => value === 'CLOSED');
  return <section aria-label="차선 추천 발표 대시보드" className="w-full max-w-[1840px] mx-auto mb-3 p-4 rounded-xl bg-slate-900 border border-teal-600">
    <div className="flex flex-wrap justify-between gap-3">
      <div><strong>차선 추천 · 가상 교통 시뮬레이션</strong><p role="status">권장 {result.lane ? `${result.lane}차선` : '보류'} · {result.reason}</p></div>
      <div>픽업: {complete ? '인계 완료' : scenarioState.orderReady ? '준비 완료' : '준비 중'} · 단계 {step}/3 · 모의 현재 {currentLane}차선</div>
    </div>
    <div className="grid grid-cols-4 gap-2 my-3" aria-label="차선별 상태">
      {CurbLane.lanes.map(l => {
        const cost = result.costs.find(c => c.id === l.id);
        const recommended = result.lane === l.id;
        return <div key={l.id} className={`p-3 rounded border ${recommended ? 'border-teal-300 bg-teal-900' : step === 2 && l.id === 2 ? 'border-amber-400 bg-amber-950' : 'border-slate-600'}`}>
          <strong>{l.id}차선 {['↰', '↑↰', '↑', '↱'][l.id - 1]} {recommended ? '★ 추천' : ''}</strong>
          <p>{!cost || !Number.isFinite(cost.cost) ? '경로 제외' : `${cost.cost.toFixed(0)}초 비용`}</p>
          {step === 2 && l.id === 2 && <p>좌회전 병목 예상</p>}
        </div>;
      })}
    </div>
    <div className="flex flex-wrap gap-2">
      {['일반 주행', '병목 회피', '픽업 접근·복귀'].map((label, i) => <button disabled={disabled} aria-pressed={step === i + 1} className="px-4 py-2 border rounded disabled:opacity-40" key={label} onClick={() => select(i + 1)}>{i + 1}. {label}</button>)}
      <button disabled={disabled} className="px-4 py-2 border rounded" onClick={() => select(step % 3 + 1)}>다음 단계 →</button>
      <button className="px-4 py-2 border rounded" onClick={onClose}>고급 설정 보기</button>
    </div>
    {step > 1 && <div className="flex flex-wrap items-center gap-3 mt-3">
      <label><input type="checkbox" checked={gapSafe} disabled={disabled} onChange={e => setGapSafe(e.target.checked)} /> 모의 안전 간격 확보</label>
      <button disabled={disabled || !valid || !change.allowed || phase === 'PARKED'} onClick={() => setCurrentLane(change.lane)}>인접 차선 변경</button>
      {step === 3 && <button disabled={disabled || currentLane !== 4 || phase !== 'APPROACH'} onClick={() => { setPhase('PARKED'); onScenario(3, 'PARK'); }}>베이 정차 · 기존 인계 진행</button>}
      {step === 3 && <button disabled={disabled || !valid || !complete || phase !== 'PARKED' || !gapSafe || vehicleState.gear !== 'P' || vehicleState.speedKph !== 0} onClick={() => { setCurrentLane(3); setPhase('REJOIN'); setGapSafe(false); onScenario(3, 'REJOIN'); }}>인계 후 본선 합류</button>}
      <span>{phase === 'PARKED' ? '오른쪽 IVI에서 직접 승인·인계하세요' : change.reason}</span>
    </div>}
    {notice && <p role="alert">{notice}</p>}
    <p className="text-xs text-slate-400 mt-2">1/2/3: 시나리오 · 실제 도로/차량 연동 없음 · 차선 위치는 안내 시뮬레이션이며 3D 차량 좌표와 독립</p>
  </section>;
}

```

## simulator/js/lane-engine.test.cjs (신규)

```javascript
const assert = require('node:assert/strict');
const { rank, nextChange } = require('./lane-engine.js');
assert.equal(rank({}).lane, 2);
const blocked = rank({traffic:{2:{queue:1,blockProbability:.7,extraWait:20},3:{queue:1}}});
assert.equal(blocked.lane, 3);
assert.equal(blocked.costs.find(x=>x.id===2).cost,28);
assert.equal(blocked.costs.find(x=>x.id===3).cost,17);
assert.equal(rank({phase:'APPROACH'}).lane,4);
assert.equal(rank({currentLane:4,phase:'REJOIN'}).lane,3);
assert.equal(rank({valid:false}).lane,null);
assert.equal(rank({traffic:{2:{closed:true},3:{closed:true}}}).lane,null);
assert.equal(nextChange(2,4,true).lane,3);
assert.equal(nextChange(2,3,false).allowed,false);
assert.equal(nextChange(2,3,true,false).allowed,false);
assert.equal(rank({currentLane:1,distance:0}).lane,null);
console.log('PASS: lane costs, curb approach/rejoin, stale/closed/unreachable, adjacent safe change');

```

## simulator/index.html (수정 구간)


```jsx
  <script src="js/lane-engine.js"></script>
  <script type="text/babel" src="js/presentation-ui.jsx"></script>
  <style>.presentation-mode footer,.presentation-mode .advanced-interlocks{display:none}</style>
```


```jsx
    function App() {
      const [presentationMode, setPresentationMode] = useState(true);
```


```jsx
      // 9. 단축키 지원 (입력 필드 포커스 시 비활성화)
      // ============================================================================
      useEffect(() => {
        const handleKeyDown = (e) => {
          const tag = (e.target && e.target.tagName) || '';
          if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || e.target?.isContentEditable) {
            return;
          }
          if (presentationMode) return;
          if (e.key === '1') setCameraMode('EXTERIOR');
          else if (e.key === '2') setCameraMode('IVI_FOCUS');
          else if (e.key === '3') setCameraMode('TRUNK_HANDOFF');
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
      }, [presentationMode]);

      // ============================================================================
```


```jsx
      const runLaneScenario = useCallback((step, action) => {
        if (mode !== 'WEB_DEMO' || commandStatus.pendingRequestId || vehicleState.doors.trunk !== 'CLOSED') return;
        setAutoPlayState(prev => ({ ...prev, playing: false, narration: '', simulatedInputBanner: false }));
        const parked = action === 'PARK';
        setCameraMode(parked ? 'TRUNK_HANDOFF' : 'EXTERIOR');
        sendEvaluatorInjection('차선 시연: ' + step + (action || ''), {
          speedKph: parked ? 0 : 30, gear: parked ? 'P' : 'D', uxRestricted: !parked
        }, { pickupZoneConfirmed: parked, ...(!action ? { handoffCompleted: false } : {}) });
      }, [mode, commandStatus.pendingRequestId, vehicleState.doors.trunk, sendEvaluatorInjection]);

      return (
        <div className={`${presentationMode ? 'presentation-mode' : ''} min-h-screen w-full bg-[#060911] text-slate-100 flex flex-col justify-between p-2 sm:p-3 select-none`}>
```


```jsx
          {presentationMode ? <LanePresentation mode={mode} vehicleState={vehicleState}
            scenarioState={scenarioState} connectionState={connectionState} nowMs={nowMs}
            pending={Boolean(commandStatus.pendingRequestId)} onScenario={runLaneScenario}
            onClose={() => setPresentationMode(false)} /> :
            <button className="p-3 border rounded mb-2" onClick={() => {
              setAutoPlayState(prev => ({ ...prev, playing: false, narration: '', simulatedInputBanner: false }));
              setPresentationMode(true);
            }}>발표 모드</button>}
```


```jsx
              <div className="advanced-interlocks p-3 rounded-xl bg-slate-900/95 border border-slate-800">
```


```jsx
              { id: 'EXTERIOR', label: '차량 전체 외관 뷰', keyHint: '' },
              { id: 'IVI_FOCUS', label: 'IVI 집중 뷰', keyHint: '' },
              { id: 'TRUNK_HANDOFF', label: '트렁크 인계 뷰', keyHint: '' },
```
