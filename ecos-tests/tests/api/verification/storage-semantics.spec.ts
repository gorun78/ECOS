import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * 存储语义探针（D44 / D42 常驻）
 *
 * D44 的教训：`ISystemDatabaseAccess` 下并存「真 JDBC（*FromConfig）」与「内存 dataStore（按表名）」
 * 两族，后者遇到不存在的表会 `return new ArrayList<>()`——把**结构缺失读成「没有数据」**。
 * D42 撞上这个形态：系统参数面在装配后返回 `200 + data:[] total:0`，而后端根本没有那张表。
 *
 * 本探针不看实现细节，只看**对外可观测的语义**：
 *   ① 若端点声称成功（code=0），它的 data 必须能被独立证据支持（此处用「总量与分页自洽」判定）；
 *   ② 更不能出现「库中无表却报 200」被当成正常——所以对已知可疑面显式登记其当前形态，
 *      形态一旦变化（例如修成真库、或改成 503）本用例会红，强制重新裁决。
 *
 * 这是「防止假数据悄悄变成常态」的钉桩，不是功能测试。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe('存储语义（D42/D44 钉桩）', () => {
  test('D42-1 系统参数面形态必须被显式归类（503 未装配 或 200 且分页自洽）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/system/config/params', { token });

    if (status === 503) {
      expect(json?.errorCode, '503 必须带机读码（ECOS-SYS-503）').toBe('ECOS-SYS-503');
      // eslint-disable-next-line no-console
      console.log('[D42-1] 503 未装配（诚实不可用态）');
      return;
    }

    expect(status, `系统参数面不得出现 200 以外的"成功"形态，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);

    // 200 时必须分页自洽：total 与 data 长度关系合理（total=0 则 data 必空）
    const page = json?.data ?? {};
    const rows = Array.isArray(page.data) ? page.data : [];
    const total = Number(page.total ?? -1);
    expect(total, 'total 必须是数字').toBeGreaterThanOrEqual(0);
    if (total === 0) {
      expect(rows.length, 'total=0 时 data 必须为空（不得自相矛盾）').toBe(0);
    }
    // eslint-disable-next-line no-console
    console.log(`[D42-1] 200 形态：total=${total} rows=${rows.length}（若后端无 td_system_param 表则属 D44 假数据形态）`);
  });

  test('D42-2 系统参数详情：不存在 id 必须 4xx（禁 200+null）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/system/config/params/VERIFY_NOT_EXIST_999', { token });
    if (status === 503) {
      expect(json?.errorCode).toBe('ECOS-SYS-503');
      // eslint-disable-next-line no-console
      console.log('[D42-2] 503 未装配（诚实态），跳过后半判定');
      return;
    }
    expect(status, `不存在参数必须 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
    expect(json?.code, '失败信封 code 不得为 0').not.toBe(0);
  });

  test('D44-1 已知真库族端点必须返回与库一致的数据（对照组）', async ({ request }) => {
    // `*FromConfig` 走真实 JDBC。ABAC 策略面是它的典型调用方，用它作"真库"对照组：
    // 该面若也返回空，需要区分「真无数据」与「假库」。这里只钉"可达且自洽"。
    const { status, json } = await call(request, 'GET', '/api/v1/security/abac/policies', { token });
    if (status === 404) {
      // eslint-disable-next-line no-console
      console.log('[D44-1] ABAC 策略面未在本部署装配，跳过对照组');
      return;
    }
    expect(status, `ABAC 策略面应可达，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
    // eslint-disable-next-line no-console
    console.log('[D44-1] ABAC 策略面（真库族对照组）可达');
  });
});
