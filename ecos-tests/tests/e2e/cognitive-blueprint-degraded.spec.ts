import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * V-COG-004（认知蓝图 `#/mission_control`）活体验证（2026-11-16，D151 修复后改写）。
 *
 * 历史：该页依赖 `GET /api/v1/cognitive/blueprint`，但端点长期缺失（live 404）⇒ 页面恒降级
 * 为「示意数据」（D151）。2026-11-16 补上真实聚合端点（`CognitiveBlueprintController`
 * 聚合六引擎 health），本 spec 验证修复后两条腿：
 *
 *   ① **真实数据腿**：端点真实可达 ⇒ 六层渲染真实 coverage（无降级横幅、无 DemoBadge）；
 *   ② **降级保底腿**：端点被人为打成 500 ⇒ 页面仍渲染降级横幅 + DemoBadge「示意数据」+
 *      诚实声明「非真实系统审计结果」（无假真理铁律 7：失败时不许静默伪装成功）。
 *
 * 判定纪律：真实腿走真实端点（不 mock）；降级腿只拦 HTTP 状态（不伪造业务数据）。
 */

test.describe.configure({ timeout: 90_000 });

const BLUEPRINT = '**/api/v1/cognitive/blueprint**';

test.describe('V-COG-004 认知蓝图 真实数据 + 降级保底', () => {
  test('端点真实可达 ⇒ 六层渲染真实 coverage，无降级横幅', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/mission_control');

    // 六层里至少首层「战略与决策层」渲染（真实数据腿，端点 200 后由 mapApiBlueprintLayer 映射）
    await expect(page.getByText(/战略与决策层/, { exact: false }).first(), '真实端点可达但未渲染六层蓝图').toBeVisible({ timeout: 30_000 });

    // 真实数据态：不应出现降级横幅（blueprintDegraded=false）
    await expect(page.getByText(/蓝图接口不可达/, { exact: false }), '端点 200 时不应出现降级横幅').toHaveCount(0);

    await expectNoErrorBoundary(page);
  });

  test('端点 500 ⇒ 降级横幅 + DemoBadge + 诚实声明「非真实系统审计结果」', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.route(BLUEPRINT, (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, message: 'boom (probe)' }) }),
    );

    await page.goto('/#/mission_control');

    // 降级横幅（blueprintDegraded=true）
    await expect(page.getByText(/蓝图接口不可达.*示意数据.*非真实系统审计结果/, { exact: false }).first(),
      '端点 500 时未渲染降级横幅（可能静默伪装成功）').toBeVisible({ timeout: 30_000 });

    // DemoBadge「示意数据」
    await expect(page.getByText('示意数据', { exact: true }).first(), '降级态缺少 DemoBadge').toBeVisible();

    // 重试按钮
    await expect(page.getByRole('button', { name: '重试加载' }).first(), '降级横幅缺少重试按钮').toBeVisible();

    // 降级不崩
    await expectNoErrorBoundary(page);
  });
});
