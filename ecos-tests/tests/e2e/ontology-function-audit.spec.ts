import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, expectHealthyPage, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详细设计-03 F03-16 / B.5 — Function 审计页门禁。
 *
 * 【路由】本体工作台（/#/ontology_workbench）→ 侧栏「Function 审计」→ FunctionAuditPage。
 *
 * 【断言口径】（B.5 审计表 + 空态语义，渲染层，不依赖审计落库）：
 *   1. 侧栏「Function 审计」入口可见；
 *   2. 表格列头（函数 / 调用者 / 状态 / 表达式 / 耗时 / 缓存 / traceId）均可见；
 *   3. 空态文案解释"审计上下文缺失即拒收（4xx 拒收），不会写入本表"（把拒收语义呈现给用户）；
 *   4. 无 ErrorBoundary + console 无 error + 网络无意外 4xx/5xx。
 *
 * 【防 O-13 回归】列级 functionName 与 callerId 分列呈现（不再混同），
 *    数据行断言（非空且不同值）需真实审计数据，归 API 侧 ontology-function-audit（tests/api）。
 *
 * 【前置】gateway :8080 + 前端 :3000。
 */

const COLS = [
  '函数',
  '调用者',
  '状态',
  '表达式',
  '耗时',
  '缓存',
  'traceId',
];

test.describe('F03-16/B.5 Function 审计页', () => {
  test.beforeEach(async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/ontology_workbench', { waitUntil: 'domcontentloaded' });
  });

  test('侧栏入口 + 审计表列头可见', async ({ page }) => {
    const entry = page.getByRole('button', { name: /Function 审计|Function Audit/i }).first();
    await expect(entry, '侧栏入口缺失').toBeVisible({ timeout: 15_000 });
    await entry.click();

    for (const col of COLS) {
      await expect(
        page.getByRole('columnheader', { name: new RegExp(col, 'i') })
          .or(page.getByText(new RegExp(`^${col}$`, 'i')))
          .first(),
        `列头缺失：${col}`
      ).toBeVisible({ timeout: 15_000 });
    }
    await expectNoErrorBoundary(page);
  });

  test('空态解释拒收语义（无上下文→拒收，不写本表）', async ({ page }) => {
    const entry = page.getByRole('button', { name: /Function 审计|Function Audit/i }).first();
    await entry.click();

    // 后端无审计数据 → 空态；文案须命中"拒收/不会写入"语义（B.5 空态要求）
    const emptyHint = page
      .getByText(/拒收|不会写入|已被 400|ECOS-ONTO-060|rejected|NOT written/i)
      .first();
    await expect(emptyHint, '空态未呈现拒收语义').toBeVisible({ timeout: 15_000 });
    expectHealthyPage(page);
  });
});
