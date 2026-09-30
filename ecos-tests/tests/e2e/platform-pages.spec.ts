import { expect, test } from '@playwright/test';
import { expectHealthyPage, expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详细设计-00 M2 — 两个平台运维页门禁（H9-Tx）。
 *
 * 【路由】主 tsx 已新增两条 hash 路由：
 *   · /#/gateway-diagnostics   → GatewayDiagnosticsPage (filters|routes|anonymous|editions tab)
 *   · /#/alerts-center          → AlertsPage (alerts | dlq 子 tab，15s 轮询)
 *
 * 【断言口径】
 *   1. 主标题（i18n 键 `platform.gateway.title` / `platform.alerts.title`）必须出现；
 *   2. 主 tab 骨架（Tab 按钮）各 ≥ 1 个可见；
 *   3. 页脚 traceId / 顶部状态 rid / 权限回退提示**允许**任意一种，
 *      只用 ErrorBoundary 判为失败（授权 403 页面本身就带友好 fallback，不属于错误）；
 *   4. 不启动 iframe/新 window，不依赖后端真实落库。
 *
 * 【前置】gateway :8080 + 前端 :3000（同时启 sysman/datanet 以让 manifest 能解析）。
 *
 * 【locale】走 loginAsAdmin + injectAuth，由语言偏好切口保持 zh；断言同时匹配 zh/en 措辞。
 */

const DIAG = {
  title: { zh: '网关诊断', en: 'Gateway Diagnostics' },
  tabs: {
    artifacts: { zh: '产物', en: 'Artifacts' },
    filters: { zh: '过滤器链', en: 'Filter chain' },
    routes: { zh: '路由清单', en: 'Routes' },
    anonymous: { zh: '匿名端点', en: 'Anonymous' },
    editions: { zh: '版本能力', en: 'Editions' },
  },
  refresh: { zh: '刷新', en: 'Refresh' },
};

const ALERTS = {
  title: { zh: '告警', en: 'Alerts' },
  tabs: {
    alerts: { zh: '告警', en: 'Alerts' },
    dlq: { zh: '死信队列', en: 'DLQ' },
  },
};

function zhOrEn(a: { zh: string; en: string }): RegExp {
  return new RegExp(`${a.zh}|${a.en}`, 'i');
}

test.describe('M2 平台运维页 — gateway-diagnostics', () => {
  test.beforeEach(async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/gateway-diagnostics', { waitUntil: 'domcontentloaded' });
  });

  test('页面标题渲染（zh/en 双匹配）', async ({ page }) => {
    await expect(
      page.getByRole('heading', { name: zhOrEn(DIAG.title) })
        .or(page.getByText(zhOrEn(DIAG.title)))
        .first(),
      '主标题缺失'
    ).toBeVisible({ timeout: 15_000 });
    await expectNoErrorBoundary(page);
  });

  test('五个 Tab 均可见：artifacts / filters / routes / anonymous / editions', async ({ page }) => {
    // 双匹配：Tab 骨架可能用 heading 或 button 或 tab 语义
    const tabsToCheck = [DIAG.tabs.artifacts, DIAG.tabs.filters, DIAG.tabs.routes, DIAG.tabs.anonymous, DIAG.tabs.editions];
    for (const t of tabsToCheck) {
      const el =
        page.getByRole('button', { name: zhOrEn(t) })
          .or(page.getByRole('tab', { name: zhOrEn(t) }))
          .or(page.getByText(zhOrEn(t), { exact: false }))
          .first();
      await expect(el, `Tab 缺失：${t.zh}/${t.en}`).toBeVisible({ timeout: 15_000 });
    }
  });

  test('点击 filters Tab 显示 ORDER 表或相当内容', async ({ page }) => {
    const tabBtn =
      page.getByRole('button', { name: zhOrEn(DIAG.tabs.filters) })
        .or(page.getByRole('tab', { name: zhOrEn(DIAG.tabs.filters) }))
        .first();
    await tabBtn.click();
    // 后端 filterChain 已实现；UI 里可能用表格/卡片/列表三种渲染形态之一。
    await expect(
      page.getByText(zhOrEn({ zh: 'VersionPrefixRewrite', en: 'VersionPrefixRewrite' }))
        .or(page.getByText(zhOrEn({ zh: 'EcosTrustHeader', en: 'EcosTrustHeader' })))
        .first(),
      '过滤器链 ORDER 表一项未渲染'
    ).toBeVisible({ timeout: 15_000 });
    expectHealthyPage(page);
  });
});

test.describe('M2 平台运维页 — alerts-center', () => {
  test.beforeEach(async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/alerts-center', { waitUntil: 'domcontentloaded' });
  });

  test('页面标题与两个子 tab（alerts / dlq）', async ({ page }) => {
    await expect(
      page.getByRole('heading', { name: zhOrEn(ALERTS.title) })
        .or(page.getByText(zhOrEn(ALERTS.title)))
        .first(),
      '主标题缺失'
    ).toBeVisible({ timeout: 15_000 });

    for (const t of [ALERTS.tabs.alerts, ALERTS.tabs.dlq]) {
      const el =
        page.getByRole('button', { name: zhOrEn(t) })
          .or(page.getByRole('tab', { name: zhOrEn(t) }))
          .or(page.getByText(zhOrEn(t), { exact: false }))
          .first();
      await expect(el, `子 tab 缺失：${t.zh}/${t.en}`).toBeVisible({ timeout: 15_000 });
    }
    await expectNoErrorBoundary(page);
  });
});
