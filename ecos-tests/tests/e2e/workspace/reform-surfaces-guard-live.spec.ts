import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * 六态「行为级」live 用例（复盘 S3 欠账：此前只有结构守卫 = 分支存在性检查，
 * 未验证 403/404/503/离线分支在真实页面上的实际呈现）。
 *
 * 诚实约束：
 * - 只对被注入的端点做 page.route 失败模拟（模拟的是「后端裁决结果」，
 *   其余请求全部走真实后端）；断言 = 页面如实呈现后端语义，禁本地合成数据。
 * - 503 横幅必须带后端透传的 errorCode 与 traceId（无伪造通过态）。
 * - 有意失败用例不调 expectHealthyPage（页面按设计会打 logger.error 聚合日志），
 *   与既有 cognition 503 用例同口径 —— 仅断言不落 ErrorBoundary、文案如实。
 */

const SCENARIO = process.env.ECOS_E2E_SCENARIO_ID ?? 'sc001';
const BP_ROUTE = `**/api/v1/business/scenarios/${SCENARIO}/blueprints**`;

const json500 = (status: number, body: Record<string, unknown>, headers?: Record<string, string>) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body),
  ...(headers ? { headers } : {}),
});

test.describe('六态行为面 — 权限/未找到/引擎降级/离线', () => {
  test('L2 场景详情 403 → 无权限横幅（不渲染任何场景数据）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.route(`**/api/v1/workspace/scenarios/${SCENARIO}`, (r) =>
      r.fulfill(json500(403, { code: 403, message: 'forbidden', success: false })),
    );
    await page.goto(`/#/workbench/${SCENARIO}`);
    await expect(page.getByText('你没有权限查看该场景。').first()).toBeVisible({ timeout: 30_000 });
    // 无权限时不得出现蓝图中心面（数据面整体拒绝，非局部遮罩）
    await expect(page.getByText('场景蓝图')).toHaveCount(0);
    await expectNoErrorBoundary(page);
  });

  test('L2 场景详情 404 → 未找到横幅（与错误态区分）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.route(`**/api/v1/workspace/scenarios/${SCENARIO}`, (r) =>
      r.fulfill(json500(404, { code: 404, message: 'scenario not found', success: false })),
    );
    await page.goto(`/#/workbench/${SCENARIO}`);
    await expect(page.getByText('未找到该场景').first()).toBeVisible({ timeout: 30_000 });
    // 404 不得被误呈现为通用错误（apiFetchData 错误对象须携带 status，2026-10-12 修复）
    await expect(page.getByText(/场景详情加载失败/)).toHaveCount(0);
    await expectNoErrorBoundary(page);
  });

  test('蓝图列表 403 → 蓝图面呈现「无权访问」，不伪造版本列表', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, {
      expectDegraded: [/\/api\/v1\/business\/scenarios\/[^/]+\/blueprints(\?|$|\/)/],
    });
    await page.route(BP_ROUTE, (r) =>
      r.fulfill(json500(403, { code: 403, message: 'blueprint access denied', success: false })),
    );
    await page.goto(`/#/workbench/${SCENARIO}`);
    await expect(page.getByText('场景蓝图').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('无权访问该场景蓝图').first()).toBeVisible();
    // 拒绝态下不得出现版本切换按钮/状态轨（无合成数据）
    await expect(page.getByRole('button', { name: /^v\d+/ })).toHaveCount(0);
    await expectNoErrorBoundary(page);
  });

  test('蓝图列表 503 → 引擎横幅含后端 errorCode 与 traceId（透传、不本地兜底）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, {
      expectDegraded: [/\/api\/v1\/business\/scenarios\/[^/]+\/blueprints(\?|$|\/)/],
    });
    await page.route(BP_ROUTE, (r) =>
      r.fulfill(
        json500(
          503,
          { code: 503, errorCode: 'ENGINE_DISABLED', message: 'blueprint engine unavailable', success: false },
          { 'X-Trace-Id': 'e2e-bp-503-trace' },
        ),
      ),
    );
    await page.goto(`/#/workbench/${SCENARIO}`);
    await expect(page.getByText('场景蓝图').first()).toBeVisible({ timeout: 30_000 });
    // workspace.bp.engine_unavailable = 「蓝图服务暂不可用（{code}），traceId={traceId}」
    await expect(
      page.getByText(/蓝图服务暂不可用（ENGINE_DISABLED），traceId=e2e-bp-503-trace/).first(),
    ).toBeVisible();
    await expect(page.getByText(/尚无蓝图版本|v1/)).toHaveCount(0);
    await expectNoErrorBoundary(page);
  });

  test('L1 离线 → 离线 banner + 加载失败并存（hash 重挂载触发，不刷新页面）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/workbench');
    await page.waitForLoadState('networkidle');
    // 先真实加载一次，再切离线并做 SPA 内部路由重挂载（离线时禁止任何页面级 reload/导航）
    await context.setOffline(true);
    await page.evaluate(() => {
      window.location.hash = '#/';
    });
    await page.evaluate(() => {
      window.location.hash = '#/workbench';
    });
    await expect(page.getByText(/当前处于离线模式/).first()).toBeVisible({ timeout: 30_000 });
    // 离线 ≠ 错误遮掩：失败原因如实呈现（workspace.l1.refresh_failed），且有重试入口
    await expect(page.getByText(/场景列表加载失败/).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /重试|刷新/ }).first()).toBeVisible();
    await expectNoErrorBoundary(page);
    await context.setOffline(false);
  });
});
