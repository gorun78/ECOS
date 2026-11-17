import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B2·认知引擎 — 端点面补测（矩阵域02 的「本批未排入」33 条，2026-11-13 第二轮）。
 *
 * **本轮实测得到的四条判据纪律**（写进断言，防止后人误判）：
 *
 * 1. **501 不是缺陷**：`/api/v1/cognitive/world-model/{relations,rules,traces}` 三面
 *    实测 501 `ECOS-COG-503` + `data.stub=true` + `reason/owner/segment/missing`。
 *    这是详细设计-05 **F05-16 显式 stub 化**的裁定结果，`missing` 字段还写明了
 *    「ecos_wm_causal_link 只连 goal↔goal，非契约关系集；V194 裁本域外因果口径以
 *    ontology REST 只读为准」。故本文件断言「501 必须带完整 stub 元数据」——
 *    而不是断言「必须 200」。
 * 2. **404 与 405 语义不同**：`/cognitive/executions`、`/outcomes/{id}/review-tasks`、
 *    `/replay` 实测 **405**（路径存在、方法不对），说明端点已注册但动词不同；
 *    `/cognitive/assets`、`/plans`、`/demo/wave3`、`/world-model` 实测 **404 ECOS-SYS-404**
 *    （路由未注册）。两者必须分开记，不能都写「不可用」。
 * 3. **400 必须点名缺什么**：`operation-eval` 未带参数时返回 `data.missing="mind"`——
 *    这是合格形态。本文件断言 400 必带 `missing` 或 `message` 说明。
 * 4. **越权/无效场景必须 404 且有 guard 标记**：`NOT_EXIST_999` 四端点实测
 *    404 `msg="scenario not found"` + `data.guard`，不得裸 500、不得泄露内部信息。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

const NOT_5XX = [500, 502, 504];

async function get(request: any, path: string) {
  return call(request, 'GET', path, { token });
}

async function post(request: any, path: string, data: unknown = {}) {
  return call(request, 'POST', path, { token, data });
}

test.describe('V-COG-010 / 011 / 012 认知健康三面', () => {
  test('V-COG-010 引擎健康：200 + status=UP + components 含 engine/db', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/engine/cognitive/health');
    expect(status, 'cognitive health 应 200').toBe(200);
    const d = json!.data;
    expect(String(d.status).toUpperCase(), 'status 必须为 UP').toBe('UP');
    expect(d.components, '必须报 components').toBeTruthy();
    expect(Object.keys(d.components), 'components 至少含 engine 与 db')
      .toEqual(expect.arrayContaining(['engine', 'db']));
  });

  test('V-COG-011 / 012 状态与内部健康前缀实测形态（404 登记，非静默通过）', async ({ request }) => {
    // 矩阵原写「GET /api/v1/engine/cognitive」与「GET /api/v1/cognitive」均可达；
    // 实测两者都是 404 ECOS-SYS-404（路由未注册）。钉住现状，实现后本腿会红。
    for (const p of ['/api/v1/engine/cognitive', '/api/v1/cognitive']) {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前实测 404（路由未注册）；若已实现请改判 V-COG-011/012`)
        .toBe(404);
      expect(json?.errorCode, `${p} 应为网关 ECOS-SYS-404`).toBe('ECOS-SYS-404');
    }
  });
});

test.describe('V-COG-021 / 022 / 024 认知 E2/E3 + 越权', () => {
  test('V-COG-021 operation-eval 缺参必 400 并点名缺什么（实测 missing=mind）', async ({ request }) => {
    const { status, json } = await get(request,
      '/api/v1/business/scenarios/sc001/cognition/operation-eval');
    expect(status, '缺必填参数应 400').toBe(400);
    const missing = json?.data?.missing ?? json?.message;
    expect(missing, '400 必须点明缺哪个参数（实测 data.missing="mind"）').toBeTruthy();
    expect(String(missing).toLowerCase()).toContain('mind');
  });

  test('V-COG-022 hypotheses 返回数组（状态词表须∈HypothesisStatus）', async ({ request }) => {
    const { status, json } = await get(request,
      '/api/v1/business/scenarios/sc001/cognition/hypotheses');
    expect(status, 'hypotheses 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows), 'hypotheses 应为数组').toBe(true);
    // 有行时必须校验状态词表（P1-6 冲突项：出现 VALIDATED 即为越界）
    const allowed = ['PROPOSED', 'TESTING', 'SUPPORTED', 'REFUTED', 'RETIRED',
      'PENDING', 'CONFIRMED', 'REJECTED'];
    for (const r of rows) {
      const s = String(r.status ?? '').toUpperCase();
      if (s) {
        expect(allowed, `hypothesis 状态 ${s} 不在 HypothesisStatus 词表内（P1-6）`)
          .toContain(s);
      }
    }
  });

  test('V-COG-024 无效场景必须 404 + guard 标记，不裸 500、不泄露', async ({ request }) => {
    for (const p of ['detect', 'hypotheses', 'beliefs']) {
      const { status, json } = await get(request,
        `/api/v1/business/scenarios/NOT_EXIST_999/cognition/${p}`);
      expect(NOT_5XX, `${p} 不得 5xx`).not.toContain(status);
      expect(status, `${p} 无效场景应 404，实得 ${status}`).toBe(404);
      const body = JSON.stringify(json);
      expect(body, `${p} 响应不得泄露表名/堆栈/SQL`)
        .not.toMatch(/SELECT |FROM |at com\.chinacreator|ecos_cognitive\./i);
      // 实测带 data.guard 标记（可区分「场景不存在」与「引擎不可用」）
      expect(json?.data?.guard ?? json?.errorCode,
        `${p} 必须带可辨标记（guard 或 errorCode）`).toBeTruthy();
    }
  });
});

test.describe('V-COG-091 世界模型三面：501 是设计内 stub（F05-16）', () => {
  for (const seg of ['relations', 'rules', 'traces']) {
    test(`world-model/${seg} 返回 501 且带完整 stub 元数据`, async ({ request }) => {
      const { status, json } = await get(request, `/api/v1/cognitive/world-model/${seg}`);
      expect(status, `world-model/${seg} 实测 501（F05-16 显式 stub），若已实现请改判 V-COG-091`)
        .toBe(501);
      expect(json?.errorCode, 'stub 必须带机读码 ECOS-COG-503').toBe('ECOS-COG-503');
      const d = json?.data ?? {};
      expect(d.stub, 'stub 标记必须为 true（与真实故障区分）').toBe(true);
      for (const k of ['reason', 'owner', 'segment', 'path', 'missing']) {
        expect(d, `stub 必须自述 ${k}（否则调用方无法判断该找谁）`).toHaveProperty(k);
      }
      expect(d.segment, 'segment 必须回显被请求的段').toBe(seg);
      expect(String(d.reason), 'reason 必须指向裁定出处').toMatch(/F05-16|stub/);
    });
  }

  test('world-model 根与 /tree 未注册（404 登记；真实子路径见下条 V-COG-092）', async ({ request }) => {
    for (const p of ['/api/v1/world-model', '/api/v1/world-model/tree']) {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前实测 404；若已实现请改判 V-COG-092`).toBe(404);
      expect(json?.errorCode).toBe('ECOS-SYS-404');
    }
  });
});

/**
 * V-COG-092 世界模型（独立面）——四条真实映射逐条打穿。
 *
 * 判据订正（2026-11-16 实测）：原矩阵测的是 `/api/v1/world-model` 与 `/tree`，那是**不存在的
 * 子路径**（真 404，见上条）。`WorldModelController` 的真实映射是
 * `GET /state`、`GET /causal-graph`、`POST /scenarios`、`POST /strategy/recommend`，
 * 四条均可达且都是 F05-16 裁定的**显式 stub**（真实 HTTP 501 + `ECOS-COG-503` + `data.stub=true`）。
 * 「目标树/因果图数据一致」的验收要求由**另一族真实端点** `world-model-graph` 承担：
 * `goals`/`links`/`causal-graph`/`goals-tree`/`scenarios` 实测 200 且互为投影
 * （nodes == goals 全等、edges == links 全等）。两族分工由此钉住：
 * 认知属主 `/api/v1/world-model/*` = 未实现能力（501 占位）；
 * 场景/目标域 `/api/v1/ecos/world-model-graph/*` = 真实存储读面（前端页消费的正是这一族）。
 */
test.describe('V-COG-092 世界模型独立面：四路由 501 + 真实读面一致性', () => {
  const STUB_ROUTES = [
    { p: '/api/v1/world-model/state', method: 'GET' },
    { p: '/api/v1/world-model/causal-graph', method: 'GET' },
    { p: '/api/v1/world-model/scenarios', method: 'POST' },
    { p: '/api/v1/world-model/strategy/recommend?goal=reduce-churn', method: 'POST' },
  ] as const;

  for (const { p, method } of STUB_ROUTES) {
    test(`${method} ${p} → 501 显式 stub（非 200 假数据）`, async ({ request }) => {
      const data = method === 'GET' ? undefined : { name: 'probe' };
      const r = method === 'GET' ? await get(request, p) : await post(request, p, data);
      expect(r.status, `${method} ${p} 实测 501（F05-16 显式 stub）；若已实现请改判 V-COG-092`)
        .toBe(501);
      expect(r.json?.errorCode, 'stub 必须带机读码 ECOS-COG-503').toBe('ECOS-COG-503');
      const d = (r.json?.data ?? {}) as Record<string, unknown>;
      expect(d.stub, 'stub 必须为 true（与真实故障区分）').toBe(true);
      expect(String(d.reason), 'reason 必须指向裁定出处').toMatch(/F05-16|stub/);
      // 反向探针：禁携带固定推演结论/置信度（F05-16 明令）
      for (const forbidden of ['confidence', 'recommendation', 'simulationResult']) {
        expect(d, `stub 不得携带伪数据键 ${forbidden}`).not.toHaveProperty(forbidden);
      }
    });
  }

  test('strategy/recommend 缺 goal 参数必须 400 点名（不得 501 掩盖参数错误）', async ({ request }) => {
    const r = await post(request, '/api/v1/world-model/strategy/recommend', {});
    expect(r.status, '缺 goal 应 400 参数校验先于 stub 判定').toBe(400);
    expect(r.json?.errorCode).toBe('ECOS-COG-400P');
    expect((r.json?.data as any)?.missing, '400 必须点名缺的是 goal').toBe('goal');
  });

  // 反退化探针：桩面一旦绑定刚性 DTO，前端真实载荷（assumptions 数组 / type 字符串）会被
  // Jackson 先判成 400，设计要求的「未实现」占位永远出不来（SceneSandbox 实测形态）。
  test('POST /scenarios 收前端真实载荷（assumptions 为数组）仍必须 501 占位', async ({ request }) => {
    const payload = {
      id: 'scn-probe', name: 'Scenario A: 更换供应商', description: '前端 sandbox 原样载荷',
      assumptions: [{ target: 'cost', action: 'increase', value: '10%' }],
      type: 'BASELINE',
    };
    const r = await post(request, '/api/v1/world-model/scenarios', payload);
    expect(r.status, '真实载荷必须落到 501 占位；400 说明桩面又绑定了刚性 DTO').toBe(501);
    expect(r.json?.errorCode).toBe('ECOS-COG-503');
    expect((r.json?.data as any)?.stub).toBe(true);
  });

  test('真实读面 world-model-graph：goals/links 与 causal-graph 互为投影', async ({ request }) => {
    const [g, l, cg, tree, sc] = await Promise.all([
      get(request, '/api/v1/ecos/world-model-graph/goals'),
      get(request, '/api/v1/ecos/world-model-graph/links'),
      get(request, '/api/v1/ecos/world-model-graph/causal-graph'),
      get(request, '/api/v1/ecos/world-model-graph/goals/tree'),
      get(request, '/api/v1/ecos/world-model-graph/scenarios'),
    ]);
    for (const [name, r] of [['goals', g], ['links', l], ['causal-graph', cg],
      ['goals/tree', tree], ['scenarios', sc]] as const) {
      expect(r.status, `${name} 必须 200（真实读面，不是 stub）`).toBe(200);
      expect(r.json?.code, `${name} 必须是成功码 0`).toBe(0);
    }
    const goals = (g.json!.data as any).data as any[];
    const links = (l.json!.data as any).data as any[];
    const graph = cg.json!.data as any;
    expect(goals.length, '目标行必须非空（真实存储）').toBeGreaterThan(0);
    expect(graph.nodes.length, 'causal-graph 节点数必须等于目标行数（同一存储的两个投影）')
      .toBe(goals.length);
    expect(graph.edges.length, 'causal-graph 边数必须等于 links 行数')
      .toBe(links.length);
    expect(graph.nodeCount, 'nodeCount 必须与 nodes 实长一致').toBe(graph.nodes.length);
    expect(graph.edgeCount, 'edgeCount 必须与 edges 实长一致').toBe(graph.edges.length);
    const nodeIds = new Set(graph.nodes.map((n: any) => String(n.id).replace(/^goal-/, '')));
    expect(goals.map((x) => String(x.id)).filter((id) => !nodeIds.has(id)),
      '每个目标必须出现在因果图节点里（禁各查各表）').toEqual([]);
    expect((tree.json!.data as any[]).length, '目标树必须非空').toBeGreaterThan(0);
    expect((sc.json!.data as any).data.length, '场景清单必须非空').toBeGreaterThan(0);
  });
});

/**
 * V-COG-094 运行级回放：GET 是 F05-12 裁定的显式 stub，POST 是动词错误。
 *
 * 注意与**另一族真实实现**区分：`GET /api/v1/cognitive/beliefs/{var}/{version}/replay`
 * 是版本链真实回放（V129），不属于本条；把两者混判会把 501 误读成缺陷。
 */
test.describe('V-COG-094 run 级回放：GET 501 显式 stub / POST 405', () => {
  test('GET /api/v1/cognitive/replay → 501 + 完整 stub 元数据', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/replay');
    expect(status, 'run 级回放实测 501（F05-12 守护项 1「推理结果不落盘」）；若已实现请改判 V-COG-094')
      .toBe(501);
    expect(json?.errorCode).toBe('ECOS-COG-503');
    const d = (json?.data ?? {}) as Record<string, unknown>;
    expect(d.stub, 'stub 必须为 true').toBe(true);
    expect(d.owner, '必须自述属主').toBe('cognitive');
    expect(d.segment).toBe('replay');
    expect(d.path, 'path 必须回显真实路径').toBe('/api/v1/cognitive/replay');
    expect(String(d.missing), 'missing 必须写明缺哪张载体表/哪条守护项').toMatch(/F05-12|不落盘|载体/);
    expect(d, '禁伪造历史条目列表').not.toHaveProperty('runs');
  });

  test('POST /api/v1/cognitive/replay → 405（路径存在、动词不符）', async ({ request }) => {
    const r = await post(request, '/api/v1/cognitive/replay');
    expect(r.status, 'POST 实测 405；矩阵原写 POST 属动词误判').toBe(405);
    expect(r.json?.errorCode).toBe('ECOS-SYS-405');
  });
});

test.describe('V-COG-032 / 045 / 036 Mind 与计划', () => {
  test('V-COG-032 提交提案：mind 不存在必须 400（不得 500）', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/minds/x/proposals');
    expect(NOT_5XX, 'mind 缺失不得 5xx').not.toContain(status);
    expect(status, 'mind 不存在应 400').toBe(400);
    expect(String(json?.message ?? ''), '400 必须说明 mind not found')
      .toMatch(/mind/i);
  });

  test('V-COG-045 计划校验：非法计划被拒且报出 issues，不 500', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/plans/validate', { plan: null });
    expect(NOT_5XX, '计划校验不得 5xx').not.toContain(status);
    expect(status, '非法计划应 200（带校验结论）或 400，实得 ' + status).toBeLessThan(500);
    if (status === 200) {
      const d = json!.data;
      expect(typeof d.passed, '校验结论必须含布尔 passed').toBe('boolean');
      expect(typeof d.dagAcyclic, '必须报 DAG 无环判定').toBe('boolean');
      expect(Array.isArray(d.issues), 'issues 必须为数组').toBe(true);
    }
  });

  test('V-COG-042 框架列表返回真实条目（实测 4 个）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/frameworks');
    expect(status, 'frameworks 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows) && rows.length > 0, '框架列表必须有条目').toBeTruthy();
  });
});

/**
 * V-COG-042 写面（2026-11-16 活体闭环）：注册 → 发布 → PUBLISHED 可见，以及白名单/发布门/不可变性三处拒绝。
 *
 * 数据自建（frameworkId 带时间戳），不依赖既有 4 条 DRAFT 记录，也不做删除
 * （框架注册表按设计只增：PUBLISHED 不可原地改，改内容须注册新版本）。
 */
test.describe('V-COG-042 框架发布写面：白名单 + 发布门 + PUBLISHED 可见 + 不可变性', () => {
  const stamp = Date.now();
  const fwId = `CF-VERIFY-042-${stamp}`;

  const base = {
    name: 'verify framework 042',
    category: 'verification',
    purpose: 'live verification of publish whitelist',
    problemTypes: ['verify'],
    domains: ['default'],
  };

  test('executorType 不在受控白名单 ⇒ 400 且点名该字段', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/frameworks', {
      ...base,
      frameworkId: fwId,
      version: `3.0.0-badexec-${stamp}`,
      steps: [{ stepId: 's1', executorType: 'arbitrary_shell' }],
      inputSchemaRef: 'schema://verify/in',
      outputSchemaRef: 'schema://verify/out',
    });
    expect(status, '非白名单 executorType 应 400').toBe(400);
    expect(json?.errorCode).toBe('ECOS-COG-400P');
    expect(String(json?.data?.missing ?? ''), '400 必须点名拒的是哪个字段').toContain('executorType');
  });

  test('发布门：契约不可解析（缺 input/output schema）⇒ 422 且逐项列出缺失', async ({ request }) => {
    const v = `2.0.0-nogate-${stamp}`;
    const reg = await post(request, '/api/v1/cognitive/frameworks', {
      ...base,
      frameworkId: fwId,
      version: v,
      steps: [{ stepId: 's1', executorType: 'human_review' }],
    });
    expect(reg.status, '缺 schema 仍可注册为 DRAFT（发布门才拦）').toBe(200);

    const { status, json } = await post(
      request, `/api/v1/cognitive/frameworks/${fwId}/${v}/publish`, {}
    );
    expect(status, '契约不可解析时发布应 422').toBe(422);
    expect(json?.errorCode).toBe('ECOS-COG-422R');
    const missing = String(json?.data?.missing ?? '');
    expect(missing, '必须列出 inputSchemaRef 缺失').toContain('inputSchemaRef');
    expect(missing, '必须列出 outputSchemaRef 缺失').toContain('outputSchemaRef');
  });

  test('注册 DRAFT → 发布 PUBLISHED → status=PUBLISHED 列表可见 → 再发布 409', async ({ request }) => {
    const v = `1.0.0-${stamp}`;
    const reg = await post(request, '/api/v1/cognitive/frameworks', {
      ...base,
      frameworkId: fwId,
      version: v,
      steps: [{ stepId: 's1', executorType: 'deterministic_tool' }],
      inputSchemaRef: 'schema://verify/in',
      outputSchemaRef: 'schema://verify/out',
      contractResolvable: true,
      governance: { allowDirectSideEffects: false },
    });
    expect(reg.status, '合法框架应注册成功').toBe(200);
    expect(reg.json?.data?.status, '注册后必须是 DRAFT').toBe('DRAFT');
    expect(String(reg.json?.data?.contentHash ?? ''), '注册必须落内容哈希').toMatch(/^sha256:[0-9a-f]{64}$/);

    const pub = await post(request, `/api/v1/cognitive/frameworks/${fwId}/${v}/publish`, {});
    expect(pub.status, '合法框架发布应 200').toBe(200);
    expect(pub.json?.data?.status, '发布后必须是 PUBLISHED').toBe('PUBLISHED');

    const list = await get(request, '/api/v1/cognitive/frameworks?status=PUBLISHED');
    expect(list.status).toBe(200);
    const rows = (list.json!.data as any[]) ?? [];
    const hit = rows.find((r) => r.frameworkId === fwId && r.version === v);
    expect(hit, 'PUBLISHED 过滤列表必须能看到刚发布的版本').toBeTruthy();
    expect(hit.status, '可见条目状态须为 PUBLISHED').toBe('PUBLISHED');

    const again = await post(request, `/api/v1/cognitive/frameworks/${fwId}/${v}/publish`, {});
    expect(again.status, '已发布版本再次发布应 409（不可原地改）').toBe(409);
    expect(again.json?.errorCode).toBe('ECOS-COG-409R');
  });
});

test.describe('V-COG-074 / 075 / 087 / 071 契约面：不存在资源必须 404 且自述', () => {
  const cases = [
    { p: '/api/v1/cognitive/outcomes/x/backtest', id: 'V-COG-074', must: /outcome/i },
    { p: '/api/v1/cognitive/decision-basis/x', id: 'V-COG-087', must: /decision/i },
    { p: '/api/v1/cognitive/forecast-runs/x/results', id: 'V-COG-071', must: /forecast/i },
    { p: '/api/v1/cognitive/forecast-runs/x/evidence', id: 'V-COG-071', must: /forecast/i },
  ];

  for (const { p, id, must } of cases) {
    test(`${id} ${p} 不存在 id ⇒ 404 且点名资源类型`, async ({ request }) => {
      const { status, json } = await get(request, p);
      expect(NOT_5XX, `${p} 不得 5xx`).not.toContain(status);
      expect(status, `${p} 应 404`).toBe(404);
      expect(String(json?.message ?? ''), `${p} 必须点名哪种资源不存在`).toMatch(must);
    });
  }

  test('V-COG-075 review-tasks 实测 405（路径在、动词不对）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/outcomes/x/review-tasks');
    expect(status, 'review-tasks 当前实测 405；若改为 GET 请更新本腿与 V-COG-075')
      .toBe(405);
    expect(json?.errorCode, '405 必须带机读码').toBe('ECOS-SYS-405');
  });
});

test.describe('V-COG-103 / 104 / 094 目录与演示面：区分 405 与 404', () => {
  test('objects 目录返回真实条目（实测 11 条）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/objects');
    expect(status, 'cognitive/objects 应 200').toBe(200);
    expect((json!.data as any[]).length, '对象目录必须有条目').toBeGreaterThan(0);
  });

  test('executions / replay / assets-proposals 实测 405：端点已注册但动词不同', async ({ request }) => {
    const m405 = [
      { p: '/api/v1/cognitive/executions', method: 'GET' },
      { p: '/api/v1/cognitive/replay', method: 'POST' },
      { p: '/api/v1/cognitive/assets/x/proposals', method: 'GET' },
    ];
    for (const { p, method } of m405) {
      const r = method === 'GET' ? await get(request, p) : await post(request, p);
      expect(r.status, `${p} 当前实测 405（路径存在、方法不符）；若改动词请更新 V-COG-046/054/094`)
        .toBe(405);
      expect(r.json?.errorCode, `${p} 405 必须带 ECOS-SYS-405`).toBe('ECOS-SYS-405');
    }
  });

  test('assets / plans / demo-wave3 路由未注册（404 登记）', async ({ request }) => {
    for (const p of ['/api/v1/cognitive/assets', '/api/v1/cognitive/plans',
      '/api/v1/cognitive/demo/wave3']) {
      const { status, json } = await get(request, p);
      expect(status, `${p} 当前实测 404；若已实现请改判 V-COG-103/104`).toBe(404);
      expect(json?.errorCode).toBe('ECOS-SYS-404');
    }
  });
});

test.describe('V-COG-084 通用预测：缺 metric 必须 400 点名', () => {
  test('forecast 空体 ⇒ 400 + FCST-400 文案含 metric', async ({ request }) => {
    const { status, json } = await post(request, '/api/v1/cognitive/forecast', {});
    expect(NOT_5XX, 'forecast 不得 5xx').not.toContain(status);
    expect(status, '缺 metric 应 400').toBe(400);
    expect(String(json?.message ?? ''), '400 必须点名 metric').toContain('metric');
  });
});

/**
 * V-COG-083 mental-reviews（2026-11-16 活体闭环）。
 *
 * 矩阵原写 `POST /mental-review`——路径与动词都不对，源码是 `MentalReviewController`
 * 的 `@GetMapping("/mental-reviews")`（复数 + GET）。本块按源码口径打，覆盖四向：
 * ① 白名单 tag 返回八段结构真实聚合；② 缺 tag 400 点名；③ 非法 tag 400 且报白名单；
 * ④ 非法 since 400 点名 ISO 格式 + 未来 since 返回全零（证明 since 下界真的进了 SQL，
 * 不是装饰参数）。tag 白名单单源 = `MentalEventPublisher.REVIEW_TAG`。
 */
test.describe('V-COG-083 心理复盘：周期聚合 + tag 白名单 + since 下界', () => {
  const TAG = 'P2b-mental-layer-review';

  test('白名单 tag ⇒ 200 且八段结构完整 + summary 计数与各段行数自洽', async ({ request }) => {
    const { status, json } = await get(request, `/api/v1/cognitive/mental-reviews?tag=${TAG}`);
    expect(NOT_5XX, 'mental-reviews 不得 5xx').not.toContain(status);
    expect(status, '白名单 tag 应 200').toBe(200);
    const d = json!.data as any;
    expect(Object.keys(d).sort(), '复盘报告必须含八段既有键（只增键不改契约）').toEqual(
      ['beliefTimelines', 'evidence', 'hypotheses', 'reconstructionMode', 'runImpacts',
        'since', 'summary', 'tag', 'warnAlerts'].sort(),
    );
    expect(d.tag, '回显 tag 必须等于请求 tag').toBe(TAG);
    expect(d.since, '未传 since 时应为 null（非空串）').toBeNull();
    expect(String(d.reconstructionMode), '必须自述重建口径（Kafka 事件未落 PG）')
      .toContain('three-table-version-chain');

    // summary 计数必须与各段实际行数自洽——防「计数是写死的」这类假绿
    const versions = (d.beliefTimelines as any[]).reduce(
      (n, g) => n + (g.versions?.length ?? 0), 0);
    expect(d.summary.beliefVariables, 'summary.beliefVariables == beliefTimelines 组数')
      .toBe((d.beliefTimelines as any[]).length);
    expect(d.summary.beliefVersions, 'summary.beliefVersions == 各变量版本行数之和')
      .toBe(versions);
    expect(d.summary.hypothesesTotal, 'summary.hypothesesTotal == hypotheses 行数')
      .toBe((d.hypotheses as any[]).length);
    expect(d.summary.evidenceCount, 'summary.evidenceCount == evidence 行数')
      .toBe((d.evidence as any[]).length);
    expect(d.summary.runsSuperseded, 'summary.runsSuperseded == runImpacts 行数')
      .toBe((d.runImpacts as any[]).length);
    expect(d.summary.warnAlerts, 'summary.warnAlerts == warnAlerts 行数')
      .toBe((d.warnAlerts as any[]).length);
    expect(d.summary.hypothesesInvalidated, '已失效假设数不得超过假设总数')
      .toBeLessThanOrEqual(d.summary.hypothesesTotal);

    // 至少一段有真实数据，否则「返回真实分析」这半句无实证
    const total = versions + d.summary.hypothesesTotal + d.summary.evidenceCount
      + d.summary.runsSuperseded + d.summary.warnAlerts;
    expect(total, '库内既有心智层种子数据，聚合不得全空').toBeGreaterThan(0);

    // 版本链内部有序（版本号严格递增），复盘时间线才可用
    for (const g of d.beliefTimelines as any[]) {
      const vs = (g.versions as any[]).map((v) => v.version);
      expect(vs, `变量 ${g.variableName} 版本链必须升序`).toEqual([...vs].sort((a, b) => a - b));
      for (const v of g.versions as any[]) {
        expect(Number(v.version), '版本号必须是数字').toBeGreaterThan(0);
        expect(Array.isArray(v.distribution), '信念分布必须是数组（已解析 JSON）').toBeTruthy();
      }
    }
  });

  test('缺 tag ⇒ 400 且 data.missing 点名 tag', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/mental-reviews');
    expect(status, '缺必填 tag 应 400').toBe(400);
    expect(json?.errorCode, '参数门必须带机读码').toBe('ECOS-COG-400P');
    expect(String((json?.data as any)?.missing ?? ''), '400 必须点名缺的字段').toBe('tag');
  });

  test('非法 tag ⇒ 400 且在文案里报白名单（防无差别全量导出）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/cognitive/mental-reviews?tag=bogus-tag');
    expect(status, '非白名单 tag 应 400 而非 200').toBe(400);
    const msg = String(json?.message ?? '');
    expect(msg, '必须点出非法 tag 值').toContain('bogus-tag');
    expect(msg, '必须把白名单写给调用方').toContain(TAG);
  });

  test('非法 since ⇒ 400 点名 ISO-8601；未来 since ⇒ 200 且各段全零（下界真生效）', async ({ request }) => {
    const bad = await get(request,
      `/api/v1/cognitive/mental-reviews?tag=${TAG}&since=not-a-date`);
    expect(bad.status, '非法 ISO 时间应 400').toBe(400);
    expect(String(bad.json?.message ?? ''), '必须点名格式要求').toContain('ISO-8601');
    expect(String(bad.json?.message ?? ''), '必须回显非法输入').toContain('not-a-date');

    const future = await get(request,
      `/api/v1/cognitive/mental-reviews?tag=${TAG}&since=2099-01-01T00:00:00`);
    expect(future.status, '未来下界是合法查询，应 200').toBe(200);
    const fd = future.json!.data as any;
    expect(fd.summary, '未来下界下所有计数必须为 0（若回出数据说明 since 没进 SQL）').toEqual({
      beliefVariables: 0, beliefVersions: 0, hypothesesTotal: 0, hypothesesInvalidated: 0,
      evidenceCount: 0, runsSuperseded: 0, warnAlerts: 0,
    });
    expect(fd.beliefTimelines, '未来下界下 belief 时间线必须为空').toEqual([]);
    expect(fd.evidence, '未来下界下证据时间线必须为空').toEqual([]);
  });
});
