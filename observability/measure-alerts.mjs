// Mesure le délai de détection des alertes : provoque une panne, relève l'état des alertes chaque seconde.
//   node measure-alerts.mjs error     # coupe SQL Server : le backend répond en 500  -> HighErrorRate
//   node measure-alerts.mjs down      # arrête le backend                             -> BackendDown
//   node measure-alerts.mjs latency   # charge de recherche triée par distance (VUS, 32 par défaut) -> HighLatencyP95
// Prérequis : pile démarrée (docker compose up -d) et, pour « error » et « down », du trafic en cours
// (docker compose --profile load run -d --rm k6) afin que les taux soient calculables.
import { execSync, spawn } from 'node:child_process';

const PROM = process.env.PROM ?? 'http://localhost:9090';
const scenario = process.argv[2];
const sh = (cmd) => execSync(cmd, { stdio: ['ignore', 'pipe', 'inherit'] }).toString();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const fault = {
  error: { alert: 'HighErrorRate', inject: () => sh('docker compose stop mssql'), repair: () => sh('docker compose start mssql') },
  down: { alert: 'BackendDown', inject: () => sh('docker compose stop backend'), repair: () => sh('docker compose start backend') },
  latency: {
    alert: 'HighLatencyP95',
    inject: () => {
      spawn('docker', ['compose', '--profile', 'load', 'run', '-d', '--rm', '-e', 'SCENARIO=search_nearest', '-e', `VUS=${process.env.VUS ?? 32}`, '-e', 'DURATION=8m', 'k6'], { stdio: 'ignore' });
    },
    repair: () => {
      const ids = sh('docker ps -q --filter label=com.docker.compose.service=k6').split(/\s+/).filter(Boolean);
      if (ids.length) sh(`docker stop ${ids.join(' ')}`);
    },
  },
}[scenario];
if (!fault) throw new Error('scénario : error | down | latency');

// Délai d'attente court : sous forte charge, un Prometheus lent ne doit pas bloquer la mesure.
async function state() {
  try {
    const r = await (await fetch(`${PROM}/api/v1/alerts`, { signal: AbortSignal.timeout(4000) })).json();
    const a = r.data.alerts.find((x) => x.labels.alertname === fault.alert);
    return a ? a.state : 'inactive';
  } catch {
    return 'injoignable';
  }
}

async function p95() {
  try {
    const q = 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="backend",uri!~"/actuator.*"}[1m])))';
    const r = await (await fetch(`${PROM}/api/v1/query?query=${encodeURIComponent(q)}`, { signal: AbortSignal.timeout(4000) })).json();
    const v = r.data.result[0]?.value[1];
    return v === undefined || v === 'NaN' ? 'n/a' : `${Number(v).toFixed(2)} s`;
  } catch {
    return 'n/a';
  }
}

const t = { start: Date.now() };

// La mesure n'a de sens que depuis un état sain : on attend que l'alerte soit retombée (3 minutes au plus).
for (let i = 0; i < 180 && (await state()) !== 'inactive'; i++) {
  if (i === 0) console.log(`[${fault.alert}] alerte encore active, attente du retour à l'état normal...`);
  await sleep(1000);
}
if ((await state()) !== 'inactive') throw new Error(`${fault.alert} n'est pas redevenue inactive : mesure annulée`);
console.log(`[${fault.alert}] état initial : inactive`);
fault.inject();
t.injected = Date.now();
console.log('panne injectée à t = 0 s');

let last = '';
let lastReport = 0;
while (Date.now() - t.injected < 6 * 60_000) {
  const s = await state();
  const dt = ((Date.now() - t.injected) / 1000).toFixed(0);
  if (scenario === 'latency' && Date.now() - lastReport > 15_000) {
    lastReport = Date.now();
    console.log(`  (t = ${dt.padStart(3)} s, p95 observé : ${await p95()}, état ${s})`);
  }
  if (s !== last) {
    console.log(`t = ${dt.padStart(3)} s : ${s}`);
    if (s === 'pending') t.pending = Date.now();
    if (s === 'firing') { t.firing = Date.now(); break; }
    last = s;
  }
  await sleep(1000);
}

fault.repair();
t.repaired = Date.now();
console.log('panne réparée à t =', ((t.repaired - t.injected) / 1000).toFixed(0), 's');
while (Date.now() - t.repaired < 3 * 60_000) {
  if ((await state()) === 'inactive') {
    console.log(`alerte résolue ${((Date.now() - t.repaired) / 1000).toFixed(0)} s après la réparation`);
    break;
  }
  await sleep(1000);
}

console.log('\nRésumé');
if (t.pending) console.log(`  détection (pending) : ${((t.pending - t.injected) / 1000).toFixed(0)} s`);
if (t.firing) console.log(`  alerte (firing)     : ${((t.firing - t.injected) / 1000).toFixed(0)} s`);
else console.log('  l\'alerte ne s\'est pas déclenchée dans les 6 minutes');
