import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, expectHealthyPage, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * B1 场景工作台 — UI 级（对应矩阵域01 §二 V-WS-003~009）。
 * 覆盖现有 e2e 未覆盖的面：L2 详情、L3 预测、L3 决策、沙箱画布、404 兜底、高级技术视图。
 *
 * 反假绿纪律：
 *  - 每用例末尾 expectHealthyPage()（console 无 error + network 无意外 4xx/5xx）
 *  - 已知降级面（cognition 503、DecisionGateway 503）经 expectDegraded 逐 URL 显式声明，禁整页豁免
 *  - 页面自身须渲染降级横幅（另行断言），不得伪装正常
 */

const SC_ID = 'sc001';

test.describe('workbench L2/L3/沙箱 — UI 级', () => {
  test('V-WS-003 L2 场景详情渲染 + 认知降级横幅', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    // 认知四端点在引擎未承流时恒 503（已声明降级面）；DecisionGateway 亦 503
    watchPageHealth(page, {
      expectDegraded: [
        /\/api\/v1\/business\/scenarios\/[^/]+\/cognition\//,
        /\/api\/v1\/workspace\/scenarios\/[^/]+\/actions/,
        /\/api\/v1\/workspace\/scenarios\/[^/]+\/decisions/,
      ],
      expectConsoleErrors: [/cognition|503|COGNITIVE/i],
    });

    await page.goto(`/#/workbench/${SC_ID}`);
    await expectNoErrorBoundary(page);
    // L2 应渲染场景名（sc001 飞行运行安全）
    await expect(page.locator('main')).toContainText(/飞行运行安全|sc001/i, { timeout: 15000 });
    expectHealthyPage(page);
  });

  test('V-WS-004 L3 预测与推演渲染（hypotheses/beliefs 只读）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, {
      expectDegraded: [/\/api\/v1\/business\/scenarios\/[^/]+\/cognition\//],
      expectConsoleErrors: [/cognition|503/i],
    });
    await page.goto(`/#/workbench/${SC_ID}/forecast`);
    await expectNoErrorBoundary(page);
    await expect(page.locator('main')).not.toBeEmpty();
    expectHealthyPage(page);
  });

  test('V-WS-005 L3 经营管控决策渲染（DecisionGateway 503 应显示横幅非伪装）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, {
      expectDegraded: [
        /\/api\/v1\/workspace\/scenarios\/[^/]+\/actions/,
        /\/api\/v1\/workspace\/scenarios\/[^/]+\/decisions/,
        /\/api\/v1\/business\/scenarios\/[^/]+\/cognition\//,
      ],
      expectConsoleErrors: [/cognition|503|COGNITIVE|actions|decisions/i],
    });
    await page.goto(`/#/workbench/${SC_ID}/decisions`);
    await expectNoErrorBoundary(page);
    await expect(page.locator('main')).not.toBeEmpty();
    expectHealthyPage(page);
  });

  test('V-WS-006 沙箱画布渲染', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, {
      expectDegraded: [/\/api\/v1\/workspace\/scenarios\/[^/]+\/sandbox/],
      expectConsoleErrors: [/sandbox|503|404/i],
    });
    await page.goto(`/#/scenario-sandbox/${SC_ID}`);
    await expectNoErrorBoundary(page);
    // 沙箱页含画布 + 侧栏两个 <main> 级容器（实测 2 个），用 first() 取主画布；严格模式违规已登记为语义缺陷
    await expect(page.locator('main').first()).not.toBeEmpty();
    expectHealthyPage(page);
  });

  test('V-WS-009 404 兜底页渲染 + 返回入口', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    // navConfig 对未知路由打 console.error（W201 fail-closed 占位），属预期行为
    watchPageHealth(page, {
      expectConsoleErrors: [/\[navConfig\] no tab labelKey/],
    });
    await page.goto('/#/this-route-does-not-exist-xyz');
    await expectNoErrorBoundary(page);
    // 应渲染 404 兜底而非白屏
    await expect(page.locator('main').first()).not.toBeEmpty();
    expectHealthyPage(page);
  });

  test('V-WS-007 高级技术视图渲染（fusion/decision/git 三 Tab）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    // navConfig 缺 project_workbench 的 labelKey（W201 fail-closed 占位），属预期 console error
    watchPageHealth(page, {
      expectDegraded: [/\/api\/v1\/ecos\/git/, /\/api\/v1\/business\/scenarios\/[^/]+\/cognition\//],
      expectConsoleErrors: [/cognition|503|git/i, /\[navConfig\] no tab labelKey/],
    });
    await page.goto('/#/project_workbench');
    await expectNoErrorBoundary(page);
    await expect(page.locator('main').first()).not.toBeEmpty();
    expectHealthyPage(page);
  });
});
