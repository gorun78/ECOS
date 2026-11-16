import { expect, test, type Page } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth, expectHealthyPage } from '../../helpers/ecos';

/**
 * 控制面板 UI 交互专项批（卷08 V-CTRL-SEC-006 / DICT-004~006 / GOV-004）。
 *
 * 判定纪律：
 *   - 每条用例都成对给出「正向可达」+「反向不成立」两步（切换后状态不丢 vs 切走后旧面板不在 DOM；
 *     防重复提交 = 请求数恒 1 vs 无禁用则必然 >=2）。
 *   - 选择器全部取自源码实际渲染面（role/aria/title/i18n 文案），不写 CSS 猜测；
 *     文案断言用 .first() 容忍同页多处重复标签。
 */

const TAB_LABEL = {
  prevent: '身份与访问',
  detect: '安全策略',
  policies: '策略控制面',
  audit: '安全审计',
  auditQuery: '审计查询',
} as const;

async function tabButton(page: Page, label: string) {
  return page.getByRole('tab', { name: new RegExp(label) });
}

/**
 * 删除确认模态的作用域。
 * 页面上同时存在行内「删除」图标（title=删除）与模态确认「删除」按钮，按 role+name 会 strict violation，
 * 故所有模态内断言都必须先落到这个容器上。
 */
function deleteModal(page: Page) {
  return page.locator('div.fixed.inset-0').filter({ hasText: '确认删除' }).last();
}

test.describe('卷08 控制面板 UI 交互', () => {
  test('V-CTRL-SEC-006 安全中心五 Tab 互切：URL ?tab= 同步且面板互斥', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/security-center');
    await expect(await tabButton(page, TAB_LABEL.prevent)).toHaveAttribute('aria-selected', 'true');

    // 正向：逐个点击五 Tab → aria-selected 收敛到该 Tab + URL 带 ?tab=<id>
    const seq: Array<[string, string]> = [
      [TAB_LABEL.detect, 'detect'],
      [TAB_LABEL.policies, 'policies'],
      [TAB_LABEL.audit, 'audit'],
      [TAB_LABEL.auditQuery, 'audit-query'],
      [TAB_LABEL.prevent, 'prevent'],
    ];
    for (const [label, id] of seq) {
      await (await tabButton(page, label)).click();
      await expect(await tabButton(page, label)).toHaveAttribute('aria-selected', 'true');
      await expect.poll(() => page.url(), { message: `${label} 点击后 URL 应带 tab=${id}` })
        .toContain(`tab=${id}`);
      // 反向：其它四个 Tab 必须同时为未选中（互斥，不是「都高亮」）
      for (const [otherLabel] of seq) {
        if (otherLabel === label) continue;
        await expect(await tabButton(page, otherLabel)).toHaveAttribute('aria-selected', 'false');
      }
    }

    // 反向探针（刷新保态 / 非法值回落），口径同 SecurityCenterLayout.normalizeTabParam
    await page.goto('/#/security-center?tab=audit');
    await expect(await tabButton(page, TAB_LABEL.audit)).toHaveAttribute('aria-selected', 'true');
    await page.reload();
    await expect(await tabButton(page, TAB_LABEL.audit)).toHaveAttribute('aria-selected', 'true');
    await page.goto('/#/security-center?tab=ghost-tab');
    await expect(await tabButton(page, TAB_LABEL.prevent)).toHaveAttribute('aria-selected', 'true');

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-CTRL-DICT-005 DictLeftPanel 真实渲染：数据表列表 + 字典项分组树', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    // 前置：经 API 造一张真实表（UI 断言必须落在真实数据上，不靠种子假设）
    const created = await request.post('/api/v1/dict/tables', {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: { name: 'e2e_ui_dict_tbl', nameZh: 'E2E UI 表', schema: 'ecos_data', description: 'ui probe', source: 'PostgreSQL' },
    });
    expect(created.ok(), `建表失败 HTTP ${created.status()}`).toBeTruthy();
    const createdId = (await created.json())?.data?.id as string;
    expect(createdId).toBeTruthy();

    try {
      await page.goto('/#/dict');
      // 两模式切换按钮在位（DictLeftPanel 顶部 pill）
      const tableTab = page.getByRole('button', { name: new RegExp('数据表管理') });
      const dictTab = page.getByRole('button', { name: new RegExp('字典项管理') });
      await expect(tableTab).toBeVisible();
      await expect(dictTab).toBeVisible();

      // 模式 A（数据表）：真实行可见（表名精确匹配我方创建的那张）
      await tableTab.click();
      await expect(page.getByText('e2e_ui_dict_tbl', { exact: true })).toBeVisible({ timeout: 15_000 });
      // 反向：不存在的表名不得出现（说明列表不是全量渲染）
      await expect(page.getByText('e2e_ui_dict_tbl_not_exist', { exact: true })).toHaveCount(0);

      // 模式 B（字典项）：G1~G5 分组树根节点 + 「个类型」计数真实
      await dictTab.click();
      await expect(page.getByText(/个类型/).first()).toBeVisible({ timeout: 15_000 });
      const groupRows = page.locator('div[class*="border-b"] >> text=/类型 · .* 项/');
      await expect.poll(() => groupRows.count(), { message: '字典分组树至少渲染一个分组行' }).toBeGreaterThan(0);
      // 反向：切到字典模式后，数据表行必须消失（两模式互斥，不是叠加）
      await expect(page.getByText('e2e_ui_dict_tbl', { exact: true })).toHaveCount(0);

      await expectNoErrorBoundary(page);
      expectHealthyPage(page);
    } finally {
      await request.delete(`/api/v1/dict/tables/${createdId}`, { headers: { Authorization: `Bearer ${token}` } });
    }
  });

  test('V-CTRL-DICT-004a 删除确认 — table：模态点明类型与表名，取消不删、确认真删', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    const auth = { Authorization: `Bearer ${token}` };
    const jsonAuth = { ...auth, 'Content-Type': 'application/json' };
    await injectAuth(context, token);
    watchPageHealth(page);

    const run = Date.now().toString(36);
    const tbl = `e2e_del_tbl_${run}`;
    const tRes = await request.post('/api/v1/dict/tables', {
      headers: jsonAuth,
      data: { name: tbl, nameZh: 'E2E 删除表', schema: 'ecos_data', description: 'del probe', source: 'PostgreSQL' },
    });
    expect(tRes.ok()).toBeTruthy();
    const tableId = (await tRes.json())?.data?.id as string;

    try {
      await page.goto('/#/dict');
      await page.getByRole('button', { name: /数据表管理/ }).click();
      const row = page.getByText(tbl, { exact: true });
      await expect(row).toBeVisible({ timeout: 20_000 });

      await row.locator('xpath=ancestor::div[contains(@class,"flex-col")][1]').getByTitle('删除').click();
      const modal = deleteModal(page);
      await expect(modal).toBeVisible();
      // 三类型共用同一模态，能区分类型的只有这句文案
      await expect(modal).toContainText(`确定要删除数据表「${tbl}」`);

      // 反向：取消后行仍在 + 后端仍可取到
      await modal.getByRole('button', { name: '取消' }).click();
      await expect(modal).toHaveCount(0);
      await expect(row).toBeVisible();
      const stillThere = await request.get(`/api/v1/dict/tables/${tableId}`, { headers: auth });
      expect(stillThere.status(), '取消后后端不应删除').toBe(200);

      // 正向：确认后列表消失 + 后端 404
      await row.locator('xpath=ancestor::div[contains(@class,"flex-col")][1]').getByTitle('删除').click();
      await deleteModal(page).getByRole('button', { name: '删除' }).click();
      await expect(page.getByText(tbl, { exact: true })).toHaveCount(0, { timeout: 20_000 });
      const gone = await request.get(`/api/v1/dict/tables/${tableId}`, { headers: auth });
      expect(gone.status(), '确认删除后后端应已无该表').toBe(404);

      await expectNoErrorBoundary(page);
      expectHealthyPage(page);
    } finally {
      await request.delete(`/api/v1/dict/tables/${tableId}`, { headers: auth });
    }
  });

  test('V-CTRL-DICT-004b 删除确认 — column：字段级模态与真实删除', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    const auth = { Authorization: `Bearer ${token}` };
    const jsonAuth = { ...auth, 'Content-Type': 'application/json' };
    await injectAuth(context, token);
    watchPageHealth(page);

    const run = Date.now().toString(36);
    const tbl = `e2e_col_tbl_${run}`;
    const tRes = await request.post('/api/v1/dict/tables', {
      headers: jsonAuth,
      data: { name: tbl, nameZh: '占位表', schema: 'ecos_data', source: 'PostgreSQL' },
    });
    expect(tRes.ok()).toBeTruthy();
    const tableId = (await tRes.json())?.data?.id as string;
    const cRes = await request.post(`/api/v1/dict/tables/${tableId}/columns`, {
      headers: jsonAuth,
      data: { name: 'e2e_ui_col', type: 'VARCHAR', length: 32, nullable: true, primaryKey: false, description: 'probe' },
    });
    expect(cRes.ok(), `建字段失败 HTTP ${cRes.status()} ${await cRes.text()}`).toBeTruthy();

    try {
      await page.goto('/#/dict');
      await page.getByRole('button', { name: /数据表管理/ }).click();
      await page.getByText(tbl, { exact: true }).click();
      await expect(page.getByText('字段列表')).toBeVisible({ timeout: 20_000 });

      const colRow = page.getByText('e2e_ui_col', { exact: true }).first();
      await expect(colRow).toBeVisible({ timeout: 20_000 });
      await colRow.locator('xpath=ancestor::div[contains(@class,"grid")][1]').getByTitle('删除').click();
      const modal = deleteModal(page);
      await expect(modal).toContainText('确定要删除字段「e2e_ui_col」');
      await modal.getByRole('button', { name: '删除' }).click();
      await expect(modal).toHaveCount(0);

      const after = await request.get(`/api/v1/dict/tables/${tableId}`, { headers: auth });
      const cols = ((await after.json())?.data?.columns ?? []) as Array<{ name: string }>;
      expect(cols.some((c) => c.name === 'e2e_ui_col'), '确认删除后后端字段应已消失').toBe(false);

      await expectNoErrorBoundary(page);
      expectHealthyPage(page);
    } finally {
      await request.delete(`/api/v1/dict/tables/${tableId}`, { headers: auth });
    }
  });

  test('V-CTRL-DICT-004c 删除确认 — dictItem：字典项级模态与真实删除', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    const auth = { Authorization: `Bearer ${token}` };
    const jsonAuth = { ...auth, 'Content-Type': 'application/json' };
    await injectAuth(context, token);
    watchPageHealth(page);

    const run = Date.now().toString(36);
    const dictType = `e2e_ui_dict_type_${run}`;
    const iRes = await request.post('/api/v1/system/dict', {
      headers: jsonAuth,
      data: { dictType, dictCode: 'e2e_ui_item', dictLabel: 'E2E字典项', status: 'active', sortOrder: 999 },
    });
    expect(iRes.ok(), `建字典项失败 HTTP ${iRes.status()} ${await iRes.text()}`).toBeTruthy();

    try {
      await page.goto('/#/dict');
      await page.getByRole('button', { name: /字典项管理/ }).click();
      await page.getByPlaceholder('搜索字典类型...').fill(dictType);
      const dtRow = page.getByText(dictType, { exact: true });
      await expect(dtRow.first(), `字典类型 ${dictType} 未出现在左树`).toBeVisible({ timeout: 20_000 });
      await dtRow.first().click();
      // D82 回归守卫：左树搜索词不得连带过滤右侧字典项列表
      // （改前共用一个 state，搜类型名必然把右表过滤成「未找到匹配」）
      await expect(page.getByText('未找到匹配', { exact: false })).toHaveCount(0);

      const itemCell = page.getByText('E2E字典项', { exact: true });
      await expect(itemCell).toBeVisible({ timeout: 20_000 });
      await itemCell.locator('xpath=ancestor::div[contains(@class,"grid")][1]').getByTitle('删除').click();
      const modal = deleteModal(page);
      // 既有实现口径：dictItem 复用 DeleteConfirm 时 targetType 映射成 column ⇒ 文案为「字段」。
      // 这是 D80（模态类型标签与实体不符）的现状证据，本用例先把「能弹、能删、后端生效」钉住。
      await expect(modal).toContainText('确定要删除字段「E2E字典项」');
      await modal.getByRole('button', { name: '删除' }).click();
      await expect(page.getByText('E2E字典项', { exact: true })).toHaveCount(0, { timeout: 20_000 });

      const after = await request.get(`/api/v1/system/dict/${dictType}`, { headers: auth });
      const items = ((await after.json())?.data ?? []) as Array<{ dictCode: string }>;
      expect(items.some((x) => x.dictCode === 'e2e_ui_item'), '确认删除后该字典项应已不在 active 列表').toBe(false);

      await expectNoErrorBoundary(page);
      expectHealthyPage(page);
    } finally {
      await request.delete(`/api/v1/system/dict/${dictType}/e2e_ui_item`, { headers: auth });
    }
  });

  test('V-CTRL-DICT-006 setSaving 防重复提交：连点保存只发一次写请求', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    const writes: string[] = [];
    page.on('request', (req) => {
      const u = req.url();
      if (req.method() === 'POST' && /\/api\/v1\/dict\/tables$/.test(u)) writes.push(u);
    });

    await page.goto('/#/dict');
    await page.getByRole('button', { name: new RegExp('数据表管理') }).click();
    await page.getByRole('button', { name: /新建数据表/ }).click();
    // 精确 placeholder：左栏搜索框是「搜索表名...」，用 /表名/ 会误填搜索框（首轮实测踩坑）
    await page.getByPlaceholder('例如：user_info').fill('e2e_ui_dupguard');

    const saveBtn = page.getByRole('button', { name: /保存表信息/ });
    await expect(saveBtn).toBeEnabled();

    // 三连点（不 await 逐次：模拟用户狂点）
    await Promise.all([saveBtn.click(), saveBtn.click().catch(() => {}), saveBtn.click().catch(() => {})]);
    // 保存完成后按钮从「保存中(disabled)」恢复，等列表出现我方表名即完成
    await expect(page.getByText('e2e_ui_dupguard', { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await page.waitForTimeout(1500); // 给潜在的第二次请求留出发送窗口

    expect(writes.length, `重复提交：期望 1 次 POST /tables，实得 ${writes.length} 次`).toBe(1);

    // 清理
    const list = await request.get('/api/v1/dict/tables', { headers: { Authorization: `Bearer ${token}` } });
    const items = ((await list.json())?.data ?? []) as Array<{ id: string; name: string }>;
    for (const it of items.filter((x) => x.name === 'e2e_ui_dupguard')) {
      await request.delete(`/api/v1/dict/tables/${it.id}`, { headers: { Authorization: `Bearer ${token}` } });
    }

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-CTRL-GOV-004 390×844 视口下平台治理 Tab：不裁剪 + 溢出时横滚机制真的生效', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/#/platform-governance');
    const tablist = page.locator('[role="tablist"]').first();
    await expect(tablist).toBeVisible({ timeout: 15_000 });

    // ① 现实态（实测 3 个 Tab 合计 263px，390px 视口放得下）：横滚样式在位，
    //    且三个 Tab 全部完整可见、未被裁剪 —— 这是用户可感知的真实要求。
    const metrics = await tablist.evaluate((el) => {
      const cs = getComputedStyle(el);
      return {
        overflowX: cs.overflowX,
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth,
        tabs: Array.from(el.querySelectorAll('[role="tab"]')).map((tb) => {
          const r = tb.getBoundingClientRect();
          return { text: tb.textContent?.trim() ?? '', left: r.left, right: r.right, width: r.width };
        }),
        viewportWidth: window.innerWidth,
      };
    });
    expect(metrics.overflowX, 'Tab 容器必须声明 overflow-x-auto（横滚能力的来源）').toMatch(/auto|scroll/);
    expect(metrics.tabs, '三个 Tab 应全部渲染').toHaveLength(3);
    for (const tb of metrics.tabs) {
      expect(tb.width, `Tab「${tb.text}」被压缩为 0 宽`).toBeGreaterThan(0);
      expect(tb.left, `Tab「${tb.text}」左侧越出视口`).toBeGreaterThanOrEqual(-0.5);
      expect(tb.right, `Tab「${tb.text}」右侧越出视口（被裁剪）`).toBeLessThanOrEqual(metrics.viewportWidth + 0.5);
    }
    // 反向：当前内容未溢出（scrollWidth == clientWidth）——记下这一点，下面用受控溢出证明机制不是摆设
    expect(metrics.scrollWidth, '前置确认：390 视口下 3 个 Tab 恰好放得下，尚未产生溢出')
      .toBeLessThanOrEqual(metrics.clientWidth + 1);

    // ② 受控溢出探针：把容器压到 120px 宽（不改组件、只改测试侧样式），
    //    若 overflow-x-auto 只是写了一行 class 而实际被 flex 挤扁，这里就滚不动。
    await tablist.evaluate((el) => {
      (el as HTMLElement).style.maxWidth = '120px';
      (el as HTMLElement).dataset.probe = '1';
    });
    const overflow = await tablist.evaluate((el) => ({ sw: el.scrollWidth, cw: el.clientWidth }));
    expect(overflow.sw, `受控溢出下应可滚：scrollWidth(${overflow.sw}) > clientWidth(${overflow.cw})`)
      .toBeGreaterThan(overflow.cw);

    // 滚到最右 → 最后一个 Tab 完整进入容器可视区；滚回最左 → 移出可视区（证明位移真实发生）
    const lastTab = page.getByRole('tab', { name: /平台自检/ });
    await tablist.evaluate((el) => { el.scrollLeft = el.scrollWidth; });
    await expect(lastTab).toBeInViewport({ timeout: 5000 });
    await tablist.evaluate((el) => { el.scrollLeft = 0; });
    await expect(lastTab).not.toBeInViewport({ timeout: 5000 });

    // 受控溢出期间点击仍然可达（横滚不是只让人看，功能也得可用）
    await tablist.evaluate((el) => { el.scrollLeft = el.scrollWidth; });
    await lastTab.click();
    await expect(lastTab).toHaveAttribute('aria-selected', 'true');
    await expect.poll(() => page.url()).toContain('tab=diagnostics');

    // ③ 桌面回归：1600 宽下容器自然不溢出（横向布局没被移动端样式误伤）
    await page.setViewportSize({ width: 1600, height: 1000 });
    await page.reload();
    const wide = await page.locator('[role="tablist"]').first().evaluate((el) => ({
      scrollWidth: el.scrollWidth, clientWidth: el.clientWidth,
    }));
    expect(wide.scrollWidth, '宽视口下不应出现溢出滚动').toBeLessThanOrEqual(wide.clientWidth + 1);

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
