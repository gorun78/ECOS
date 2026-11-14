import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B5 知识引擎 — 端点面补充（矩阵域06 的 V-KB-023~055 等 29 条，2026-11-13 验证批）。
 *
 * 覆盖 `kb-engine-impl` 下 18 个 Controller 的真实端点。反假绿纪律（本批三条教训）：
 *  1. **空集不洗白成「功能正常」**：本组用例只钉「可达 + 信封自洽 + 必填校验生效」，
 *     不把 `data:[]` 当成「检索有结果」；对「必须非空」的面（知识库列表/导航分类）另行断言。
 *  2. **必填缺失必须 4xx**：多个端点实测对空体返回 400（如 retrieval/search 缺 question、
 *     graph/search 缺 keyword、profiles/resolve 缺 metric），逐条做反向腿，
 *     否则「空查询返回空集」会被误当成功。
 *  3. **路径必须来自 Controller 真实映射**：本组每条都记录前缀来源的 Controller 名，
 *     避免本批此前把集合根路径当成子路径探（D43 同族）。
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function getJson(request: any, path: string) {
  return call(request, 'GET', path, { token });
}

async function postJson(request: any, path: string, data: unknown) {
  return call(request, 'POST', path, { token, data });
}

function expectOk(status: number, json: any, label: string): void {
  expect(status, `${label} HTTP 应 200，实得 ${status}（${json?.message ?? ''}）`).toBe(200);
  expect(json?.code, `${label} code 应为 0`).toBe(0);
}

function expectRejected(status: number, json: any, label: string): void {
  expect(status, `${label} 必须 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
  expect(status, `${label} 不得 5xx`).toBeLessThan(500);
  expect(json?.code, `${label} 失败信封 code 不得为 0`).not.toBe(0);
}

test.describe('知识库与检索（V-KB-023 / 024 / 025 / 026）', () => {
  test('V-KB-023 知识库列表双前缀一致且非空', async ({ request }) => {
    const a = await getJson(request, '/api/v1/knowledge-bases');
    const b = await getJson(request, '/api/knowledge-bases');
    expectOk(a.status, a.json, 'knowledge-bases v1');
    expectOk(b.status, b.json, 'knowledge-bases 兼容前缀');
    expect(a.json!.data.length, '知识库列表必须有种子（空集不算通过）').toBeGreaterThan(0);
    expect(JSON.stringify(b.json!.data), '双前缀必须返回同一份数据').toBe(JSON.stringify(a.json!.data));
  });

  test('V-KB-024 知识检索：缺 question 必须 400（禁空查询返回空集冒充成功）', async ({ request }) => {
    const bad = await postJson(request, '/api/v1/knowledge/retrieval/search', { query: '本体', topK: 5 });
    expectRejected(bad.status, bad.json, 'retrieval/search 缺 question');
    expect(String(bad.json?.message ?? ''), '拒绝原因须点名 question').toContain('question');
  });

  test('V-KB-025 生命周期：资产列表 + 审计面可达且分页字段自洽', async ({ request }) => {
    const assets = await getJson(request, '/api/v1/knowledge/assets?page=1&pageSize=5');
    expectOk(assets.status, assets.json, 'knowledge/assets');
    const audit = await getJson(request, '/api/v1/knowledge/lifecycle/audit?page=1&pageSize=5');
    expectOk(audit.status, audit.json, 'lifecycle/audit');
    expect(Array.isArray(assets.json!.data), '资产列表应为数组').toBe(true);
    expect(Array.isArray(audit.json!.data), '审计列表应为数组').toBe(true);
  });

  test('V-KB-026 设置读写面：settings 非空 + upload-enabled 返回布尔闸门', async ({ request }) => {
    const settings = await getJson(request, '/api/v1/knowledge/settings');
    expectOk(settings.status, settings.json, 'knowledge/settings');
    expect(Array.isArray(settings.json!.data) && settings.json!.data.length > 0,
      '设置项必须有配置行（空集说明读不到 sys_config 分组）').toBeTruthy();
    const gate = await getJson(request, '/api/v1/knowledge/settings/extract/upload-enabled');
    expectOk(gate.status, gate.json, 'upload-enabled');
    expect(typeof gate.json!.data.allowed, 'allowed 必须为布尔').toBe('boolean');
  });
});

test.describe('档案 / 抽取 / 本体树 / RAG（V-KB-027 / 031~035）', () => {
  test('V-KB-027 档案：列表可达 + resolve 缺 metric 必须 400 + stock 带证据表名', async ({ request }) => {
    const list = await getJson(request, '/api/v1/knowledge/profiles');
    expectOk(list.status, list.json, 'profiles 列表');
    const bad = await getJson(request, '/api/v1/knowledge/profiles/resolve?kbId=kb-default');
    expectRejected(bad.status, bad.json, 'profiles/resolve 缺 metric');
    expect(String(bad.json?.message ?? ''), '拒绝原因须点名 metric').toContain('metric');
    const stock = await getJson(request, '/api/v1/knowledge/profiles/stock');
    expectOk(stock.status, stock.json, 'profiles/stock');
    expect(String(stock.json!.data?.evidence?.table ?? ''), 'stock 必须自报证据表名')
      .toContain('ecos_kb_profile');
  });

  test('V-KB-031 抽取运行：水位线可达 + 不存在 taskId 必须 404', async ({ request }) => {
    const wm = await getJson(request, '/api/v1/knowledge/extract/watermarks');
    expectOk(wm.status, wm.json, 'extract/watermarks');
    const missing = await getJson(request, '/api/v1/knowledge/extract/runs/VERIFY_NOT_EXIST_999');
    expect(missing.status, '不存在 taskId 必须 404（禁 200+空壳）').toBe(404);
    expect(missing.json?.code, '失败信封 code 不得为 0').not.toBe(0);
  });

  test('V-KB-032 结构化抽取：jobs 分页 + 不存在 status 必须 404', async ({ request }) => {
    const jobs = await getJson(request, '/api/v1/knowledge/extract/structured/jobs?page=1&pageSize=5');
    expectOk(jobs.status, jobs.json, 'structured/jobs');
    const missing = await getJson(request, '/api/v1/knowledge/extract/structured/status/VERIFY_NOT_EXIST_999');
    expect(missing.status, '不存在 taskId 的 status 必须 404').toBe(404);
  });

  test('V-KB-032b 结构化抽取日志：无记录时返回文档化占位条目而非 500（登记 P3）', async ({ request }) => {
    const logs = await getJson(request, '/api/v1/knowledge/extract/structured/logs/VERIFY_NOT_EXIST_999');
    expectOk(logs.status, logs.json, 'structured/logs');
    const rows = logs.json!.data as any[];
    expect(Array.isArray(rows) && rows.length > 0, '无审计行时须给占位条目（不为空、不抛 500）').toBeTruthy();
    // 占位条目是文档化设计（StructuredExtractController T6 注释），但它对任意 jobId 都返回，
    // 因此这里显式登记该形态——若将来改成 404 或空数组，本用例会红，强制重新裁决。
    expect(String(rows[0]?.message ?? ''), '当前占位条目文案为 [FAIL_TO_RETRO]').toContain('FAIL_TO_RETRO');
  });

  test('V-KB-033 定时抽取：列表可达（要求 agentId 之外的调度 CRUD 读面）', async ({ request }) => {
    const list = await getJson(request, '/api/v1/knowledge/extract/scheduled');
    expectOk(list.status, list.json, 'extract/scheduled');
    expect(Array.isArray(list.json!.data), '调度列表应为数组').toBe(true);
  });

  test('V-KB-034 本体树可达', async ({ request }) => {
    const tree = await getJson(request, '/api/v1/knowledge/extract/ontology-tree');
    expectOk(tree.status, tree.json, 'extract/ontology-tree');
    expect(Array.isArray(tree.json!.data), '本体树应为数组').toBe(true);
  });

  test('V-KB-035 RAG：查询真实返回且降级态自述原因 + 图健康面可达', async ({ request }) => {
    const rag = await postJson(request, '/api/v1/knowledge/rag/rag', { query: '本体', topK: 3 });
    expectOk(rag.status, rag.json, 'rag/rag');
    const d = rag.json!.data;
    expect(d.query, 'RAG 必须回显查询词').toBe('本体');
    expect(Array.isArray(d.sources), 'sources 应为数组').toBe(true);
    // 向量不可用时必须 degraded=true 并给出原因（禁静默假装召回正常）
    if (d.vectorDegraded === true) {
      expect(String(d.vectorDegradedReason ?? ''), '降级必须自述原因').not.toBe('');
    }
    const health = await getJson(request, '/api/v1/knowledge/rag/graph/health');
    expectOk(health.status, health.json, 'rag/graph/health');
    expect(typeof health.json!.data.healthy, 'healthy 必须为布尔').toBe('boolean');
  });
});

test.describe('图谱与同步（V-KB-041~045）', () => {
  test('V-KB-041 ECOS 图谱返回 nodes/edges/stats 三段且无悬空边', async ({ request }) => {
    const { status, json } = await getJson(request, '/api/v1/knowledge/ecos-graph');
    expectOk(status, json, 'knowledge/ecos-graph');
    const d = json!.data;
    expect(Array.isArray(d.nodes) && Array.isArray(d.edges), 'nodes/edges 必须为数组').toBeTruthy();
    const ids = new Set((d.nodes as any[]).map((n) => n.id));
    const dangling = (d.edges as any[]).filter((e) => !ids.has(e.source) || !ids.has(e.target));
    expect(dangling, `不得出现悬空边（造边）: ${JSON.stringify(dangling).slice(0, 200)}`).toHaveLength(0);
  });

  test('V-KB-042 图查询：非法查询拒绝（缺 keyword / 缺端点）', async ({ request }) => {
    const ok = await postJson(request, '/api/v1/knowledge/graph/search', { keyword: '本体', limit: 5 });
    expectOk(ok.status, ok.json, 'graph/search 合法');
    expect(Array.isArray(ok.json!.data), 'search 结果应为数组').toBe(true);

    const empty = await postJson(request, '/api/v1/knowledge/graph/search', {});
    expectRejected(empty.status, empty.json, 'graph/search 缺 keyword');
    expect(String(empty.json?.message ?? ''), '拒绝原因须点名 keyword').toContain('keyword');

    const badPath = await postJson(request, '/api/v1/knowledge/graph/path', { fromId: 'x', toId: 'y' });
    expectRejected(badPath.status, badPath.json, 'graph/path 缺端点');
  });

  test('V-KB-043 实体链接：双前缀都在 + 缺 entityName 必须 400', async ({ request }) => {
    for (const path of ['/api/v1/knowledge/entity-link/link', '/api/v1/knowledge/graph/entity-link/link']) {
      const bad = await postJson(request, path, { text: '本体' });
      expectRejected(bad.status, bad.json, `${path} 缺 entityName`);
      expect(String(bad.json?.message ?? ''), '拒绝原因须点名 entityName').toContain('entityName');
    }
  });

  test('V-KB-044 同步状态/日志/任务三面可达，且 graph/sync 前缀独立可用', async ({ request }) => {
    const status = await getJson(request, '/api/v1/knowledge/sync/status');
    expectOk(status.status, status.json, 'sync/status');
    expect(Array.isArray(status.json!.data.objectTypes), 'objectTypes 应为数组').toBe(true);

    const logs = await getJson(request, '/api/v1/knowledge/sync/logs?page=1&pageSize=5');
    expectOk(logs.status, logs.json, 'sync/logs');

    const jobs = await getJson(request, '/api/v1/knowledge/sync/jobs');
    expectOk(jobs.status, jobs.json, 'sync/jobs');

    const gStatus = await getJson(request, '/api/v1/knowledge/graph/sync/status');
    expectOk(gStatus.status, gStatus.json, 'graph/sync/status');
    // diff.matched 三态：true/false=已完成对账；null=对账做不了（如 Neo4j 投影不可读）。
    // 2026-11-13 修正：原断言「必须是 boolean」把 `null` 判红，但那正是诚实表达——
    // 实测 `reason="Neo4j 投影计数不可读：Unsupported authentication token, scheme 'none'…"`，
    // 此时给 true/false 都是编造结论。改为「boolean 或 null，但 null 必须自述原因」。
    const matched = gStatus.json!.data.diff?.matched;
    expect([true, false, null], `diff.matched 只允许 boolean 或 null，实得 ${JSON.stringify(matched)}`)
      .toContain(matched);
    if (matched === null) {
      expect(String(gStatus.json!.data.reason ?? ''),
        '对账未完成（matched=null）时必须自述原因，禁静默留空')
        .not.toBe('');
    }
  });

  test('V-KB-045 同步状态域维度 lastSyncTime 仍为 null（P1-13 残余，登记 PARTIAL 不得记 PASS）', async ({ request }) => {
    const { status, json } = await getJson(request, '/api/v1/knowledge/sync/status');
    expectOk(status, json, 'sync/status（域维度）');
    const rows = json!.data.objectTypes as any[];
    // 本用例的价值是「钉住残余形态」：P1-13 修了 ALL 维度，域维度（Table/Column/Task/Indicator）仍 null。
    // 一旦某天域维度也被回填，本断言会红 → 强制把矩阵 V-KB-045 从 PARTIAL 改判 PASS。
    const byType = Object.fromEntries(rows.map((r) => [r.type, r.lastSyncTime]));
    // eslint-disable-next-line no-console
    console.log(`[V-KB-045] lastSyncTime byType=${JSON.stringify(byType)}`);
    expect(rows.some((r) => r.type === 'ALL'), '状态体必须含 ALL 维度（P1-13 已修的维度）').toBeTruthy();
    const domainRows = rows.filter((r) => r.type !== 'ALL');
    expect(domainRows.length, '必须有域维度行').toBeGreaterThan(0);
    const allNull = domainRows.every((r) => r.lastSyncTime === null);
    expect(allNull, `域维度 lastSyncTime 当前应仍为 null（P1-13 残余）；若已回填请把矩阵 V-KB-045 改判 PASS。实得=${JSON.stringify(byType)}`)
      .toBe(true);
  });
});

test.describe('合规 / 评估 / 假设 / 导航（V-KB-051~055）', () => {
  test('V-KB-051 合规规则：列表真实 + 不存在 id 必须 404 + 规则族可达', async ({ request }) => {
    const list = await getJson(request, '/api/v1/knowledge/compliance-rules?page=1&pageSize=5');
    expectOk(list.status, list.json, 'compliance-rules 列表');
    const missing = await getJson(request, '/api/v1/knowledge/compliance-rules/VERIFY_NOT_EXIST_999');
    expect(missing.status, '不存在规则必须 404').toBe(404);
    const rules = await getJson(request, '/api/v1/knowledge/rules?page=1&pageSize=5');
    expectOk(rules.status, rules.json, 'knowledge/rules');
  });

  test('V-KB-052 知识评估：真实计算并如实标注降级', async ({ request }) => {
    const evalRun = await postJson(request, '/api/v1/knowledge/eval/run', {});
    expectOk(evalRun.status, evalRun.json, 'eval/run');
    const d = evalRun.json!.data;
    expect(String(d.reportId ?? ''), '评估必须产出 reportId').not.toBe('');
    expect(typeof d.degraded, 'degraded 必须为布尔（禁静默假指标）').toBe('boolean');
    expect(typeof d.recallAt5, 'recallAt5 必须为数值').toBe('number');
  });

  test('V-KB-053 假设：列表可达 + resolve 缺 scenarioType 必须 400', async ({ request }) => {
    const list = await getJson(request, '/api/v1/knowledge/assumptions?page=1&pageSize=5');
    expectOk(list.status, list.json, 'assumptions 列表');
    const bad = await getJson(request, '/api/v1/knowledge/assumptions/resolve?kbId=kb-default');
    expectRejected(bad.status, bad.json, 'assumptions/resolve 缺 scenarioType');
    expect(String(bad.json?.message ?? ''), '拒绝原因须点名 scenarioType').toContain('scenarioType');
  });

  test('V-KB-055 导航分类：树真实非空 + 域/模块/产品面可达 + version_no 为数值', async ({ request }) => {
    const cats = await getJson(request, '/api/v1/knowledge/nav/categories');
    expectOk(cats.status, cats.json, 'nav/categories');
    expect((cats.json!.data as any[]).length, '导航分类必须有种子（空树不算通过）').toBeGreaterThan(0);

    for (const path of ['/api/v1/knowledge/nav/tags', '/api/v1/knowledge/nav/domains',
      '/api/v1/knowledge/nav/modules', '/api/v1/knowledge/nav/products?page=1&pageSize=5']) {
      const r = await getJson(request, path);
      expectOk(r.status, r.json, path);
    }

    // 矩阵要求「version_no 数值递增（非字符串）」：分类行的版本字段若出现必须是数值
    const first = (cats.json!.data as any[])[0];
    if ('versionNo' in first) {
      expect(typeof first.versionNo, `versionNo 必须是数值，实为 ${typeof first.versionNo}`).toBe('number');
    }
  });
});

test.describe('写越权与上传安全（V-KB-082 / 083）', () => {
  test('V-KB-082 知识写越权：无 token 写分类必须被拒', async ({ request }) => {
    const res = await request.fetch('/api/v1/knowledge/nav/categories', {
      method: 'POST',
      data: { name: 'x', domain: 'default' },
    });
    expect(res.status(), '无 token 写必须被拒').toBeGreaterThanOrEqual(401);
    expect(res.status(), '不得 5xx').toBeLessThan(500);
  });

  test('V-KB-083 文档上传安全：恶意脚本类型必须 415（白名单外），合法 txt 才受理', async ({ request }) => {
    // ① 恶意类型（.jsp / .sh）→ 415 + ECOS-KB-031
    for (const [name, type] of [['evil.jsp', 'application/x-jsp'], ['evil.sh', 'application/x-sh']]) {
      const res = await request.fetch('/api/v1/knowledge/ingest/documents', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        multipart: { file: { name, mimeType: type, buffer: Buffer.from('#!/bin/sh\nrm -rf /') } },
      });
      expect(res.status(), `${name} 必须被白名单拒绝（415），实得 ${res.status()}`).toBe(415);
      const body = await res.json().catch(() => ({}));
      expect(body?.errorCode, `${name} 拒绝必须带机读码 ECOS-KB-031`).toBe('ECOS-KB-031');
    }

    // ② 对照：合法 txt 必须受理（否则上面的 415 可能只是「所有上传都坏」的假绿）
    const ok = await request.fetch('/api/v1/knowledge/ingest/documents', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      multipart: { file: { name: 'verify-ok.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') } },
    });
    expect([200, 202], `合法 txt 应受理，实得 ${ok.status()}`).toContain(ok.status());
  });

  test('V-KB-083b 上传端点的 Content-Type 闸门：JSON 打 multipart 端点必须 415（D47）', async ({ request }) => {
    const res = await request.fetch('/api/v1/knowledge/ingest/documents', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({ file: 'evil.jsp' }),
    });
    expect(res.status(), `非 multipart 的请求体必须 415，实得 ${res.status()}`).toBe(415);
    const body = await res.json().catch(() => ({}));
    expect(body?.errorCode, 'D47 机读码必须是 ECOS-SYS-415').toBe('ECOS-SYS-415');
  });
});

test.describe('引擎与认知供给（辅助面）', () => {
  test('引擎 status/health 一致 + 认知供给三面可达', async ({ request }) => {
    const status = await getJson(request, '/api/v1/engine/knowledge/status');
    expectOk(status.status, status.json, 'engine/knowledge/status');
    expect(status.json!.data.status, '引擎状态应为 RUNNING').toBe('RUNNING');

    const index = await getJson(request, '/api/v1/knowledge/index-status');
    expectOk(index.status, index.json, 'knowledge/index-status');
    expect(typeof index.json!.data.pgVectorAvailable, 'pgVectorAvailable 必须为布尔').toBe('boolean');

    for (const path of ['/api/v1/knowledge/cognition/hypotheses',
      '/api/v1/knowledge/cognition/beliefs', '/api/v1/knowledge/cognition/minds']) {
      const r = await getJson(request, path);
      expectOk(r.status, r.json, path);
      expect(Array.isArray(r.json!.data), `${path} 应为数组`).toBe(true);
    }
  });
});
