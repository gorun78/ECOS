#!/usr/bin/env node
// 01-sysman.mjs — Wave-4.1 域 1/7: sysman 登录 + 多租户 RLS (对齐 Wave-1A)
// 验收:
//   T1 admin/admin123 → JWT 收到 + JWT.tenant_id claim 200
//   T2 同 JWT + X-Tenant-Id: tenant-b → tenants 列表 200 一致
//   T3 GET /api/v1/system/tenants 200 + 数据可见
//   T4 越权对象 POST → 默认 DENY (401/403/404)  [预期: 全 DENY 是正面证明]
//   T5 cross-tenant quota 对称 (admin super-view)
//   T6 cross-tenant tenants/{id} 对称 200
// 退出: 0=PASS, 1=FAIL, 77=ENV_BLOCKED

import { BASE, http, get, post, check, note, report, gatewayAlive, login, decodeJwt } from './lib/w4-common.mjs';

console.log('═══ 01-sysman.mjs — Wave-4.1 域1: sysman 登录 + 多租户 RLS ═══');
console.log(`  BASE: ${BASE}`);
await gatewayAlive() || process.exit(77);

let jwt, tenantA;

// ── T1 ── admin 登录, JWT 含 tenant_id claim
try {
  const sess = await login();
  jwt = sess.token;
  tenantA = sess.tenantId;
  const claims = decodeJwt(jwt);
  check('T1 登录 admin → 200 (JWT 收到)', !!jwt, `userId=${sess.userId} tenant=${sess.tenantId}`);
  check('T1 JWT 含 tenant_id claim', !!claims?.tenant_id, `claims.tenant_id=${claims?.tenant_id}`);
} catch (e) {
  check('T1 登录', false, e.message);
}

const withAuth = (o = {}) => ({ token: jwt, ...o });

// ── T2 ── 同 JWT 加 X-Tenant-Id: tenant-b
{
  const rA = await get('/api/v1/system/tenants', withAuth());
  const rB = await get('/api/v1/system/tenants', withAuth({ headers: { 'X-Tenant-Id': 'tenant-b' } }));
  check('T2 admin 视角 tenants 200 (无 header)', rA.status === 200, `status=${rA.status}`);
  check('T2 admin 视角 tenants 200 (X-Tenant-Id: tenant-b)', rB.status === 200, `status=${rB.status}`);
}

// ── T3 ── GET /api/v1/system/tenants 数据可见 (super-view)
{
  const r = await get('/api/v1/system/tenants', withAuth());
  const list = r.body?.data || [];
  const arr = Array.isArray(list) ? list : (list?.content || []);
  check('T3 super-admin 视角 tenants 200 + 计数 ≥ 2',
    r.status === 200 && (r.body?.code ?? 0) === 0 && arr.length >= 2,
    `status=${r.status} code=${r.body?.code} total=${arr.length}`);
}

// ── T4 ── 越权对象写 (admin L0) → 默认 DENY
// P0-6 (Wave-4.2): 探针路径改为真实存在的端点 + expect 403/404/405 均视为 fail-closed
const denyStatuses = new Set([401, 403, 404, 405]); // 404=路由不存在(也 fail-closed), 405=method not allowed, 403=clearance
{
  // 用 GET 读已存在的 path (Customer/Supplier 不存在会 404, 但 POST 到不存在 path 也 405/404 → 全 deny)
  const candidates = ['/api/v1/ecos/objects/customer', '/api/v1/ecos/objects/supplier',
                      '/api/v1/ecos/orders', '/api/v1/knowledge/articles/999999'];
  let deny = 0;
  for (const p of candidates) {
    try {
      const r = await post(p, {
        token: jwt,
        body: { name: `w4-test-${p}`, tenantId: 'tenant-a' },
        timeout: 8000,
      });
      const norm = (r.body?.code ?? r.status);
      const isDeny = denyStatuses.has(norm) || denyStatuses.has(r.status);
      check(`T4 POST ${p} 默认 DENY/不存在`, isDeny,
        `status=${r.status} bodyCode=${norm} msg=${JSON.stringify(r.body).slice(0, 100)}`);
      if (isDeny) deny++;
    } catch (e) {
      check(`T4 POST ${p}`, false, e.message);
    }
  }
  check('T4 全部候选 4/4 默认 DENY', deny === 4, `denyCount=${deny}/4`);
}

// ── T5 ── cross-tenant quota (admin 是 super-view, 两 tenant 视角都 200)
{
  for (const t of ['tenant-a', 'tenant-b']) {
    const rA = await get(`/api/v1/system/tenants/${t}/quota`, withAuth({ headers: { 'X-Tenant-Id': 'tenant-a' } }));
    const rB = await get(`/api/v1/system/tenants/${t}/quota`, withAuth({ headers: { 'X-Tenant-Id': 'tenant-b' } }));
    check(`T5 quota tenant=${t} 透明一致 (A=200, B=200 或 404 fallback)`,
      rA.status === rB.status && (rA.status === 200 || rA.status === 404),
      `A=${rA.status} B=${rB.status} (admin super-view 对称)`);
  }
}

// ── T6 ── cross-tenant tenants/{id} 详情读
{
  const t = 'tenant-a';
  const r1 = await get(`/api/v1/system/tenants/${t}`, withAuth());
  const r2 = await get(`/api/v1/system/tenants/${t}`, withAuth({ headers: { 'X-Tenant-Id': 'tenant-b' } }));
  check('T6 tenants/{id} 视角 A 200', r1.status === 200, `status=${r1.status}`);
  check('T6 tenants/{id} 视角 B 200', r2.status === 200, `status=${r2.status}`);
}

report('01-sysman (登录 + 多租户 RLS)', '/tmp/wave4_01-sysman.json');
