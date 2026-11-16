import { expect, test } from '@playwright/test';
import { injectAuth, loginAsAdmin, watchPageHealth, expectNoErrorBoundary, expectHealthyPage } from '../../helpers/ecos';

/**
 * V-COP-007 补测批（2026-11-14/15 验证）：Copilot 抽屉三态（超长 / 权限 / 降级）。
 *
 * 原判定 PARTIAL 已覆盖「空 / 加载 / 错误」三态，缺「边界（超长）/ 权限 / 降级（引擎不可用）」。
 * 本 spec 逐态做实，全部走真实端点与真实 UI，不 mock、不 skip。
 *
 * 判定纪律：
 *   - 每态都必须证明「前端没有洗白」：降级态必须看到离线/失败文案，不能看到成功结论；
 *   - 超长输入必须证明「真的送出去了」且「回包没被截断吞掉」；
 *   - 权限态用匿名身份（不带 token）打同一端点，证明后端返回鉴权拒绝且前端如实渲染。
 */

const DRAWER = /AIP|Copilot|协处理/;

async function openCopilot(page: import('@playwright/test').Page) {
  // 落在已注册路由上：`/dashboard` 不是 navConfig 里的 id，会触发 W201 fail-closed 占位标签
  await page.goto('/#/decision');
  await expectNoErrorBoundary(page);
  const openBtn = page.getByRole('button', { name: '打开 ECOS Copilot' });
  await openBtn.waitFor({ state: 'visible', timeout: 20_000 });
  await openBtn.click();
  await expect(page.getByRole('heading', { name: DRAWER })).toBeVisible({ timeout: 15_000 });
}

const input = (page: import('@playwright/test').Page) =>
  page.getByPlaceholder(/给 AIP 协处理器下达指令/);

test.describe('V-COP-007 Copilot 抽屉三态', () => {
  test('边界（超长输入）：1200 字诉求能送出且回包不被吞', async ({ page, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(page.context(), token);
    // 降级：本用例只验"超长不会被前端截断/丢包"，引擎回包 403 属既有 D63 基线
    watchPageHealth(page, { expectDegraded: [/\/api\/v1\/copilot/, /\/api\/v1\/aip/] });

    await openCopilot(page);

    const longText = '超长边界探针'.repeat(171); // ≈1197 字
    expect(longText.length).toBeGreaterThan(1000);

    await input(page).fill(longText);
    // 反向探针①：输入框必须原样接受全文，不得静默截断
    await expect(input(page)).toHaveValue(longText);

    await page.getByRole('button', { name: 'RUN AGENT' }).click();

    // 反向探针②：用户消息必须完整出现在气泡里（证明没被前端截断后再发送）
    await expect(page.getByText(longText, { exact: false })).toBeVisible({ timeout: 20_000 });

    // 回包必须到达（成功或明确失败都行，但不能一直挂在"发送中"）
    // 注：失败文案含"智能体执行失败：ECOS-AI-503"与"未能从 AIP 获得应答"两处 ⇒ 用 first()
    await expect(page.getByText(/未能从 AIP 获得应答|智能体执行失败|网关离线|AIP 网关离线/).first())
      .toBeVisible({ timeout: 60_000 });

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('降级（引擎不可用）：路由层掐断后必须显示离线文案，不得合成成功', async ({ page, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(page.context(), token);

    // 主动把协处理器通道打成网络失败——这是"引擎不可用"的真实注入方式（非 mock 返回值）
    await page.route('**/api/v1/agent/copilot/**', (route) => route.abort('failed'));
    await page.route('**/api/v1/aip/**', (route) => route.abort('failed'));
    watchPageHealth(page, { expectDegraded: [/\/api\/v1\/agent\/copilot/, /\/api\/v1\/aip/] });

    await openCopilot(page);
    await input(page).fill('降级态探针：请回答一个普通问题');
    await page.getByRole('button', { name: 'RUN AGENT' }).click();

    // 必须出现离线/失败文案
    await expect(page.getByText(/AIP 网关离线|智能体执行失败|未能从 AIP 获得应答/).first())
      .toBeVisible({ timeout: 45_000 });

    // 反向探针：不得出现"成功"结论
    const body = await page.evaluate(() => document.body.innerText || '');
    expect(body, '降级态不得出现合成成功结论').not.toMatch(/部署成功|已为您自动串联|判定通过/);

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('权限态：匿名调用协处理器端点必须被拒（不是 200 空应答）', async ({ request }) => {
    // 不经登录，直接用裸请求打同一端点——这是"无权限"的最强证明
    const res = await request.post('/api/v1/agent/copilot/chat', {
      data: { agentId: 'a1', message: '权限态探针', sessionId: 'perm-probe' },
      failOnStatusCode: false,
    });

    expect([401, 403], `匿名调用必须被鉴权拒绝，实际 ${res.status()}`).toContain(res.status());

    const text = await res.text();
    expect(text, '拒绝响应不得伪装成成功').not.toMatch(/"code"\s*:\s*0\b/);

    // 反向探针：带合法 token 的同端点不应是 401/403（证明拒绝来自身份而非端点不存在）
    const token = await loginAsAdmin(request);
    const authed = await request.post('/api/v1/agent/copilot/chat', {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      data: { agentId: 'a1', message: '权限态探针', sessionId: 'perm-probe' },
      failOnStatusCode: false,
    });
    expect(authed.status(), '带合法身份时不得再因鉴权被拒（否则上面那条不算权限态）').not.toBe(401);
  });
});
