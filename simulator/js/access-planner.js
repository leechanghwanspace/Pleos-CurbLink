/* Demo suitability criteria, not a legal accessibility certification. */
(function(root){
 const zones=[
  {id:'DOOR',label:'정문 바로 앞',x:70,z:588,walk:8,slope:9,curb:15,side:0.8,stepFree:false,crossing:true,staff:false,available:true},
  {id:'A2',label:'동측 무단차 하차 베이 A-2',x:70,z:610,walk:32,slope:2.5,curb:0,side:1.8,stepFree:true,crossing:false,staff:true,available:true},
  {id:'A3',label:'북측 넓은 하차 베이 A-3',x:70,z:648,walk:55,slope:1.2,curb:0,side:2.4,stepFree:true,crossing:false,staff:true,available:true}
 ];
 function initial(){return {needs:{wheelchair:true,wide:false,gentle:false,staff:true},zone:null,unavailableIds:[],spaceConfirmed:false,rampReady:false,staffAccepted:false,sideObstacle:false,approved:false};}
 function evaluate(needs,candidates=zones,unavailableIds=[]){
  return candidates.map(zone=>{
   const checks=[['운영 중',zone.available],['계단 없는 보행 연결',zone.stepFree],['차도 횡단 없음',!zone.crossing],['낮은 연석',zone.curb<=2],['완만한 경사',zone.slope<=(needs.gentle?2:5)],['옆문 여유 공간',zone.side>=(needs.wide?2:needs.wheelchair?1.5:1)],['지원 직원 배치',!needs.staff||zone.staff]];
   const failures={'운영 중':'운영 불가·미확인','계단 없는 보행 연결':'계단·단차가 있는 보행로','차도 횡단 없음':'차도 횡단 필요','낮은 연석':'연석 높이 초과','완만한 경사':'경사 조건 초과','옆문 여유 공간':'옆문 공간 부족','지원 직원 배치':'지원 직원 미배치'};
   const reasons=checks.filter(c=>!c[1]).map(c=>failures[c[0]]);if(unavailableIds.includes(zone.id))reasons.push('사용자 확인 · 이번 여정 이용 불가');return {...zone,checks,reasons,eligible:reasons.length===0,cost:reasons.length?Infinity:zone.walk+zone.slope*(needs.gentle?8:2)};
  }).sort((a,b)=>a.cost-b.cost||a.walk-b.walk);
 }
 function apply(state,action,safe){
  if(!safe||state.kind!=='ACCESS'||!state.access)return state;
  const access={...state.access},next={...state,access};
  if(action.type==='NEED'&&['ACCESS_SETUP','ACCESS_RESELECT'].includes(state.phase)&&['wheelchair','wide','gentle','staff'].includes(action.key)){access.needs={...access.needs,[action.key]:Boolean(action.value)};return next;}
  const setup=['ACCESS_SETUP','ACCESS_RESELECT'].includes(state.phase);
  if(action.type==='UNAVAILABLE'&&(setup||state.phase==='ACCESS_WAIT')&&state.stopped&&state.gear==='P'&&zones.some(z=>z.id===action.id)){
   access.unavailableIds=[...new Set([...(access.unavailableIds||[]),action.id])];
   if(access.zone?.id===action.id){access.zone=null;access.approved=false;access.spaceConfirmed=false;access.rampReady=false;access.staffAccepted=false;return {...next,phase:'ACCESS_RESELECT',waiting:true};}
   return next;
  }
  if(action.type==='GO'&&setup){
   const best=evaluate(access.needs,zones,access.unavailableIds).find(z=>z.eligible);if(!best)return state;
   access.zone=best;access.approved=false;access.spaceConfirmed=false;access.rampReady=false;access.staffAccepted=false;
   const reselect=state.phase==='ACCESS_RESELECT';
   return {...next,routeOverrides:MissionEngine.accessRoutes(best,reselect?state.pose:null),zone:{id:best.id,label:best.label},stage:reselect?1:state.stage,stageTime:0,travel:0,phase:reselect?'ACCESS_APPROACH':'MAIN',waiting:false,stopped:false,gear:'D'};
  }
  if(state.phase==='ACCESS_WAIT'&&['spaceConfirmed','rampReady','staffAccepted','sideObstacle'].includes(action.type)){access[action.type]=Boolean(action.value);return next;}
  return state;
 }
 function canExit(state,safe){const a=state.access;return Boolean(safe&&a&&state.gear==='P'&&state.stopped&&state.phase==='ACCESS_WAIT'&&a.zone&&evaluate(a.needs,[a.zone],a.unavailableIds)[0].eligible&&a.spaceConfirmed&&!a.sideObstacle&&(!a.needs.wheelchair||a.rampReady)&&(!a.needs.staff||a.staffAccepted));}
 function position(state){
  const zone=state.access?.zone;if(!zone)return null;
  const points=[[zone.x+1,zone.z],[76,zone.z],[76,610],[80,610]],segments=points.slice(1).map((p,i)=>Math.hypot(p[0]-points[i][0],p[1]-points[i][1]));
  const duration=zone.id==='A3'?42:12;let distance=segments.reduce((a,b)=>a+b,0)*Math.min(1,state.stageTime/duration);
  for(let i=0;i<segments.length;i++){if(distance<=segments[i]||i===segments.length-1){const t=segments[i]?Math.min(1,distance/segments[i]):1;return {x:points[i][0]+(points[i+1][0]-points[i][0])*t,z:points[i][1]+(points[i+1][1]-points[i][1])*t};}distance-=segments[i];}
 }
 const api={zones,initial,evaluate,apply,canExit,position};root.AccessPlanner=api;if(typeof module!=='undefined')module.exports=api;
})(typeof globalThis!=='undefined'?globalThis:window);
