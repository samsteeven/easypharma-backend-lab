// Trafic de démonstration pour les tableaux de bord.
//   SCENARIO=mixed            : débit constant (RATE req/s) réparti sur 4 types de requêtes
//   SCENARIO=nearby | search_nearest | search_price | catalogue : une seule requête, VUS utilisateurs en boucle
import http from 'k6/http';
import { check } from 'k6';

const inputs = JSON.parse(open('./inputs.json'));
const BASE = __ENV.BASE || 'http://backend:8080';
const SCENARIO = __ENV.SCENARIO || 'mixed';
const DURATION = __ENV.DURATION || '3m';
const RATE = Number(__ENV.RATE || 20);
const VUS = Number(__ENV.VUS || 20);

export const options = {
  scenarios: {
    load:
      SCENARIO === 'mixed'
        ? { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 40, maxVUs: 120 }
        : { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ email: 'bench@test.local', password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' } });
  return { token: res.json('data.access_token') };
}

const kinds = [
  ...Array(8).fill('nearby'), ...Array(4).fill('search_nearest'), ...Array(3).fill('search_price'), ...Array(5).fill('catalogue'),
];

export default function (data) {
  const n = __ITER * 7 + __VU * 13;
  const p = inputs.points[n % inputs.points.length];
  const kind = SCENARIO === 'mixed' ? kinds[n % kinds.length] : SCENARIO;
  const term = encodeURIComponent(inputs.terms[n % inputs.terms.length]);
  const headers = { Authorization: `Bearer ${data.token}` };
  let res;

  if (kind === 'nearby') {
    res = http.get(`${BASE}/api/v1/pharmacies/nearby?latitude=${p.lat}&longitude=${p.lon}&radiusKm=15`, { tags: { kind } });
  } else if (kind === 'search_nearest') {
    res = http.get(`${BASE}/api/v1/patient/search?query=${term}&sortBy=POSTGIS_NEAREST&userLat=${p.lat}&userLon=${p.lon}`, { headers, tags: { kind } });
  } else if (kind === 'search_price') {
    res = http.get(`${BASE}/api/v1/patient/search?query=${term}&sortBy=PRICE`, { headers, tags: { kind } });
  } else {
    res = http.get(`${BASE}/api/v1/medications/search?query=${encodeURIComponent(inputs.prefixes[n % inputs.prefixes.length])}`, { headers, tags: { kind } });
  }
  check(res, { 'HTTP 200': (r) => r.status === 200 });
}
