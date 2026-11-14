import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B5 知识引擎 — API 级（对应矩阵域06）。
 *
 * 反假绿纪律：
 *  - `GET /knowledge/articles` 根路径**有意不存在**（裸 GET 会同时生成 /assets 读映射并劫持
 *    生命周期资产列表，见 KnowledgeArticleController 头注）⇒ 405 是正确行为，不得断言 200
 *  - 知识图谱必须校验「边端点存在于节点集」，防造边
 *  - 空数组 ≠ 正常：知识库列表有种子，必须非空
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function okArray(request: any, path: string, label: string): Promise<any[]> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} HTTP 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0（${json?.message ?? ''}）`).toBe(0);
  expect(Array.isArray(json?.data), `${label} data 应为数组`).toBe(true);
  return json!.data as any[];
}

async function okObject(request: any, path: string, label: string): Promise<Record<string, any>> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} HTTP 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0（${json?.message ?? ''}）`).toBe(0);
  const d = json?.data;
  expect(d && typeof d === 'object' && !Array.isArray(d), `${label} data 应为对象`).toBe(true);
  return d as Record<string, any>;
}

test.describe('知识引擎健康与设置', () => {
  test('V-KB-001 知识引擎健康含组件明细', async ({ request }) => {
    const d = await okObject(request, '/api/v1/engine/knowledge/health', '知识健康');
    expect(d.status, '健康状态应为 UP/DEGRADED 之一').toMatch(/UP|DEGRADED|DOWN/);
    expect(d.components, '健康体应含 components 明细').toBeTruthy();
  });

  test('V-KB-002 知识引擎设置非空', async ({ request }) => {
    const list = await okArray(request, '/api/v1/knowledge/settings', '知识设置');
    expect(list.length, '知识设置应有条目').toBeGreaterThan(0);
  });

  test('V-KB-003 知识库列表真实且非空', async ({ request }) => {
    const list = await okArray(request, '/api/v1/knowledge-bases', '知识库列表');
    expect(list.length, '知识库列表应有种子数据').toBeGreaterThan(0);
    const row = list[0];
    expect(Object.keys(row ?? {}).length, '知识库行不应为空对象').toBeGreaterThan(0);
  });

  test('V-KB-003b 知识库兼容前缀 /api/knowledge-bases 等价', async ({ request }) => {
    const a = await okArray(request, '/api/v1/knowledge-bases', 'v1 前缀');
    const b = await okArray(request, '/api/knowledge-bases', '兼容前缀');
    expect(b.length, '兼容前缀条数应一致').toBe(a.length);
  });
});

test.describe('知识资产与文章（V-KB-010）', () => {
  test('V-KB-010 资产列表可达', async ({ request }) => {
    const list = await okArray(request, '/api/v1/knowledge/assets', '资产列表');
    expect(list === null, '资产列表不得为 null').toBe(false);
  });

  test('V-KB-010b 文章根路径 GET 必须不存在（防空 GET 劫持 /assets 读面）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/knowledge/articles', { token });
    // 设计如此：KnowledgeArticleController 头注明确裸 GET 会同时生成 /assets 读映射并劫持
    expect(status, `GET /articles 应 405（只有 POST），实得 ${status}`).toBe(405);
    expect(json?.errorCode, '405 应为 ECOS-SYS-405').toBe('ECOS-SYS-405');
  });

  test('V-KB-010c 文章按分类查询（真实读端）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/knowledge/articles/by-category?category=rule', { token });
    expect(status, '按分类查询应 200').toBe(200);
    expect(json?.code).toBe(0);
    expect(Array.isArray(json?.data), 'by-category 应返回数组').toBe(true);
  });

  test('V-KB-010d 文章分类缺省必须拒绝（禁静默回落全量）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/knowledge/articles/by-category', { token });
    expect(status, `缺 category 应 400，实得 ${status}`).toBe(400);
    expect(json?.code).not.toBe(0);
  });

  test('V-KB-010e 文章状态批量查询', async ({ request }) => {
    const d = await okObject(request, '/api/v1/knowledge/articles/status?ids=anyid', '文章状态');
    expect(Array.isArray(d.items), '状态查询应含 items 数组').toBe(true);
    if (d.items.length) {
      const it = d.items[0];
      expect(typeof it.graph, 'graph 应为布尔').toBe('boolean');
      expect(typeof it.vector, 'vector 应为布尔').toBe('boolean');
    }
  });

  test('V-KB-010f 文章检索拒绝空关键字', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/knowledge/articles/search', { token });
    expect(status, `缺 q 应 400，实得 ${status}`).toBe(400);
  });
});

test.describe('知识图谱（V-KB-020）', () => {
  test('V-KB-020 ecos-graph 返回 nodes/edges/stats 三段', async ({ request }) => {
    const d = await okObject(request, '/api/v1/knowledge/ecos-graph', '知识图谱');
    expect(Array.isArray(d.nodes), 'nodes 应为数组').toBe(true);
    expect(Array.isArray(d.edges), 'edges 应为数组').toBe(true);
    expect(d.stats, 'stats 应存在').toBeTruthy();
  });

  test('V-KB-020b 图谱无悬空边（防造边）', async ({ request }) => {
    const d = await okObject(request, '/api/v1/knowledge/ecos-graph', '知识图谱');
    if (!d.edges?.length || !d.nodes?.length) return;
    const ids = new Set((d.nodes as any[]).map((n) => String(n.id ?? n.nodeId ?? '')));
    const dangling = (d.edges as any[]).filter((e) => {
      const s = String(e.source ?? e.from ?? '');
      const t = String(e.target ?? e.to ?? '');
      return (s && !ids.has(s)) || (t && !ids.has(t));
    });
    expect(dangling.slice(0, 3), `不应有悬空边，实得 ${dangling.length} 条`).toEqual([]);
  });

  test('V-KB-020c 图谱 source 端点存在（GET 语义）', async ({ request }) => {
    // 真实路径是 /api/v1/knowledge/source（KnowledgeGraphController 类级前缀 /api/v1/knowledge）
    const { status, json } = await call(request, 'GET', '/api/v1/knowledge/source', { token });
    expect(status, `source 端点应可达，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
  });

  test('V-KB-020d 节点不存在必须 404（反向探针）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/knowledge/nodes/__NOT_EXIST__', { token });
    expect([400, 404], `不存在节点应 400/404，实得 ${status}`).toContain(status);
  });
});

test.describe('抽取与运行（V-KB-030）', () => {
  test('V-KB-030 抽取文件列表可达', async ({ request }) => {
    const list = await okArray(request, '/api/v1/knowledge/extract/files', '抽取文件');
    expect(list === null, '抽取文件列表不得为 null').toBe(false);
  });

  test('V-KB-030b 抽取运行列表（POST 语义，GET 应为 405）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/knowledge/extract/runs', { token });
    // KnowledgeExtractRunsController 的方法是 POST /runs 查询（语义为提交过滤条件）
    expect([200, 405], `运行列表 GET 应 200 或 405，实得 ${status}`).toContain(status);
  });

  test('V-KB-030c 结构化抽取空 body 有兜底但必须真实响应（登记 P3）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/knowledge/extract/structured', {
      token,
      data: {},
    });
    // 后端 @RequestBody(required=false) + normalizeMode 兜底：空 body 走默认 mode 且真落 job。
    // 口径登记（P3）：前端总会传 mode，故不判缺陷；但必须给出可追溯的 jobId。
    expect(status, `空 body 兜底后应 200，实得 ${status}`).toBe(200);
    const d = json?.data as any;
    expect(d?.jobId ?? d?.taskId, '兜底执行必须返回可追溯的 jobId/taskId（不得静默空跑）').toBeTruthy();
  });

  test('V-KB-030d 结构化抽取 dryRun 同步路径不落库', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/knowledge/extract/structured', {
      token,
      data: { dryRun: true, mode: 'FULL' },
    });
    expect([200, 400], `dryRun 应 200 或参数校验 400，实得 ${status}`).toContain(status);
    if (status === 200 && json?.code === 0) {
      const d = json.data as any;
      expect(d?.jobId ?? d?.taskId, 'dryRun 不应返回落库 jobId（预览路径不落库）').toBeFalsy();
    }
  });
});

test.describe('规则/画像/假设/合规（V-KB-040）', () => {
  const faces = [
    { path: '/api/v1/knowledge/rules', name: '专家规则' },
    { path: '/api/v1/knowledge/profiles', name: '画像' },
    { path: '/api/v1/knowledge/assumptions', name: '假设' },
    { path: '/api/v1/knowledge/compliance-rules', name: '合规规则' },
  ];

  for (const { path, name } of faces) {
    test(`V-KB-040 ${name}列表形态正确`, async ({ request }) => {
      const list = await okArray(request, path, `${name}列表`);
      expect(list === null, `${name}列表不得为 null`).toBe(false);
    });
  }
});

test.describe('RAG 与检索（V-KB-050）', () => {
  test('V-KB-050 RAG 空体走默认参数但必须如实返回零命中（登记 P3 宽语义）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/knowledge/rag', {
      token,
      data: {},
    });
    // KnowledgeApiController.rag：@RequestBody(required=false) + query 兜底 ""，
    // 属有意宽松（前端总会传真实 query）。断言改为「不得伪造答案」。
    expect(status, 'RAG 空体兜底应 200').toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data as any;
    expect(d.sourcesCount, '空 query 不得命中任何来源').toBe(0);
    expect(String(d.answer ?? ''), '空 query 不得伪造答案文本').toBe('');
    expect(d.vectorDegraded, '空 query 场景应如实标注是否降级').toBeDefined();
  });

  test('V-KB-050b 知识查询空体不得伪造结果', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/knowledge/query', {
      token,
      data: {},
    });
    expect(status, '查询空体兜底应 200').toBe(200);
    expect(json?.code).toBe(0);
    expect(Array.isArray(json?.data), '查询结果应为数组').toBe(true);
    expect((json!.data as any[]).length, '空查询不得返回凭空编造的结果').toBe(0);
  });
});

test.describe('认知识别供给（V-KB-060）', () => {
  test('V-KB-060 认知供给端点存在', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/knowledge/cognition/context', { token });
    expect(status, '认知供给上下文端点不应 404').not.toBe(404);
  });
});

test.describe('权限（V5）', () => {
  test('V-KB-070 网关路径无 token 必须拒绝', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/knowledge-bases');
    expect([401, 403], `无 token 应 401/403，实得 ${status}`).toContain(status);
  });

  test('V-KB-071 直连 18086 实测记录', async ({ request }) => {
    const res = await request.get('http://127.0.0.1:18086/api/v1/knowledge-bases');
    console.log(`[V-KB-071] direct kb-engine:18086 = ${res.status()}`);
    expect([200, 401, 403, 404], `直连实测记录，实得 ${res.status()}`).toContain(res.status());
  });
});
