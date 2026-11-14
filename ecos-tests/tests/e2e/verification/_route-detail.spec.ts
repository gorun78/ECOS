import { test } from '@playwright/test';
import fs from 'node:fs';
import { injectAuth, loginAsAdmin } from '../../helpers/ecos';

test.setTimeout(300_000);

const TARGETS = ['security-center', 'iam', 'guardrails', 'kanban', 'telemetry', 'tokens', 'tenants', 'alerts', 'world_model', 'mission_control', 'monitor', 'ops_dashboard', 'objects', 'marketplace', 'business-workbench', 'engine-data'];

test('detail probe', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  const out: any[] = [];
  for (const t of TARGETS) {
    const errs: string[] = [];
    const bad: string[] = [];
    page.on('console', (m) => {
      if (m.type() === 'error' && !/^Failed to load resource/i.test(m.text())) errs.push(m.text().slice(0, 400));
    });
    page.on('pageerror', (e) => errs.push('pageerror: ' + String(e).slice(0, 400)));
    page.on('response', (r) => {
      if (r.url().includes('/api/') && r.status() >= 400) bad.push(`${r.status()} ${r.url().replace('http://localhost:3000', '')}`);
    });
    await page.goto(`/#/${t}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(5000);
    const mainText = await page.evaluate(() => {
      const m = document.querySelector('main');
      return (m?.innerText || '').replace(/\s+/g, ' ').slice(0, 600);
    }).catch(() => '');
    const bodyFull = await page.evaluate(() => (document.body.innerText || '').replace(/\s+/g, ' ').slice(0, 1500)).catch(() => '');
    out.push({ route: t, mainText, bodyFull, errs: [...new Set(errs)], bad: [...new Set(bad)] });
    console.log(`\n===== ${t} =====\nMAIN: ${mainText.slice(0, 400)}`);
    console.log(`ERRS(${new Set(errs).size}): ${[...new Set(errs)].slice(0, 3).join(' || ').slice(0, 500)}`);
    console.log(`BAD: ${[...new Set(bad)].join(', ')}`);
    page.removeAllListeners();
  }
  fs.writeFileSync('.tmp-route-detail.json', JSON.stringify(out, null, 2), 'utf8');
});
