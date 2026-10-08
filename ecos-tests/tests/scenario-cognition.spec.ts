import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { ECOS_BASE } from '../playwright.config';
import { loginAsAdmin, call, injectAuth, watchPageHealth, expectNoErrorBoundary } from './helpers/ecos';

/**
 * 详细设计-05（认知域）B 章冻结验收用例 — `tests/scenario-cognition.spec.ts`。
 *
 * 用例名与 design-05 验收行逐字一致（COG-E1/E2/E3/E4/503/3/stub/越权/IA）。
 * 分两面：
 *  - API 面：request 直连 gateway（project baseURL = ECOS_API，默认 http://localhost:8080），
 *    凭据只从 ecos-tests/.env 的 ECOS_USER/ECOS_PASS 读（helpers/ecos.ts creds()，禁写死）。
 *  - UI 面：前端 http://localhost:3000/#/（HashRouter），token 经 localStorage.token 注入
 *    （helpers injectAuth）。:3000 不可达时默认 skip；`CognitiveRightPanel` 尚未挂载到
 *    场景工作台路由（design-05 B.0 IA 归一属 M1 批次），面板缺失的 UI 用例亦 skip 并注明。
 *
 * 契约（后端 ScenarioCognitionController / CognitiveErrorMatrixIntegrationTest 对齐）：
 *  GET /api/v1/business/scenarios/{id}/cognition[/detect|/operation-eval|/hypotheses|/beliefs]
 *  错误矩阵 400/403/404/500/503；引擎未启用 = 503；world-model 系端点 = 501 + data.stub=true。
 */

const COG_ENDPOINTS = ['', '/detect', '/operation-eval', '/hypotheses', '/beliefs'] as const;
const cogPath = (scenarioId: string, suffix = ''): string =>
  `/api/v1/business/scenarios/${encodeURIComponent(scenarioId)}/cognition${suffix}`;

/** 取场景列表第一条 id（/api/v1/workspace/scenarios，与 ScenarioManagementView 同源）；无数据返回 null。 */
async function firstScenarioId(request: APIRequestContext): Promise<string | null> {
  const token = await loginAsAdmin(request);
  const { status, json } = await call(request, 'GET', '/api/v1/workspace/scenarios', { token });
  if (status !== 200 || !json) return null;
  const arr = Array.isArray(json.data) ? json.data : Array.isArray(json) ? json : [];
  const hit = arr.find((x: unknown) => {
    const o = x as { id?: unknown; scenarioId?: unknown };
    return typeof o?.id === 'string' || typeof o?.scenarioId === 'string';
  }) as { id?: string; scenarioId?: string } | undefined;
  return hit ? String(hit.id ?? hit.scenarioId) : null;
}

async function frontendAlive(request: APIRequestContext): Promise<boolean> {
  const res = await request.get(ECOS_BASE, { timeout: 3_000 }).catch(() => null);
  return !!res;
}

/** 打开场景工作台并等待认知右栏；前端离线或面板未挂载（M1 IA 归一待落地）时 skip。 */
async function openCognitionPanel(page: Page, request: APIRequestContext): Promise<void> {
  test.skip(!(await frontendAlive(request)), `前端 ${ECOS_BASE} 不可用 — UI 用例默认跳过`);
  const token = await loginAsAdmin(request);
  await injectAuth(page.context(), token);
  await watchPageHealth(page);
  await page.goto(`${ECOS_BASE}/#/project_workbench`);
  await expectNoErrorBoundary(page);
  const panel = page.getByTestId('cognitive-right-panel');
  test.skip(
    (await panel.count()) === 0,
    'CognitiveRightPanel 尚未挂载到场景工作台路由（design-05 B.0 IA 归一 = M1 批次）；挂载后本组用例自动生效',
  );
  await expect(panel).toBeVisible();
}

/** 点击右栏 Tab（文案来自 locales/scenario/zh-CN.json，injectAuth 已置 ecos_locale=zh）。 */
async function openTab(page: Page, label: string): Promise<void> {
  await page.getByRole('button', { name: label, exact: true }).click();
}

// ═════════════════════════ API 面（gateway :8080） ═════════════════════════

test.describe('场景认知 API 契约（gateway，design-05 B.9-7）', () => {
  test('COG-越权 未登录访问右栏端点被拒', async ({ request }) => {
    test.skip(!(await frontendOrApiAlive(request)), 'gateway 不可用 — 无法验证 fail-closed');
    for (const suffix of COG_ENDPOINTS) {
      const res = await request.get(cogPath('cog-probe-scenario', suffix));
      expect(
        [401, 403],
        `未登录 GET ${cogPath('<id>', suffix)} 必须 fail-closed（401/403），实得 ${res.status()}`,
      ).toContain(res.status());
      expect(res.status(), '匿名访问绝不允许 200 泄漏认知数据').not.toBe(200);
    }
  });

  test('COG-stub 未实现能力显示占位而非假数据', async ({ request }) => {
    test.skip(!(await frontendOrApiAlive(request)), 'gateway 不可用 — 无法验证 501 stub 诚实面');
    const token = await loginAsAdmin(request);
    // world-model 系端点本期显式 stub：501 + data.stub=true（禁 200+假数据，X-43/DG-9）
    for (const path of ['/api/v1/world-model/state', '/api/v1/world-model/causal-graph']) {
      const { status, json } = await call(request, 'GET', path, { token });
      expect(status, `${path} 应显式 501（capability not implemented），禁静默假数据回落`).toBe(501);
      const stubFlag =
        (json?.data as { stub?: boolean } | undefined)?.stub ??
        (json as { stub?: boolean } | null | undefined)?.stub;
      expect(stubFlag, `${path} 501 响应必须携带 data.stub=true 供前端渲染占位`).toBe(true);
    }
  });
});

async function frontendOrApiAlive(request: APIRequestContext): Promise<boolean> {
  const res = await request.get('/api/health', { timeout: 3_000 }).catch(() => null);
  return !!res;
}

// ═════════════════════════ UI 面（前端 :3000，HashRouter） ═════════════════════════

test.describe('场景认知右栏 UI（:3000，离线默认 skip）', () => {
  test('COG-E1 右栏探测列表渲染', async ({ page, request }) => {
    await openCognitionPanel(page, request);
    await openTab(page, '可用 Mind 探测');
    const panel = page.getByTestId('cognitive-right-panel');
    await expect(
      panel.getByText('该场景未绑定 Mind').first(),
      'E1 空态（200 data:[]）必须渲染"未绑定"空态而非报错（B.3）',
    ).toBeVisible({ timeout: 15_000 }).or(
      expect(panel.locator('[role="status"], [role="alert"]').first()).toBeVisible(),
    );
    // 非空场景：至少一条 Mind 行（name + 能力集 chips）
    const rows = panel.getByText('能力集');
    if ((await rows.count()) > 0) {
      await expect(rows.first()).toBeVisible();
    }
  });

  test('COG-E2 评分排序与 FLAT 标注', async ({ page, request }) => {
    await openCognitionPanel(page, request);
    const id = await firstScenarioId(request);
    test.skip(!id, '需要至少一条场景数据');
    await openTab(page, '运行评价');
    const panel = page.getByTestId('cognitive-right-panel');
    const flatMarks = panel.getByText('趋势：平稳');
    const scoreRows = panel.getByRole('progressbar');
    const rowCount = await scoreRows.count();
    test.skip(rowCount === 0, '该场景暂无评价行（E2 空态合法）');
    // 本期 trend 恒 FLAT（trendSource=NOT_IMPLEMENTED 真实标注，禁伪造趋势）
    await expect(flatMarks).toHaveCount(rowCount);
    // 评分降序（后端 Top-N 排序的前端渲染一致性）
    const scores = await scoreRows.evaluateAll((els) =>
      els.map((el) => Number((el.getAttribute('aria-valuenow') ?? '0'))),
    );
    for (let i = 1; i < scores.length; i++) {
      expect(scores[i], 'E2 列表必须按评分降序').toBeLessThanOrEqual(scores[i - 1]);
    }
  });

  test('COG-E3 置信度三色分段', async ({ page, request }) => {
    await openCognitionPanel(page, request);
    await openTab(page, '假设列表');
    const panel = page.getByTestId('cognitive-right-panel');
    const confPills = panel.locator('span[title]').filter({ hasText: /0\.\d\d|—/ });
    const n = await confPills.count();
    test.skip(n === 0, '该场景暂无假设行（E3 空态合法）');
    for (let i = 0; i < n; i++) {
      const pill = confPills.nth(i);
      // 三色分段不依赖颜色单一通道（B.7）：数值文本 + 三段 pip 结构同时在场
      await expect(pill.getByText(/0\.\d\d|—/)).toBeVisible();
      await expect(pill.locator('span[aria-hidden] span')).toHaveCount(3);
    }
  });

  test('COG-E4 未映射显示为占位而非丢弃', async ({ page, request }) => {
    await openCognitionPanel(page, request);
    const id = await firstScenarioId(request);
    test.skip(!id, '需要至少一条场景数据');
    await openTab(page, '信念列表');
    const panel = page.getByTestId('cognitive-right-panel');
    const token = await loginAsAdmin(request);
    const { status, json } = await call(request, 'GET', cogPath(id!, '/beliefs'), { token });
    test.skip(status !== 200, `E4 API 未就绪（HTTP ${status}）`);
    const apiRows = Array.isArray(json?.data) ? json.data : (json?.data as { rows?: unknown[] })?.rows ?? [];
    test.skip(apiRows.length === 0, '该场景暂无信念行');
    const unmapped = panel.getByText('未映射');
    const nullCaps = (apiRows as Array<{ capability?: string | null }>).filter((r) => !r.capability);
    if (nullCaps.length > 0) {
      await expect(unmapped).toHaveCount(nullCaps.length);
    }
  });

  test('COG-503 引擎停止时右栏置灰 banner', async ({ page, request }) => {
    test.skip(!(await frontendAlive(request)), `前端 ${ECOS_BASE} 不可用 — UI 用例默认跳过`);
    await page.route('**/api/v1/business/scenarios/**/cognition/detect**', (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, message: 'ECOS-COG-500 engine not enabled' }),
      }),
    );
    const token = await loginAsAdmin(request);
    await injectAuth(page.context(), token);
    await watchPageHealth(page);
    await page.goto(`${ECOS_BASE}/#/project_workbench`);
    const panel = page.getByTestId('cognitive-right-panel');
    test.skip((await panel.count()) === 0, 'CognitiveRightPanel 尚未挂载（M1 IA 归一）');
    const banner = page.getByTestId('cog-engine-down-banner');
    await expect(banner, '503 未启用 → 右栏 banner（B.5，禁与空态混淆）').toContainText('认知引擎未启用');
    await expect(panel.locator('[aria-disabled="true"]').first(), '引擎停止时内容区置灰不可交互').toBeVisible();
  });

  test('COG-3 探索式通道显示"非核算口径"', async ({ page, request }) => {
    await openCognitionPanel(page, request);
    await openTab(page, '运行评价');
    await expect(
      page.getByTestId('cog-non-accounting-hint'),
      '探索式（评价/反事实）通道必须显式标注"非核算口径"（B-3/B.3，禁与核算口径混淆）',
    ).toContainText('非核算口径');
  });

  test('COG-IA 认知四件套唯一入口', async ({ page, request }) => {
    await openCognitionPanel(page, request);
    // 认知四件套（探测/评价/假设/信念）唯一入口 = 场景工作台右栏；全页不得出现第二实例
    await expect(page.getByTestId('cognitive-right-panel')).toHaveCount(1);
    const panel = page.getByTestId('cognitive-right-panel');
    for (const label of ['可用 Mind 探测', '运行评价', '假设列表', '信念列表']) {
      await expect(panel.getByRole('button', { name: label, exact: true })).toBeVisible();
    }
  });
});
