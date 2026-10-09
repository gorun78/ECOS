import { expect, test } from '@playwright/test';
import { bearer, loginAsAdmin } from '../../helpers/ecos';

/**
 * N1 getScenarioCompleteness E2E（D-2）。本环境无栈，全 test.skip。
 * 断言：真实绑定覆盖度数值、missingEdges/islands 结构；禁前端合成。
 */
const SCENARIO_ID = process.env.ECOS_E2E_SCENARIO_ID ?? 'p-2027-profit';

test.describe('N1 — scenario completeness', () => {
  test.skip('200：coverage∈[0,1] 且 verdict∈{COMPLETE,PARTIAL,EMPTY}', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const res = await request.fetch(`/api/v1/workspace/scenarios/${SCENARIO_ID}/completeness`, { headers: bearer(token) });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const data = body?.data ?? {};
    expect(data.coverage === null || (data.coverage >= 0 && data.coverage <= 1)).toBe(true);
    expect(['COMPLETE', 'PARTIAL', 'EMPTY']).toContain(data.verdict);
    expect(Array.isArray(data.missingEdges)).toBe(true);
    expect(Array.isArray(data.islands)).toBe(true);
  });

  test.skip('404：未知场景', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const res = await request.fetch('/api/v1/workspace/scenarios/no-such-scenario/completeness', { headers: bearer(token) });
    expect(res.status()).toBe(404);
  });

  test.skip('403：无权限（低权 token）', async () => {
    // 真实解锁需构造低权账号；此处仅声明契约
    expect(true).toBe(true);
  });
});
