import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * V-DATA-009（数据看板）的**空态/错误态**活体验证（2026-11-16）。
 *
 * 矩阵缺口原文：「4 看板数据源已逐条探明，2 条被 D33 阻塞 … 『空态/错误态』渲染仍属 UI 交互面未验」。
 *
 * 取证范围（D33 已登记 biz_dashboard/ops_dashboard 后端面 404，不在本条重复）：
 *   本 spec 只验**数据源可用**的那两条看板之代表 —— 数据质量看板 `#/dq_dashboard` 的
 *   维度 Tab（数据源 `GET /api/v1/dq/rules/dimension-registry` 实测 200，6 维度真实返回）。
 *
 * 判定：
 *   ①真实 200 ⇒ 六维度注册表渲染（非空、无错误占位）；
 *   ②拦截 500 ⇒ 页面渲染「规则加载失败」错误消息（`dw.dqRule.loadFailed`）+ 维度空占位，
 *     整页不进 ErrorBoundary（失败可见、不崩、不伪装成功）。
 */

test.describe.configure({ timeout: 90_000 });

const DIM_REGISTRY = '**/api/v1/dq/rules/dimension-registry**';

test.describe('V-DATA-009 数据质量看板 空态/错误态', () => {
  test('真实 200 ⇒ 六维度注册表渲染（数据源真实，非空）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/dq_dashboard?tab=dimension');

    // 维度 Tab 默认可能不是首 Tab，点一次「6 维度」确保落位（多尺寸 DOM，只取可见）
    const dimTab = page.locator('button:visible', { hasText: /6\s*维度|维度/ }).first();
    if (await dimTab.isVisible().catch(() => false)) {
      await dimTab.click();
    }

    // 权重雷达标题出现 ⇒ 维度数据已加载并渲染（radarTitle = dw.dqRule.radarTitle）
    await expect(page.getByText('权重雷达', { exact: true }).first(), '维度 Tab 未渲染权重雷达（数据未加载）').toBeVisible({ timeout: 30_000 });

    // 不应出现错误占位「维度注册表为空」
    await expect(page.getByText('维度注册表为空（后端 T3 未返回）'), '真实 200 时不应出现维度空错误占位').toHaveCount(0);

    await expectNoErrorBoundary(page);
  });

  test('拦截 500 ⇒ 渲染真实错误原因（非「T3 未返回」误导）+ 不崩溃不伪装', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 只把维度注册表打成 500（传输层构造，不伪造业务结论）
    await page.route(DIM_REGISTRY, (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, message: 'boom (probe)', success: false }) }),
    );

    await page.goto('/#/dq_dashboard?tab=dimension');
    const dimTab = page.locator('button:visible', { hasText: /6\s*维度|维度/ }).first();
    if (await dimTab.isVisible().catch(() => false)) {
      await dimTab.click();
    }

    // D152 修复后：500 不再被折叠成 null，而是上抛让 UI 显示真实错误原因。
    // 空态占位 + 错误明细（humanizeErrorBody 产出的真实 message，含 boom/probe 或 HTTP 500）。
    await expect(page.getByText('维度注册表为空（后端 T3 未返回）').first(), '500 时未渲染空占位').toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/boom \(probe\)|HTTP 500|500/).first(),
      'D152：500 时应显示真实错误原因，而非只显示「后端 T3 未返回」').toBeVisible({ timeout: 10_000 });

    // 不得渲染伪造的雷达图
    await expect(page.getByText('权重雷达', { exact: true }), '500 失败时不应渲染权重雷达（伪造成功）').toHaveCount(0);

    await expectNoErrorBoundary(page);
  });
});
