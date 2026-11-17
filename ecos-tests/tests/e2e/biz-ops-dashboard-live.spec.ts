import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * V-DATA-009（数据看板）biz/ops 两页活体验证（D33 修复后，2026-11-16）。
 *
 * 历史：`#/biz_dashboard` 与 `#/ops_dashboard` 共用 `fetchBizDashboard()` →
 * `GET /api/v1/ecos/biz/dashboard`，但该端点后端从未实现（live 404，D33）⇒ 两页恒报错。
 *
 * D33 修法（用户裁决 = cognitive 前缀方案）：新增 `GET /api/v1/cognitive/biz-dashboard`
 * （`BizDashboardController` 读 `ecos_cognitive.ecos_biz_contract/project/metric/target` +
 * `ecos_control.td_organization`，真实聚合），前端 `fetchBizDashboard` 改指新路径。
 *
 * 本 spec 验证：
 *   ① biz 看板：渲染真实统计（项目 3 / 合同 5 / 部门 12），无加载失败横幅；
 *   ② ops 看板：渲染真实月度趋势与部门分布，无加载失败横幅。
 */

test.describe.configure({ timeout: 90_000 });

test.describe('V-DATA-009 biz/ops 看板真实数据', () => {
  test('biz_dashboard 渲染真实统计，无加载失败', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/biz_dashboard');

    // 页面落位（Topbar 面包屑/标题「信科数据仪表盘」）
    await expect(page.getByText(/信科数据仪表盘|数据仪表盘/, { exact: false }).first(), '未落到 biz 看板').toBeVisible({ timeout: 30_000 });

    // 真实数据腿：不得出现加载失败
    await expect(page.getByText(/加载失败|加载数据失败|API .* 404/), 'D33 回归：biz 看板仍在报加载失败').toHaveCount(0);

    // 真实合同数 5 / 项目数 3（聚合端点真值；页面上以数字形式出现）
    await expect(page.getByText('5', { exact: true }).first(), '未渲染真实合同数 5').toBeVisible({ timeout: 20_000 });

    await expectNoErrorBoundary(page);
  });

  test('ops_dashboard 渲染真实趋势与部门分布，无加载失败', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/ops_dashboard');

    await expect(page.getByText('产值分配看板', { exact: false }).first(), '未落到 ops 看板').toBeVisible({ timeout: 30_000 });

    // 不得出现加载失败（D33 回归探针）
    await expect(page.getByText(/看板数据加载失败|加载看板数据失败/), 'D33 回归：ops 看板仍在报加载失败').toHaveCount(0);

    // 真实趋势区渲染（monthlyTrend 由 project.start_date 真实聚合，3 个月份）
    await expect(page.getByText(/月度产值趋势/).first(), 'ops 未渲染月度产值趋势区').toBeVisible({ timeout: 20_000 });
    // 部门分布区（12 个真实部门）
    await expect(page.getByText(/部门产值分布|部门产值明细/).first(), 'ops 未渲染部门分布区').toBeVisible();

    await expectNoErrorBoundary(page);
  });
});
