import { test } from '@playwright/test';
import { injectAuth, loginAsAdmin } from '../../helpers/ecos';

test('trace marketplace crash', async ({ page, request, context }) => {
  test.setTimeout(120_000);
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  page.on('console', (m) => {
    const t = m.text();
    if (/navConfig/.test(t)) return;
    // eslint-disable-next-line no-console
    console.log(`[${m.type()}] ${t.slice(0, 900)}`);
  });
  page.on('pageerror', (e) => {
    // eslint-disable-next-line no-console
    console.log(`[pageerror] ${String(e).slice(0, 1200)}`);
  });
  await page.goto('/#/marketplace', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(8000);
  const body = await page.evaluate(() => (document.querySelector('main')?.innerText || '').slice(0, 400));
  // eslint-disable-next-line no-console
  console.log(`MAIN: ${body}`);
});
