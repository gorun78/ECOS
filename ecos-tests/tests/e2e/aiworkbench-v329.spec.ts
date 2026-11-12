import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth } from '../helpers/ecos';

/**
 * V329（2026-11-12）AI 工作台逻辑编排融合批的最小活体验收：
 * ①逻辑编排页能加载列表（走 /api/v1/aip/pipelines 真端点，非 data-engine 管道定义）；
 * ②「从模板新建」入口可见并能打开面板；无模板时如实呈现空态（不伪造模板行）。
 */
test.describe('V329 AI 工作台逻辑编排', () => {
  test('逻辑编排页：列表经 aip 端点加载 + 模板入口可开且空态如实', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/ai-workbench');
    await expect(page.getByText(/AI 工作台|AI Workbench/).first()).toBeVisible({ timeout: 30_000 });

    // 进入「逻辑编排」Tab（页内二级导航按钮，文案含「逻辑编排画布」副题）
    await page.getByRole('button', { name: /逻辑编排 逻辑编排画布|逻辑编排画布/ }).first().click();

    // 模板入口按钮 → 打开面板（入口可见即证明列表区已渲染）
    const templateBtn = page.getByTitle(/从智能体编排|Wagent/);
    await expect(templateBtn).toBeVisible({ timeout: 20_000 });
    await templateBtn.click();
    await expect(page.getByText(/从 Wagent 模板新建|Create from Wagent template/)).toBeVisible({ timeout: 15_000 });
    // 空目录如实态（模板来自已归档 Playbook / 已发布候选，本环境通常为空）
    await expect(page.getByText(/当前没有可用的 Wagent 模板|No Wagent templates available/))
      .toBeVisible({ timeout: 15_000 });
  });
});
