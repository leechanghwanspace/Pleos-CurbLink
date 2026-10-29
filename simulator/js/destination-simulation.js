(function(root) {
  function initial(mode, zone) {
    return { mode, zone, distance: 600, lane: 2, lanePosition: 2, speedKph: 36, elapsed: 0, stopped: false };
  }
  function advance(state, dt) {
    if (state.stopped) return state;
    const profile = CurbLane.destinationProfile(state.mode, state.distance, state.zone);
    const recommendation = CurbLane.rank({ currentLane: state.lane, distance: state.distance,
      destinationMode: state.mode, zone: state.zone, traffic: { 4: { queue: 3 } } });
    const target = recommendation.lane ?? state.lane;
    const next = CurbLane.nextChange(state.lane, target, true); // Fixed demo road has clear gaps.
    const laneTarget = next.allowed ? next.lane : state.lane;
    const lanePosition = state.lanePosition + Math.sign(laneTarget - state.lanePosition) * Math.min(Math.abs(laneTarget - state.lanePosition), dt / 3);
    const lane = Math.abs(lanePosition - laneTarget) < .001 ? laneTarget : state.lane;
    const speedKph = Math.max(0, Math.min(profile.speedKph, state.speedKph + dt * 3.6));
    // Advance the same longitudinal position used by the map and the vehicle snapshot.
    const distance = Math.max(0, state.distance - speedKph / 3.6 * dt);
    const stopped = distance < .15 && lane === state.zone.entryLane;
    return { ...state, lane, lanePosition, distance: stopped ? 0 : distance,
      speedKph: stopped ? 0 : speedKph, elapsed: state.elapsed + dt, stopped };
  }
  root.DestinationSimulation = { initial, advance };
  if (typeof module !== 'undefined') module.exports = root.DestinationSimulation;
})(typeof globalThis !== 'undefined' ? globalThis : window);
