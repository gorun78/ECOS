import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * 工作台 L3 预测与推演 E2E（F07-06 / F07-12 / F07-22）。
 * 本环境无栈，全 test.skip；解锁需真实 gateway + 前端。
 */
const SCENARIO_ID = process.env.ECOS_E2E_SCENARIO_ID ?? 'p-2027-profit';

test.describe('workbench L3 — 预测与推演', () => {
  test.skip('推演页假设/信念两栏渲染，无 verdict 合成', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto(`/#/workbench/${SCENARIO_ID}/forecast`);
    await expect(page.getByText(/Forecast|推演/i).first()).toBeVisible();
    await expectNoErrorBoundary(page);
  });

  test.skip('引擎不可用 → 503 原样穿透，横幅 + 重试按钮', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.route('**/api/v1/business/scenarios/*/cognition/hypotheses', (r) =>
      r.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'ENGINE_DISABLED', traceId: 't', message: 'engine disabled', data: null }) }),
    );
    await page.goto(`/#/workbench/${SCENARIO_ID}/forecast`);
    await expect(page.getByText(/unavailable|不可用/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Retry|重试/i })).toBeVisible();
  });

  test.skip('边界态：时间窗越界提示，不静默通过', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.route('**/api/v1/workspace/scenarios/*/forecast-scope*', (r) =>
      r.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'BAD_REQUEST', message: 'horizon out of bounds', data: null }) }),
    );
    await page.goto(`/#/workbench/${SCENARIO_ID}/forecast`);
    await expect(page.locator('main').getByText(/adjust|越界|超出/i).first()).toBeVisible();
  });
});
