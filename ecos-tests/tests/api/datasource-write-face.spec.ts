import { test, expect } from '@playwright/test';
import { loginAsAdmin } from '../helpers/ecos';

/**
 * V-DATA-001 写面补验（2026-11-16）：数据源「新建连接 + 测试连接」。
 *
 * 矩阵原文判据：「连接列表真实（MetadataController/DataSourceController）；新建连接走真实 POST；
 * 测试连接返回真实结果」——列表腿早已绿，**写面两腿（新建/测试）此前未做**，故 PARTIAL。
 *
 * 本 spec 在 API 级跑完整写面闭环（自建自清，不留残留）：
 *   ① POST /api/v1/datanet/datasource 新建 → 200 且返回真实 datasourceId；
 *   ② POST /{id}/test 测试已存连接 → 200 且 success=true（真连 PG）；
 *   ③ POST /test 用**错误 host** 的原始配置 → 200 但 success=false「连接失败」
 *      ⇒ 证明测试连接是真实探测，不是恒 true 的假成功；
 *   ④ DELETE /{id} 清理 → 200，复查 GET → 404（无残留）。
 *
 * 判定纪律：创建用 `VERIFY-` 前缀 + 时间戳，断言后必删；任一断言失败也要走 finally 清理。
 */

test.describe.configure({ timeout: 90_000 });

const BASE = '/api/v1/datanet/datasource';
const NAME = `VERIFY-VD001-${Date.now()}`;

test.describe('V-DATA-001 数据源写面（新建/测试/清理）', () => {
  test('新建→测试成功→错误配置测失败→删除闭环', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const auth = { Authorization: `Bearer ${token}` };
    let createdId: string | undefined;

    try {
      // ① 新建（真实 POST，连接配置指向本机 PG）
      const createRes = await request.post(BASE, {
        headers: auth,
        data: {
          datasourceName: NAME,
          datasourceType: 'POSTGRESQL',
          description: 'V-DATA-001 write-face probe',
          connectionConfig: JSON.stringify({
            host: 'localhost', port: 5432, database: 'sys_man', username: 'postgres', password: 'postgres',
          }),
        },
      });
      expect(createRes.status(), '新建应 200').toBe(200);
      const createBody = await createRes.json();
      createdId = createBody?.data?.datasourceId;
      expect(createdId, '新建必须返回真实 datasourceId').toBeTruthy();
      expect(createBody?.data?.datasourceName).toBe(NAME);

      // ② 测试已存连接 → 真实成功
      const testRes = await request.post(`${BASE}/${createdId}/test`, { headers: auth });
      expect(testRes.status(), '测试连接应 200').toBe(200);
      const testBody = await testRes.json();
      expect(testBody?.data?.success, '本机 PG 连通，测试应 success=true').toBe(true);
      expect(testBody?.data?.datasourceId).toBe(createdId);

      // ③ 错误配置必须测出失败（证明真实探测，非恒 true）
      const badRes = await request.post(`${BASE}/test`, {
        headers: auth,
        data: {
          datasourceName: 'VERIFY-VD001-BAD',
          datasourceType: 'POSTGRESQL',
          connectionConfig: JSON.stringify({
            host: 'no-such-host-xyz', port: 5432, database: 'nope', username: 'u', password: 'p',
          }),
        },
      });
      expect(badRes.status(), '原始配置测试应 200（业务失败在 body）').toBe(200);
      const badBody = await badRes.json();
      expect(badBody?.data?.success, '错误 host 必须测出 success=false（真实探测，非假成功）').toBe(false);
    } finally {
      // ④ 清理（无论断言成败）
      if (createdId) {
        const delRes = await request.delete(`${BASE}/${createdId}`, { headers: auth });
        expect(delRes.status(), '清理删除应 200').toBe(200);
        const checkRes = await request.get(`${BASE}/${createdId}`, { headers: auth });
        expect(checkRes.status(), '删除后复查应 404（无残留）').toBe(404);
      }
    }
  });
});
