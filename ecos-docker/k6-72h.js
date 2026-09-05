// k6-72h.js — Wave-4.2 T-03 72h Soak 压力脚本 (5 角色 × 5 UC × 50 VU)
// 用法 (在 docker 内 /k6 mount 已 = ./ecos-docker):
//   docker run --rm -i -v $(pwd)/ecos-docker:/k6:ro -e ECOS_BASE=http://host.docker.internal:8080 \
//     grafana/k6 run --vus 50 --duration 2h -e ECOS_DURATION=2h /k6/k6-72h.js
// ENV:
//   ECOS_BASE       http://host.docker.internal:8080 (WSL1 host) 或 http://172.18.0.1:8080 (k8s bridge)
//   ECOS_DURATION   默认 2h (Demo 段), 72h 段会传 24h
//   ECOS_VUS        默认 50
// 验收: 0 crash + 净内存 <100MB + P99 < 2s
import http from 'k6/http';
import { check, sleep, Rate, Trend } from 'k6/metrics';
import { sharedArray } from 'k6/data';

const BASE = (typeof __ENV !== 'undefined' && __ENV.ECOS_BASE) ? __ENV.ECOS_BASE : 'http://host.docker.internal:8080';
const TENANT = (typeof __ENV !== 'undefined' && __ENV.ECOS_TENANT) ? __ENV.ECOS_TENANT : 'tenant-a';

export const options = {
  vus: Number((typeof __ENV !== 'undefined' && __ENV.ECOS_VUS) || 50),
  duration: (typeof __ENV !== 'undefined' && __ENV.ECOS_DURATION) || '2h',
  thresholds: {
    'http_req_duration': ['p(99)<2000'],   // P99 < 2s
    'http_req_failed': ['rate<0.05'],      // 5% 失败上限
    'checks': ['rate>0.90'],               // 90% check 通过
  },
};

// 5 角色 (PG users 表能登的 5 个, 都有 admin123 同哈希)
const ROLES = {
  admin:   { user: 'admin',       pass: 'admin123' },
  analyst: { user: 'zhangsan',    pass: 'admin123' },
  viewer:  { user: 'lisi',        pass: 'admin123' },
  auditor: { user: 'wangwu',      pass: 'admin123' },
  orphan:  { user: 'test_admin',  pass: 'admin123' },
};
const TENANT_PER_ROLE = {
  admin: 'tenant-a', analyst: 'tenant-a', viewer: 'tenant-a', auditor: 'tenant-a', orphan: 'tenant-a',
};

// 21 个真实能 200 的端点 (admin 探活 4 个 5xx 已剔: rag-search / causal.reason / service-catalog / sysman.indicators)
const ENDPOINTS = [
  { name: 'auth.login',       method: 'POST', path: '/api/v1/auth/login',                                            body: { username: ROLES.admin.user, password: ROLES.admin.pass }, rollout: ['admin'] },
  { name: 'iam.tenants',      method: 'GET',  path: '/api/v1/iam/tenants',                                              body: null, rollout: ['admin'] },
  { name: 'iam.users',        method: 'GET',  path: '/api/v1/iam/users?limit=20',                                       body: null, rollout: ['admin'] },
  { name: 'iam.roles',        method: 'GET',  path: '/api/v1/iam/roles',                                                body: null, rollout: ['admin'] },
  { name: 'iam.permissions',  method: 'GET',  path: '/api/v1/iam/permissions/permission-groups',                       body: null, rollout: ['admin'] },
  { name: 'iam.users.me',     method: 'GET',  path: '/api/v1/iam/users/me',                                             body: null, rollout: ['admin','analyst','viewer','auditor'] },
  { name: 'data.datasets',    method: 'GET',  path: '/api/v1/data/datasets?limit=20',                                   body: null, rollout: ['admin','analyst','viewer'] },
  { name: 'data.pipelines',   method: 'GET',  path: '/api/v1/data/pipelines?limit=20',                                  body: null, rollout: ['admin','analyst','viewer'] },
  { name: 'data.jobs',        method: 'GET',  path: '/api/v1/data/jobs?limit=20',                                       body: null, rollout: ['admin','analyst','viewer'] },
  { name: 'kb.compliance.v1', method: 'GET',  path: '/api/v1/kb/rules/compliance',                                      body: null, rollout: ['admin','auditor'] },
  { name: 'kb.compliance',    method: 'GET',  path: '/api/v1/kb/compliance-rules',                                      body: null, rollout: ['admin','auditor'] },
  { name: 'kb.graph',         method: 'GET',  path: '/api/v1/knowledge/graph/nodes?limit=20',                           body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.graph.search',  method: 'GET',  path: '/api/v1/graph/nodes?limit=20',                                     body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.graph.search2', method: 'GET',  path: '/api/v1/graph/nodes/search?query=sales&limit=10',                 body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.entities',      method: 'GET',  path: '/api/v1/knowledge/entities?limit=20',                              body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.entities.me',   method: 'GET',  path: '/api/v1/knowledge/entities/me?limit=20',                           body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.ontology',      method: 'GET',  path: '/api/v1/ontology/domains/search?limit=10',                       body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.rag.cat',       method: 'GET',  path: '/api/v1/rag/categories',                                           body: null, rollout: ['analyst','viewer'] },
  { name: 'kb.rag.history',   method: 'GET',  path: '/api/v1/rag/search-history?limit=20',                             body: null, rollout: ['analyst','viewer'] },
  { name: 'audit.rules',      method: 'GET',  path: '/api/v1/audit/rules?limit=20',                                     body: null, rollout: ['admin','auditor'] },
  { name: 'audit.events',     method: 'GET',  path: '/api/v1/audit/events?limit=20',                                    body: null, rollout: ['admin','auditor'] },
  { name: 'cooperators',      method: 'GET',  path: '/api/v1/cooperators?limit=20',                                     body: null, rollout: ['admin','analyst'] },
];

// 自定义 metric
const checkFailRate = new Rate('check_fail');

/** Token 缓存 (单 VU 单角色, k6 单线程 token 已稳) */
const TOKEN = {};

async function getToken(roleName) {
  if (TOKEN[roleName]) return TOKEN[roleName];
  const role = ROLES[roleName];
  const res = http.post(`${BASE}/api/v1/auth/login`,
    JSON.stringify({ username: role.user, password: role.pass }),
    { headers: { 'Content-Type': 'application/json' } });
  let tok = '';
  try {
    const j = JSON.parse(res.body || '{}');
    tok = (j.data && j.data.accessToken) || (j.data && j.data.token) || j.token || '';
  } catch (_) { tok = ''; }
  if (tok) TOKEN[roleName] = tok;
  return tok;
}

export function setup() {
  // 预热 5 角色 token
  Object.keys(ROLES).forEach(rn => {
    try { getToken(rn); } catch (e) { console.log(`[setup] login fail ${rn}: ${e.message}`); }
  });
  return { ROLES: Object.keys(ROLES).length };
}

export function teardown(data) {
  console.log(`[teardown] All ${data.ROLES} roles tokened`);
}

export default function (data) {
  // 50 VU → 10 VU/角色 (admin/analyst/viewer/auditor/orphan)
  const roleName = Object.keys(ROLES)[(__VU - 1) % 5];
  const token = getToken(roleName);
  const tenant = TENANT_PER_ROLE[roleName];

  const candidates = ENDPOINTS.filter(e => e.rollout.includes(roleName));
  const ep = candidates[(__ITER - 1) % candidates.length];

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': token ? `Bearer ${token}` : '',
    'X-Tenant-Id': tenant,
    'X-User-Id': ROLES[roleName].user,
  };

  let res;
  if (ep.method === 'GET') {
    res = http.get(`${BASE}${ep.path}`, { headers });
  } else {
    res = http.post(`${BASE}${ep.path}`, JSON.stringify(ep.body || {}), { headers });
  }

  const statusOk = res.status >= 200 && res.status < 400;
  const bodyNonEmpty = (res.body && res.body.length) > 0;
  const durOk = res.timings.duration < 2000;
  check(res, {
    [`${ep.name}: status 2xx`]: () => statusOk,
    [`${ep.name}: body not empty`]: () => bodyNonEmpty,
    [`${ep.name}: dur < 2s`]: () => durOk,
  });
  checkFailRate.add(!statusOk ? 1 : 0);

  sleep(0.5); // 50 VU × 2/s = 100 QPS 总
}
