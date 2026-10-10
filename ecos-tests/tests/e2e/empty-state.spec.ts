import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详设-08 §2.3 状态矩阵（附件 §11.8 六场景 + 权限态扩展）— 空态面。
 * 冻结标识：`pw empty-state.spec.ts › P01 空态显示引导而非报错`（§2.3 行"空状态"）。
 *
 * 【口径】空状态 = 列表端点 200 且 data.length=0 ⇒ 引导文案 + 示例目标，
 * 与"加载失败"视觉可区分。P01（Agent 工作台新 Tab）载体落位随 **R-49**
 * （§2.1：/agent_studio 域内新 Tab，本轮登记不排期）⇒ 未落位前一律 test.fixme，
 * 落位属主接通后去掉 fixme 即得可执行用例（断言体已按 §2.3 写足）。
 *
 * 【凭据】只走 env（ECOS_USER/ECOS_PASS），禁明文。
 */

test.describe('§2.3 空态矩阵（P01 面，页面落位随 R-49）', () => {
  test.fixme('P01 空态显示引导而非报错', async ({ page, context, request }) => {
    // 落位随 R-49：P01 Tab（/agent_studio 域内）未建 ⇒ 固定跳过；接通后本行为去 fixme。
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/agent_studio', { waitUntil: 'domcontentloaded' });

    // 构造空态：拦截 Mission/待办列表端点返回 200 + 空数组（§2.3 触发条件）。
    await page.route('**/api/v1/**', async (route) => {
      const res = await route.fetch().catch(() => null);
      if (!res) return route.continue();
      const ct = res.headers()['content-type'] || '';
      if (!ct.includes('json')) return route.fallback();
      const body = await res.json().catch(() => null);
      if (body && Array.isArray(body.data) && body.data.length > 0) {
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...body, data: [] }) });
      }
      return route.fallback();
    });
    await page.reload({ waitUntil: 'domcontentloaded' });

    // 断言：出现引导性文案（zh/en 双匹配），且不出现错误态语。
    const guide = page
      .getByText(/创建第一个|示例目标|开始第一次|Create your first|example goal|Get started/i)
      .first();
    await expect(guide, '空态应显示引导文案 + 示例目标（§2.3）').toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByText(/加载失败|请求失败|Failed to load|Something went wrong/i).first(),
      '空态不得显示为加载失败（视觉可区分）',
    ).toHaveCount(0);
    await expectNoErrorBoundary(page);
  });

  test('既有同族列表页空态不判为错误（回归护栏）', async ({ page, context, request }) => {
    // alerts-center 空队列面：落位已存在，保留为可先行子集（真跑由终验裁 任务 #122）。
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/alerts-center', { waitUntil: 'domcontentloaded' });
    await expectNoErrorBoundary(page);
    await expect(
      page.getByText(/500|Internal Server Error|异常堆栈|at [A-Za-z.$]+\.[A-Za-z]+\(.*:\d+/).first(),
      '空/异常场景不得暴露堆栈（§2.3 失败面兜底）',
    ).toHaveCount(0);
  });
});
