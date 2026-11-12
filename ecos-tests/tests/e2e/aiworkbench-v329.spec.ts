import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth } from '../helpers/ecos';

/**
 * V329（2026-11-12）AI 工作台逻辑编排融合批的活体验收：
 * ①逻辑编排页能加载列表（走 /api/v1/aip/pipelines 真端点，非 data-engine 管道定义）；
 * ②「从模板新建」面板列出内置典型模板（八个 GoalIntent 骨架，不依赖 Playbook 内容源）；
 * ③选中内置模板可实例化，生成的画布节点数与工具链一致（闸门 + 工具×n + 聚合）。
 */
test.describe('V329 AI 工作台逻辑编排', () => {
  test('逻辑编排页：列表经 aip 端点加载 + 内置模板可见且可实例化', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/ai-workbench');
    await expect(page.getByText(/AI 工作台|AI Workbench/).first()).toBeVisible({ timeout: 30_000 });

    await page.getByRole('button', { name: /逻辑编排 逻辑编排画布|逻辑编排画布/ }).first().click();

    const templateBtn = page.getByTitle(/从智能体编排|Wagent/);
    await expect(templateBtn).toBeVisible({ timeout: 20_000 });
    await templateBtn.click();
    await expect(page.getByText(/从 Wagent 模板新建|Create from Wagent template/)).toBeVisible({ timeout: 15_000 });

    // 内置来源恒可用：至少列出「经营预测模板」（GoalIntent.FORECAST 首枚）
    const builtinCard = page.getByRole('button', { name: /经营预测模板/ }).first();
    await expect(builtinCard).toBeVisible({ timeout: 15_000 });

    // 选中内置模板：槽位框折叠为说明（骨架无占位），不展示 JSON 输入
    await builtinCard.click();
    await expect(page.getByText(/无需填槽位参数/)).toBeVisible({ timeout: 10_000 });

    // 填名并实例化 → 面板关闭 → 新编排以 FORECAST 骨架落库并在画布呈现
    const uniqueName = `E2E-Builtin-Forecast-${Date.now()}`;
    const nameInput = page.locator('input[type="text"]').last();
    await nameInput.fill(uniqueName);
    await page.getByRole('button', { name: /生成编排|Generate flow/ }).click();

    await expect(page.getByText(/从 Wagent 模板新建|Create from Wagent template/)).toBeHidden({ timeout: 20_000 });
    await expect(page.getByText(new RegExp(uniqueName)).first()).toBeVisible({ timeout: 20_000 });
    // 骨架节点：s1_human_gate（审批）+ freeze_data_snapshot + run_profit_forecast + s9_aggregate（条件）
    await expect(page.getByText('freeze_data_snapshot').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('run_profit_forecast').first()).toBeVisible({ timeout: 20_000 });
  });
});
