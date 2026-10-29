/* Deterministic world-space journeys. Units: metres, seconds, radians. */
(function(root){
 const presets={
  ACCESS:{label:'문화센터 · 배리어프리 하차',mode:'PICKUP',stages:[
   ['MAIN','하차 조건을 반영한 진입 차선 안내',[[-1.75,0],[-1.75,350],[1.75,405],[5.25,465],[5.25,522]],10],
   ['ACCESS_APPROACH','무단차 베이로 우회 · 저속 접근',[[5.25,522],[5.25,540],[18,552],[60,552],[70,565],[70,610]],3],
   ['ACCESS_WAIT','P단 정차 · 옆문 공간과 지원 확인',null,0,0],
   ['ACCESS_EXIT','하차 지원 · 계단 없는 입구로 이동',null,0,12]
  ]},
  PICKUP:{label:'골목 승객 픽업',mode:'PICKUP',stages:[
   ['MAIN','4차선 접근',[[ -1.75,0],[-1.75,320],[1.75,380],[5.25,440],[5.25,580]],10],
   ['ALLEY','우회전 · 주택가 골목 진입',[[5.25,580],[5.25,591],[14,600],[55,600],[65,610],[65,625]],4],
   ['BOARD','빌라 앞 정차 · 승객 탑승',null,0,7],
   ['RETURN','주택가 순환 · 출구 접근',[[65,625],[65,640],[87,640],[87,610],[77,600],[20,600]],4],
   ['MERGE','좌측 확인 · 본선 복귀',[[20,600],[12,600],[5.25,612],[5.25,655],[1.75,695]],6]
  ]},
  PARKING:{label:'목적지 주차장 주차',mode:'PARKING',stages:[
   ['MAIN','주차장 진입 차선 접근',[[-1.75,0],[-1.75,350],[1.75,405],[5.25,465],[5.25,522]],10],
   ['GATE','우회전 · 주차장 차단기 통과',[[5.25,522],[5.25,540],[18,552],[55,552]],3],
   ['AISLE','주차장 통로 · 빈 주차면 탐색',[[55,552],[64,556],[64,565],[40,565],[40,562]],2],
   ['REVERSE','R단 · 주차면 후진 정렬',[[40,562],[40,576]],1.2],
   ['PARKED','주차면 중앙 정렬 · P단',null,0,3]
  ]},
  SHORT_STOP:{label:'잠시 정차 · 비상등',mode:'SHORT_STOP',stages:[
   ['MAIN','단기 정차 구역 접근',[[-1.75,0],[-1.75,320],[1.75,380],[5.25,440],[5.25,565]],10],
   ['PULLOVER','연석 안쪽으로 감속 진입',[[5.25,565],[5.25,580],[8.9,596],[8.9,610]],3],
   ['WAIT','비상등 점멸 · 잠시 정차',null,0,12],
   ['MERGE','비상등 해제 · 간격 확인 후 재출발',[[8.9,610],[8.9,620],[5.25,637],[5.25,660],[1.75,695]],5]
  ]},
  MEAL:{label:'집 도착 시간 맞춤 식사 주문',mode:'PICKUP',stages:[
   ['MAIN','도착 예측 · 조리·배달 시간 역산',[[-1.75,0],[-1.75,320],[1.75,390],[5.25,455],[5.25,580]],10],
   ['HOME','집 앞 골목으로 우회전',[[5.25,580],[5.25,591],[16,600],[55,600],[65,610],[65,625]],4],
   ['COURIER','집 앞 정차 · 배달원 도착',null,0,8],
   ['HANDOFF','주문 음식 확인 · 수령 승인 대기',null,0,0]
  ]},
  RIVER:{label:'한강 위치 확인 · 음식 인계',mode:'SHORT_STOP',stages:[
   ['MAIN','한강 진입로로 이동',[[-1.75,0],[-1.75,320],[1.75,390],[5.25,455],[5.25,580]],10],
   ['RIVERSIDE','강변 인계 베이 B-12 진입',[[5.25,580],[5.25,591],[16,600],[65,600],[96,600],[112,616]],4],
   ['LOCATE','모의 서라운드뷰 · 인계 위치 확인',null,0,0],
   ['COURIER','확인 위치에서 배달원 접근',null,0,8],
   ['HANDOFF','주문 음식 확인 · 수령 승인 대기',null,0,0]
  ]}
 };
 function curve(points){
  const path=[];
  for(let i=0;i<points.length-1;i++){
   const a=points[Math.max(0,i-1)],b=points[i],c=points[i+1],d=points[Math.min(points.length-1,i+2)];
   const steps=Math.max(12,Math.ceil(Math.hypot(c[0]-b[0],c[1]-b[1])*2));
   for(let j=0;j<steps;j++){const t=j/steps,t2=t*t,t3=t2*t;path.push([0,1].map(k=>.5*((2*b[k])+(-a[k]+c[k])*t+(2*a[k]-5*b[k]+4*c[k]-d[k])*t2+(-a[k]+3*b[k]-3*c[k]+d[k])*t3)));}
  }
  path.push(points[points.length-1]);
  let length=0;const samples=path.map((p,i)=>{if(i)length+=Math.hypot(p[0]-path[i-1][0],p[1]-path[i-1][1]);return {x:p[0],z:p[1],s:length};});
  return {samples,length};
 }
 const routes={};for(const [key,p] of Object.entries(presets))routes[key]=p.stages.map(s=>s[2]?curve(s[2]):null);
 function sample(route,d){const list=route.samples;let i=1;while(i<list.length-1&&list[i].s<d)i++;const a=list[i-1],b=list[i],t=Math.min(1,Math.max(0,(d-a.s)/(b.s-a.s||1)));return {x:a.x+(b.x-a.x)*t,z:a.z+(b.z-a.z)*t,yaw:Math.atan2(b.x-a.x,b.z-a.z)};}
 function routeSeconds(route,cruise,travel=0,speed=0){
  let t=0;while(route.length-travel>.08&&t<2000){const step=.2;speed=Math.min(cruise,Math.sqrt(2*1.4*Math.max(0,route.length-travel)),speed+step*1.5);travel+=Math.max(.03,speed)*step;t+=step;}return t;
 }
 function remainingEta(s){
  return (s.routeOverrides||routes[s.kind]).reduce((sum,r,i)=>sum+(i<s.stage||!r?0:routeSeconds(r,presets[s.kind].stages[i][3],i===s.stage?s.travel:0,i===s.stage?s.speedKph/3.6:0)),0);
 }
 function initial(kind){
  if(!presets[kind])throw Error('Unknown mission');
  const p=presets[kind];const eta=routes[kind].reduce((sum,r,i)=>sum+(r?routeSeconds(r,p.stages[i][3]):0),0);
  return {kind,mode:p.mode,zone:{id:kind==='MEAL'?'HOME-101':kind==='RIVER'?'B-12':kind==='PARKING'?'P-07':kind==='PICKUP'?'VILLA-101':'CURB-B1',label:p.label},
   stage:0,phase:'MAIN',stageTime:0,travel:0,elapsed:0,pose:sample(routes[kind][0],0),speedKph:0,gear:'D',stopped:false,done:false,waiting:false,
   lane:2,lanePosition:2,distance:routes[kind][0].length,eta,remainingEta:eta,orderAt:Math.max(0,eta-50),orderStatus:'SCHEDULED',orderPlacedAt:null,locationShared:false,received:false,passenger:false,hazards:false};
 }
 function advance(state,dt){
  if(state.done||state.waiting)return state;
  dt=Math.min(.25,Math.max(0,dt));
  const s={...state,elapsed:state.elapsed+dt,stageTime:state.stageTime+dt};
  const p=presets[s.kind],def=p.stages[s.stage],route=(s.routeOverrides||routes[s.kind])[s.stage];s.phase=def[0];
  s.remainingEta=remainingEta(s);
  const selected=s.meal?.menuId&&typeof MealPlanner!=='undefined'?MealPlanner.menus.find(m=>m.id===s.meal.menuId):null;
  const prep=selected?.prep??30,delivery=selected?.delivery??20,lead=prep+delivery;
  const authorized=s.kind==='RIVER'||!s.meal||s.meal.confirmed;
  if(s.kind==='MEAL'&&s.orderPlacedAt===null&&authorized)s.orderAt=Math.max(0,s.elapsed+s.remainingEta-lead);
  if(s.kind==='MEAL'||s.kind==='RIVER'){
   if(!authorized)s.orderStatus='NOT_ORDERED';
   else {
    if(s.orderPlacedAt===null && (s.kind==='RIVER'||s.elapsed>=s.orderAt))s.orderPlacedAt=s.elapsed;
    const age=s.orderPlacedAt===null?-1:s.elapsed-s.orderPlacedAt;
    s.orderStatus=age<0?'SCHEDULED':age<2?'ORDERED':age<prep?'COOKING':age<lead?'DELIVERING':'ARRIVED';
   }
  }
  let finished=false;
  if(route){
   const remaining=route.length-s.travel;
   const target=Math.min(def[3],Math.sqrt(2*1.4*Math.max(0,remaining)));
   const speed=Math.min(target,s.speedKph/3.6+dt*1.5);
   s.speedKph=speed*3.6;s.travel=Math.min(route.length,s.travel+Math.max(.03,speed)*dt);
   s.pose=sample(route,s.travel);if(s.phase==='REVERSE')s.pose.yaw+=Math.PI;
   s.gear=s.phase==='REVERSE'?'R':'D';s.stopped=false;
   finished=route.length-s.travel<.08;
  }else{
   s.speedKph=0;s.gear='P';s.stopped=true;
   if(s.phase==='LOCATE'||s.phase==='HANDOFF'||s.phase==='ACCESS_WAIT'){s.waiting=true;return s;}
   finished=s.stageTime>=(s.phase==='ACCESS_EXIT'&&s.access?.zone?.id==='A3'?42:(def[4]||0));
   if(s.phase==='BOARD'&&s.stageTime>5)s.passenger=true;
   if(s.phase==='COURIER'&&s.orderStatus!=='ARRIVED')finished=false;
  }
  s.hazards=s.phase==='WAIT';s.lanePosition=Math.max(1,Math.min(4,s.pose.x/3.5+2.5));s.lane=Math.round(s.lanePosition);
  s.distance=s.stage===0?Math.max(0,routes[s.kind][0].length-s.travel):route?Math.max(0,route.length-s.travel):0;
  if(finished){
   if(s.stage===p.stages.length-1){s.done=true;s.stopped=s.kind==='PARKING'||s.kind==='ACCESS';s.speedKph=s.stopped?0:def[3]*3.6;s.gear=s.stopped?'P':'D';s.hazards=false;}
   else{s.stage++;s.stageTime=0;s.travel=0;s.phase=p.stages[s.stage][0];}
  }
  return s;
 }
 function act(s,action,safe){
  if(!safe||!s.stopped||s.gear!=='P')return s;
  if(action==='ACCESS_APPROVE'&&typeof AccessPlanner!=='undefined'&&AccessPlanner.canExit(s,safe))return {...s,access:{...s.access,approved:true},waiting:false,stage:s.stage+1,phase:'ACCESS_EXIT',stageTime:0};
  if(action==='SHARE'&&s.phase==='LOCATE')return {...s,locationShared:true,waiting:false,stage:s.stage+1,phase:'COURIER',stageTime:0};
  if(action==='RECEIVE'&&s.phase==='HANDOFF'&&s.orderStatus==='ARRIVED'&&(s.kind!=='RIVER'||s.locationShared))return {...s,waiting:false,received:true,done:true};
  return s;
 }
 function accessRoutes(zone){const result=routes.ACCESS.slice();result[1]=curve([[5.25,522],[5.25,540],[18,552],[60,552],[70,565],[zone.x,zone.z]]);return result;}
 const api={presets,routes,initial,advance,act,sample,accessRoutes};root.MissionEngine=api;if(typeof module!=='undefined')module.exports=api;
})(typeof globalThis!=='undefined'?globalThis:window);
