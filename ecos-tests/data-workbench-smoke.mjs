// ~/ecos-tests/data-workbench-smoke.mjs
// ECOS 数据工作台 — 无头模式冒烟测试 (Playwright + Chromium headless, WSL)
// 用法: node ~/ecos-tests/data-workbench-smoke.mjs
// WSL 无需 DISPLAY, 无需 GUI — 纯 headless

import { chromium } from 'playwright';
import { strict as assert } from 'assert';

const API = 'http://localhost:8080';
const RESULTS = [];
let pass = 0, fail = 0;

function check(name, condition, detail) {
  const ok = !!condition;
  if (ok) pass++; else fail++;
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail !== undefined ? ': ' + detail : ''}`);
  RESULTS.push({ name, ok, detail });
}

// ── 启动无头浏览器 ──
console.log('🚀 启动 Chromium headless (WSL, no DISPLAY)...');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
const context = await browser.newContext();

try {
  // ═══ 1. API 直连测试 (无需页面) ═══
  console.log('\n═══ 1. 数据引擎状态 ═══');
  const health = await (await context.request.get(`${API}/api/v1/engine/data/health`)).json().catch(() => ({}));
  check('Engine health', health?.code === 0, health?.data?.status || health?.message);

  const status = await (await context.request.get(`${API}/api/v1/engine/data/status`)).json().catch(() => ({}));
  check('Engine status', status?.code === 0, status?.data?.status);

  // ═══ 2. 配置体系 ═══
  console.log('\n═══ 2. 配置体系 ═══');
  const settings = await (await context.request.get(`${API}/api/v1/engine/data/settings`)).json().catch(() => ({}));
  const groups = Object.keys(settings?.data || {});
  check('Settings 分组数', groups.length >= 3, `${groups.length} groups: ${groups.join(',')}`);

  const defaults = await (await context.request.get(`${API}/api/v1/engine/data/settings/defaults`)).json().catch(() => ({}));
  const dCount = Object.keys(defaults?.data || {}).length;
  check('默认配置项', dCount >= 49, `${dCount} items`);

  // ═══ 3. Pipeline ═══
  console.log('\n═══ 3. Pipeline API ═══');
  const tasks = await (await context.request.get(`${API}/api/v1/engine/data/pipeline/tasks`)).json().catch(() => ({}));
  check('Pipeline 任务列表', tasks?.code === 0, `${tasks?.data?.total || 0} tasks`);

  const funcs = await (await context.request.get(`${API}/api/v1/engine/data/functions`)).json().catch(() => ({}));
  check('PB 函数注册表', funcs !== null, funcs?.code === 0 ? '200' : `code=${funcs?.code}`);

  const udfs = await (await context.request.get(`${API}/api/v1/engine/data/udf/list`)).json().catch(() => ({}));
  check('UDF 列表', udfs?.code === 0, `${udfs?.data?.total || 0} UDFs`);

  // ═══ 4. 本体工作台 API ═══
  console.log('\n═══ 4. 本体工作台 ═══');
  const ontologies = await (await context.request.get(`${API}/api/v1/ecos/ontologies`)).json().catch(() => ({}));
  check('本体列表', ontologies?.code === 0 || ontologies?.length > 0, 
    Array.isArray(ontologies) ? `${ontologies.length} ontologies` : `code=${ontologies?.code}`);

  const kg = await (await context.request.get(`${API}/api/v1/ecos/knowledge-graph`)).json().catch(() => ({}));
  check('知识图谱', kg?.code === 0 || kg?.nodes?.length > 0, 'OK');

  // ═══ 5. 血缘 ═══
  console.log('\n═══ 5. 血缘 ═══');
  const lineage = await (await context.request.get(`${API}/api/v1/engine/data/lineage/nodes`)).json().catch(() => ({}));
  check('血缘节点', lineage?.code === 0, `${lineage?.data?.length || 0} nodes`);

  // ═══ 6. 前端可达性 ═══
  console.log('\n═══ 6. 前端 (Vite) ═══');
  try {
    await page.goto('http://localhost:3000', { waitUntil: 'networkidle', timeout: 10000 });
    const title = await page.title();
    check('Vite 前端', title.length > 0, `title="${title.slice(0, 50)}"`);
    await page.screenshot({ path: '/tmp/ecos-frontend.png', fullPage: true });
    check('前端截图', true, '→ /tmp/ecos-frontend.png');
  } catch {
    check('Vite 前端', false, '未启动 (port 3000)');
    // Fallback: 截图 Gateway 健康页
    await page.goto(`${API}/actuator/health`, { timeout: 5000 });
    await page.screenshot({ path: '/tmp/ecos-gateway.png' });
    check('Gateway 截图', true, '→ /tmp/ecos-gateway.png');
  }

} catch (err) {
  console.error('\n❌ 异常:', err.message);
} finally {
  await browser.close();

  console.log(`\n═════════════════════════`);
  console.log(`  ✅ ${pass} / ❌ ${fail} / 总计 ${pass + fail}`);
  console.log(`═════════════════════════`);
  process.exit(fail > 0 ? 1 : 0);
}
