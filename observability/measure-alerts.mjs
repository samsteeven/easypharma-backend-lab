// Mesure le délai de détection et de notification des alertes : provoque une panne, relève l'état chaque seconde.
//   node measure-alerts.mjs error [runs]     # coupe SQL Server sous trafic : le backend répond en 500 -> HighErrorRate
//   node measure-alerts.mjs down [runs]      # arrête le backend                                       -> BackendDown
//   node measure-alerts.mjs slowdb [runs]    # SQL Server limité à 0,3 CPU sous 20 req/s (base lente)  -> HighLatencyP95
// Chaque passe : attend un état sain, injecte la panne (t = 0), relève pending / firing dans Prometheus et l'heure de
// réception de la notification dans le récepteur local (alert-sink), répare, attend le retour à la normale.
// Les résultats s'ajoutent à results/alert-delays.json.  Prérequis : pile démarrée (docker compose up -d).
import { execSync, spawn } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';

const PROM = process.env.PROM ?? 'http://localhost:9090';
const SINK = process.env.SINK ?? 'http://localhost:8099';
const scenario = process.argv[2];
const runs = Number(process.argv[3] ?? 1);
const label = process.env.LABEL ?? 'lab-5s'; // décrit le réglage mesuré (ex. lab-5s, prodlike-15s)
const sh = (cmd) => execSync(cmd, { stdio: ['ignore', 'pipe', 'inherit'] }).toString();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const k6 = (env) =>
  spawn('docker', ['compose', '--profile', 'load', 'run', '-d', '--rm', ...Object.entries(env).flatMap(([k, v]) => ['-e', `${k}=${v}`]), 'k6'], { stdio: 'ignore' });
const stopK6 = () => {
  const ids = sh('docker ps -q --filter label=com.docker.compose.service=k6').split(/\s+/).filter(Boolean);
  if (ids.length) sh(`docker stop ${ids.join(' ')}`);
};
const backendHealthy = () => {
  try {
    const id = sh('docker compose ps -q backend').trim();
    return sh(`docker inspect -f {{.State.Health.Status}} ${id}`).trim() === 'healthy';
  } catch {
    return false;
  }
};

const scenarios = {
  error: {
    alert: 'HighErrorRate',
    prepare: async () => { k6({ SCENARIO: 'mixed', RATE: 20, DURATION: '12m' }); await sleep(75_000); }, // le taux n'est calculable qu'avec du trafic
    inject: () => sh('docker compose stop mssql'),
    repair: () => sh('docker compose start mssql'),
    cleanup: stopK6,
  },
  down: {
    alert: 'BackendDown',
    prepare: async () => {},
    inject: () => sh('docker compose stop backend'),
    repair: () => sh('docker compose start backend'),
    cleanup: async () => { while (!backendHealthy()) await sleep(3000); await sleep(20_000); },
  },
  // Panne de latence déterministe : on ralentit la base, le trafic reste faible et constant. (Une charge de recherche lourde
  // à 32 utilisateurs ne convenait pas : le p95 y oscillait autour de 500 ms et dépendait de l'état de la machine.)
  slowdb: {
    alert: 'HighLatencyP95',
    prepare: async () => { k6({ SCENARIO: 'mixed', RATE: 20, DURATION: '12m' }); await sleep(75_000); },
    inject: () => sh(`docker update --cpus ${process.env.DB_CPUS ?? 0.3} ${sh('docker compose ps -q mssql').trim()}`),
    repair: () => sh(`docker update --cpus 16 ${sh('docker compose ps -q mssql').trim()}`), // 16 = tous les cœurs : plus de plafond
    cleanup: stopK6,
    // `docker update --cpus 0` ne lève pas la limite : on recrée le conteneur pour revenir à la configuration d'origine.
    final: async () => {
      sh('docker compose up -d --force-recreate --no-deps mssql');
      for (let i = 0; i < 60; i++) {
        try { if (sh(`docker inspect -f {{.State.Health.Status}} ${sh('docker compose ps -q mssql').trim()}`).trim() === 'healthy') break; } catch {}
        await sleep(3000);
      }
    },
  },
};
const sc = scenarios[scenario];
if (!sc) throw new Error('scénario : error | down | slowdb');

// Les appels portent un délai d'attente : sous forte charge, un service lent ne doit pas bloquer la mesure.
const getJson = async (url) => {
  try { return await (await fetch(url, { signal: AbortSignal.timeout(4000) })).json(); } catch { return null; }
};
async function alertState() {
  const r = await getJson(`${PROM}/api/v1/alerts`);
  if (!r) return 'injoignable';
  return r.data.alerts.find((x) => x.labels.alertname === sc.alert)?.state ?? 'inactive';
}
async function notifications() {
  const all = (await getJson(`${SINK}/received`)) ?? [];
  return all.flatMap((n) => n.alerts.filter((a) => a.alertname === sc.alert).map((a) => ({ status: a.status, at: n.received_at_ms })));
}
const secs = (t0, t) => (t == null ? null : Math.round((t - t0) / 1000));

async function onePass(n) {
  console.log(`\n--- passe ${n}/${runs} : ${sc.alert}`);
  for (let i = 0; i < 300 && (await alertState()) !== 'inactive'; i++) {
    if (i === 0) console.log("alerte encore active, attente du retour à l'état normal...");
    await sleep(1000);
  }
  if ((await alertState()) !== 'inactive') throw new Error(`${sc.alert} n'est pas redevenue inactive : mesure annulée`);
  await fetch(`${SINK}/received`, { method: 'DELETE' }).catch(() => {});
  await sc.prepare();
  if ((await alertState()) !== 'inactive') throw new Error(`${sc.alert} s'est activée avant l'injection : mesure annulée`);

  const t = {};
  sc.inject();
  t.injected = Date.now();
  console.log('panne injectée à t = 0 s');

  let last = '';
  while (Date.now() - t.injected < 8 * 60_000) {
    const s = await alertState();
    if (s !== last) {
      console.log(`t = ${String(secs(t.injected, Date.now())).padStart(3)} s : ${s}`);
      if (s === 'pending' && !t.pending) t.pending = Date.now();
      last = s;
    }
    if (s === 'firing') { t.firing = Date.now(); break; }
    await sleep(1000);
  }

  // Notification de l'alerte (firing) reçue par le récepteur.
  while (t.firing && Date.now() - t.firing < 90_000) {
    const got = (await notifications()).find((x) => x.status === 'firing');
    if (got) { t.notified = got.at; console.log(`t = ${String(secs(t.injected, got.at)).padStart(3)} s : notification « firing » reçue`); break; }
    await sleep(1000);
  }

  sc.repair();
  t.repaired = Date.now();
  console.log(`panne réparée à t = ${secs(t.injected, t.repaired)} s`);
  while (Date.now() - t.repaired < 4 * 60_000) {
    if ((await alertState()) === 'inactive') { t.resolved = Date.now(); break; }
    await sleep(1000);
  }
  while (t.resolved && Date.now() - t.resolved < 90_000) {
    const got = (await notifications()).find((x) => x.status === 'resolved');
    if (got) { t.notifiedResolved = got.at; break; }
    await sleep(1000);
  }
  await sc.cleanup();

  const result = {
    scenario, alert: sc.alert, setting: label, run: n, at: new Date().toISOString(),
    pending_s: secs(t.injected, t.pending),
    firing_s: secs(t.injected, t.firing),
    notified_s: secs(t.injected, t.notified),
    resolved_after_repair_s: secs(t.repaired, t.resolved),
    resolved_notified_after_repair_s: secs(t.repaired, t.notifiedResolved),
  };
  console.log(JSON.stringify(result));
  return result;
}

const results = [];
for (let n = 1; n <= runs; n++) results.push(await onePass(n));
await sc.final?.();

mkdirSync(new URL('./results/', import.meta.url), { recursive: true });
const file = new URL('./results/alert-delays.json', import.meta.url);
const all = existsSync(file) ? JSON.parse(readFileSync(file)) : [];
writeFileSync(file, JSON.stringify([...all, ...results], null, 2) + '\n');

const med = (a) => { const v = a.filter((x) => x != null).sort((x, y) => x - y); return v.length ? v[Math.floor(v.length / 2)] : null; };
const range = (a) => { const v = a.filter((x) => x != null); return v.length ? `${Math.min(...v)} - ${Math.max(...v)}` : 'n/a'; };
console.log(`\nRésumé ${sc.alert} (${label}, ${runs} passe(s)), en secondes depuis l'injection :`);
for (const k of ['pending_s', 'firing_s', 'notified_s']) {
  console.log(`  ${k.padEnd(10)} médiane ${med(results.map((r) => r[k]))}  (min - max : ${range(results.map((r) => r[k]))})`);
}
