import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * 工作台 L2 场景详情 E2E（F07-06 / F07-12 / F07-22）。
 * 本环境无栈，全 test.skip；解锁需真实 gateway + 前端。
 */
const SCENARIO_ID = process.env.ECOS_E2E_SCENARIO_ID ?? 'p-2027-profit';

test.describe('workbench L2 — 场景详情', () => {
  test.skip('关键指标卡 + 认知摘要 + 最近动作三段渲染', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto(`/#/workbench/${SCENARIO_ID}`);
    await expect(page.getByText(/Key Metrics|关键指标/i)).toBeVisible();
    await expectNoErrorBoundary(page);
  });

  test.skip('未找到（404）：无效 scenarioId → 显示空态而非崩溃', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.goto('/#/workbench/does-not-exist');
    await expect(page.getByText(/not found|未找到/i)).toBeVisible();
  });

  test.skip('认知摘要 503 → 只渲染横幅 + traceId，绝不合成 verdict', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.route('**/api/v1/business/scenarios/*/cognition/operation-eval', (r) =>
      r.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'ENGINE_DISABLED', traceId: 'test-503', message: 'cognitive engine not enabled', data: null }) }),
    );
    await page.goto(`/#/workbench/${SCENARIO_ID}`);
    await expect(page.getByText(/cognitive engine|engine unavailable|认知引擎/i)).toBeVisible();
    await expect(page.locator('main').getByText('GRANTED')).toHaveCount(0);
    await expect(page.locator('main').getByText('DENIED')).toHaveCount(0);
  });
});
