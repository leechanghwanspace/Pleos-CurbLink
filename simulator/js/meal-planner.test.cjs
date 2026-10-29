const assert=require('node:assert/strict');global.MealPlanner=require('./meal-planner.js');const M=require('./mission-engine.js');
function setup(){return {...M.initial('MEAL'),meal:MealPlanner.initial(),phase:'MEAL_SETUP',waiting:true,stopped:true,gear:'P',orderStatus:'NOT_ORDERED'};}
for(const role of ['SOLO','PASSENGER']){
 let s=setup();assert.equal(M.advance(s,.2),s);
 s=MealPlanner.apply(s,{type:'OCCUPANCY',value:role},true);
 s=MealPlanner.apply(s,{type:'GPS'},true);assert.equal(s.meal.step,'LOCATING');s=MealPlanner.apply(s,{type:'GPS_FIX'},true);assert.equal(s.meal.step,'DESTINATION');
 s=MealPlanner.apply(s,{type:'DESTINATION'},true);assert.equal(s.waiting,false);
 for(let i=0;i<10;i++)s=M.advance(s,.1);assert.equal(s.orderPlacedAt,null,'no order before confirmation');
 const source=role==='SOLO'?'VOICE':'TOUCH',wrong=role==='SOLO'?'TOUCH':'VOICE';
 assert.equal(MealPlanner.apply(s,{type:'SELECT',id:'BOWL',source:wrong},true),s);
 s=MealPlanner.apply(s,{type:'SELECT',id:'BOWL',source},true);assert.equal(s.meal.step,'REVIEW');
 assert.equal(MealPlanner.apply(s,{type:'CONFIRM',source},false),s);
 s=MealPlanner.apply(s,{type:'CONFIRM',source},true);assert.equal(s.meal.confirmed,true);
 for(let i=0;i<10000&&!s.waiting;i++)s=M.advance(s,.05);
 assert.equal(s.orderStatus,'ARRIVED');assert.equal(s.phase,'HANDOFF');assert.ok(Math.abs(s.pose.x-65)<.2&&Math.abs(s.pose.z-625)<.2,'meal must arrive at home');
 s=M.act(s,'RECEIVE',true);assert.ok(s.done&&s.received);console.log('PASS meal cockpit',role);
}
assert.ok(MealPlanner.recommend(50).find(m=>m.id==='PASTA').late>0);
console.log('PASS role gating, explicit approval, timing and home arrival');
for(const item of MealPlanner.menus){
 let s=setup();s=MealPlanner.apply(s,{type:'OCCUPANCY',value:'PASSENGER'},true);s=MealPlanner.apply(s,{type:'GPS'},true);s=MealPlanner.apply(s,{type:'GPS_FIX'},true);s=MealPlanner.apply(s,{type:'DESTINATION'},true);s=MealPlanner.apply(s,{type:'SELECT',id:item.id,source:'TOUCH'},true);s=MealPlanner.apply(s,{type:'CONFIRM',source:'TOUCH'},true);
 for(let i=0;i<20000&&!s.waiting;i++)s=M.advance(s,.05);
 assert.equal(s.orderStatus,'ARRIVED');assert.equal(s.meal.menuId,item.id);assert.equal(s.meal.lateAtConfirmation>0,item.id==='PASTA');console.log('PASS menu lead time',item.id);
}
