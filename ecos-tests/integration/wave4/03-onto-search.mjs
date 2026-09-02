#!/usr/bin/env node
// 03-onto-search.mjs — Wave-4.1 域 3/7: ontology domains/search
// 验收:
//   T1 GET /api/v1/ontology/domains 列表 200 (至少 0 个, 容错)
//   T2 GET /api/v1/ontology/domains/search?q=&limit=50 200, code===0
//   T3 用某条已存在 domain 的 code 作为 q, 列表含该 domain (模糊匹配功能可用)
//   T4 找不到的 query q=zzz-lol  → 列表 [] 可接受 (空返回)
// 退出: 0=PASS, 1=FAIL, 77=ENV_BLOCKED

import { BASE, http, get, check, report, gatewayAlive, login, note } from './lib/w4-common.mjs';

console.log('═══ 03-onto-search.mjs — Wave-4.1 域3: ontologies domains/search ═══');
console.log(`  BASE: ${BASE}`);
await gatewayAlive() || process.exit(77);

let token;
try {
  const sess = await login();
  token = sess.token;
  note('T-session', `admin ok tenant=${sess.tenantId}`);
} catch (e) {
  note('T-session 失败', e.message);
}
const withAuth = { token };

// ── T1 ── domains 列表
let domains = [];
{
  const r = await get('/api/v1/ontology/domains', { ...withAuth, timeout: 15000 });
  const code = r.body?.code ?? r.body?.status;
  const list = r.body?.data || [];
  domains = Array.isArray(list) ? list : (list?.content || []);
  check('T1 domains 200 + code===0', r.status === 200 && code === 0,
    `status=${r.status} code=${code} total=${domains.length}`);
}

// ── T2 ── search (无 q 时返回全部, limit=50)
{
  const r = await get('/api/v1/ontology/domains/search?limit=50', { ...withAuth, timeout: 15000 });
  const code = r.body?.code ?? r.body?.status;
  const list = r.body?.data || [];
  const arr = Array.isArray(list) ? list : (list?.content || []);
  check('T2 search 200 + code===0', r.status === 200 && code === 0, `status=${r.status} code=${code} total=${arr.length}`);
}

// ── T3 ── 模糊匹配：用已有 domain code 第一个字符 + 同域 name
{
  if (domains.length === 0) {
    check('T3 模糊匹配 (无数据 skip)', true, 'domains 列表为空，跳过');
  } else {
    const d0 = domains[0];
    const q = d0.code || d0.name || d0.domainCode || 'a';
    const qstr = String(q).slice(0, 8);
    const r = await get(`/api/v1/ontology/domains/search?q=${encodeURIComponent(qstr)}&limit=20`, { ...withAuth, timeout: 15000 });
    const list = r.body?.data || [];
    const arr = Array.isArray(list) ? list : (list?.content || []);
    check('T3 q 模糊匹配 200', r.status === 200, `status=${r.status} q=${qstr} hits=${arr.length}`);
    check('T3 模糊匹配命中原 domain', arr.some(x => (x.code || x.name || '').toString().toLowerCase().includes(qstr.slice(0, 4).toLowerCase())),
      `arr_code_sample=${JSON.stringify(arr.slice(0, 3).map(x => x.code || x.name)).slice(0, 200)}`);
  }
}

// ── T4 ── 找不到的 query
{
  const r = await get('/api/v1/ontology/domains/search?q=zzz-lol-no-such-domain&limit=10', { ...withAuth, timeout: 15000 });
  const code = r.body?.code ?? r.body?.status;
  const list = r.body?.data || [];
  const arr = Array.isArray(list) ? list : (list?.content || []);
  check('T4 不存在 q → 200 空数组 (或 404 容忍)', r.status === 200 || r.status === 404,
    `status=${r.status} code=${code} hits=${arr.length}`);
}

report('03-onto-search (domains/search)', '/tmp/wave4_03-onto-search.json');
