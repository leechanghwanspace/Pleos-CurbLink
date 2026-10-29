function DestinationDemo({ enabled, safe, onFrame, onActive, onStart }) {
 const [state,setState]=React.useState(null),[playing,setPlaying]=React.useState(false),[rate,setRate]=React.useState(3);
 const [landmark,setLandmark]=React.useState('B-12 표지판 · 강변 진입로 3번');
 const ref=React.useRef(null),callbacks=React.useRef({enabled,safe,onFrame});callbacks.current={enabled,safe,onFrame};
 const start=React.useCallback(kind=>{
  if(!enabled||!safe)return;
  const initial=MissionEngine.initial(kind);
  const next=kind==='MEAL'?{...initial,meal:MealPlanner.initial(),phase:'MEAL_SETUP',waiting:true,stopped:true,gear:'P',orderStatus:'NOT_ORDERED'}:kind==='ACCESS'?{...initial,access:AccessPlanner.initial(),phase:'ACCESS_SETUP',waiting:true,stopped:true,gear:'P'}:initial;
  if(kind==='MEAL')setRate(1);ref.current=next;setState(next);onStart();onActive(true);onFrame(next);setPlaying(!['MEAL','ACCESS'].includes(kind));
 },[enabled,safe,onStart,onActive,onFrame]);
 React.useEffect(()=>{
  const handler=e=>{if(e.repeat||e.ctrlKey||e.altKey||e.metaKey||e.target.isContentEditable||/INPUT|TEXTAREA|SELECT/.test(e.target.tagName))return;const index=['1','2','3','4','5','6'].indexOf(e.key);if(index>=0){e.preventDefault();start(['PICKUP','PARKING','SHORT_STOP','MEAL','RIVER','ACCESS'][index]);}};
  window.addEventListener('keydown',handler);return()=>window.removeEventListener('keydown',handler);
 },[start]);
 React.useEffect(()=>{
  if(!playing)return;
  let previous=performance.now();const timer=setInterval(()=>{
   const now=performance.now(),dt=Math.min(.2,(now-previous)/1000)*rate;previous=now;
   if(!callbacks.current.enabled||!callbacks.current.safe){setPlaying(false);return;}
   // Substeps preserve route timing at accelerated playback rates.
   let next=ref.current;for(let left=dt;left>.00001;left-=Math.min(.05,left))next=MissionEngine.advance(next,Math.min(.05,left));
   ref.current=next;setState(next);callbacks.current.onFrame(next);if(next.done||next.waiting)setPlaying(false);
  },50);return()=>clearInterval(timer);
 },[playing,rate]);
 const accessAction=action=>{
  const next=AccessPlanner.apply(ref.current,action,enabled&&safe);
  if(next===ref.current)return;ref.current=next;setState(next);onFrame(next);if(action.type==='GO')setPlaying(true);
 };
 const mealAction=action=>{
  const next=MealPlanner.apply(ref.current,action,enabled&&safe);
  if(next===ref.current)return;ref.current=next;setState(next);onFrame(next);
  if(action.type==='DESTINATION')setPlaying(true);
 };
 const act=action=>{
  const next=MissionEngine.act(ref.current,action,enabled&&safe);
  if(next===ref.current)return;if(action==='SHARE')next.locationLandmark=landmark;ref.current=next;setState(next);onFrame(next);setPlaying(!next.done&&!next.waiting);
 };
 React.useEffect(()=>{
  if(!state)return;
  const selector=state.waiting?(['MEAL_SETUP','ACCESS_SETUP'].includes(state.phase)?'.meal-layout':state.phase==='ACCESS_WAIT'?'.access-cockpit':state.phase==='LOCATE'?'.location-console':'.handoff-console'):['COURIER','RETURN','MERGE'].includes(state.phase)?'.driver-scene':null;
  if(selector){const timer=requestAnimationFrame(()=>document.querySelector(selector)?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'center'}));return()=>cancelAnimationFrame(timer);}
 },[state?.waiting,state?.phase]);
 const preset=state&&MissionEngine.presets[state.kind];
 const food=state&&['MEAL','RIVER'].includes(state.kind);
 const statusNames={SCHEDULED:'도착 시각에 맞춰 주문 예약',ORDERED:'모의 주문 접수',COOKING:'매장에서 조리 중',DELIVERING:'배달원이 인계 지점으로 이동 중',ARRIVED:'배달원 도착 · 음식 수령 가능'};
 return <section className="destination-panel w-full max-w-[1840px] mx-auto mb-3" aria-label="목적별 여정 시뮬레이션">
  <div className="journey-toolbar"><strong>CURBLINK <small>목적이 다른 여섯 개의 여정</small></strong><div className="journey-presets">{['PICKUP','PARKING','SHORT_STOP','MEAL','RIVER','ACCESS'].map((kind,i)=><button key={kind} disabled={!enabled||!safe} aria-pressed={state?.kind===kind} onClick={()=>start(kind)}>{i+1} {['골목 픽업','주차장 주차','잠시 정차','식사 시간 맞춤','한강 음식 인계','배리어프리 하차'][i]}</button>)}</div></div>
  <div className="journey-actions"><button disabled={!state||state.done||state.waiting||!enabled||!safe} onClick={()=>setPlaying(v=>!v)}>{playing?'일시 정지':'이어서 재생'}</button><label>재생 속도 <select aria-label="재생 속도" value={rate} onChange={e=>setRate(Number(e.target.value))}>{[1,3,5,10].map(n=><option key={n} value={n}>{n}×</option>)}</select></label><span>{state?`${Math.round(state.speedKph)}km/h · ${state.gear} · ${state.done?'완료':state.waiting?'확인 대기':playing?'진행 중':'일시 정지'}`:'프리셋 하나로 전체 여정 재생'}</span></div>
  {!safe&&<p role="alert" className="text-amber-300">안전 상태 이상으로 재생이 중지됩니다. 연결·센서·도어 상태를 확인하세요.</p>}
  {state?.kind==='ACCESS'&&state.access?<div className="meal-layout"><div className="meal-road"><MissionViewport state={state} playing={playing}/></div><AccessCabin state={state} onAction={accessAction} onApprove={()=>act('ACCESS_APPROVE')} enabled={enabled} safe={safe}/></div>:state?.kind==='MEAL'&&state.meal?<div className="meal-layout"><div className="meal-road"><MissionViewport state={state} playing={playing}/></div><MealCabin state={state} onAction={mealAction} enabled={enabled} safe={safe}/></div>:<MissionViewport state={state} playing={playing}/>}
  {preset&&<ol className="journey-timeline" aria-label="시나리오 진행 단계">{preset.stages.map((step,i)=><li key={step[0]} aria-current={!state.done&&state.stage===i?'step':undefined} className={state.done||i<state.stage?'complete':i===state.stage?'active':''}><b>{state.done||i<state.stage?'✓':i+1}</b><span>{step[1]}</span></li>)}</ol>}
  {state&&<div className="journey-details" role="status"><span>월드 좌표 {state.pose.x.toFixed(1)}, {state.pose.z.toFixed(1)}m</span><span>차량 방향 {Math.round(state.pose.yaw*180/Math.PI)}°</span><span>{state.passenger?'승객 1명 탑승':state.kind==='PARKING'&&state.done?'P-07 주차면 주차 완료':state.hazards?'비상등 켜짐 · 정차 시간 '+Math.floor(state.stageTime)+'초':state.done?'여정 완료':'모의 경과 '+Math.floor(state.elapsed)+'초'}</span></div>}
  {state?.kind==='PARKING'&&['AISLE','REVERSE','PARKED'].includes(state.phase)&&<div className="location-console"><div><span className="journey-eyebrow">PARK ASSIST · 합성 탑뷰</span><h3>{state.gear==='R'?'후진 정렬 중':'주차면 위치 확인'}</h3><MissionMap state={state} surround/></div><div><h3>P-07 예약 주차면</h3><p>주차장 진입 → 통로 이동 → R단 후진 → 주차면 중앙 정렬</p><p>주차면 중심: 가상 좌표 40, 576m</p><p>현재 좌표: {state.pose.x.toFixed(2)}, {state.pose.z.toFixed(2)}m</p><p>기어 {state.gear} · {state.speedKph.toFixed(1)}km/h</p><strong>{state.done?'✓ 지정 주차면 주차 완료':'차량 위치와 주차면이 함께 표시됩니다.'}</strong></div></div>}
  {state?.kind==='PICKUP'&&state.phase==='BOARD'&&<div className="handoff-console"><div><strong>리버빌라 101 앞 · 예약 승객이 차량으로 이동 중</strong><p>{state.passenger?'승객 1명 탑승 완료 · 출구로 이동합니다.':'보행자 접근 → 조수석 탑승 → 인원 확인'}</p></div><MissionMap state={state} surround/></div>}
  {food&&!state.meal&&<div className="food-console"><div><span className="journey-eyebrow">ARRIVAL-AWARE ORDER · 모의 주문</span><h3>도착에 맞춰 준비되는 한 끼</h3><p>주문 #024 · 도시락 1개 · 조리 30초 + 배달 20초 <small>(발표용 축약 시간)</small></p><div className="food-metrics"><span>초기 도착 예측<strong>{Math.round(state.eta)}초</strong></span><span>차량 도착까지<strong>{Math.max(0,Math.ceil(state.remainingEta))}초</strong></span><span>주문 전송 시점<strong>출발 +{Math.round(state.kind==='RIVER'?0:state.orderAt)}초</strong></span></div></div><div className="food-order-status"><b>{statusNames[state.orderStatus]}</b><p>{state.orderPlacedAt===null?'조리·배달 시간을 역산하여 시뮬레이션 주문을 예약했습니다.':`모의 주문 전송 완료 · ${Math.max(0,Math.floor(state.elapsed-state.orderPlacedAt))}초 경과`}</p><div className="order-track">{['ORDERED','COOKING','DELIVERING','ARRIVED'].map((key,i)=><span key={key} className={['ORDERED','COOKING','DELIVERING','ARRIVED'].indexOf(state.orderStatus)>=i?'lit':''}>{['접수','조리','배달','도착'][i]}</span>)}</div></div></div>}
  {state?.phase==='LOCATE'&&<div className="location-console"><div><span className="journey-eyebrow">SURROUND VIEW · 합성 위치 확인 화면</span><h3>주소 대신 차량 주변 풍경으로 만나기</h3><p>동일한 가상 월드를 전·후·좌·우 방향으로 렌더링한 모의 카메라입니다.</p><MissionMap state={state} surround/></div><div><h3>한강 인계 위치 카드</h3><div className="camera-landmarks">{[[0,"전방 · B-12 표지판"],[Math.PI/2,"우측 · 강변 산책로"],[Math.PI,"후방 · 진입로 3번"],[-Math.PI/2,"좌측 · 주차 구역"]].map(([angle,label])=><div key={label}><MissionViewport state={state} playing={false} compact cameraOffset={angle}/><span>{label}</span></div>)}</div><label>배달원이 확인할 기준점<select aria-label="인계 기준점" value={landmark} onChange={e=>setLandmark(e.target.value)}><option>B-12 표지판 · 강변 진입로 3번</option><option>산책로 옆 흰색 차량 · B-12</option></select></label><p>인계 지점: B-12 / 가상 좌표 {state.pose.x.toFixed(1)}, {state.pose.z.toFixed(1)}m</p><p>공유 내용: 정차 베이·기준점·차량 위치. 외부 전송 없이 시뮬레이션 내에서만 전달합니다.</p><button disabled={!safe||!enabled} onClick={()=>act('SHARE')}>이 위치를 모의 배달원에게 전달</button></div></div>}
  {state?.phase==='HANDOFF'&&!state.received&&<div className="handoff-console"><div><strong>배달원 도착 · 주문 #024 대조 완료</strong><p>{state.locationShared?`공유 기준점: ${landmark}`:state.kind==='MEAL'?'집 · 리버빌라 101에서 음식 수령':'예약된 식사 장소에서 음식 수령'} · 차량 정차 / P단 확인</p></div><button disabled={!safe||!enabled||!state.stopped||state.orderStatus!=='ARRIVED'} onClick={()=>act('RECEIVE')}>음식 확인 · 수령 완료</button></div>}
  {state?.received&&<div className="handoff-console"><strong>✓ 주문 #024 인계 완료 · 식사를 시작하세요</strong><span>수령 확인 기록이 시뮬레이션에 반영되었습니다.</span></div>}
  <p className="text-xs text-slate-400 mt-3">1 골목 픽업 / 2 주차 / 3 잠시 정차 / 4 식사 주문 / 5 한강 인계 / 6 배리어프리 하차 · 모든 경로·교통·주문·카메라 화면은 모의 데이터입니다.</p>
 </section>;
}
