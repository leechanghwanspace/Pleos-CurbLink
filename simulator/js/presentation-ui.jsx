function LanePresentation({ mode, vehicleState, scenarioState, connectionState, nowMs, onScenario, onClose, pending, keyboardEnabled = true }) {
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
      if (!keyboardEnabled) return;
      if (e.repeat || e.ctrlKey || e.altKey || e.metaKey || /INPUT|TEXTAREA|SELECT|BUTTON/.test(e.target.tagName) || e.target.isContentEditable) return;
      if (['1', '2', '3'].includes(e.key)) { e.preventDefault(); select(Number(e.key)); }
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, [select, keyboardEnabled]);
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
