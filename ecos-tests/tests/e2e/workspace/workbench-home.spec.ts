import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * 工作台 L1「我的场景」首屏 E2E（详细设计-07 F07-06 / F07-25）。
 *
 * <p>仅源码交付，Playwright 工程存在但本环境不可真跑（无 gateway:8080 + 前端:3000），
 * 因此全部 test.skip。真跑需 PMO 在有栈环境解锁。</p>
 */

test.describe('workbench L1 — 我的场景', () => {
  test.skip('六态：加载→数据态卡片网格渲染', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/workbench');
    await expect(page.getByRole('heading', { name: /Workbench|工作台/i })).toBeVisible();
    await expectNoErrorBoundary(page);
  });

  test.skip('首屏不出现技术概念 Mind / Goal Tree / Causal', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.goto('/#/workbench');
    for (const term of ['Mind', 'Goal Tree', 'Causal']) {
      await expect(page.locator('main').getByText(term, { exact: false })).toHaveCount(0);
    }
  });

  test.skip('高级视图默认折叠，可展开', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.goto('/#/workbench');
    const toggle = page.getByRole('button', { name: /Advanced View|高级视图/i });
    await expect(toggle).toBeVisible();
    await toggle.click();
    await expect(page.getByRole('link', { name: /technical|技术/i })).toBeVisible();
  });

  test.skip('离线态：navigator.onLine=false 时展示 banner，不静默 200', async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    await page.context().setOffline(true);
    await page.goto('/#/workbench');
    await expect(page.getByText(/Offline|离线/i)).toBeVisible();
  });
});
