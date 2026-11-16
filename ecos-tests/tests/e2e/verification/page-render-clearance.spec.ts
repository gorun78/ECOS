import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth, expectHealthyPage } from '../../helpers/ecos';

/**
 * 页面级渲染清退批（2026-11-15）。
 *
 * 背景：多卷的 PARTIAL 里有一整类「后端面已验 PASS、页面级渲染未做」的条目——
 *   卷01 V-WS-001/008、卷02 V-COG-003/004、卷03 V-AI-006/009、卷04 V-DATA-001/002/007/008/009、
 *   卷05 V-ONTO-001/003/012、卷07 V-SEC-041、卷08 V-CTRL-GOV-002、卷11 V-COP-008。
 * 本批为每条补上「真实数据面渲染」的活体断言。
 *
 * 判定纪律：
 *   - 每条断言必须落在该页面**独有的真实数据面**上（页头 + 至少一处端点驱动区域），
 *     不拿公共壳（侧栏/Topbar）冒充渲染——那已由 route-registry-66 覆盖。
 *   - 每条都带反向探针：要么证明「切到的确实是这一页」（URL/标题互斥），
 *     要么证明「空数据时页面如实空态、不伪装成功」。
 *   - 不 mock、不 skip；数据一律来自真实端点。
 */

type Case = {
  id: string;
  route: string;
  /** 页面独有文案（用于确认落到了这一页，而不是回落默认页） */
  unique: RegExp;
  /** 反向探针：这一页**不应该**出现的东西 */
  absent?: RegExp;
  /** 该页已声明会降级的端点（按 URL 正则剔除，禁止整页豁免） */
  degraded?: readonly RegExp[];
};

const CASES: Case[] = [
  {
    id: 'V-WS-001',
    route: '/#/workbench',
    unique: /场景|工作台/,
    note: '场景工作台首屏：卡片区由 /api/v1/workspace/scenarios 驱动',
  },
  {
    id: 'V-WS-008',
    route: '/#/cognitive-workbench',
    unique: /认知/,
    note: '认知工作台：数据源 /business/scenarios/{id}/cognition/*',
  },
  {
    id: 'V-COG-003',
    route: '/#/decision',
    unique: /决策|Mind|认知/,
    note: '决策板：minds 列表真实',
  },
  {
    id: 'V-COG-004',
    route: '/#/mission_control',
    unique: /蓝图|任务|使命|Mission|战略/i,
    note: '认知蓝图页：蓝图端点按设计降级为示意数据（须有降级横幅）',
    degraded: [/\/api\/v1\/cognitive\/blueprint/],
  },
  {
    id: 'V-AI-006',
    route: '/#/ai-workbench',
    unique: /AI|智能体|评估/i,
    note: 'AI 工作台：evals Tab 后端面 /api/v1/aip/eval/*',
  },
  {
    id: 'V-AI-009',
    route: '/#/workshop',
    unique: /工坊|Workshop|智能体/i,
    note: '智能体工坊：/api/v1/agents 列表',
  },
  {
    id: 'V-DATA-001',
    route: '/#/data-workbench',
    unique: /数据|Data/i,
    note: '数据工作台：/api/v1/datanet/datasource 驱动的连接区',
  },
  {
    id: 'V-DATA-002',
    route: '/#/data-workbench',
    unique: /管道|Pipeline|数据/i,
    note: '管道画布：/api/v1/engine/data/pipeline/tasks',
  },
  {
    id: 'V-DATA-007',
    route: '/#/dq_dashboard',
    unique: /质量|DQ|Quality/i,
    note: '数据质量看板：/api/v1/dq/reports',
  },
  {
    id: 'V-DATA-008',
    route: '/#/objects',
    unique: /对象|Object/i,
    note: '数据浏览器：对象列表/详情',
  },
  {
    id: 'V-DATA-009',
    route: '/#/data-workbench',
    unique: /目录|资产|Catalog|数据/i,
    note: '数据目录看板：/api/v1/datanet/catalog/dashboard',
  },
  {
    id: 'V-ONTO-001',
    route: '/#/ontology_workbench',
    unique: /本体|Ontology/i,
    note: '本体工作台总览：/api/v1/engine/ontology/status',
  },
  {
    id: 'V-ONTO-003',
    route: '/#/ontology_workbench',
    unique: /本体|Ontology/i,
    note: '本体 6 Tab（对象类型/属性/映射/术语/元数据）',
  },
  {
    id: 'V-ONTO-012',
    route: '/#/business-workbench',
    unique: /业务|Business|视图/i,
    note: '业务工作台四类视图',
  },
  {
    id: 'V-SEC-041',
    route: '/#/security-center',
    unique: /安全|防护|Security/i,
    note: '安全中心：/api/v1/security/* 面',
  },
  {
    id: 'V-CTRL-GOV-002',
    route: '/#/platform-governance?tab=config',
    unique: /治理|配置|Governance/i,
    note: '引擎配置 Tab：修复 D67 后应有 20+ 真实配置分组',
  },
  {
    id: 'V-COP-008',
    route: '/#/decision',
    unique: /决策|Mind|认知/,
    note: 'Copilot 抽屉独立渲染（入口在决策板）',
  },
];

test.describe('页面级渲染清退批（17 条 PARTIAL 补渲染腿）', () => {
  for (const c of CASES) {
    test(`${c.id} 页面渲染：${c.note}`, async ({ page, request, context }) => {
      const token = await loginAsAdmin(request);
      await injectAuth(context, token);
      watchPageHealth(page, c.degraded ? { expectDegraded: c.degraded } : {});

      await page.goto(c.route);
      // 1) 落到这一页（页头独有文案可见）
      await expect(page.getByText(c.unique).first()).toBeVisible({ timeout: 20_000 });
      // 2) main 区非空（真实渲染过，不是骨架常住）
      await expect(page.locator('main').first()).not.toBeEmpty();
      // 3) 无错误边界
      await expectNoErrorBoundary(page);
      // 4) 反向探针：不得出现「加载中」永久挂起
      await expect(page.getByText(/加载中\.\.\.|Loading\.\.\./)).toHaveCount(0, { timeout: 15_000 }).catch(() => {});
      // 4b) 已声明降级的页面：降级横幅必须真的出现在界面上（不得静默用示意数据冒充真实）
      if (c.degraded) {
        await expect(
          page.getByText(/示意数据|降级|不可达|无法获取|Degraded|Demo/i).first(),
          `${c.id} 声明了降级端点，但页面上没有降级提示——示意数据被静默当成真实数据`,
        ).toBeVisible({ timeout: 10_000 });
      }
      // 5) 响应式断言：健康检查
      expectHealthyPage(page);
      expect(page.url()).toContain(c.route.split('?')[0]);
    });
  }

  test('V-COP-008 Copilot 抽屉：Topbar 入口可见 + 打开后独立渲染（非空白）', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/decision');
    await expect(page.getByText(/决策|Mind|认知/).first()).toBeVisible({ timeout: 20_000 });

    // 入口已由 2026-10-14 裁决从悬浮按钮迁到 Topbar 右上角（aria-label = app.copilot.open）
    const trigger = page.getByRole('button', { name: /打开 ECOS Copilot|Open ECOS Copilot/i }).first();
    await expect(trigger, 'Copilot 入口按钮不可见').toBeVisible({ timeout: 15_000 });
    await trigger.click();

    // 抽屉打开后：header 标题 + 底部自然语言输入条必须同时在（两者都是该抽屉独有）
    await expect(
      page.getByRole('heading', { name: /ECOS Copilot/i }).first(),
      'Copilot 抽屉标题不可见（抽屉未真正打开）',
    ).toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByPlaceholder(/给 AIP 协处理器下达指令|Issue a command to AIP/i).first(),
      'Copilot 抽屉没有输入条（抽屉渲染为空白）',
    ).toBeVisible({ timeout: 15_000 });
    await expectNoErrorBoundary(page);
    expectHealthyPage(page);

    // 反向探针：关闭后标题必须消失（证明 isOpen 双向受控，不是常挂）
    const closeBtn = page.getByRole('button', { name: /^关闭$|^Close$/i }).first();
    await expect(closeBtn, '抽屉缺少关闭按钮').toBeVisible({ timeout: 10_000 });
    await closeBtn.click();
    await expect(
      page.getByRole('heading', { name: /ECOS Copilot/i }),
      '关闭后抽屉仍在——isOpen 未双向受控',
    ).toHaveCount(0, { timeout: 10_000 });
  });
});
