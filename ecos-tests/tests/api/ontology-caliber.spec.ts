import { test, expect } from '@playwright/test';
import { call, loginAsAdmin } from '../helpers/ecos';

/**
 * F03-01 口径只读消费面（CaliberController / 卷 09 §3.3 V214 引用视图）。
 *
 * 原为 `test.skip(!LIVE, ...) + expect(true)` 占位骨架，2026-11-13 验证批 D37 处置：
 * 两条 skip 是「无断言恒绿/恒跳过」，属方案 §五「无 skip 洗白」红线打击对象。
 * 现改为真实活体断言，口径按实测基线（库内零口径 = 合法初态）：
 *   - 列表：200 且 data 为数组（空数组是真实态，不是失败）
 *   - 详情：不存在 code 必须 404 且带 errorCode（不得 200 空壳）
 */
test.describe('ontology calibers (F03-01 只读消费面)', () => {
  test.describe.configure({ mode: 'serial' });

  let token = '';

  test.beforeAll(async ({ request }) => {
    token = await loginAsAdmin(request);
  });

  test('V-ONTO-009 口径列表 200 且 data 为数组（空库也是真实态）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ontology/calibers?limit=50', { token });
    expect(status, `GET /api/v1/ontology/calibers 期望 200，实得 ${status}`).toBe(200);
    expect(Array.isArray(json?.data), `data 必须为数组，实得 ${JSON.stringify(json?.data)?.slice(0, 200)}`).toBeTruthy();
    console.log(`[V-ONTO-009] calibers=${(json?.data as any[]).length}`);
  });

  test('V-ONTO-009b 不存在口径的 current 必须 404 + errorCode（反向探针）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ontology/calibers/VERIFY_NOT_EXIST_999/current', { token });
    expect(status, `不存在的口径 current 期望 404，实得 ${status}`).toBe(404);
    expect(json?.errorCode, '404 必须带可追踪 errorCode，不得裸 404').toBeTruthy();
    console.log(`[V-ONTO-009b] status=${status} errorCode=${json?.errorCode}`);
  });
});
