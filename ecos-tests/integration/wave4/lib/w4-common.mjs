// lib/w4-common.mjs — Wave-4.1 多域联调共享工具库
// 用法:
//   import { BASE, ADMIN, http, j, check, report, gatewayAlive, login } from './w4-common.mjs';
//
// 约定:
//   - BASE 默认 http://localhost:8080 (ECOS_BASE 可覆盖)
//   - admin / admin123 (seed.sql 默认凭据)
//   - 所有用例独立可跑: node XX.mjs [exit 0=PASS, 1=FAIL, 77=ENV_BLOCKED]
//   - 不依赖 Playwright, 仅 Node 20+ fetch

export const BASE = process.env.ECOS_BASE || 'http://localhost:8080';
export const ADMIN = {
  username: process.env.ECOS_ADMIN_USER || 'admin',
  password: process.env.ECOS_ADMIN_PASSWORD || 'admin123',
};

let pass = 0, fail = 0, warn = 0;
const RESULTS = [];

export function reset() { pass = 0; fail = 0; warn = 0; RESULTS.length = 0; }

/** 通用 fetch 封装 — 返回 { status, body, raw } */
export async function http(method, path, { token, body, headers: h, timeout = 30000, form } = {}) {
  const url = path.startsWith('http') ? path : `${BASE}${path}`;
  const headers = { ...(h || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) {
    payload = form; // FormData — 不强行 Content-Type
  } else if (body !== undefined) {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(url, {
    method,
    headers,
    body: payload,
    signal: AbortSignal.timeout(timeout),
  });
  let data;
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('application/json')) {
    try { data = await res.json(); } catch { data = { _text: (await res.text().catch(() => '')).slice(0, 500) }; }
  } else {
    const txt = await res.text().catch(() => '');
    data = { _text: txt.slice(0, 1000) };
  }
  return { status: res.status, body: data, headers: res.headers, raw: ct };
}

export const get = (p, o) => http('GET', p, o);
export const post = (p, o) => http('POST', p, o);
export const put = (p, o) => http('PUT', p, o);
export const del = (p, o) => http('DELETE', p, o);

/** 断言: 通过打印 ✅, 失败打印 ❌ 并累加 fail*/
export function check(name, cond, detail) {
  const ok = !!cond;
  if (ok) pass++; else fail++;
  const icon = ok ? '✅' : '❌';
  const tail = detail !== undefined ? `: ${String(detail).slice(0, 250)}` : '';
  console.log(`  ${icon} ${name}${tail}`);
  RESULTS.push({ name, ok, detail });
  return ok;
}

export function note(name, detail) {
  console.log(`  ℹ️  ${name}${detail !== undefined ? `: ${detail}` : ''}`);
}

/** 登录 — 抛 GATEWAY_DOWN 异常 (env blocked) */
export async function login(username = ADMIN.username, password = ADMIN.password) {
  let r;
  try {
    r = await http('POST', '/api/v1/auth/login', {
      body: { username, password },
      timeout: 15000,
    });
  } catch (e) {
    const err = new Error(`GATEWAY_DOWN: ${e.message}`);
    err.name = 'GATEWAY_DOWN';
    throw err;
  }
  if (r.status === 401) throw new Error(`LOGIN_401 user=${username}`);
  if (r.status !== 200 && r.status !== 201) {
    throw new Error(`LOGIN_${r.status}: ${JSON.stringify(r.body).slice(0, 200)}`);
  }
  const data = r.body?.data || r.body;
  const token = data?.accessToken || data?.token || data?.access_token;
  if (!token) throw new Error(`LOGIN_NO_TOKEN: ${JSON.stringify(data).slice(0, 200)}`);
  return {
    token,
    userId: data?.userId ?? data?.userId,
    username: data?.username,
    roles: data?.roles || [],
    tenantId: data?.tenantId || decodeJwt(token)?.tenant_id,
    raw: data,
  };
}

/** 解码 JWT payload 不验签 */
export function decodeJwt(token) {
  try {
    const part = token.split('.')[1];
    const bin = Buffer.from(part, 'base64url').toString('utf8');
    return JSON.parse(bin);
  } catch { return null; }
}

/** 探活 */
export async function gatewayAlive() {
  try {
    const r = await fetch(`${BASE}/api/health`, { signal: AbortSignal.timeout(3000) });
    return r.status === 200;
  } catch { return false; }
}

/** 顶层报告 — 写结果到 /tmp 并判定退出码 */
export function report(label, outPath) {
  const summary = {
    label,
    pass,
    fail,
    warn,
    total: pass + fail,
    passRate: pass + fail > 0 ? (pass / (pass + fail) * 100).toFixed(1) + '%' : 'n/a',
    results: RESULTS,
    verdict: fail === 0 ? 'PASS' : 'FAIL',
  };
  console.log(`\n${'═'.repeat(60)}`);
  console.log(`  📊 ${label}`);
  console.log(`  ✅ PASS ${pass} / ❌ FAIL ${fail} / ⚠️  WARN ${warn}`);
  console.log(`  通过率: ${summary.passRate}`);
  console.log(`  判定: ${summary.verdict}`);
  console.log(`${'═'.repeat(60)}`);
  if (outPath) {
    try {
      import('node:fs').then(fs => fs.writeFileSync(outPath, JSON.stringify(summary, null, 2)));
    } catch {}
  }
  process.exit(fail === 0 ? 0 : 1);
}

/** 预 gate: gateway 不可达 → exit 77 */
export async function gate(label) {
  const up = await gatewayAlive();
  if (!up) {
    console.error(`[ENV_BLOCKED] ${label}: Gateway ${BASE} 不可达 — 先启动 ~/start-gateway.sh`);
    process.exit(77);
  }
}
