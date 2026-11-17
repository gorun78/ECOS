import { expect, test } from '@playwright/test';
import { expectHealthyPage, expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../../helpers/ecos';

/**
 * V-WS-001（L1 我的场景）/ V-WS-002（新建场景模态）剩余 UI 腿的活体验证。
 *
 * <p>矩阵此前把这两条降为 PARTIAL，理由是「『新建』开模态 / 折叠区默认态 / 表单校验 / 防重复提交」
 * 只有静态门禁与渲染腿、浏览器内未点验。本 spec 逐腿补上：</p>
 * <ul>
 *   <li>V-WS-001-a 「新建场景」快捷入口确实打开模态（不是跳转占位）；</li>
 *   <li>V-WS-001-b 高级视图折叠区默认收起、点击展开可见、再点收起复不可见；</li>
 *   <li>V-WS-002-a 空名称提交 → info toast 且**不发 POST**（校验前置于网络）；</li>
 *   <li>V-WS-002-b 名称超长按 maxLength=255 截断（浏览器真实执行属性约束）；</li>
 *   <li>V-WS-002-c 模板 chips 预填业务目标与初始口径；</li>
 *   <li>V-WS-002-d 防重复提交：提交窗口内按钮 disabled，连点两次只发 1 个 POST；</li>
 *   <li>V-WS-002-e 真实 POST 创建 → toast + 跳转 L2 + L1 列表出现新卡片（真库留痕后清理）。</li>
 * </ul>
 *
 * <p><b>假绿纪律</b>：d 用例用 page.route 只**接管 POST 的响应时序**（延后 1200ms），
 * GET 列表一律 route.continue() 走真实网络 —— 目的是造出确定的"提交中"窗口，
 * 不宣称后端可用；e 用例走**零拦截真实链路**并在结束时 DELETE 清理自建数据。</p>
 */

const NEW_BTN = /新建场景/;
const NAME_REQUIRED = '请先填写场景名称。';
const ADV_HINT = '面向工程与治理角色的完整视图（默认折叠）。';
const ADV_LINK = '展开技术视图';

/**
 * L1 列表加载收敛上限。dev 库累积 200+ 场景时单次 GET 载荷 ~100KB、卡片渲染 200+ 张，
 * 浏览器实测收敛 ~10s（列表无分页，属已知规模问题，非本条验证目标），故取 45s 留足余量。
 */
const LIST_SETTLE_MS = 45_000;

/** 打开 L1 并等场景列表加载收敛（loading 文案消失），保证后续点击不是点到骨架态。 */
async function openL1(page: Parameters<typeof watchPageHealth>[0]): Promise<void> {
  await page.goto('/#/workbench');
  await expectNoErrorBoundary(page);
  await expect(page.getByText('加载场景列表…')).toHaveCount(0, { timeout: LIST_SETTLE_MS });
}

async function openCreateModal(page: Parameters<typeof watchPageHealth>[0]): Promise<void> {
  await page.getByRole('button', { name: NEW_BTN }).first().click();
  await expect(page.locator('#new-scenario-name')).toBeVisible();
}

test.describe('workbench L1 / 新建场景模态 — UI 级补腿', () => {
  test('V-WS-001-a 快捷入口「新建场景」打开模态（非跳转占位）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await openL1(page);
    const btn = page.getByRole('button', { name: NEW_BTN }).first();
    await expect(btn).toBeVisible();
    // 「直接弹模态，勿做成跳转占位」—— 点击后 URL 不应离开 L1
    await btn.click();
    await expect(page.locator('#new-scenario-name')).toBeVisible();
    expect(page.url()).toContain('#/workbench');
    // Esc 关闭（closeOnEscape 默认 true）后模态必须整体出 DOM，不是隐藏
    await page.keyboard.press('Escape');
    await expect(page.locator('#new-scenario-name')).toHaveCount(0);
    expectHealthyPage(page);
  });

  test('V-WS-001-b 高级视图默认收起，点击展开，再点收起', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    // 折叠区内 Link 指向 /project_workbench（该页 navConfig 缺 labelKey，W201 打 console.error）
    watchPageHealth(page, { expectConsoleErrors: [/\[navConfig\] no tab labelKey/] });

    await openL1(page);
    // 默认收起：条件渲染 ⇒ 提示语与入口链接均不在 DOM
    await expect(page.getByText(ADV_HINT)).toHaveCount(0);
    await expect(page.getByRole('link', { name: ADV_LINK })).toHaveCount(0);

    const toggle = page.getByRole('button', { name: /高级视图/ });
    await expect(toggle).toBeVisible();
    await toggle.click();
    await expect(page.getByText(ADV_HINT)).toBeVisible();
    await expect(page.getByRole('link', { name: ADV_LINK })).toBeVisible();

    await toggle.click();
    await expect(page.getByText(ADV_HINT)).toHaveCount(0);
    await expect(page.getByRole('link', { name: ADV_LINK })).toHaveCount(0);
    expectHealthyPage(page);
  });

  test('V-WS-002-a 空名称提交 → 提示且不发 POST', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    const posts: string[] = [];
    page.on('request', (r) => {
      if (r.method() === 'POST' && r.url().includes('/api/v1/workspace/scenarios')) posts.push(r.url());
    });

    await openL1(page);
    await openCreateModal(page);
    await page.locator('#new-scenario-name').fill('   '); // 空白串也必须被 trim 后拒绝
    await page.getByRole('button', { name: /^确认$/ }).click();

    await expect(page.getByRole('status').filter({ hasText: NAME_REQUIRED })).toBeVisible();
    expect(posts, '校验未过不得发 POST').toHaveLength(0);
    // 模态保持开启，用户可继续修正
    await expect(page.locator('#new-scenario-name')).toBeVisible();
    expectHealthyPage(page);
  });

  test('V-WS-002-b 名称 300 字符按 maxLength=255 截断', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await openL1(page);
    await openCreateModal(page);
    const nameInput = page.locator('#new-scenario-name');
    await nameInput.fill('长'.repeat(300));
    expect((await nameInput.inputValue()).length).toBe(255);
    expectHealthyPage(page);
  });

  test('V-WS-002-c 模板 chips 预填业务目标与初始口径', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await openL1(page);
    await openCreateModal(page);
    // 默认选中「经营预测模板」⇒ 目标与口径已预填
    await expect(page.locator('#new-scenario-goal')).toHaveValue('2027 年利润达成 1.2 亿');
    await expect(page.getByLabel('初始口径')).toHaveValue('cal-finance-v2.3');

    await page.getByRole('button', { name: '成本诊断模板' }).click();
    await expect(page.locator('#new-scenario-goal')).toHaveValue('定位项目成本偏差根因');
    await expect(page.getByLabel('初始口径')).toHaveValue('cal-project-v1.1');

    await page.getByRole('button', { name: '空白场景' }).click();
    await expect(page.getByLabel('初始口径')).toHaveValue('—');
    expectHealthyPage(page);
  });

  test('V-WS-002-d 防重复提交：提交窗口内按钮 disabled，连点只发 1 个 POST', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    let postCount = 0;
    let body = '';
    // 只延迟 POST 的响应以造出确定的"提交中"窗口；其余请求（含 GET 列表）走真实网络
    await page.route('**/api/v1/workspace/scenarios', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      postCount += 1;
      body = route.request().postData() ?? '';
      await new Promise((r) => setTimeout(r, 1200));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, success: true, data: { id: '' } }),
      });
    });

    await openL1(page);
    await openCreateModal(page);
    await page.locator('#new-scenario-name').fill('E2E-DEDUP-UI');
    const confirm = page.getByRole('button', { name: /^确认$/ });
    await confirm.click();
    // 提交中：按钮 disabled（连点第二次不会触发 submit）
    await expect(confirm).toBeDisabled();
    await confirm.click({ force: true });
    await expect(page.getByRole('status').filter({ hasText: '后端未回传新场景 id' })).toBeVisible();

    expect(postCount, '同一次提交只允许 1 个 POST').toBe(1);
    const parsed = JSON.parse(body) as { name?: string; businessGoal?: string };
    expect(parsed.name).toBe('E2E-DEDUP-UI');
    expect(parsed.businessGoal).toBe('2027 年利润达成 1.2 亿');
    expectHealthyPage(page);
  });

  test('V-WS-002-e 真实 POST 创建 → 跳转 L2 → L1 列表出现新卡片（用完即删）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    // 跳转后的 L2 会打 cognition / actions / decisions 三个已声明的降级面
    watchPageHealth(page, {
      expectDegraded: [
        /\/api\/v1\/business\/scenarios\/[^/]+\/cognition\//,
        /\/api\/v1\/workspace\/scenarios\/[^/]+\/actions/,
        /\/api\/v1\/workspace\/scenarios\/[^/]+\/decisions/,
      ],
      expectConsoleErrors: [/cognition|503|COGNITIVE|actions|decisions/i],
    });

    const name = `E2E-L1-${Date.now()}`;
    let createdId = '';
    try {
      await openL1(page);
      await openCreateModal(page);
      await page.locator('#new-scenario-name').fill(name);
      await page.getByRole('button', { name: /^确认$/ }).click();

      await expect(page.getByRole('status').filter({ hasText: `场景「${name}」已创建（草稿）` })).toBeVisible();
      await page.waitForURL(/#\/workbench\/sc_/, { timeout: 20000 });
      createdId = page.url().split('/workbench/')[1].split('/')[0];
      expect(createdId).toMatch(/^sc_/);
      await expect(page.locator('main')).toContainText(name, { timeout: 20000 });

      // L1 列表出现新卡片（onCreated → load() 的刷新腿）
      await page.goto('/#/workbench');
      await expect(page.getByText('加载场景列表…')).toHaveCount(0, { timeout: LIST_SETTLE_MS });
      await expect(page.locator('main').getByText(name, { exact: true })).toBeVisible({ timeout: LIST_SETTLE_MS });
      expectHealthyPage(page);
    } finally {
      if (createdId) {
        const del = await request.delete(`/api/v1/workspace/scenarios/${createdId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        expect(del.ok(), `清理自建场景 ${createdId} 失败 ${del.status()}`).toBeTruthy();
      }
    }
  });
});
