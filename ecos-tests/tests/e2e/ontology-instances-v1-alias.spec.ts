import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * D34 修复验证（2026-11-16）：`#/ops_apps` 运营应用页的实例查询从 404 转可达。
 *
 * 历史：页面调 `GET /api/v1/ontology/entities/{Customer,Facility}/instances`，
 * 后端只有无 v1 路径 `/api/ontology/entities/{type}/instances` ⇒ v1 恒 404 ⇒ 首屏报错。
 *
 * 修法：新增 `OntologyEntityInstanceCompatController`（类级 `/api/v1/ontology`，
 * 仅 `entities/{type}/instances` 一条）返回空列表。**不用类级别名**——实测
 * `OntologyCompatController` 类级并列挂 v1 会让 `/api/v1/ontology/mappings` 撞车变 500。
 *
 * 判定：页面渲染「暂无客户数据 / 暂无维护记录」**空态**，不是错误态；
 * 且回归断言 `/api/v1/ontology/mappings` 仍 200（未被别名打坏）。
 */

test.describe.configure({ timeout: 90_000 });

test.describe('D34 ops_apps 实例 v1 别名', () => {
  test('ops_apps 渲染空态而非错误态（v1 instances 可达）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/ops_apps');
    await expect(page.getByText(/运营应用|业务工作区/, { exact: false }).first(), '未落到 ops_apps 页').toBeVisible({ timeout: 30_000 });

    // 客户区空态（真实：无实例存储，诚实返回空列表）。
    // 注：维护区在当前布局下不渲染（页面快照无「工况温度/Workshop」段），故此处只验客户区；
    // Facility 路径的可达性由下方 HTTP 腿直接断言。
    await expect(page.getByText('暂无客户数据').first(), 'D34 回归：客户区未渲染空态（可能仍是 404 错误态）').toBeVisible({ timeout: 20_000 });

    // 不得出现错误横幅（role=alert 的 opError）
    await expect(page.locator('[role="alert"]').filter({ hasText: /HTTP_ERROR|PERMISSION_DENIED|NOT_FOUND|404/ }),
      'D34 回归：页面仍渲染错误横幅').toHaveCount(0);

    await expectNoErrorBoundary(page);
  });

  test('两条 v1 实例路径均 200（Customer + Facility）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    for (const type of ['Customer', 'Facility']) {
      const r = await request.get(`/api/v1/ontology/entities/${type}/instances`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(r.status(), `v1 instances (${type}) 应 200`).toBe(200);
      const body = await r.json();
      expect(Array.isArray(body?.data), `${type} data 应为数组`).toBe(true);
    }
  });

  test('回归：/api/v1/ontology/mappings 未被打坏（仍 200）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const r = await request.get('/api/v1/ontology/mappings', { headers: { Authorization: `Bearer ${token}` } });
    expect(r.status(), 'D34 别名撞车回归：/api/v1/ontology/mappings 应仍 200').toBe(200);
  });
});
