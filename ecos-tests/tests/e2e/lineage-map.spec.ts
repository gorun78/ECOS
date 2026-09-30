import { expect, test } from '@playwright/test';
import { expectHealthyPage, expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 迁移自 ecos-tests/lineage-smoke.mjs（断言 A1–A6 逐条对应）。
 * 覆盖铁律 V4 三项：渲染无 ErrorBoundary / console 无 error / network 无意外 4xx·5xx。
 * 前置：gateway :8080 + 前端 :3000（或 BFF）已启动。
 */

const TAB_LABEL = '数据血缘';
const TARGET_TABLE = 'test_table';

test('血缘地图页可用：A1–A6', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  watchPageHealth(page);

  const lineageResponse = page
    .waitForResponse((res) => res.url().includes('/engine/data/lineage'), { timeout: 30_000 })
    .catch(() => null);

  await page.goto(`/#/data-workbench?lineageTable=${encodeURIComponent(TARGET_TABLE)}`, { waitUntil: 'domcontentloaded' });

  const tab = page.locator('button').filter({ hasText: TAB_LABEL }).first();
  await expect(tab, '工作台未渲染或未出现「数据血缘」Tab').toBeVisible({ timeout: 30_000 });
  await lineageResponse;

  await test.step('A1 页面未落入 ErrorBoundary 且工作台已渲染', async () => {
    await expectNoErrorBoundary(page);
    await expect(page.locator('body')).toContainText(TAB_LABEL);
  });

  await test.step('A2 血缘 Tab 存在且处于激活态', async () => {
    await expect(tab, '激活 Tab 应带 border-l-2 标记').toHaveClass(/border-l-2/);
  });

  await test.step('A3 ?lineageTable 透传并回填单表查询输入框', async () => {
    const filled = await page.evaluate((tbl) =>
      Array.from(document.querySelectorAll('input')).some((i) => i.value === tbl), TARGET_TABLE);
    expect(filled, '单表查询输入框未回填 lineageTable 查询串').toBe(true);
  });

  await test.step('A4 页面发起的血缘接口请求成功（无 4xx/5xx）', async () => {
    const res = await lineageResponse;
    expect(res, '页面未发起 /engine/data/lineage 请求').not.toBeNull();
    expect(res!.status(), `血缘接口 HTTP ${res!.status()}`).toBeLessThan(400);
  });

  await test.step('A5 + A6 console 无 error、无意外 4xx/5xx（铁律 V4）', async () => {
    expectHealthyPage(page);
  });

  await page.screenshot({ path: test.info().outputPath('lineage.png'), fullPage: true });
});
