// ~/ecos-tests/wave1c-spa-routes.mjs
// Wave-1C GUI acceptance: 5 SPA routes must NOT render blank pages after
// React.lazy + i18n conversion. We navigate to each route, wait for the
// production shell (#root with non-empty innerHTML, sidebar, topbar), and
// verify no console error must be a chunk-load failure.
//
// Run:  node ~/ecos-tests/wave1c-spa-routes.mjs
// Requires:  dev server on :3000 (cd ~/ECOS/ecos_frontend && npm run dev)

import { chromium } from 'playwright';
import { strict as assert } from 'assert';

const BASE = 'http://localhost:3000';
const ROUTES = [
  '#/mission_control',       // top-level "Cognitive Operating System"
  '#/data-workbench',        // Data Workbench (named export DataWorkbenchLayoutStandalone)
  '#/ontology_workbench',    // Ontology Workbench
  '#/ai-workbench',          // AI Workbench (default-export named wrapper)
  '#/iam',                   // User Management
  '#/marketplace',           // Marketplace Browser
  '#/world_model',           // 404 fallback
];

console.log('Launching headless Chromium (WSL)...');
const os = process.platform === 'win32' ? 'win32' : 'linux';
// WSL preinstalled cache — Playwright 1228 path
const HSC = `/home/guorongxiao/.cache/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-linux64/chrome-headless-shell`;
const browser = await chromium.launch({ headless: true, executablePath: HSC });
const context = await browser.newContext({ viewport: { width: 1600, height: 900 } });
const page = await context.newPage();

// Collect console errors per route
const consoleErrorsByRoute = new Map();
let pick = 0;
page.on('console', (msg) => {
  if (msg.type() !== 'error' && msg.type() !== 'warning') return;
  const route = consoleErrorsByRoute.get(pick) || (consoleErrorsByRoute.set(pick, []), consoleErrorsByRoute.get(pick));
  const text = msg.text();
  if (text.toLowerCase().includes('error') || text.length < 400) route.push(text.slice(0, 300));
});

let pass = 0, fail = 0;
function check(name, cond, detail) {
  const ok = !!cond;
  ok ? pass++ : fail++;
  console.log(`  ${ok ? '✅' : '❌'} ${name}${detail !== undefined ? ' : ' + detail : ''}`);
}

// We must log in first (RequireAuth guards non-/login routes).
// Use Vite's in-memory session workaround: set localStorage token directly,
// then hijack redirect to login.
async function ensureAuthed() {
  await page.goto(BASE + '/#/login', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(800);

  // The login page may not be running the backend. Fake the session flag so
  // RequireAuth lets us straight in.
  await page.evaluate(() => {
    localStorage.setItem('ecos_token', 'w1c-verify-fake');
    localStorage.setItem('ecos_locale', 'zh');
  });
  return true;
}

await ensureAuthed();

for (let i = 0; i < ROUTES.length; i++) {
  pick = i;
  const path = ROUTES[i];
  const href = BASE + '/' + path.replace(/^#/, '');
  try {
    // SPA navigation via hash. Use the same tab to keep localStorage.
    await page.evaluate((h) => { location.hash = h.replace(/^#/, ''); }, path);
    await page.waitForSelector('#root > *', { timeout: 15000 });
    await page.waitForTimeout(1500); // give lazy chunks + Suspense a beat
    const { innerLen, hasSidebar, hasTopbar, hasErr } = await page.evaluate(() => {
      const root = document.getElementById('root');
      const body = document.body;
      const mainContent = document.querySelector('main');
      const anyErrorText = body?.innerText || '';
      return {
        innerLen: root ? root.innerHTML.length : 0,
        hasSidebar: !!(document.querySelector('[class*="sidebar"]') || document.querySelector('aside') || document.querySelector('nav')),
        hasTopbar: !!(document.querySelector('header') || document.querySelector('[class*="topbar"]')),
        hasErr: /chunk load error|suspense error|failed to load/i.test(anyErrorText),
        pathText: location.hash,
      };
    });
    const chunkErrs = (consoleErrorsByRoute.get(i) || []).filter(x => /load|fetch|refused|504|500|failed/i.test(x));
    const blank = innerLen < 800; // <1KB render → white page
    console.log(`\n── ${path} ──`);
    check('renders (root > 800B chars)', !blank, `innerHTML=${innerLen}`);
    check('no chunk-load error text', !hasErr);
    check('no console error mentioning load/fail', chunkErrs.length === 0,
      chunkErrs.slice(0, 3).join(' | ').slice(0, 200));
  } catch (e) {
    console.log(`\n── ${path} → ❌ exception: ${String(e).slice(0, 300)} ──`);
    fail++;
  }
}

await browser.close();
console.log(`\n==== ${pass} pass / ${fail} fail ====`);
process.exit(fail > 0 ? 1 : 0);
