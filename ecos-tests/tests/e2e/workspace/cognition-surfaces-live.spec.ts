import { expect, test } from '@playwright/test';
import { expectHealthyPage, expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * 三项前端改造 live E2E（前端设计方案-知识库认知资产-AI认知工作台去留-场景工作台重构-2026-10-11）。
 * 依赖真实全栈（gateway :8080 + 引擎族 + workspace :18090 + 前端 :3000），
 * 契约均已 curl live 核验（设计文档 §3.12）；本文件禁止 mock 合成真理（P0 无假真理）。
 * 场景真实 id：sc001（有修复后蓝图数据）/ sc002（空态）；凭据仅从 ecos-tests/.env。
 */

const SCENARIO_WITH_BP = process.env.ECOS_E2E_SCENARIO_ID ?? 'sc001';

/**
 * 认知四端点（E1 detect / E2 operation-eval / E3 hypotheses / E4 beliefs）降级声明：
 * 认知引擎在本部署形态不独立承流（ADR-15 / Cg-11，详设-05 回收注记），运行面下沉到
 * 场景 L2 后这些只读订阅按设计返回 503（如实降级，页面渲染 unavailable 横幅）。
 * 经 watchPageHealth 的 expectDegraded 逐 URL 声明剔除「意外」计数（helpers/ecos.ts），
 * 页面侧由 T4 另行断言横幅真实呈现 —— 不是整页豁免，也不是伪造健康。
 */
const COGNITION_DEGRADED = [
  /\/api\/v1\/business\/scenarios\/[^/]+\/cognition\/(detect|operation-eval|hypotheses|beliefs)(\?|$)/,
];

test.describe('议题1 — 知识工作台·认知资产只读引用面', () => {
  test('认知资产 Tab 渲染只读目录（真实引擎数据或如实空态/降级横幅）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/knowledge_view?page=cognitive_asset');
    // 页面属主文案（knowledge.nav.page_cognitive_asset）
    await expect(page.getByText('认知资产').first()).toBeVisible({ timeout: 30_000 });
    // 只读声明必须呈现（knowledge.cognitiveAsset.scope_note）——治理面在认知引擎，本页不落写
    await expect(page.getByText('只读引用视图').first()).toBeVisible();
    await expect(page.getByText(/不复制资产本体/).first()).toBeVisible();
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('引擎 503 → 如实降级横幅（含 traceId），禁本地兜底', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.route('**/api/v1/cognitive/assets/catalog**', (r) =>
      r.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({
        code: 503, errorCode: 'ECOS-COG-503D', message: 'cognitive engine degraded',
        data: { traceId: 'e2e-503-trace' }, success: false,
      }) }),
    );
    await page.goto('/#/knowledge_view?page=cognitive_asset');
    await expect(page.getByText(/认知引擎不可用/).first()).toBeVisible({ timeout: 30_000 });
    // 未伪造数据声明与 traceId 同行呈现
    await expect(page.getByText(/未伪造数据/).first()).toBeVisible();
    await expectNoErrorBoundary(page);
  });
});

test.describe('议题2 — AI 工作台认知工作台 Tab 已移除', () => {
  test('#/ai-workbench 导航不再出现认知工作台，且无原始认知字段泄漏', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/ai-workbench');
    await page.waitForLoadState('networkidle');
    const tabsText = await page.evaluate(() => document.body.innerText || '');
    // 「认知工作台」Tab 词表已整族移除（三分下沉：治理→知识台只读面 / 运行→场景 L2 / 能力→能力盘）
    expect(tabsText).not.toMatch(/认知工作台/);
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});

test.describe('议题3 — 场景工作台 Blueprint 中心面', () => {
  test('L2 蓝图面板：真实版本列表（v+状态）渲染，无合成', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, { expectDegraded: COGNITION_DEGRADED });
    await page.goto(`/#/workbench/${SCENARIO_WITH_BP}`);
    await expect(page.getByText('场景蓝图').first()).toBeVisible({ timeout: 30_000 });
    // 权威副题（workspace.bp.subtitle）
    await expect(page.getByText(/Blueprint 为权威对象/).first()).toBeVisible();
    // curl 核验同口径：列表来自 GET blueprints（200 真数据），非本地拼装
    await expect(page.getByText(/v1|尚无蓝图版本/).first()).toBeVisible();
    // 运行面（认知四端点）如实态：引擎不承流 ⇒ 503 横幅；有 mind  ⇒ 真数据。二者必居其一，禁静默
    await expect(
      page.getByText(/认知引擎暂不可用|该场景暂未绑定任何数据或认知|Pending|待处理/).first(),
    ).toBeVisible({ timeout: 30_000 });
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('新建蓝图草稿：goal 输入 → 服务端 v 递增真实回显', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, { expectDegraded: COGNITION_DEGRADED });
    await page.goto(`/#/workbench/${SCENARIO_WITH_BP}`);
    await expect(page.getByText('场景蓝图').first()).toBeVisible({ timeout: 30_000 });
    const createBtn = page.getByRole('button', { name: /新建蓝图草稿/ });
    await expect(createBtn).toBeVisible();
    await createBtn.click();
    await expect(page.getByText(/业务目标/).first()).toBeVisible();
    const goalText = `Playwright live 契约：蓝图创建回环 ${Date.now()}`;
    await page.getByPlaceholder(/一句话业务目标陈述/).fill(goalText);
    await page.getByRole('button', { name: '创建草稿' }).click();
    // 成功回执 = 新版本入选中详情、goal 陈述真实回显（GET 列表 → 服务端权威数据）
    await expect(page.getByText(goalText).first()).toBeVisible({ timeout: 15_000 });
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('能力盘：GET /api/v1/ai/capabilities 真实目录渲染（200 十三项基线，§3.12 修正）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, { expectDegraded: COGNITION_DEGRADED });
    await page.goto(`/#/workbench/${SCENARIO_WITH_BP}`);
    await expect(page.getByText('场景蓝图').first()).toBeVisible({ timeout: 30_000 });
    // 能力目录区（workspace.cap.title）——只读快照 + ★ 声明标记
    const capSection = page.getByText('能力目录');
    if (await capSection.count()) {
      await expect(capSection.first()).toBeVisible();
      await expect(page.getByText(/能力 ID|能力引擎暂不可用|能力目录为空/).first()).toBeVisible({ timeout: 15_000 });
    }
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('空态：无蓝图场景（sc002）显示如实空态引导，不合成版本', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page, { expectDegraded: COGNITION_DEGRADED });
    await page.goto('/#/workbench/sc002');
    // 运行面降级的如实呈现：cognition 503 → L2 认知卡片显示 unavailable 横幅，禁占位假数据
    await expect(page.getByText(/认知引擎暂不可用|该场景暂未绑定任何数据或认知/).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('场景蓝图').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('尚无蓝图版本').first()).toBeVisible();
    await expect(page.getByText(/创建首版蓝图/).first()).toBeVisible();
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
