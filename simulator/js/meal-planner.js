(function(root){
 const menus=[
  {id:'BOWL',name:'닭구이 덮밥',detail:'따뜻한 한 그릇 · 추천',price:10900,prep:30,delivery:20,icon:'🍱'},
  {id:'SALAD',name:'연어 샐러드',detail:'가볍게 먹는 식사',price:11900,prep:20,delivery:15,icon:'🥗'},
  {id:'PASTA',name:'토마토 파스타',detail:'주문 후 바로 조리',price:13900,prep:75,delivery:40,icon:'🍝'}
 ];
 function recommend(eta){return menus.map(m=>({...m,lead:m.prep+m.delivery,late:Math.max(0,m.prep+m.delivery-eta)})).sort((a,b)=>a.late-b.late||Math.abs(eta-a.lead)-Math.abs(eta-b.lead));}
 function initial(){return {occupancy:'SOLO',step:'GPS',menuId:null,confirmed:false,source:null,transcript:'현재 위치를 확인한 뒤 집까지의 도착 시간을 계산합니다.'};}
 function apply(state,action,safe){
  if(!safe||state.kind!=='MEAL'||!state.meal)return state;
  const meal={...state.meal},next={...state,meal};
  if(action.type==='OCCUPANCY'&&!meal.confirmed){meal.occupancy=action.value==='PASSENGER'?'PASSENGER':'SOLO';return next;}
  if(action.type==='GPS'&&meal.step==='GPS'){meal.step='LOCATING';meal.transcript='모의 GPS 신호를 탐색하고 차량 위치를 지도에 맞추고 있습니다.';return next;}
  if(action.type==='GPS_FIX'&&meal.step==='LOCATING'){meal.step='DESTINATION';meal.transcript='모의 GPS 위치를 확인했습니다. 목적지를 집으로 설정할까요?';return next;}
  if(action.type==='DESTINATION'&&meal.step==='DESTINATION'){
   meal.step='MENU';meal.transcript=`집까지 약 ${Math.ceil(state.remainingEta)}초입니다. 조리와 배달이 도착 전에 끝나는 메뉴를 찾겠습니다.`;
   return {...next,phase:'MAIN',waiting:false,stopped:false,gear:'D'};
  }
  const permitted=meal.occupancy==='SOLO'?action.source==='VOICE':action.source==='TOUCH';
  if(!permitted)return state;
  if(action.type==='SELECT'&&['MENU','REVIEW'].includes(meal.step)){
   const menu=menus.find(m=>m.id===action.id);if(!menu)return state;meal.menuId=menu.id;meal.step='REVIEW';meal.source=action.source;
   meal.transcript=`${menu.name}, ${menu.price.toLocaleString('ko-KR')}원입니다. 조리 ${menu.prep}초, 배달 ${menu.delivery}초를 고려해 집 도착에 맞춰 주문할까요?`;return next;
  }
  if(action.type==='BACK'&&meal.step==='REVIEW'){meal.step='MENU';return next;}
  if(action.type==='CONFIRM'&&meal.step==='REVIEW'&&!meal.confirmed){
   const menu=menus.find(m=>m.id===meal.menuId);if(!menu)return state;
   meal.confirmed=true;meal.step='TRACK';meal.source=action.source;meal.lateAtConfirmation=Math.max(0,menu.prep+menu.delivery-state.remainingEta);
   meal.transcript=meal.lateAtConfirmation>0?`주문을 확인했습니다. 집 도착 후 약 ${Math.ceil(meal.lateAtConfirmation)}초 추가 대기가 예상됩니다.`:'주문을 확인했습니다. 집 도착 시간에 맞춰 조리를 시작하도록 예약합니다.';
   return {...next,orderStatus:'SCHEDULED',orderAt:state.elapsed+Math.max(0,state.remainingEta-menu.prep-menu.delivery)};
  }
  return state;
 }
 const api={menus,recommend,initial,apply};root.MealPlanner=api;if(typeof module!=='undefined')module.exports=api;
})(typeof globalThis!=='undefined'?globalThis:window);
