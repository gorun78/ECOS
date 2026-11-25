import { test, expect, APIRequestContext } from '@playwright/test';
import { ECOS_BASE } from '../playwright.config';
import { loginAsAdmin, call, injectAuth, watchPageHealth, expectNoErrorBoundary } from './helpers/ecos';

/**
 * 详细设计-05（认知域）B 章冻结验收用例 — `tests/scenario-cognition.spec.ts`。
 *
 * 用例名与 design-05 验收行逐字一致（COG-E1/E2/E3/E4/503/3/stub/越权/IA）。
 * 分两面：
 *  - API 面：request 直连 gateway（project baseURL = ECOS_API，默认 http://localhost:8080），
 *    凭据只从 ecos-tests/.env 的 ECOS_USER/ECOS_PASS 读（helpers/ecos.ts creds()，禁写死）。
 *    后端端点与错误矩阵不受前端改版影响，**全部保留**。
 *  - UI 面：**2026-11-25 用户裁决——认知四卡（CognitiveRightPanel）随「高级/调试」整块下线**，
 *    原 7 条依赖右栏挂载的 UI 用例（COG-E1/E2/E3/E4/503/3/IA）已无被测对象，整批退役；
 *    只保留一条「下线守卫」，钉住定义面与运行面都不得再渲染右栏。
 *    组件自身的渲染契约（E1~E4 Tab、503 横幅、limit 钳制等）仍由前端 vitest
 *    `pages/scenario/cognition/__tests__/sevenStates.matrix.spec.tsx` 直接渲染组件覆盖，
 *    能力验证不依赖页面挂载点。
 *
 * 契约（后端 ScenarioCognitionController / CognitiveErrorMatrixIntegrationTest 对齐）：
 *  GET /api/v1/business/scenarios/{id}/cognition[/detect|/operation-eval|/hypotheses|/beliefs]
 *  错误矩阵 400/403/404/500/503；引擎未启用 = 503；world-model 系端点 = 501 + data.stub=true。
 */

const COG_ENDPOINTS = ['', '/detect', '/operation-eval', '/hypotheses', '/beliefs'] as const;
const cogPath = (scenarioId: string, suffix = ''): string =>
  `/api/v1/business/scenarios/${encodeURIComponent(scenarioId)}/cognition${suffix}`;

/**
 * 取场景列表第一条 id（与 ScenarioManagementView 同源）。
 *
 * <p>必须走前端 origin（:3000 vite proxy → workspace :18090），不能走 project baseURL（gateway :8080）：
 * gateway 单体的 {@code GatewayApplication} 用 excludeFilter 整包排除
 * {@code com.chinacreator.gzcm.workspace.controller}（同名 Bean 冲突裁决，workspace 端点只在 :18090 承流），
 * 故 {@code /api/v1/workspace/scenarios} 在 :8080 恒为 404 —— 走网关会让所有 UI 用例静默 skip 成假绿。
 */
async function firstScenarioId(request: APIRequestContext): Promise<string | null> {
  const token = await loginAsAdmin(request);
  const { status, json } = await call(request, 'GET', `${ECOS_BASE}/api/v1/workspace/scenarios`, { token });
  if (status !== 200 || !json) return null;
  const arr = Array.isArray(json.data) ? json.data : Array.isArray(json) ? json : [];
  const hit = arr.find((x: unknown) => {
    const o = x as { id?: unknown; scenarioId?: unknown };
    return typeof o?.id === 'string' || typeof o?.scenarioId === 'string';
  }) as { id?: string; scenarioId?: string } | undefined;
  return hit ? String(hit.id ?? hit.scenarioId) : null;
}

// 2026-11-25：`openCognitionPanel` / `openTab` / `frontendAlive` 三个辅助随认知四卡下线一并删除
// （前两者定位的 `cognitive-right-panel` 已不再渲染，留着即死代码 + 误导后来人；
//  `frontendAlive` 只剩它们调用，现由下方 `frontendOrApiAlive` 统一承担存活探测）。

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

test.describe('场景认知右栏 UI — 下线守卫（:3000，离线默认 skip）', () => {
  /**
   * 2026-11-25 用户裁决：「高级/调试」整块下线 ⇒ 认知四卡（CognitiveRightPanel）
   * 随之不再挂载。原 7 条依赖右栏的 UI 用例（COG-E1/E2/E3/E4/503/3/IA）已无被测对象，
   * 整批退役；此处只保留一条守卫，钉住「定义页与运行面都不得再渲染右栏」。
   *
   * 四卡组件自身的渲染契约（Tab 切换、503 横幅、limit 钳制等）仍由前端 vitest
   * `pages/scenario/cognition/__tests__/sevenStates.matrix.spec.tsx` 直接渲染组件覆盖，
   * 能力验证不依赖页面挂载点；后端端点契约由上方 API 组用例继续承证。
   */
  test('COG-IA 认知四卡已下线：定义面与运行面都不得出现右栏', async ({ page, request }) => {
    test.skip(!(await frontendOrApiAlive(request)), `前端 ${ECOS_BASE} 不可用 — UI 用例默认跳过`);
    const token = await loginAsAdmin(request);
    const id = await firstScenarioId(request);
    test.skip(!id, '无可用场景数据 — 无法打开 L2 场景详情');
    await injectAuth(page.context(), token);
    await watchPageHealth(page);

    for (const suffix of ['?tab=definition', '']) {
      await page.goto(`${ECOS_BASE}/#/workbench/${encodeURIComponent(id as string)}${suffix}`);
      await expectNoErrorBoundary(page);
      await expect(
        page.getByTestId('cognitive-right-panel'),
        `认知四卡已于 2026-11-25 下线，${suffix || '默认面'}不得再渲染右栏`,
      ).toHaveCount(0);
      // 下线不等于页面坏掉：主体仍须有真实内容
      await expect(page.locator('main').first()).not.toBeEmpty();
    }
  });
});
