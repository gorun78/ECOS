import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详设-08 §2.3 状态矩阵 — 加载面。
 * 冻结标识：`pw loading.spec.ts`（§2.3 行"加载"，用例名以 P-3 工程落地为准）。
 *
 * 【口径】请求 in-flight ⇒ 骨架屏（skeleton）；**禁止 spinner 覆盖整页**。
 * P01~P13 相关加载界面落位随 **R-49**（登记不排期）⇒ 先 test.fixme 占位，
 * 断言体按 §2.3 写足，落位属主接通后去掉 fixme 即可执行。
 *
 * 【凭据】只走 env（ECOS_USER/ECOS_PASS），禁明文。
 */

test.describe('§2.3 加载矩阵（页面落位随 R-49）', () => {
  test.fixme('请求在途显示骨架屏而非整页spinner', async ({ page, context, request }) => {
    // 落位随 R-49：P01 Mission 列表在途态界面未建 ⇒ 固定跳过。
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 人为加延迟，把界面钉在 in-flight 窗口。
    await page.route('**/api/v1/**', async (route) => {
      await new Promise((r) => setTimeout(r, 2_500));
      return route.continue();
    });
    await page.goto('/#/agent_studio', { waitUntil: 'commit' });

    // 骨架屏证据：aria-busy / data-testid*=skeleton / .animate-pulse 占位块。
    const skeleton = page
      .locator('[aria-busy="true"], [data-testid*="skeleton" i], .animate-pulse')
      .first();
    await expect(skeleton, 'in-flight 应出现骨架屏（§2.3）').toBeVisible({ timeout: 10_000 });

    // 禁止整页 spinner 覆盖：全屏遮罩层上不得只有 spinner。
    const overlaySpinner = page.locator(
      '[role="status"].fixed.inset-0, .fixed.inset-0:has(.animate-spin):not(:has(.animate-pulse))',
    );
    await expect(overlaySpinner, '禁止 spinner 覆盖整页（§2.3）').toHaveCount(0);
    await expectNoErrorBoundary(page);
  });

  test.fixme('降级轮询兜底时显示"非实时"标注', async ({ page, context, request }) => {
    // §2.3"降级"行的界面侧联动（unit: DegradationBanner.test.tsx 属主并行批次 #121）。
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/agent_studio', { waitUntil: 'domcontentloaded' });
    await expect(
      page.getByText(/非实时|polling fallback|near real-time/i).first(),
      '轮询兜底须显式标注（§2.3 降级）',
    ).toBeVisible({ timeout: 15_000 });
    await expectNoErrorBoundary(page);
  });
});
