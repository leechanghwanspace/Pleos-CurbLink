const assert=require('node:assert/strict');global.AccessPlanner=require('./access-planner.js');global.MissionEngine=require('./mission-engine.js');const A=AccessPlanner,M=MissionEngine;
const needs=A.initial().needs;
assert.equal(A.evaluate(needs).find(z=>z.eligible).id,'A2');
assert.equal(A.evaluate({...needs,wide:true}).find(z=>z.eligible).id,'A3');
assert.equal(A.evaluate({...needs,gentle:true}).find(z=>z.eligible).id,'A3');
assert.equal(A.evaluate(needs).find(z=>z.id==='DOOR').eligible,false);
assert.ok(A.evaluate(needs,A.zones.map(z=>({...z,available:false}))).every(z=>!z.eligible));
for(const wide of [false,true]){
 let s={...M.initial('ACCESS'),phase:'ACCESS_SETUP',stopped:true,waiting:true,gear:'P',access:A.initial()};
 s=A.apply(s,{type:'NEED',key:'wide',value:wide},true);s=A.apply(s,{type:'GO'},true);
 for(let i=0;i<10000&&!s.waiting;i++)s=M.advance(s,.1);
 assert.equal(s.phase,'ACCESS_WAIT');assert.equal(s.gear,'P');assert.ok(Math.abs(s.pose.z-(wide?648:610))<.2);
 assert.equal(M.act(s,'ACCESS_APPROVE',true),s);
 for(const type of ['spaceConfirmed','rampReady','staffAccepted'])s=A.apply(s,{type,value:true},true);
 s=A.apply(s,{type:'sideObstacle',value:true},true);assert.equal(M.act(s,'ACCESS_APPROVE',true),s);
 s=A.apply(s,{type:'sideObstacle',value:false},true);assert.equal(M.act(s,'ACCESS_APPROVE',false),s);
 s=M.act(s,'ACCESS_APPROVE',true);assert.ok(s.access.approved);
 for(let i=0;i<1000&&!s.done;i++)s=M.advance(s,.1);
 assert.ok(s.done&&s.stopped);assert.equal(s.gear,'P');const p=A.position(s);assert.equal(p.x,80);assert.equal(p.z,610);
 console.log('PASS accessible route',wide?'A3 wide bay':'A2 nearest suitable bay');
}
console.log('PASS candidate exclusions, no-match, live approval and obstacle interlocks');
{
 let s={...M.initial('ACCESS'),phase:'ACCESS_SETUP',stopped:true,waiting:true,gear:'P',access:A.initial()};
 for(const key of ['wheelchair','staff'])s=A.apply(s,{type:'NEED',key,value:false},true);
 s=A.apply(s,{type:'GO'},true);for(let i=0;i<10000&&!s.waiting;i++)s=M.advance(s,.1);
 s=A.apply(s,{type:'spaceConfirmed',value:true},true);assert.ok(A.canExit(s,true),'walking support does not require an unrequested ramp or staff');
 s=M.act(s,'ACCESS_APPROVE',true);for(let i=0;i<1000&&!s.done;i++)s=M.advance(s,.1);assert.ok(s.done);console.log('PASS walking support without wheelchair equipment');
}
