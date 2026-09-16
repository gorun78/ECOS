/**
 * lineage-smoke.mjs — 血缘地图 E2E 冒烟（浏览器）
 *
 * 验证 2026-09-16 从悬空 stash 回放的血缘地图改动在浏览器里真实可用：
 *   A1 页面未落入 ErrorBoundary / 未卡在 RequireAuth loading
 *   A2 「全链路数据血缘地图」Tab 存在且处于激活态
 *   A3 ?lineageTable=test_table 被透传，单表查询输入框已回填
 *   A4 /api/v1/engine/data/lineage/* 请求成功（无 4xx/5xx）
 *   A5 console 无 error
 *   A6 无意外 4xx/5xx
 *
 * 前置：gateway :8080 UP、前端 dev :3000 UP。
 * 运行（cwd 不限）：
 *   node D:\workspace\javaprojects\ECOS\ecos-tests\lineage-smoke.mjs
 */
import { createRequire } from 'module';
import fs from 'fs';
import os from 'os';
import path from 'path';

// playwright 装在 ecos_frontend/node_modules，脚本自身不在其目录树下 → 用 createRequire 定位
const require = createRequire('file:///D:/workspace/javaprojects/ECOS/ecos_frontend/package.json');
const { chromium } = require('playwright');

const API = 'http://localhost:8080';
const APP = 'http://localhost:3000';
const TARGET_TABLE = 'test_table';
const TAB_LABEL = '全链路数据血缘地图';

const outDir = path.join(os.tmpdir(), 'ecos-lineage-smoke');
fs.mkdirSync(outDir, { recursive: true });

const results = [];
const record = (name, ok, got) => results.push({ name, ok, got });

async function login() {
  const r = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  });
  const j = await r.json().catch(() => ({}));
  return j?.data?.accessToken || j?.data?.token || '';
}

async function main() {
  const token = await login();
  if (!token) {
    console.error('login failed — gateway :8080 是否在跑？');
    process.exit(1);
  }
  console.log('LOGIN ok, token=', token.slice(0, 24) + '...');

  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  await ctx.addInitScript(([tk]) => {
    localStorage.setItem('token', tk);
    localStorage.setItem('username', 'admin');
    localStorage.setItem('roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('ecos_locale', 'zh');
  }, [token]);
  const page = await ctx.newPage();
  page.setDefaultTimeout(20000);

  const consoleErrors = [];
  const apiCalls = [];
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text());
  });
  page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + String(e)));
  page.on('response', (res) => {
    const url = res.url();
    if (url.includes('/api/')) apiCalls.push({ url: url.replace(APP, ''), status: res.status() });
  });

  const target = `${APP}/#/data-workbench?lineageTable=${encodeURIComponent(TARGET_TABLE)}`;
  await page.goto(target, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(6000); // 等 RequireAuth 校验 + 懒加载 chunk + 血缘首拉

  const shot = path.join(outDir, 'lineage.png');
  await page.screenshot({ path: shot, fullPage: true });

  const bodyText = await page.evaluate(() => document.body.innerText || '');

  // ---- A1 未落入 ErrorBoundary / 未卡 loading ----
  const boundaryHit = /Something went wrong|Unexpected Application Error|应用发生错误|useTheme must be used within/i.test(bodyText);
  const renderStalled = !bodyText.includes(TAB_LABEL);
  record('A1 无 ErrorBoundary 且工作台已渲染', !boundaryHit && !renderStalled,
    `boundary=${boundaryHit} hasTabLabel=${!renderStalled} bodyLen=${bodyText.length}`);

  // ---- A2 血缘 Tab 激活 ----
  const tabInfo = await page.evaluate((label) => {
    const btns = Array.from(document.querySelectorAll('button'));
    const hit = btns.find((b) => (b.textContent || '').includes(label));
    if (!hit) return { found: false };
    return {
      found: true,
      active: (hit.className || '').includes('border-l-2'),
      activeCount: btns.filter((b) => (b.className || '').includes('border-l-2')).length,
    };
  }, TAB_LABEL);
  record('A2 血缘 Tab 存在且激活', tabInfo.found === true && tabInfo.active === true,
    JSON.stringify(tabInfo));

  // ---- A3 initialTable 透传回填 ----
  const filled = await page.evaluate((tbl) =>
    Array.from(document.querySelectorAll('input')).some((i) => i.value === tbl), TARGET_TABLE);
  record('A3 单表查询回填 lineagetable', filled === true, `inputValue===${TARGET_TABLE} → ${filled}`);

  // ---- A4 血缘接口无 4xx/5xx ----
  const lineageCalls = apiCalls.filter((c) => c.url.includes('/engine/data/lineage'));
  const lineageOk = lineageCalls.length > 0 && lineageCalls.every((c) => c.status < 400);
  record('A4 血缘接口请求成功', lineageOk, JSON.stringify(lineageCalls));

  // ---- A5 console 无 error ----
  const realErrors = consoleErrors.filter((e) => !/favicon|ResizeObserver|DevTools/i.test(e));
  record('A5 console 无 error', realErrors.length === 0,
    realErrors.length ? realErrors.slice(0, 3).map((e) => e.slice(0, 200)).join(' | ') : `errors=${consoleErrors.length}`);

  // ---- A6 无意外 4xx/5xx ----
  const unexpected = apiCalls.filter((c) => c.status >= 400);
  record('A6 无意外 4xx/5xx', unexpected.length === 0, JSON.stringify(unexpected));

  // ---- 输出 ----
  console.log('\n--- API 调用 ---');
  apiCalls.forEach((c) => console.log(`  ${c.status} ${c.url}`));
  console.log('\n--- 结论 ---');
  let fail = 0;
  for (const r of results) {
    if (!r.ok) fail++;
    console.log(`  ${r.ok ? 'PASS' : 'FAIL'} ${r.name} : ${r.got}`);
  }
  console.log(`\n截图: ${shot}`);
  console.log(fail === 0 ? '\n血缘地图 E2E: PASS' : `\n血缘地图 E2E: FAIL (${fail} 项)`);

  await browser.close();
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => { console.error('smoke-fail:', e); process.exit(1); });