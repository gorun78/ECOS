import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, expectHealthyPage, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详细设计-03 F03-16 / B.1 — 口径与指标治理页门禁。
 *
 * 【路由】本体工作台（/#/ontology_workbench）→ 侧栏「口径与指标」→ CaliberPage。
 *
 * 【断言口径】（B.1 三栏 + 状态，渲染层，不依赖业务落库）：
 *   1. 侧栏「口径与指标」入口可见；
 *   2. 三栏骨架标题（列表 / 时间线 / 绑定指标）均可见；
 *   3. 空态或列表/加载骨架之一出现（后端无口径数据时展示空态引导，属合法初态）；
 *   4. 无 ErrorBoundary + console 无 error + 网络无意外 4xx/5xx。
 *
 * 【前置】gateway :8080 + 前端 :3000。
 * 【locale】loginAsAdmin + injectAuth，断言 zh/en 双匹配。
 */

const ZH = {
  entry: '口径与指标',
  list: '口径列表',
  timeline: '版本时间线',
  bound: '绑定指标',
};

test.describe('F03-16/B.1 口径与指标治理页', () => {
  test.beforeEach(async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/ontology_workbench', { waitUntil: 'domcontentloaded' });
  });

  test('侧栏入口 + 三栏骨架标题可见', async ({ page }) => {
    // 点击侧栏「口径与指标」
    const entry = page
      .getByRole('button', { name: /口径与指标|Calibers & Metrics/i })
      .first();
    await expect(entry, '侧栏入口缺失').toBeVisible({ timeout: 15_000 });
    await entry.click();

    for (const label of [ZH.list, ZH.timeline, ZH.bound]) {
      await expect(
        page.getByText(new RegExp(label, 'i')).first(),
        `栏标题缺失：${label}`
      ).toBeVisible({ timeout: 15_000 });
    }
    await expectNoErrorBoundary(page);
  });

  test('空态/加载骨架之一出现且页面健康', async ({ page }) => {
    const entry = page.getByRole('button', { name: /口径与指标|Calibers & Metrics/i }).first();
    await entry.click();

    // 后端无 seed → 空态引导"新建口径"；有数据 → 列表行。两者之一即可。
    const listOrEmpty = page
      .getByRole('button', { name: /新建口径|New Caliber/i })
      .or(page.getByText(/暂无口径|No calibers/i))
      .first();
    await expect(listOrEmpty, '空态/列表引导未出现').toBeVisible({ timeout: 15_000 });
    expectHealthyPage(page);
  });
});
