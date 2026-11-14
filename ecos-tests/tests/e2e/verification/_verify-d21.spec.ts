import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin } from '../../helpers/ecos';

test('D21: /iam 与 /security-center 不再整页崩', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  for (const route of ['iam', 'security-center']) {
    const errs: string[] = [];
    page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 200)); });
    await page.goto(`/#/${route}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(6000);
    const main = await page.evaluate(() => (document.querySelector('main')?.innerText || '').replace(/\s+/g, ' ').slice(0, 260));
    console.log(`[${route}] MAIN=${main}`);
    console.log(`[${route}] HOOK_ERR=${errs.filter((e) => /Rendered more hooks|Rules of Hooks/i.test(e)).length}`);
    expect(main, `${route} 落入 ErrorBoundary`).not.toContain('页面渲染出错');
    await expectNoErrorBoundary(page);
    page.removeAllListeners();
  }
});
