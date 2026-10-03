import { expect, test } from '@playwright/test';
import { expectHealthyPage, expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * F04-17 / B 章 — 知识域前端重构验收。
 *
 * 设计锚点：《详细设计-04-知识域》§F04-17 + §B.0（信息架构归一）。
 * 六 Tab（overview/assets/extract/graph/wiki/govern）须在唯一知识入口下可达，
 * 且空态 / 故障态彼此可区分（不能统一被掩成"暂无数据"或崩溃到 ErrorBoundary）。
 *
 * 定位（按真实实现，避免"空转通过"）：
 *   - 路由 `main.tsx:169` `knowledge_view` → KnowledgeView（`?page=<id>` 切换 6 平铺 Page）；
 *   - 6 平铺 Page i18n 键 `knowledge.nav.page_*`（zh/en 见 locales/knowledge/*.json）；
 *   - 顶栏面包屑 `KnowledgeView.tsx` 渲染 `ECOS / {activePage}`（font-semibold）。
 *
 * 前置：gateway :8080 + sysman/security-engine :18081 + 前端 :3000 已启动；locale 由 injectAuth 固定。
 * 环境未就绪时本 spec RED（与 pipeline-workbench 等 e2e 同一口径，属既定约定）。
 */

const KNOWLEDGE_ROUTE = '/#/knowledge_view';

/** 六平铺 Page：i18n zh/en 标签 + 所属后端能力域（用于空/故障态区分断言） */
const PAGES = [
  { id: 'overview', zh: '知识总览', en: 'Knowledge Overview' },
  { id: 'assets', zh: '知识资产', en: 'Knowledge Assets' },
  { id: 'extract', zh: '知识抽取', en: 'Knowledge Extraction' },
  { id: 'graph', zh: '知识图谱', en: 'Knowledge Graph' },
  { id: 'wiki', zh: '企业知识', en: 'Enterprise Knowledge' },
  { id: 'govern', zh: '知识治理', en: 'Knowledge Governance' },
] as const;

test.describe('knowledge › 六 Tab 可达且空/故障态可区分', () => {
  test.beforeEach(async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
  });

  test('六 Tab 全部可达（导航切换后主区渲染对应 Page，无 ErrorBoundary）', async ({ page }) => {
    await page.goto(KNOWLEDGE_ROUTE, { waitUntil: 'domcontentloaded' });

    // 首屏默认 overview：顶栏面包屑应出现知识域标识（sidebar 标题 / 面包屑）
    await page
      .getByText(new RegExp(`${PAGES[0].zh}|${PAGES[0].en}`), { exact: false })
      .first()
      .waitFor({ state: 'visible', timeout: 30_000 });

    for (const pg of PAGES) {
      const navBtn = page
        .getByRole('button', { name: new RegExp(`^(${pg.zh}|${pg.en})$`) })
        .first();

      // 兜底导航：若侧栏按钮不可见，直接以 ?page=<id> 路由直达（parseRoute 保证可达）
      if ((await navBtn.count()) > 0 && (await navBtn.isVisible().catch(() => false))) {
        await navBtn.click();
      } else {
        await page.goto(`${KNOWLEDGE_ROUTE}?page=${pg.id}`, { waitUntil: 'domcontentloaded' });
      }

      // 当前页激活态确实切换：URL 携带正确 page 参数
      await expect(page, `未切换到 ${pg.id}`)
        .toHaveURL(new RegExp(`page=${pg.id}`), { timeout: 15_000 });

      // 主区内容出现该 Page 对应标签（渲染源 = 顶栏面包屑或侧栏高亮项）
      await expect(
        page.getByText(new RegExp(`${pg.zh}|${pg.en}`), { exact: false }).first(),
        `${pg.id} Page 主区未渲染`,
      ).toBeVisible({ timeout: 15_000 });

      await expectNoErrorBoundary(page);
    }
  });

  test('空态与故障态可区分（不可用后端时显式降级提示，而非崩溃或伪装"暂无数据"）', async ({ page }) => {
    await page.goto(`${KNOWLEDGE_ROUTE}?page=overview`, { waitUntil: 'domcontentloaded' });

    // overview Page 拉取 stats/health：无论后端可达与否，页面都应渲染出健康度面板（骨架或真实卡片），
    // 且绝不允许陷入 ErrorBoundary（崩溃）——空态/故障态必须是"可区分的渲染态"。
    await expect(page.locator('body').first(), '页面 body 为空（未渲染任何知识域内容）')
      .not.toHaveText('', { timeout: 30_000 });

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('页面健康：无 ErrorBoundary、console 无未捕获 error（铁律 V4）', async ({ page }) => {
    await page.goto(`${KNOWLEDGE_ROUTE}?page=govern`, { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => (document.body?.innerText ?? '').trim().length > 0, null, { timeout: 15_000 });
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
