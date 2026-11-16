import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth, expectHealthyPage } from '../../helpers/ecos';

/**
 * 横切 UI 专项批（卷09 V-X-021 断网离线态 / V-X-022 慢网络骨架屏）。
 *
 * 判定纪律：两条都必须是「人为制造故障 → 页面如实呈现 → 恢复后回正常」三段式，
 * 而不是只看到某个元素就在。且每条都配反向探针：条件不成立时该表现必须消失。
 */

test.describe('卷09 横切断面 UI', () => {
  test('V-X-021 断网：显式离线横幅（role=alert）+ 恢复后转「已恢复」', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, {
      // 断网期间 /api/* 全部失败属预期：逐 URL 无法枚举（browser 报 net::ERR_INTERNET_DISCONNECTED，
      // page.on('response') 不触发），故此处网络面天然无条目；console 面的 net::ERR 需显式声明。
      expectConsoleErrors: [/net::ERR_INTERNET_DISCONNECTED/, /Failed to fetch/i],
    });

    await page.goto('/#/workbench');
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible({ timeout: 20_000 });
    // 反向前置：联网状态下横幅不存在（证明后面看到的是断网造成，不是常驻元件）
    await expect(page.getByRole('alert').filter({ hasText: /网络已断开|服务暂不可用/ })).toHaveCount(0);

    // 断网 → NetworkErrorBanner 订阅 window 'offline' 事件（NetworkErrorBanner.tsx:41）
    await context.setOffline(true);
    const offlineAlert = page.getByRole('alert').filter({ hasText: '网络已断开' });
    await expect(offlineAlert).toBeVisible({ timeout: 20_000 });
    // 文案必须点明「API 请求已被拦截」而非笼统报错
    await expect(offlineAlert).toContainText('API 请求已被拦截');
    // 反向：断网横幅是 aria-live=assertive 的 alert，不是 status（屏幕阅读器口径正确性）
    await expect(offlineAlert).toHaveAttribute('aria-live', 'assertive');
    await expect(page.getByText('服务暂不可用')).toHaveCount(0);

    // 恢复 → 转「网络连接已恢复」（15s 后自动消失）
    await context.setOffline(false);
    await expect(page.getByRole('status').filter({ hasText: '网络连接已恢复' })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('alert').filter({ hasText: '网络已断开' })).toHaveCount(0);
    // 反向：恢复态是 status(polite) 而不是 alert
    await expect(page.getByRole('status').filter({ hasText: '网络连接已恢复' })).toHaveAttribute('aria-live', 'polite');

    await expectNoErrorBoundary(page);
  });

  test('V-X-022 慢网络：加载期骨架屏存在且非白屏，数据到达后骨架消失', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 人为把 AgentBuilder 的工具目录接口拖慢 3s（骨架屏只在真实慢响应窗口内可见）。
    // 选 /agent-builder 而非 contract_manager：后者的数据面是 D31 已登记缺口
    // （/api/v1/ecos/objects/Contract 恒 404），慢网窗口内会先撞 404 再进空态，测不出骨架。
    const SLOW_PATH = /\/api\/v1\/agents\/tools/;
    await page.route(SLOW_PATH, async (route) => {
      await new Promise((r) => setTimeout(r, 3000));
      await route.continue();
    });

    const nav = page.goto('/#/agent-builder');
    // 加载窗口内：animate-pulse 骨架条目必须存在（LoadingSkeleton 的唯一视觉特征）
    const skeleton = page.locator('.animate-pulse');
    await expect(skeleton.first(), '慢网络下应出现骨架屏，而不是白屏').toBeVisible({ timeout: 8000 });
    expect(await skeleton.count(), '骨架屏应为多条（table variant 多行）').toBeGreaterThan(1);
    // 反向：此刻页面不是白屏 —— body 有可见文本（壳 + 骨架占位）
    const bodyLen = await page.evaluate(() => (document.body.innerText || '').length);
    expect(bodyLen, '骨架期不得白屏').toBeGreaterThan(0);

    await nav;
    // 数据到达后：骨架必须消失（否则是「永远骨架」假加载）
    await expect.poll(() => skeleton.count(), {
      message: '数据到达后骨架屏仍未消失', timeout: 30_000,
    }).toBe(0);

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
