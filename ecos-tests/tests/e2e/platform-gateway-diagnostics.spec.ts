/**
 * 平台治理 · 网关诊断 E2E — 详细设计 00 §B 3.1 + §C.8 V4 三项断言。
 *
 * 覆盖：
 *  - 登录（凭据走 process.env，见 tests/helpers/ecos.ts 的 creds()）
 *  - 访问 #/platform/gateway-diagnostics
 *  - 5 区块标题渲染：服务健康/过滤链顺序/路由清单/匿名端点/档位能力矩阵
 *  - [data-testid=error-boundary] 计数 0（铁律 V4 第一项）
 *  - ?tab=routes 切换后路由清单表格非空
 *
 * 注意：本 spec 自测环境「后端未上线」§D.5.3 端点尚未部署，
 * 4xx/5xx 网络断言对 `gateway-diagnostics` 与 `monitor/alerts` 用例面**豁免**
 * （详见下方 expectNoUnplannedApi）。仍在用例末尾执行 ErrorBoundary 与
 * console error 两条铁律 V4 项（这两条不依赖后端响应码）。
 */
import { expect, test } from '@playwright/test';
import {
  expectNoErrorBoundary,
  injectAuth,
  loginAsAdmin,
  watchPageHealth,
} from '../helpers/ecos';

// 诊断页未上线端点的环境豁免（后端 §D.5.3 落地时移除）
const UNPLANNED_GATEWAY_ENDPOINTS = [
  '/api/v1/system/gateway-diagnostics',
  '/api/v1/monitor/alerts',
];

test('网关诊断页可用：B-1（5 区块 + 无 ErrorBoundary + tab=routes 非空）', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);

  // 受控的 network 监视器：仅放行「尚未上线」的两条端点的 4xx/5xx
  const unexpected: { url: string; status: number }[] = [];
  const consoleErrors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });
  page.on('response', (res) => {
    if (!res.url().includes('/api/')) return;
    if (res.status() < 400) return;
    const expectUnplanned = UNPLANNED_GATEWAY_ENDPOINTS.some((p) =>
      res.url().includes(p)
    );
    if (!expectUnplanned) unexpected.push({ url: res.url(), status: res.status() });
  });

  const diagPromise = page
    .waitForResponse((res) => res.url().includes('/system/gateway-diagnostics'), {
      timeout: 30_000,
    })
    .catch(() => null);

  await page.goto('/#/platform/gateway-diagnostics', {
    waitUntil: 'domcontentloaded',
  });

  await expect(page.locator('[data-testid=gw-page-title]'), '诊断页标题未渲染').toBeVisible({ timeout: 30_000 });

  await test.step('B-1.1 5 区块标题渲染（服务健康 / 过滤链 / 路由清单 / 匿名端点 / 档位矩阵）', async () => {
    for (const blockId of ['services', 'filterChain', 'routes', 'anonymous', 'editions']) {
      const section = page.locator(`[data-testid=gw-section-${blockId}]`);
      await expect(section, `区块 ${blockId} 未渲染`).toBeVisible();
      const h3 = section.locator('h3');
      await expect(h3, `区块 ${blockId} 缺标题`).toBeVisible();
    }
  });

  await test.step('B-1.2 铁律 V4 项 1：ErrorBoundary 计数 0', async () => {
    const text = await page.evaluate(() => document.body.innerText || '');
    expect(
      /Something went wrong|Unexpected Application Error|应用发生错误|useTheme must be used within/i.test(
        text
      ),
      '页面落入 ErrorBoundary'
    ).toBeFalsy();
    // 同时断言 data-testid=error-boundary 节点不存在（未来若挂上该 testid 也守门）
    const errorBoundaryCount = await page.locator('[data-testid=error-boundary]').count();
    expect(errorBoundaryCount, '页面出现 error-boundary testid 节点').toBe(0);
  });

  await test.step('B-1.3 role="tablist" + 4 个 role="tab" + 默认 filters 选中', async () => {
    const tablist = page.locator('[role=tablist]');
    await expect(tablist, '诊断页缺 role=tablist').toBeVisible();
    const tabs = tablist.locator('[role=tab]');
    await expect(tabs).toHaveCount(4);
    await expect(page.locator('[data-testid=gw-tab-filters]')).toHaveAttribute('aria-selected', 'true');
  });

  await test.step('B-1.4 切 ?tab=routes → 路由清单表格非空', async () => {
    await page.click('[data-testid=gw-tab-routes]');
    await expect(page.locator('[data-testid=gw-tab-routes]')).toHaveAttribute('aria-selected', 'true');
    // URL 已同步 ?tab=routes
    await page.waitForURL(/tab=routes/, { timeout: 5_000 }).catch(() => null);
    // 表格非空 = 至少 1 行 data-testid=gw-route-row
    const rows = page.locator('[data-testid=gw-route-row]');
    const count = await rows.count();
    expect(count, '路由清单表格应非空（至少 1 行）').toBeGreaterThan(0);
  });

  await test.step('B-1.5 铁律 V4 项 2：console 无 error（favicon 404 例外）', async () => {
    const fatal = consoleErrors.filter(
      (e) => !/favicon|ResizeObserver loop|DevTools/i.test(e)
    );
    expect(fatal, `console error: ${fatal.slice(0, 3).join(' | ')}`).toHaveLength(0);
  });

  await test.step('B-1.6 铁律 V4 项 3：无意外 4xx/5xx（豁免未上线端点）', async () => {
    expect(
      unexpected,
      `出现未豁免的 4xx/5xx: ${unexpected.slice(0, 3).map((r) => `${r.url} → ${r.status}`).join(' | ')}`
    ).toHaveLength(0);
  });

  // 等首次诊断返包（可能为 404 / 403，按 E2E 用例口径均豁免）
  await diagPromise;

  await page.screenshot({ path: test.info().outputPath('platform-gateway-diag.png'), fullPage: true });
});

test('告警中心路由注册：/platform/alerts 可挂载（端点未上线豁免）', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);

  page.on('console', (msg) => {
    if (msg.type() === 'error') throw new Error(`console error: ${msg.text()}`);
  });

  await page.goto('/#/platform/alerts', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('[data-testid=alerts-page-title]'), '告警中心页标题未渲染').toBeVisible({
    timeout: 30_000,
  });
  // 过滤条存在
  await expect(page.locator('[data-testid=alerts-severity-filter]')).toBeVisible();
  await expect(page.locator('[data-testid=alerts-status-filter]')).toBeVisible();
  // 轮询徽标存在（"自动刷新中（15 秒）"文案由 i18n 注入）
  await expect(page.locator('[data-testid=alerts-polling]')).toBeVisible();
  await page.screenshot({ path: test.info().outputPath('platform-alerts.png'), fullPage: true });
});
