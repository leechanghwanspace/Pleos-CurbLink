// node scripts/export-scenarios.cjs [--check]
// The APK packages data only; no browser, web server or CDN is used at runtime.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { presets } = require('../simulator/js/mission-engine.js');
const root = path.join(__dirname, '../app/src/androidTest/assets/scenarios');
fs.mkdirSync(root, { recursive: true });
for (const [id, preset] of Object.entries(presets)) {
  const value = { version: 1, id, label: preset.label, stages: preset.stages.map(s => ({
    id: s[0], label: s[1], points: s[2], speed: s[3], wait: s[4] ?? 0
  })) };
  const file = path.join(root, id.toLowerCase() + '.json');
  if (process.argv.includes('--check')) assert.deepEqual(JSON.parse(fs.readFileSync(file, 'utf8')), value, id);
  else fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
}
console.log('Six native scenario fixtures match the web definitions.');
