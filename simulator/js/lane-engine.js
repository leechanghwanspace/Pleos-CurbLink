(function (root) {
  'use strict';
  const lanes = [
    { id: 1, turns: ['LEFT'] }, { id: 2, turns: ['STRAIGHT', 'LEFT'] },
    { id: 3, turns: ['STRAIGHT'] }, { id: 4, turns: ['STRAIGHT', 'RIGHT'], curb: true }
  ];
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
  function rank(input) {
    const { currentLane = 2, phase = 'CRUISE', distance = 300,
      traffic = {}, valid = true } = input;
    if (!valid) return { lane: null, reason: '정보 만료 · 차선 추천 보류', costs: [], nextAction: '차선 정보가 갱신될 때까지 현재 경로를 확인하세요', nextLane: null };
    const context=input.context||{};
    if(!Number.isInteger(currentLane)||currentLane<1||currentLane>4||!Number.isFinite(distance)||distance<0||Object.values(traffic).some(t=>['queue','signalWait','blockProbability','extraWait','uncertainty'].some(k=>t[k]!=null&&!Number.isFinite(t[k]))))return {lane:null,reason:'경로 정보 확인 필요',costs:[]};
    const exit=context.exitMeters!=null&&context.exitMeters<=1500, exitLane=context.exitLane??4;
    if(exit&&(!Number.isFinite(context.exitMeters)||context.exitMeters<0||!Number.isInteger(exitLane)||exitLane<1||exitLane>4))return {lane:null,reason:'경로 정보 확인 필요',costs:[]};
    if(exit&&(context.laneChangeAllowed===false||Math.abs(currentLane-exitLane)*150>context.exitMeters))return {lane:currentLane,reason:'분기점 진입 보류 · 재탐색 필요',costs:[],alternatives:[],nextAction:'현재 차선 유지 · 경로 제공자의 다음 안내 확인',nextLane:null};
    const profile = input.destinationMode ? destinationProfile(input.destinationMode, distance, input.zone) : null;
    const approaching = exit || (profile ? profile.approaching : phase === 'APPROACH');
    const goal = exit ? exitLane : approaching ? (profile?.targetLane ?? 4) : 3;
    const urgency = profile ? profile.approachWeight : approaching ? 4 + 12 * (1 - Math.min(1, Math.max(0, distance) / 300)) : 0;
    const costs = lanes.map(lane => {
      const t = traffic[lane.id] || {};
      const allowed = !t.closed && (context.laneChangeAllowed!==false || lane.id===currentLane) && (approaching ? lane.id >= Math.min(currentLane, goal) && lane.id <= Math.max(currentLane, goal) : lane.turns.includes('STRAIGHT'));
      const changes = Math.abs(lane.id - currentLane);
      // 50 m per change is a demo feasibility assumption, not a road safety standard.
      const reachable = changes * 50 <= distance;
      const travel = 12;
      const queue = Math.max(0, t.queue || 0) * 2 + Math.max(0, t.signalWait || 0);
      const mixed = ((lane.id===2 && !context.simultaneousGreen) || lane.id===4) ? Math.max(0, Math.min(1, t.blockProbability || 0)) * Math.max(0, t.extraWait || 0) : 0;
      const change = changes * 3;
      const approach = Math.abs(lane.id - goal) * (exit ? 24 : urgency) + (!approaching && lane.id===4 ? 8 : 0);
      const uncertainty = Math.max(0, Math.min(1, t.uncertainty || 0)) * 10;
      return { ...lane, travel, queue, mixed, change, approach, uncertainty,
        cost: allowed && reachable ? travel + queue + mixed + change + approach + uncertainty : Infinity };
    });
    const cheapest = costs.reduce((a, b) => b.cost < a.cost ? b : a);
    const best = costs.find(x=>!approaching&&x.id===currentLane&&Number.isFinite(x.cost)&&x.cost<=cheapest.cost+3)||cheapest;
    const lane=Number.isFinite(best.cost)?best.id:null;
    const waiting=id=>{const t=traffic[id]||{};return t.queue>0||t.signalWait>0||t.blockProbability*t.extraWait>0;};
    const nextLane=lane!=null&&lane!==currentLane?currentLane+Math.sign(lane-currentLane):null;
    return { lane, costs, nextLane,
      nextAction:lane==null?'현재 경로 확인 · 안전한 곳에서 안내 재확인':lane===currentLane?`현재 ${currentLane}차선 유지`:`${nextLane}차선 방향 변경 준비 · 표지와 안전 간격 확인`,
      alternatives: context.simultaneousGreen && !approaching ? costs.filter(x=>[2,3].includes(x.id)&&Number.isFinite(x.cost)&&x.cost<=best.cost+6).map(x=>x.id) : Number.isFinite(best.cost) ? [best.id] : [],
      reason: lane==null?'도달 가능한 차선 없음 · 추천 보류':exit ? `${context.exitMeters}m 앞 분기점 · ${exitLane}차선까지 한 차선씩 미리 준비` : approaching ? '선택한 정차·주차 진입 차선으로 순차 접근' : context.simultaneousGreen && [2,3].includes(lane) ? '직좌 동시 신호 · 2·3차선 비용 비교' : lane===3&&waiting(2)?'2차선 대기 영향 · 직진 전용 추천':lane!==4&&waiting(4)?'4차선 대기 영향 · 차선별 비용 비교':lane===currentLane?'변경 비용을 고려해 현재 차선 유지':'차선별 예상 비용 비교 · 직진 흐름 유지' };
  }
  function nextChange(current, target, gapSafe, legal = true) {
    if (target == null || current === target) return { lane: current, allowed: false, reason: '현재 차선 유지' };
    return { lane: current + Math.sign(target - current), allowed: Boolean(gapSafe && legal),
      reason: gapSafe && legal ? '인접 차선으로 변경 가능' : '안전 간격 또는 변경 허용 구간 대기' };
  }
  const api = { lanes, rank, nextChange, DestinationMode, demoDestination, findOptimalCurbZone, destinationProfile };
  root.CurbLane = api;
  if (typeof module !== 'undefined') module.exports = api;
})(typeof globalThis !== 'undefined' ? globalThis : window);
