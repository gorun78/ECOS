import { test } from '@playwright/test';
import { injectAuth, loginAsAdmin } from '../../helpers/ecos';

test('trace business-workbench requests', async ({ page, request, context }) => {
  test.setTimeout(180_000);
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  page.on('request', (r) => {
    const u = r.url();
    if (u.includes('/api/')) {
      // eslint-disable-next-line no-console
      console.log(`REQ ${r.method()} ${u.replace('http://localhost:3000', '')}`);
    }
  });
  await page.goto('/#/business-workbench', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(9000);
});
