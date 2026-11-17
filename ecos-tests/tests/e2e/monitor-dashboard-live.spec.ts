import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * D32 复核（2026-11-16）：`#/monitor` 监控中心聚合面。
 *
 * 台账原文：「前端取聚合面 `/api/v1/monitor`（404），后端只有 /alerts、/dlq 等子族，无根聚合端点」。
 *
 * 本轮实测复核结论（与台账不符，须以实测为准）：
 *   - 前端 `monitorClient.ts` 的 `MONITOR_BASE = ${API_BASE}/monitor` = `/api/monitor`（**无 v1**）；
 *   - gateway `MonitorController` 挂在 `/api/monitor` 且有根 `@GetMapping`，**完整实现**：
 *     真实 JVM 指标（cpu_cores/cpu_load/heap_used_mb/heap_max_mb）+ active_alerts +
 *     alerts_today + recent_alerts + open_dq_issues + chartData；
 *   - 活体 `GET /api/monitor` → **200**（真实值：cpu_cores 4 / heap 281/768）。
 *   ⇒ D32 的「前端调错 /api/v1/monitor」为**误记**，链路本就通。
 *
 * 本 spec 把这条复核固化为常驻断言，防止后人再按旧台账误改。
 */

test.describe.configure({ timeout: 90_000 });

test.describe('D32 监控中心聚合面', () => {
  test('GET /api/monitor 返回真实 JVM 指标与告警统计', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const r = await request.get('/api/monitor', { headers: { Authorization: `Bearer ${token}` } });
    expect(r.status(), '/api/monitor 应 200').toBe(200);
    const body = await r.json();
    const d = body?.data ?? {};
    expect(d.system, '缺 system 块').toBeTruthy();
    expect(typeof d.system.cpu_cores, 'cpu_cores 应为数字').toBe('number');
    expect(typeof d.system.heap_used_mb, 'heap_used_mb 应为数字').toBe('number');
    expect(typeof d.system.heap_max_mb, 'heap_max_mb 应为数字').toBe('number');
    expect(Array.isArray(d.recent_alerts), 'recent_alerts 应为数组').toBe(true);
    expect(typeof d.active_alerts, 'active_alerts 应为数字').toBe('number');
    expect(typeof d.open_dq_issues, 'open_dq_issues 应为数字').toBe('number');
  });

  test('#/monitor 页面渲染监控数据，无加载失败', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/monitor');
    await expect(page.getByText(/监控|Monitor/i).first(), '未落到监控页').toBeVisible({ timeout: 30_000 });

    // 真实指标上屏（CPU Cores 标签来自页面 KPI 定义）
    await expect(page.getByText(/CPU Cores/i).first(), '未渲染 CPU Cores KPI').toBeVisible({ timeout: 20_000 });

    // 不得出现 404 错误态
    await expect(page.getByText(/404|资源不存在|加载失败/), 'D32 回归：监控页报错').toHaveCount(0);

    await expectNoErrorBoundary(page);
  });
});
