import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * V-WS-008（场景与认知闭环）的**错误态/降级态横幅**活体验证（2026-11-16）。
 *
 * 矩阵缺口原文：「错误态 / 降级态横幅未验（只验了渲染与数据源）」。
 * 本 spec 补的就是这一层：`#/cognitive-workbench`（重定向 → `/decision/cognition`，
 * 组件 CognitiveWorkbench）的三个数据源里，scenarios 腿失败时必须渲染共享 ErrorState
 * （`role="alert"` 红色横幅 + 失败标题 + 重试按钮），而不是静默空白或伪装成功。
 *
 * 判定纪律：
 *   - 用 route 拦截把 `GET /api/v1/workspace/scenarios` 打成 503，横幅必须出现；
 *     这是「降级语义真实渲染」的正例（不是 mock 业务结论，只构造传输层失败）。
 *   - 对照腿：同页不拦截时 scenarios 真实 200，横幅**不出现**（证明横幅确由失败驱动）。
 *   - 不 mock 业务数据本体；只拦截一条数据源的 HTTP 状态。
 */

test.describe.configure({ timeout: 90_000 });

test.describe('V-WS-008 认知工作台 错误态/降级横幅', () => {
  test('scenarios 腿 503 ⇒ 渲染 role=alert 失败横幅 + 重试按钮', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 只拦截 scenarios 数据源，打成 503（其余腿放行，避免整页崩）
    await page.route('**/api/v1/workspace/scenarios**', (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 503, message: 'engine down (probe)' }),
      }),
    );

    await page.goto('/#/cognitive-workbench');
    // 重定向落点 /decision/cognition，页头文案 = workspace.cognition.title
    await expect(page.getByText('经营决策 · 场景认知闭环', { exact: false }).first(), '未落到认知工作台').toBeVisible({ timeout: 30_000 });

    // 错误态横幅：role=alert + 标题「场景读取失败」
    const banner = page.locator('[role="alert"]').filter({ hasText: '场景读取失败' }).first();
    await expect(banner, 'scenarios 503 时未渲染失败横幅（role=alert + 场景读取失败）').toBeVisible({ timeout: 20_000 });

    // 横幅内有重试按钮（重试 = workspace.cognition.retry）
    await expect(banner.getByRole('button', { name: '重试' }), '失败横幅缺少重试按钮').toBeVisible();

    // 降级不崩溃：整页无 ErrorBoundary
    await expectNoErrorBoundary(page);
  });

  test('对照：scenarios 腿 200 空数组 ⇒ 失败横幅不出现（横幅确由失败驱动）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 构造 scenarios 成功空态（当前环境安全引擎 fail-closed 致该端点真实 503，
    // 无法用「不拦截」做对照；改为强制 200 空数组，只验证「成功态不渲染失败横幅」这一逆向不变量）。
    await page.route('**/api/v1/workspace/scenarios**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: 'ok', data: [] }) }),
    );

    await page.goto('/#/cognitive-workbench');
    await expect(page.getByText('经营决策 · 场景认知闭环', { exact: false }).first(), '未落到认知工作台').toBeVisible({ timeout: 30_000 });

    // 等 scenarios 腿完成（加载文案消失）
    await expect(page.getByText('正在读取场景…').first(), '场景加载未结束').toBeHidden({ timeout: 30_000 });

    // 成功态：「场景读取失败」横幅不应出现
    await expect(page.locator('[role="alert"]').filter({ hasText: '场景读取失败' }), '200 成功态不应出现失败横幅').toHaveCount(0);

    await expectNoErrorBoundary(page);
  });
});
