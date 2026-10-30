function AccessCabin({state,onAction,onApprove,enabled,safe}){
 const a=state.access,rows=AccessPlanner.evaluate(a.needs,AccessPlanner.zones,a.unavailableIds),best=rows.find(z=>z.eligible),setup=['ACCESS_SETUP','ACCESS_RESELECT'].includes(state.phase),waiting=state.phase==='ACCESS_WAIT';
 const ready=AccessPlanner.canExit(state,enabled&&safe);
 return <section className="meal-cockpit access-cockpit" aria-label="배리어프리 하차 안내 디스플레이"><div className="meal-cockpit-top"><span>CURBLINK ACCESS</span><span>문화센터 · 필요한 지원 선택</span></div><div className="meal-screen">
 <header><span>계단 없는 마지막 이동 경로</span><span>{state.gear} · {Math.round(state.speedKph)}km/h</span></header>
 <h2>{setup?'가까운 곳보다, 내리기 편한 곳':state.done?'하차 · 입구 이동 완료':waiting?'내리기 전에 한 번 더 확인':state.phase==='ACCESS_EXIT'?'계단 없는 입구로 이동 중':'선택한 하차 지점으로 안내 중'}</h2>
 <p>진단명이나 장애 증명 없이 필요한 공간과 도움만 선택합니다.</p>
 {setup?<><div className="access-needs">{[['wheelchair','휠체어 옆문 공간 필요'],['wide','더 넓은 옆문 공간'],['gentle','완만한 경사 우선'],['staff','직원 승하차 도움 요청']].map(([key,label])=><label key={key}><input type="checkbox" checked={a.needs[key]} disabled={!safe||!enabled} onChange={e=>onAction({type:'NEED',key,value:e.target.checked})}/>{label}</label>)}</div>
 <p>{a.needs.wheelchair?'휠체어 이동: 옆문 전개 공간과 무단차 연결을 확인합니다.':'보행 지원: 계단 없는 길 중 걷는 거리가 짧은 지점을 우선합니다.'}</p><div className="access-candidates">{rows.map(z=><article key={z.id} className={z.eligible&&z.id===best?.id?'recommended':!z.eligible?'excluded':''}><strong>{z.id===best?.id?'★ 추천 · ':''}{z.label}</strong><p>입구까지 {z.walk}m · 경사 {z.slope}% · 옆문 {z.side}m</p><small>{z.eligible?'✓ 무단차 연결 · 차도 횡단 없음':`제외: ${z.reasons.join(' / ')}`}</small></article>)}</div>
 {rows.filter(z=>!a.unavailableIds?.includes(z.id)).map(z=><button key={z.id} disabled={!safe||!enabled} onClick={()=>onAction({type:'UNAVAILABLE',id:z.id})}>{z.id} · 이 지점 이용 불가</button>)}
 {!best&&<p role="status">지원 조건을 확인하거나 현장 도움을 요청해 주세요. 안전 조건을 자동 완화하지 않습니다.</p>}
 <button className="access-go" disabled={!best||!safe||!enabled} onClick={()=>onAction({type:'GO'})}>{best?`${best.id} 추천 하차 지점으로 안내`:'조건에 맞는 하차 지점이 없습니다'}</button></>:<>
 <div className="access-selected"><small>선택된 하차 지점</small><strong>{a.zone.label}</strong><div><span>이동 {a.zone.walk}m</span><span>경사 {a.zone.slope}%</span><span>옆문 {a.zone.side}m</span></div><p>정문 앞 계단·차도 횡단을 피하고 무단차 보행로로 연결합니다.</p></div>
 <MissionMap state={state} surround/>
 {waiting&&<button disabled={!enabled||!safe} onClick={()=>onAction({type:'UNAVAILABLE',id:a.zone.id})}>이 지점 이용 불가 · 대안 비교</button>}
 {waiting&&<div className="access-checks"><label><input type="checkbox" disabled={!enabled||!safe} checked={a.spaceConfirmed} onChange={e=>onAction({type:'spaceConfirmed',value:e.target.checked})}/>옆문과 보행로 여유 공간 확인</label>{a.needs.wheelchair&&<label><input type="checkbox" disabled={!enabled||!safe} checked={a.rampReady} onChange={e=>onAction({type:'rampReady',value:e.target.checked})}/>차량 램프·리프트 준비 확인 (모의)</label>}{a.needs.staff&&<label><input type="checkbox" disabled={!enabled||!safe} checked={a.staffAccepted} onChange={e=>onAction({type:'staffAccepted',value:e.target.checked})}/>요청한 직원 지원 수락 확인</label>}<label className="access-test"><input type="checkbox" disabled={!enabled||!safe} checked={a.sideObstacle} onChange={e=>onAction({type:'sideObstacle',value:e.target.checked})}/>시연용 측면 장애물 주입</label><button disabled={!ready} onClick={onApprove}>주변 확인 · 하차 안내 시작</button>{a.sideObstacle&&<p role="alert">측면 장애물이 있어 하차 안내를 보류합니다.</p>}</div>}
 {state.phase==='ACCESS_EXIT'&&!state.done&&<p className="access-positive">{a.needs.wheelchair?'휠체어 이동 공간을 확보하고':'차량 옆 안전 공간에서'} 계단 없는 입구까지 이동합니다.</p>}
 {state.done&&<p className="access-positive">✓ 문화센터 입구 도착 · 차량은 P단 정차 유지</p>}
 </>}
 <footer>수치·구역은 시연용 가정값입니다. 이용 불가는 사용자 확인이며 이번 여정에만 적용합니다. 실제 도어·리프트 제어 없이 하차 동선을 재현합니다.</footer></div></section>;
}
