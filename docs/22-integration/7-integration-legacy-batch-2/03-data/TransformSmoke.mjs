#!/usr/bin/env node
/**
 * Wave-2B ge (D→I) 集成 smoke — 验 TransformController 端点
 *
 * 对应：/home/guorongxiao/ECOS/docs/3-data/02-1-Wave2B-ge-D到I-收口.md T5
 *
 * 端点：
 * 1. GET  /api/v1/engine/data/transform/meta     — 6 类步骤清单
 * 2. POST /api/v1/engine/data/transform/execute  — 合法 cleansing+mapping 链路
 * 3. POST /api/v1/engine/data/transform/execute  — 未知 step type 应 400
 *
 * 运行前提：
 *   - Gateway 8080 (WSL `~/start-gateway.sh` enterprise profile)
 *   - Node 20+ (fetch 无 Playwright)
 *   - PG 16 可连接（Gateway 默认连 127.0.0.1:5432，postgres/postgres）
 *
 * 凭据：admin / admin123 (seed.sql 默认)
 * 用法：node TransformSmoke.mjs
 *
 * 退出码：
 *   0 = 全部 PASS
 *   1 = 任一 FAIL
 *
 * @author ECOS Wave-2B ge D→I 收口
 */

const BASE = 'http://localhost:8080';
const USER = 'admin';
const PASS = 'admin123';

let passed = 0;
let failed = 0;
let skipped = 0;
const results = [];

function record(name, ok, detail) {
  const status = ok ? 'PASS' : 'FAIL';
  if (ok) passed++; else failed++;
  results.push({ name, status, detail });
  console.log(`[${status}] ${name}${detail ? ` — ${detail}` : ''}`);
}

function recordSkip(name, reason) {
  skipped++;
  results.push({ name, status: 'SKIP', detail: reason });
  console.log(`[SKIP] ${name} — ${reason}`);
}

/**
 * 登录 — 走 /api/v1/auth/login（或 /auth/login，看核心看哪个能通）
 */
async function login() {
  const paths = ['/api/v1/auth/login', '/auth/login'];
  for (const p of paths) {
    try {
      const r = await fetch(BASE + p, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: USER, password: PASS }),
      });
      if (r.status === 200) {
        const j = await r.json();
        const token = j?.data?.accessToken || j?.data?.token || j?.accessToken;
        if (token) return { token, path: p };
      }
    } catch (e) {
      // 试下一个
    }
  }
  throw new Error('登录失败 — Gateway 未启或凭据不对: ' + USER + '/' + PASS);
}

/**
 * 带 token 调接口
 */
async function call(token, method, path, body) {
  const headers = { Authorization: `Bearer ${token}` };
  if (body) headers['Content-Type'] = 'application/json';
  const r = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  let j = null;
  try { j = await r.json(); } catch (_) { /* ignore */ }
  return { status: r.status, json: j };
}

function main() {
  console.log('────────────────────────────────────────────');
  console.log('Wave-2B ge (D→I) TransformController 集成 Smoke');
  console.log(`Target: ${BASE}`);
  console.log('────────────────────────────────────────────');

  // ── 1) 登录 ──
  let token;
  try {
    const { token: tk, path } = await login();
    token = tk;
    console.log(`[INFO] 登录成功 via ${path}`);
  } catch (e) {
    console.warn('[WARN] 登录失败 — 标记 3 项 SKIP 并退出');
    recordSkip('GET /transform/meta', '登录失败，无法发请求: ' + e.message);
    recordSkip('POST /transform/execute (valid)', '登录失败');
    recordSkip('POST /transform/execute (unknown type)', '登录失败');
    console.log('──── FAIL:' + failed + ' SKIP:' + skipped + ' ────');
    process.exit(1);
  }

  // ── 2) GET /transform/meta — 6 类步骤清单 ──
  try {
    const { status, json } = await call(token, 'GET', '/api/v1/engine/data/transform/meta');
    const data = json?.data;
    const ok = status === 200 &&
      json?.code === 0 &&
      Array.isArray(data?.availableSteps) &&
      data.availableSteps.length === 6 &&
      data.totalSteps === 6;
    record('GET /transform/meta (6 steps)',
      ok, `status=${status} code=${json?.code} totalSteps=${data?.totalSteps}`);
    if (!ok) {
      console.log('  ↳ 实际响应:', JSON.stringify(json).slice(0, 400));
    }
  } catch (e) {
    record('GET /transform/meta (6 steps)', false, '网络/resource 不可达: ' + e.message);
  }

  // ── 3) POST /transform/execute 合法链路：cleansing + mapping ──
  try {
    const payload = {
      input: {
        columns: ['raw_name', 'raw_age'],
        rows: [
          { raw_name: '  张三  ', raw_age: 30 },
          { raw_name: '李四', raw_age: 25 },
        ],
      },
      chain: [
        { type: 'cleansing', params: { trimWhitespace: true } },
        { type: 'mapping', params: { mapping: { raw_name: 'name', raw_age: 'age' }, keepUnmapped: true } },
      ],
    };
    const { status, json } = await call(token, 'POST', '/api/v1/engine/data/transform/execute', payload);
    const data = json?.data;
    const rows = data?.output?.rows || [];
    const row0 = rows[0] || {};
    const ok = status === 200 &&
      json?.code === 0 &&
      data?.success === true &&
      typeof row0.name === 'string' &&
      row0.name === '张三';
    record('POST /transform/execute (cleansing+mapping)',
      ok, `status=${status} code=${json?.code} success=${data?.success} row0.name=${JSON.stringify(row0.name)}`);
    if (!ok) {
      console.log('  ↳ 实际响应:', JSON.stringify(json).slice(0, 600));
    }
  } catch (e) {
    record('POST /transform/execute (cleansing+mapping)', false, e.message);
  }

  // ── 4) POST /transform/execute 未知 step type 应 400 ──
  try {
    const payload = {
      input: { columns: ['a'], rows: [] },
      chain: [{ type: 'notExist' }],
    };
    const { status, json } = await call(token, 'POST', '/api/v1/engine/data/transform/execute', payload);
    const ok = status === 200 && json?.code === ApiResponse_BAD_REQUEST_CODE() &&
      typeof json?.message === 'string' && json.message.includes('notExist');
    record('POST /transform/execute (unknown type → 400)',
      ok, `status=${status} code=${json?.code} message=${json?.message}`);
    if (!ok) {
      console.log('  ↳ 实际响应:', JSON.stringify(json).slice(0, 400));
    }
  } catch (e) {
    record('POST /transform/execute (unknown type → 400)', false, e.message);
  }

  // ── 5) 汇总 ──
  console.log('────────────────────────────────────────────');
  const total = passed + failed;
  console.log(`合计: ${total} 项 — PASS=${passed} FAIL=${failed} SKIP=${skipped}`);
  const allPass = failed === 0 && passed + skipped >= 3;
  console.log(`判定: ${allPass ? '✅ GO' : '❌ NO-GO'}`);
  console.log('────────────────────────────────────────────');
  process.exit(allPass ? 0 : 1);
}

function ApiResponse_BAD_REQUEST_CODE() {
  return 400;
}

main().catch((e) => {
  console.error('[FATAL]', e);
  process.exit(2);
});
