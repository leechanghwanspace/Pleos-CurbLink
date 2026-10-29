const assert=require('node:assert/strict'),M=require('./mission-engine.js');
for(const kind of Object.keys(M.presets).filter(kind=>kind!=='ACCESS')){
 let s=M.initial(kind),seen=new Set(),hazard=false,reverse=false,share=false;
 for(let i=0;i<40000&&!s.done;i++){
  s=M.advance(s,.05);seen.add(s.phase);hazard ||= s.hazards;reverse ||= s.gear==='R';
  assert.ok(Number.isFinite(s.pose.x)&&Number.isFinite(s.pose.yaw));
  if(s.waiting){
   assert.equal(s.gear,'P');assert.equal(s.speedKph,0);
   const action=s.phase==='LOCATE'?'SHARE':'RECEIVE';
   assert.equal(M.act(s,action,false),s,'unsafe actions rejected');
   s=M.act(s,action,true);if(action==='SHARE')share=true;
  }
 }
 assert.ok(s.done,kind+' must finish');
 if(kind==='PICKUP'){assert.ok(s.passenger);assert.ok(seen.has('ALLEY')&&seen.has('RETURN')&&seen.has('MERGE'));assert.ok(s.pose.z>680);assert.equal(s.gear,'D');}
 if(kind==='PARKING'){assert.ok(reverse);assert.equal(s.gear,'P');assert.ok(Math.abs(s.pose.x-40)<.1&&Math.abs(s.pose.z-576)<.1);}
 if(kind==='SHORT_STOP'){assert.ok(hazard);assert.equal(s.hazards,false);assert.equal(s.gear,'D');}
 if(kind==='MEAL'){assert.ok(s.received);assert.ok(s.orderPlacedAt>=s.orderAt);}
 if(kind==='RIVER'){assert.ok(share&&s.locationShared&&s.received);}
 console.log('PASS',kind,[...seen].join(' → '),s.elapsed.toFixed(1)+'s');
}
// Delivery readiness follows vehicle arrival rather than a shared label-only timer.
{
 let s=M.initial('MEAL'),ready=null,arrived=null;
 for(let i=0;i<10000&&!s.waiting;i++){
  s=M.advance(s,.05);
  if(s.orderStatus==='ARRIVED'&&ready===null)ready=s.elapsed;
  if(s.phase==='COURIER'&&arrived===null)arrived=s.elapsed;
 }
 assert.ok(Math.abs(ready-arrived)<1,'food and vehicle should arrive within one simulated second');
 assert.equal(M.act(s,'RECEIVE',false),s);
 console.log('PASS arrival-aware food timing, delta:',Math.abs(ready-arrived).toFixed(2)+'s');
}
