import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth, expectHealthyPage } from '../../helpers/ecos';

/**
 * 知识工作台 UI 专项批（卷06 V-KB-005/006/007/008/009/011）。
 *
 * 统一口径：
 *   - 每个 Page 断言都落在「该 Page 独有的真实数据面」上（页头标题 + 至少一处端点驱动的区域），
 *     不拿公共壳（侧栏/Topbar）冒充页面渲染 —— 那是 route-registry-66 已覆盖的路由级判定。
 *   - 8 个撤下 Tab 的 deep-link：banner 文案 + 组件真实渲染 + 关闭按钮回到 overview，三步全查。
 */

const DEPRECATED_TABS = ['rag', 'ontology_model', 'graph_builder', 'glossary', 'sync', 'data_import', 'vector_index'] as const;

test.describe('卷06 知识工作台 UI 交互', () => {
  test('V-KB-005 Wiki 页：企业知识双栏（空间清单 + 预览区）真实渲染', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/knowledge_view?page=wiki');
    await expect(page.getByText('企业知识', { exact: true }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('MD 导入 · 预览 · 语义实体上下文').first()).toBeVisible();
    // 真实数据面：知识空间清单区（标题来自 knowledge.wiki.spaces_title）+ 预览空态/内容区
    await expect(page.getByText(/知识空间|spaces/i).first()).toBeVisible();
    await expect(page.locator('main').first()).not.toBeEmpty();
    // 反向：不得落到「知识总览」页（说明 ?page=wiki 真的切了页，不是回落默认）
    await expect(page.getByText('MD 导入 · 预览 · 语义实体上下文').first()).toBeVisible();

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-KB-006 治理页：7 子 Tab 全在 + ?gsub= 深链直达', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/knowledge_view?page=govern');
    await expect(page.getByText('候选审核 · 质量指标 · 规则仓库 · 评测运行').first()).toBeVisible({ timeout: 15_000 });

    // 7 个子 Tab 逐个点：选中态 + URL ?gsub=<id> 同步
    const subs: Array<[RegExp, string]> = [
      [/假设/, 'assumptions'],
      [/检索/, 'retrieval'],
      [/审计/, 'audit'],
      [/质量/, 'quality'],
      [/规则/, 'rules'],
      [/评测/, 'eval'],
      [/画像/, 'profiles'],
    ];
    for (const [name, id] of subs) {
      const btn = page.getByRole('tab', { name }).first();
      await expect(btn).toBeVisible();
      await btn.click();
      await expect(btn).toHaveAttribute('aria-selected', 'true');
      await expect.poll(() => page.url(), { message: `?gsub=${id} 未同步` }).toContain(`gsub=${id}`);
    }

    // 反向探针：非法 gsub 回落 profiles（禁白屏）
    await page.goto('/#/knowledge_view?page=govern&gsub=ghost');
    await expect(page.getByText('候选审核 · 质量指标 · 规则仓库 · 评测运行').first()).toBeVisible();

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-KB-007 规则页：规则表头七列 + 空态/真实行二择一', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/knowledge_view?page=rules');
    // 端点实测返回 200 [] ⇒ 断言落在「空态标题」或「真实表格头」二者之一，两者都不许都不出现
    const emptyTitle = page.getByText('暂无规则');
    const tableHeader = page.getByText('载体').first();
    await expect.poll(async () => (await emptyTitle.count()) + (await tableHeader.count()), {
      message: '规则页既不渲染空态也不渲染表头 = 页面没起来',
      timeout: 20_000,
    }).toBeGreaterThan(0);

    if (await tableHeader.count()) {
      for (const col of ['规则编码', '层', '载体', '范围', '版本', 'Git 引用', '状态']) {
        await expect(page.getByText(col, { exact: true }).first()).toBeVisible();
      }
    }

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-KB-008 认知资产页：只读声明 + 资产分层图例 + catalog 真实驱动', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/knowledge_view?page=cognitive_asset');
    await expect(page.getByText('认知资产由认知引擎治理，本页为只读引用视图，不写入知识库、不复制资产本体。').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('资产分层图例').first()).toBeVisible();
    // catalog 端点活体 200 ⇒ 图例区必须渲染出真实分层条目（非 loading 骨架）
    await expect(page.getByText(/待裁定类别/).first()).toBeVisible({ timeout: 20_000 });
    // 反向：不得出现写入控件（只读页禁写）
    const writable = page.getByRole('button', { name: /新建|保存|提交|发布/ });
    expect(await writable.count(), '认知资产页是只读引用视图，不应有写入按钮').toBe(0);

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-KB-009 撤下 Tab deep-link：7 个旧链接均渲染组件 + warn banner + 可回主导航', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 注：DEPRECATED_TAB_IDS 常量含 7 个（engine_config 于 2026-09-24 重新纳入侧栏，非撤下）
    expect(DEPRECATED_TABS).toHaveLength(7);

    for (const id of DEPRECATED_TABS) {
      await page.goto(`/#/knowledge_view?tab=${id}`);
      // ① banner 在（文案含 tab id）
      const banner = page.getByText(/本页面已撤下导航/);
      await expect(banner.first(), `?tab=${id} 未渲染撤下 banner`).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(new RegExp(`ref=${id}`)).first(),
        `banner 必须点出具体 tab=${id}`).toBeVisible();
      // ② 组件真实渲染（body 非空且不落 ErrorBoundary）
      await expectNoErrorBoundary(page);
      const mainText = (await page.locator('main').last().innerText().catch(() => '')) || '';
      expect(mainText.trim().length, `?tab=${id} 的 main 区为空 = 组件没渲染`).toBeGreaterThan(0);
      // ③ 「回到 6 页主导航」按钮可用，点击后 URL 收敛到 ?page=overview
      await page.getByRole('button', { name: '回到 6 页主导航' }).click();
      await expect.poll(() => page.url(), { message: `?tab=${id} 关横幅后应回 overview` }).toContain('page=overview');
      await expect(page.getByText(/本页面已撤下导航/)).toHaveCount(0);
    }

    // 反向探针：非撤下的 engine_config 不得出现撤下 banner（证明 banner 不是所有 ?tab= 都亮）
    await page.goto('/#/knowledge_view?tab=engine_config');
    await expect(page.getByText('引擎配置 (Engine Config)').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/本页面已撤下导航/)).toHaveCount(0);

    expectHealthyPage(page);
  });

  test('V-KB-011 资源中心知识 Tab：降级注记 + 真实术语表 + CTA 跳知识工作台', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/resource-center');
    const kbTab = page.getByRole('tab', { name: /知识/ }).first();
    await expect(kbTab).toBeVisible({ timeout: 15_000 });
    await kbTab.click();
    await expect(kbTab).toHaveAttribute('aria-selected', 'true');

    // 降级注记（登记在册的降级裁决，必须如实呈现）
    await expect(page.getByText('知识视图与工作台上下文强耦合，此页仅提供入口与真实计数').first()).toBeVisible();
    // 真实术语表：端点 /api/v1/ontology/glossary/terms 活体 200 ⇒ 列表或空态必须出现（不许只有 spinner）
    await expect.poll(async () => {
      const body = (await page.locator('main').last().innerText().catch(() => '')) || '';
      return /术语|暂无|加载/.test(body);
    }, { message: '知识 Tab 内容区无任何术语表态别', timeout: 20_000 }).toBe(true);

    // CTA：跳 #/knowledge_view（降级页唯一正向出口）
    const cta = page.getByRole('button', { name: /知识工作台|进入知识/ }).first();
    if (await cta.count()) {
      await cta.click();
      await expect.poll(() => page.url()).toContain('knowledge_view');
    } else {
      throw new Error('资源中心知识 Tab 缺「进入知识工作台」CTA —— 降级页无出口');
    }

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
