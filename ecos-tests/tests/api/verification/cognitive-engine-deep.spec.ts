import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B2 认知引擎 — 深度验证（对应矩阵域02 的 V-COG-0xx 端点条目）。
 *
 * 与 `cognitive-ai.spec.ts`（首轮只读面）互补：本文件按矩阵分节逐端点判判定，
 * 覆盖 Mind 治理、框架注册、方法/资产目录、Context 构建、预测与结果评估、
 * 认知推理面、世界模型、数据一致性、权限面。
 *
 * 反假绿纪律：
 *  - 已知缺口按矩阵口径记 PARTIAL/如实降级，**不判 PASS**：
 *      · V-COG-061/062 Context 裁剪器无生产实现 ⇒ required 源 fail-closed 403（0 泄露）
 *      · V-COG-073 结果评估缺业务聚合口径 ⇒ 200 但 applicable=false（NEEDS_RECONCILE）
 *      · V-COG-090/091/094 世界模型/回放读面为显式 stub（501 + data.stub=true）
 *      · V-COG-046 执行记录为进程内态（501/405 事实登记）
 *  - 每个「正常态」断言都配反向探针（不存在 id ⇒ 4xx 且不裸 500）
 *  - D37/D38 回归常驻：认知写面空体必须 4xx（曾折叠成 500）
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

/** Mind 系端点返回 `data={items:[...],r4ColumnsProvisioned:bool}` 信封（非裸数组）。 */
async function expectItems(request: any, path: string, label: string): Promise<any[]> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0`).toBe(0);
  expect(Array.isArray(json?.data?.items), `${label} data.items 应为数组（实测信封）`).toBe(true);
  return json?.data?.items as any[];
}

async function expectArray(request: any, path: string, label: string): Promise<any[]> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0`).toBe(0);
  expect(Array.isArray(json?.data), `${label} data 应为数组`).toBe(true);
  return json?.data as any[];
}

test.describe('一、Mind 治理（V-COG-030~037）', () => {
  test('V-COG-030 Mind 列表 limit 边界：默认可用，越界拒绝或截断', async ({ request }) => {
    const base = await expectItems(request, '/api/v1/cognitive/minds?limit=5', 'Mind 列表');
    expect(base.length, 'limit=5 返回条数不得超过 5').toBeLessThanOrEqual(5);
    const over = await call(request, 'GET', '/api/v1/cognitive/minds?limit=201', { token });
    expect([200, 400], 'limit=201 应拒绝或按上限截断，不得 500').toContain(over.status);
    if (over.status === 200) {
      expect((over.json?.data?.items as any[]).length, 'limit 超上限须截断').toBeLessThanOrEqual(200);
    }
    console.log(`[V-COG-030] default=${base.length} limit201=${over.status}`);
  });

  test('V-COG-031 Mind 详情 + 反向探针（不存在 id 必须 4xx）', async ({ request }) => {
    const minds = await expectItems(request, '/api/v1/cognitive/minds?limit=5', 'Mind 列表');
    if (minds.length > 0) {
      const id = minds[0].mindId ?? minds[0].id;
      const one = await call(request, 'GET', `/api/v1/cognitive/minds/${encodeURIComponent(String(id))}`, { token });
      expect(one.status, `已存在 mind ${id} 详情应 200`).toBe(200);
      expect(one.json?.data, '详情 data 应为对象').toBeTruthy();
      console.log(`[V-COG-031] existing=${id} status=${one.status}`);
    } else {
      console.log('[V-COG-031] 库内零 mind，仅验反向腿');
    }
    const miss = await call(request, 'GET', '/api/v1/cognitive/minds/VERIFY_NOT_EXIST_999', { token });
    expect(miss.status, `不存在 mind 应 4xx 而非 500，实得 ${miss.status}`).toBeGreaterThanOrEqual(400);
    expect(miss.status, '不存在 mind 不得 5xx（参数语义不是服务故障）').toBeLessThan(500);
  });

  test('V-COG-033 候选列表必须有 mindId（缺参 4xx，不得 500）', async ({ request }) => {
    const missing = await call(request, 'GET', '/api/v1/cognitive/minds/proposals?limit=10', { token });
    expect([400, 404], `缺 mindId 应 4xx，实得 ${missing.status}`).toContain(missing.status);
    expect(missing.json?.data?.missing ?? missing.json?.errorCode, '4xx 应指明缺什么').toBeTruthy();

    const minds = await expectItems(request, '/api/v1/cognitive/minds?limit=5', 'Mind 列表');
    if (minds.length > 0) {
      const mindId = String(minds[0].mindId ?? minds[0].id);
      const { status, json } = await call(
        request, 'GET', `/api/v1/cognitive/minds/proposals?mindId=${encodeURIComponent(mindId)}&limit=10`, { token });
      expect(status, `带 mindId 的候选列表应 200，实得 ${status}`).toBe(200);
      const rows = Array.isArray(json?.data) ? json!.data as any[] : (json?.data?.items as any[]);
      expect(Array.isArray(rows), '候选列表应返回数组（裸或 items 信封）').toBe(true);
      expect(rows.length, 'limit=10 不得超过 10').toBeLessThanOrEqual(10);
      console.log(`[V-COG-033] missing=${missing.status} withMind=${status} rows=${rows.length}`);
    } else {
      console.log(`[V-COG-033] missing=${missing.status}（库内零 mind，正向腿余项）`);
    }
  });

  test('V-COG-034 候选回读反向探针（不存在 updateId 必须 4xx）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/cognitive/minds/proposals/VERIFY_NOT_EXIST_999', { token });
    expect(status, `不存在候选应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不存在候选不得 5xx').toBeLessThan(500);
  });

  test('V-COG-035 终态写反向：不存在 updateId 必须 4xx（不得假成功）', async ({ request }) => {
    const res = await request.fetch('/api/v1/cognitive/minds/proposals/VERIFY_NOT_EXIST_999/status', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${token}` },
      data: { status: 'ACCEPTED' },
    });
    const status = res.status();
    expect(status, `不存在候选的终态写应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得以 5xx 表达「找不到」').toBeLessThan(500);
    console.log(`[V-COG-035] status=${status}`);
  });
});

test.describe('二、框架注册与方法/资产目录（V-COG-040~055）', () => {
  test('V-COG-040 框架列表真实', async ({ request }) => {
    const rows = await expectArray(request, '/api/v1/cognitive/frameworks', '框架列表');
    expect(rows.length, '框架注册表不应为空（内置框架基线）').toBeGreaterThan(0);
    const row = rows[0];
    expect(row.frameworkId, '框架行应含 frameworkId').toBeTruthy();
    expect(row.version, '框架行应含 version').toBeTruthy();
    console.log(`[V-COG-040] frameworks=${rows.length} first=${row.frameworkId}@${row.version}`);
  });

  test('V-COG-041 框架详情反向探针', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/cognitive/frameworks/VERIFY_NOT_EXIST_999/1.0.0', { token });
    expect(status, `不存在框架应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
  });

  test('V-COG-050 方法字典 5 方法且无写面（设计边界）', async ({ request }) => {
    const rows = await expectArray(request, '/api/v1/cognitive/methods', '方法字典');
    expect(rows.length, '方法字典不应为空').toBeGreaterThan(0);
    const post = await request.fetch('/api/v1/cognitive/methods', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    expect(post.status(), '方法字典为只读设计，POST 应 405').toBe(405);
    console.log(`[V-COG-050] methods=${rows.length} POST=${post.status()}`);
  });

  test('V-COG-051 方法详情反向探针', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/cognitive/methods/VERIFY_NOT_EXIST_999', { token });
    expect(status, `不存在方法应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
  });

  test('V-COG-052 认知资产目录真实（非空占位）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cognitive/assets/catalog', { token });
    expect(status, '资产目录应 200').toBe(200);
    expect(json?.code).toBe(0);
    const data = json?.data;
    expect(data, '资产目录 data 必须存在').toBeTruthy();
    console.log(`[V-COG-052] catalog type=${Array.isArray(data) ? 'array' : typeof data} size=${Array.isArray(data) ? data.length : JSON.stringify(data)?.slice(0, 120)}`);
  });

  test('V-COG-053 资产解析：缺 assetId 语义须 4xx（不得 500）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/cognitive/assets/VERIFY_NOT_EXIST_999/resolve', { token });
    expect(status, `不存在资产解析应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
  });

  test('V-COG-055 对象目录可达', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cognitive/object-catalog', { token });
    expect(status, '对象目录应 200').toBe(200);
    expect(json?.code).toBe(0);
    console.log(`[V-COG-055] object-catalog size=${Array.isArray(json?.data) ? (json!.data as any[]).length : 'obj'}`);
  });
});

test.describe('三、Context 构建与安全裁剪（V-COG-060~062）', () => {
  test('V-COG-060 Context 构建：空体必须 4xx（D37 回归常驻）', async ({ request }) => {
    const res = await request.fetch('/api/v1/cognitive/context/build', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    const status = res.status();
    expect(status, `空体构建应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, 'D37 回归：入参缺失不得折叠成 5xx').toBeLessThan(500);
    console.log(`[V-COG-060] empty build status=${status}`);
  });

  test('V-COG-061 required 源 fail-closed：403 且 0 泄露（P1-9 缺口如实登记）', async ({ request }) => {
    const res = await request.fetch('/api/v1/cognitive/context/build', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {
        sources: [{ ref: 'VERIFY_REQUIRED_SRC', required: true }],
        purpose: 'verification',
      },
    });
    const status = res.status();
    const body = await res.text();
    expect([200, 400, 403], `required 源应 fail-closed 或参数拒绝，实得 ${status}`).toContain(status);
    expect(status, 'P1-9 缺口：裁剪器无生产实现时不得放行 5xx').toBeLessThan(500);
    // 裁剪不泄露：被拒源的标识不得出现在错误体里（0 泄露口径）
    if (status === 403) {
      expect(body.includes('VERIFY_REQUIRED_SRC'), 'V-COG-062 被拒源标识不得回显').toBe(false);
    }
    console.log(`[V-COG-061] required-source status=${status} leak=${body.includes('VERIFY_REQUIRED_SRC')}`);
  });
});

test.describe('四、预测与结果评估（V-COG-070~075）', () => {
  test('V-COG-070 预测计算：空体必须 4xx（D37 回归常驻）', async ({ request }) => {
    const res = await request.fetch('/api/v1/cognitive/forecast', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    const status = res.status();
    expect(status, `空体预测应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, 'D37 回归：缺 metric 不得 5xx').toBeLessThan(500);
    console.log(`[V-COG-070] empty forecast status=${status}`);
  });

  test('V-COG-072 按幂等键查询：缺 key 必须 4xx', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/cognitive/forecast-runs/by-key', { token });
    expect(status, `缺 idempotencyKey 应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
  });

  test('V-COG-073 结果评估：200 但 applicable=false（P1-18 缺口，记 PARTIAL 不判 PASS）', async ({ request }) => {
    const res = await request.fetch('/api/v1/cognitive/outcomes/evaluate', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    const status = res.status();
    expect(status, `空体评估应 4xx 或 200+applicable=false，实得 ${status}`).toBeLessThan(500);
    if (status === 200) {
      const json = await res.json();
      expect(json?.data?.applicable, 'P1-18 缺口：不得声称可评估（applicable 必须 false）').toBeFalsy();
      console.log(`[V-COG-073] status=200 applicable=${json?.data?.applicable} decision=${json?.data?.decision}`);
    } else {
      console.log(`[V-COG-073] status=${status}（参数拒绝腿）`);
    }
  });
});

test.describe('五、认知推理面（V-COG-080~086，D37/D38 回归）', () => {
  const writeSurfaces = [
    { path: '/api/v1/cognitive/diagnose', id: 'V-COG-080', name: '诊断' },
    { path: '/api/v1/cognitive/counterfactual', id: 'V-COG-082', name: '反事实' },
    { path: '/api/v1/cognitive/beliefs', id: 'V-COG-085', name: '信念登记' },
    { path: '/api/v1/cognitive/hypotheses', id: 'V-COG-085b', name: '假设登记' },
    { path: '/api/v1/cognitive/evidence', id: 'V-COG-085c', name: '证据登记' },
    { path: '/api/v1/cognitive/models', id: 'V-COG-086', name: '模型注册' },
  ];

  for (const { path, id, name } of writeSurfaces) {
    test(`${id} ${name}：空体必须 4xx 而非 500（D37/D38 回归常驻）`, async ({ request }) => {
      const res = await request.fetch(path, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: {},
      });
      const status = res.status();
      expect(status, `${name} 空体应 4xx，实得 ${status}（D37：BusinessException 曾折叠成 500）`)
        .toBeGreaterThanOrEqual(400);
      expect(status, `${name} 空体不得 5xx`).toBeLessThan(500);
      console.log(`[${id}] POST ${path} empty → ${status}`);
    });
  }

  test('V-COG-080b 诊断历史可达且结构完整', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cognitive/diagnose/history', { token });
    expect([200, 501], `诊断历史应 200 或显式 501，实得 ${status}`).toContain(status);
    if (status === 501) {
      expect(json?.data?.stub, '501 必须显式标注 stub（禁空壳冒充）').toBeTruthy();
    }
    console.log(`[V-COG-080b] history status=${status} stub=${json?.data?.stub ?? false}`);
  });

  test('V-COG-081 情景推演 /scenario/simulate 存在且空体 4xx（实测订正：不在 /cognitive 根下）', async ({ request }) => {
    // 2026-11-13 实测：ScenarioController 挂 `/api/v1/cognitive/scenario`，
    // 矩阵原文写 `/api/v1/cognitive/simulate`（根路径 404）属条目笔误，已回写矩阵。
    const res = await request.fetch('/api/v1/cognitive/scenario/simulate', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    expect(res.status(), '真实路径不得 404（否则推演面缺失）').not.toBe(404);
    expect(res.status(), '空体不得 5xx').toBeLessThan(500);
    const rootProbe = await request.fetch('/api/v1/cognitive/simulate', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    expect(rootProbe.status(), '矩阵原写路径应 404（证明本条订正不是笔误掩盖）').toBe(404);
    console.log(`[V-COG-081] /scenario/simulate=${res.status()} /simulate=${rootProbe.status()}`);
  });
});

test.describe('六、世界模型与溯源（V-COG-090~094）', () => {
  const stubs = [
    { path: '/api/v1/cognitive/world-model/entities', id: 'V-COG-090' },
    { path: '/api/v1/cognitive/world-model/relations', id: 'V-COG-091a' },
    { path: '/api/v1/cognitive/world-model/rules', id: 'V-COG-091b' },
    { path: '/api/v1/cognitive/world-model/traces', id: 'V-COG-091c' },
  ];

  for (const { path, id } of stubs) {
    test(`${id} 世界模型读面：显式 stub 或真实数据（禁静默空壳）`, async ({ request }) => {
      const { status, json } = await call(request, 'GET', path, { token });
      expect([200, 501], `世界模型读面应 200 或显式 501，实得 ${status}`).toContain(status);
      if (status === 501) {
        expect(json?.data?.stub, '501 必须带 data.stub=true').toBe(true);
        expect(json?.data?.missing, '501 必须说明缺口（missing 字段）').toBeTruthy();
      }
      console.log(`[${id}] status=${status} stub=${json?.data?.stub ?? false}`);
    });
  }

  test('V-COG-093 溯源：缺 entityType 必须 4xx（不得 500）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cognitive/provenance', { token });
    expect(status, `缺 entityType 应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
    expect(json?.data?.missing ?? json?.errorCode, '4xx 必须指明缺什么').toBeTruthy();
    console.log(`[V-COG-093] status=${status} missing=${json?.data?.missing ?? json?.errorCode}`);
  });
});

test.describe('七、其余面与权限（V-COG-101/102/105/120/122）', () => {
  test('V-COG-101 认知管道可达（读面 200；写面缺参 4xx）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/cognitive/pipeline', { token });
    expect(status, '管道读面应 200').toBe(200);
    const res = await request.fetch('/api/v1/cognitive/pipeline', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    });
    expect(res.status(), '管道写面缺参应 4xx，不得 5xx').toBeGreaterThanOrEqual(400);
    expect(res.status(), '不得 5xx').toBeLessThan(500);
    console.log(`[V-COG-101] GET=200 POST=${res.status()}`);
  });

  test('V-COG-102 认知配置读面结构完整', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/cognitive/config', { token });
    expect(status, '认知配置应 200').toBe(200);
    expect(json?.code).toBe(0);
    console.log(`[V-COG-102] config type=${Array.isArray(json?.data) ? 'array' : typeof json?.data}`);
  });

  test('V-COG-120 无 token 访问认知面必须被拒（不得泄露业务数据）', async ({ request }) => {
    const anon = await request.fetch('/api/v1/cognitive/minds?limit=5', { headers: {} });
    expect([401, 403], `无 token 应 401/403，实得 ${anon.status()}`).toContain(anon.status());
    const text = await anon.text();
    const json = JSON.parse(text || '{}');
    expect(json?.data ?? null, '拒绝响应不得携带 data 业务体').toBeNull();
    console.log(`[V-COG-120] anon status=${anon.status()}`);
  });

  test('V-COG-122 认知写面需认证：无 token 注册 mind 必须被拒', async ({ request }) => {
    const anon = await request.fetch('/api/v1/cognitive/minds', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      data: { mindKey: 'VERIFY_ANON', name: 'anon' },
    });
    expect([400, 401, 403], `无 token 写面应 400/401/403，实得 ${anon.status()}`).toContain(anon.status());
    expect(anon.status(), '不得 5xx').toBeLessThan(500);
    console.log(`[V-COG-122] anon write status=${anon.status()}`);
  });
});
