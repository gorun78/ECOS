import { expect, test } from '@playwright/test';
import { expectHealthyPage, expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 迁移自 ecos-tests/data-workbench-pipeline-smoke.mjs 的浏览器面（断言 J / K / L 对应）。
 *
 * 【迁移时修正的历史失真】原 `.mjs` 打开 `/#/databench/pipeline` 并 `waitForSelector('table')`，
 *   且把"找不到表格"当空态放行 —— 实测该路由不存在（`main.tsx:139` 只有 `data-workbench`），
 *   落地页是「战略目标」，所以 J/K 一直是**空转通过**。
 *   现按真实实现定位：`ecos_frontend/src/pages/DataWorkbenchLayout.tsx:69~75`（Tab 侧栏）+
 *   `tabs/PipelineBuilderTab.tsx:96~145`（列表为按钮项，非 `<table>`）+
 *   `PipelineFlowEditor.tsx:615~632`（NodePalette + ReactFlow 画布）。
 *
 * 前置：gateway :8080 + sysman/security-engine :18081 + 前端 :3000 已启动；locale 由 injectAuth 固定为 zh。
 */

const TAB = { zh: '数据管道', en: 'Data Pipelines' };
const LIST_TITLE = { zh: '管道与同步任务', en: 'Pipelines' };
const PALETTE_TITLE = { zh: '节点工具栏', en: 'Node Palette' };
const EMPTY_HINT = { zh: '暂无管道', en: 'No pipelines' };

test.describe('数据管道构建器（/#/data-workbench → 数据管道 Tab）', () => {
  test.beforeEach(async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/data-workbench', { waitUntil: 'domcontentloaded' });

    const tab = page.getByRole('button', { name: new RegExp(`${TAB.zh}|${TAB.en}`) }).first();
    await expect(tab, '工作台侧栏未出现「数据管道」Tab（dw.tab.pipeline_builder）').toBeVisible({ timeout: 30_000 });
    await tab.click();
    await expect(
      page.getByRole('heading', { name: new RegExp(`${LIST_TITLE.zh}|${LIST_TITLE.en}`) })
        .or(page.getByText(new RegExp(`${LIST_TITLE.zh}|${LIST_TITLE.en}`)))
        .first(),
      '管道列表面板未渲染'
    ).toBeVisible({ timeout: 15_000 });
  });

  test('J 列表加载（空库空态可接受，但列表面板与新建入口必须渲染）', async ({ page }) => {
    const rows = await page.getByText(new RegExp(`${EMPTY_HINT.zh}|${EMPTY_HINT.en}`)).count();
    test.info().annotations.push({ type: 'list-state', description: rows > 0 ? '空态（暂无管道）' : '有数据' });
    await expect(page.getByRole('button', { name: /新建|New|Create/ }).first(), '「新建」入口缺失').toBeVisible();
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('K 新建 → ReactFlow 画布与节点工具栏渲染', async ({ page }) => {
    await page.getByRole('button', { name: /新建|New|Create/ }).first().click();

    await expect(page.locator('.react-flow').first(), 'ReactFlow 画布未渲染').toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByText(new RegExp(`${PALETTE_TITLE.zh}|${PALETTE_TITLE.en}`)).first(),
      'NodePalette 节点工具栏未渲染'
    ).toBeVisible({ timeout: 15_000 });
    expectHealthyPage(page);
  });

  test('L 页面健康（console 无 error、无意外 4xx/5xx，铁律 V4）', async ({ page }) => {
    await page.reload({ waitUntil: 'load' });
    // SPA 重新挂载需要时间：先等首个文本节点出现（超时即判失败），再跑严格断言，避免"未渲染"误判为"空白"
    await page.waitForFunction(() => (document.body?.innerText ?? '').trim().length > 0, null, { timeout: 15_000 });
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
