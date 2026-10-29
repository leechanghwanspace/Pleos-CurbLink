const assert = require('node:assert/strict');
global.CurbLane = require('./lane-engine.js');
const sim = require('./destination-simulation.js');
const {DestinationMode, demoDestination, findOptimalCurbZone, destinationProfile, rank} = CurbLane;
for (const [mode,id] of [['PICKUP','A3'],['SHORT_STOP','B1'],['PARKING','P1']]) {
 const zone = findOptimalCurbZone(demoDestination,mode);
 assert.equal(zone.id,id);
 let state = sim.initial(mode,zone), previous = state;
 for (let i=0;i<6000 && !state.stopped;i++) {
  state=sim.advance(state,.05);
  assert.ok(state.distance <= previous.distance);
  assert.ok(Math.abs(state.lanePosition-previous.lanePosition)<=.05/3+.00001);
  assert.ok(state.speedKph>=0);
  previous=state;
 }
 assert.ok(state.stopped,mode+' must arrive');
 assert.equal(state.lane,4); assert.equal(state.speedKph,0);
 console.log('PASS trajectory:',mode,state.elapsed.toFixed(1)+' simulated seconds');
}
assert.equal(findOptimalCurbZone({...demoDestination,requestedStopSeconds:301},'SHORT_STOP'),null);
assert.equal(findOptimalCurbZone({...demoDestination,nodes:demoDestination.nodes.map(n=>({...n,available:false}))},'PICKUP'),null);
assert.equal(findOptimalCurbZone(demoDestination,'INVALID'),null);
assert.equal(destinationProfile('PARKING',300,{entryLane:4}).approaching,false);
assert.equal(destinationProfile('PICKUP',400,{entryLane:4}).approaching,true);
assert.equal(rank({destinationMode:'PICKUP',zone:{entryLane:4},distance:400}).lane,4);
console.log('PASS mode eligibility, timing and lane cost integration');
