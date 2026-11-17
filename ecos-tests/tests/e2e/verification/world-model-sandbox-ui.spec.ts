import { expect, test } from '@playwright/test';
import {
  expectNoErrorBoundary,
  expectHealthyPage,
  injectAuth,
  loginAsAdmin,
  watchPageHealth,
} from '../../helpers/ecos';

/**
 * V-COG-092 UI 腿：世界模型「决策模拟」沙箱点推演 → 未实现占位（不是红色故障态）。
 *
 * 判据链（2026-11-16 实测）：
 *   1. 沙箱组件 `ScenarioSandbox` 的推演载荷是 `{id, name, description, assumptions: [...]}`
 *      —— `assumptions` 是**对象数组**。修复前后端桩 `WorldModelController.simulate`
 *      绑定了 `@RequestBody Scenario`（其中 `assumptions: Map<String,Object>`、
 *      `type: ScenarioType`），Jackson 先失败 ⇒ 400 `ECOS-COG-400P` ⇒ 设计要求
 *      「未实现」占位（F05-16 / DG-9）永远出不来，页面渲染成红色故障态。
 *   2. 修复 = 桩 handler 零参（不绑请求体）。本用例是它的**端到端反退化探针**：
 *      断言出现 `cog-stub-placeholder`（info 占位「未实现（占位）」）
 *      且**不出现** `cog-error-state`（红色故障态）。修复前必红。
 *   3. 同时断言请求体真的是 assumptions 数组——否则用例可能打到别的分支而假绿。
 */

/** 推演端点必然 501（显式 stub），属按设计降级面，健康检查须逐 URL 声明。 */
const DECLARED_DEGRADED = [/\/api\/v1\/world-model\/scenarios$/] as const;

test.describe('V-COG-092 世界模型决策模拟沙箱：推演落到未实现占位', () => {
  test('点「调用推演端点」→ 真实载荷 501 → cog-stub-placeholder，且无红色故障态', async ({
    page,
    request,
    context,
  }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, { expectDegraded: DECLARED_DEGRADED });

    // 抓推演请求体，证明本用例走的是含 assumptions 数组的真实前端载荷
    let simBody: Record<string, unknown> | null = null;
    page.on('request', (req) => {
      if (req.method() === 'POST' && /\/api\/v1\/world-model\/scenarios$/.test(req.url())) {
        try {
          simBody = req.postDataJSON() as Record<string, unknown>;
        } catch {
          simBody = null;
        }
      }
    });

    await page.goto('/#/world_model?tab=5');

    // Tab 5 = 决策模拟（platform.wm.tabs.simulation），URL 与选中态一致
    const simTab = page.getByRole('button', { name: /决策模拟|Simulation/ });
    await expect(simTab, 'Tab 5「决策模拟」应处于选中态').toHaveAttribute('aria-current', 'page');

    // 情景清单来自真实读面 /api/v1/ecos/world-model-graph/scenarios
    await expect(page.getByText('Scenario A: 更换供应商').first(), '情景清单必须来自真实读面')
      .toBeVisible({ timeout: 20_000 });

    const simulateBtn = page.getByTestId('sandbox-simulate');
    await expect(simulateBtn, '默认选中首个情景后推演按钮应可用').toBeEnabled();
    await simulateBtn.click();

    // 真实载荷 = assumptions 数组（修复前正是它触发 400）
    await expect.poll(() => simBody !== null, { message: '推演必须真实发出 POST /world-model/scenarios' })
      .toBe(true);
    expect(Array.isArray(simBody!.assumptions), '推演载荷必须携带 assumptions 数组（前端原样形态）')
      .toBe(true);

    // 核心断言：未实现占位（info），不是红色故障态
    const stub = page.getByTestId('cog-stub-placeholder');
    await expect(stub, '501 stub 必须渲染「未实现（占位）」info 占位').toBeVisible({ timeout: 20_000 });
    await expect(stub).toContainText(/未实现|Not implemented/);
    await expect(page.getByTestId('cog-error-state'), '桩面误绑 DTO 会退化成红色故障态（应为 0）')
      .toHaveCount(0);
    await expect(page.getByTestId('sandbox-sim-result'), '未实现时不得渲染任何推演结果').toHaveCount(0);

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
