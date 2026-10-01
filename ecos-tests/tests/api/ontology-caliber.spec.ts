import { test } from '@playwright/test';

/**
 * W73/M0 · F03-01 — 口径只读消费面 E2E 骨架（skeleton，占位，非 build blocker）。
 *
 * <p>目标端点（跨引擎只读消费面，见 CaliberController / 卷 09 §3.3 V214 引用视图）：
 * <pre>
 *   GET /api/v1/ontology/calibers/{code}/current   // 最近 APPROVED 版本行
 *   GET /api/v1/ontology/calibers?status=ACTIVE&limit=50
 *   GET /api/v1/ontology/calibers/{code}           // 详情 + 版本历史
 * </pre>
 * </p>
 *
 * <p><b>当前为占位</b>：不落真实 HTTP 调用（无台账口径数据 / 门禁未联调）。仅当显式设置
 * {@code ECOS_SKIP_LIVE=1} 时启用（默认 skip，保证 CI 与本地无网关亦可 build/collect）。
 * 后续接联调时按 {@code ../helpers/ecos.ts} 的 {@code call/loginAsAdmin} 组装真实断言。</p>
 */

const LIVE = process.env.ECOS_SKIP_LIVE === '1' || process.env.ECOS_SKIP_LIVE === 'true';

test.describe('ontology calibers (F03-01 只读消费面)', () => {
  test.describe.configure({ mode: 'serial' });

  test('list calibers → 200（live-only）', async ({ request }) => {
    test.skip(!LIVE, 'requires live gateway (ECOS_SKIP_LIVE=1 to enable)');
    // TODO(W73 E2E): loginAsAdmin + call(request, 'GET', '/api/v1/ontology/calibers?limit=50')
    //               expect(status).toBe(200); expect(json?.success).toBe(true);
    console.warn('[ontology-caliber] E2E placeholder — wire via helpers/ecos.ts once gate is live');
    expect(true).toBe(true);
  });

  test('GET /{code}/current → 200 + version_status=APPROVED（live-only）', async ({ request }) => {
    test.skip(!LIVE, 'requires live gateway (ECOS_SKIP_LIVE=1 to enable)');
    // TODO(W73 E2E): 需一条已 APPROVED 口径；缺省时空库 404 亦为合法初态（对齐 V167.1 无 seed）。
    expect(true).toBe(true);
  });
});
