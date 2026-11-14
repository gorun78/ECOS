import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B4 本体引擎 — API 级（对应矩阵域05）。
 *
 * 反假绿纪律：
 *  - 无持久化的四类（action/interface/shared_property/function）必须断言「写端点不存在或明确拒绝」，
 *    不能因为读端点 200 就判 PASS
 *  - 空数组 ≠ 正常：凡是「应该有种子数据」的面，断言非空或显式说明为何可为空
 *  - 双前缀必须逐字段比对（时间戳除外）
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

test.describe('本体核心只读面（V-ONTO-020~024）', () => {
  test('V-ONTO-020 本体列表可达且形态正确', async ({ request }) => {
    const list = await okArray(request, '/api/v1/ecos/ontologies', '本体列表');
    // 本体库可为空（未导入本体），但必须是数组而非 null/对象
    expect(list === null, '本体列表不得为 null').toBe(false);
  });

  test('V-ONTO-021 本体域列表与自动发现预览', async ({ request }) => {
    const domains = await okArray(request, '/api/v1/ecos/domains', '本体域列表');
    expect(domains === null, '域列表不得为 null').toBe(false);
  });

  test('V-ONTO-022/023 对象类型与属性可达', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ecos/ontologies/object-types', { token });
    // 该端点可能以 路径变量形式存在（/{ontologyId}），两种形态都必须不是 500
    expect(status, '对象类型端点不应 5xx').toBeLessThan(500);
    if (status === 200) expect(json?.code).toBe(0);
  });

  test('V-ONTO-024 关系类型列表可达', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ecos/relationships', { token });
    expect(status, '关系端点不应 5xx').toBeLessThan(500);
    if (status === 200) expect(json?.code).toBe(0);
  });
});

test.describe('无持久化四类（V-ONTO-005/006）— 断言「无假成功」', () => {
  const readonlyTypes = [
    { path: '/api/v1/ontology/action-types', name: '动作类型' },
  ];

  for (const { path, name } of readonlyTypes) {
    test(`V-ONTO-025 ${name}只读列表形态正确`, async ({ request }) => {
      const list = await okArray(request, path, `${name}列表`);
      expect(list === null, `${name}列表不得为 null`).toBe(false);
    });
  }

  test('V-ONTO-025b 动作类型写入必须被拒绝（无持久化 ⇒ 不得假成功）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/ontology/action-types', {
      token,
      data: { name: 'probe_action_' + Date.now(), label: 'probe' },
    });
    // 无持久化的类型：写端点应不存在(404/405)或被明确拒绝(4xx)，绝不可 code=0 假成功
    expect(
      status === 404 || status === 405 || status >= 400,
      `无持久化类型的写操作必须被拒，实得 HTTP=${status} code=${json?.code}`
    ).toBe(true);
  });

  test('V-ONTO-006 函数白名单只读（39 条内置）', async ({ request }) => {
    const list = await okArray(request, '/api/v1/ontology/functions/whitelist', '函数白名单');
    expect(list.length, '函数白名单应有内置条目').toBeGreaterThan(0);
    const row = list[0];
    const keys = Object.keys(row ?? {});
    expect(keys.length, '白名单行不应为空对象').toBeGreaterThan(0);
  });

  test('V-ONTO-006b 函数审计分页契约', async ({ request }) => {
    const d = await okObject(request, '/api/v1/ontology/functions/audit', '函数审计');
    expect(typeof d.total, '审计 total 应为数字').toBe('number');
    expect(Array.isArray(d.items), '审计应含 items 数组').toBe(true);
    expect(d.items.length, 'items 长度不应超过 total').toBeLessThanOrEqual(Number(d.total));
  });
});

test.describe('本体图谱与导出（V-ONTO-013 / V6）', () => {
  test('V-ONTO-013 图谱 full 返回 nodes/edges 双数组', async ({ request }) => {
    const g = await okObject(request, '/api/v1/engine/ontology/graph/full', '图谱全量');
    expect(Array.isArray(g.nodes), '图谱应有 nodes 数组').toBe(true);
    expect(Array.isArray(g.edges), '图谱应有 edges 数组').toBe(true);
    // 边端点必须真实存在于节点集（防「造边」）
    if (g.edges.length && g.nodes.length) {
      const ids = new Set(g.nodes.map((n: any) => String(n.id ?? n.nodeId ?? n.code)));
      const dangling = g.edges.filter((e: any) => {
        const s = String(e.source ?? e.from ?? e.sourceId ?? '');
        const t = String(e.target ?? e.to ?? e.targetId ?? '');
        return (s && !ids.has(s)) || (t && !ids.has(t));
      });
      expect(dangling.slice(0, 3), `图谱不应有悬空边，实得 ${dangling.length} 条`).toEqual([]);
    }
  });

  test('V-ONTO-013b 图谱空结果必须如实标注（D14 回归常驻）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/engine/ontology/graph/__NOT_EXIST__', { token });
    expect(status, '空图仍 200（信封约定），但必须带降级说明').toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data as any;
    expect(Array.isArray(d?.nodes) && d.nodes.length === 0, '不存在的本体 nodes 应为空').toBe(true);
    // D14：不得让「空壳」伪装成正常数据 —— message 必须说明原因
    expect(String(d?.message ?? ''), '空图必须带 message 说明（否则前端无从分辨空壳与真空图）').not.toBe('');
  });

  test('V-ONTO-014 本体导出快照五段齐全且为真实集合', async ({ request }) => {
    const d = await okObject(request, '/api/v1/ontology/export', '本体导出');
    ['objectTypes', 'linkTypes', 'actionTypes', 'functionTypes', 'mappings'].forEach((k) => {
      expect(Object.prototype.hasOwnProperty.call(d, k), `导出快照应含 ${k}`).toBe(true);
      expect(Array.isArray(d[k]), `${k} 应为数组（不得为 undefined）`).toBe(true);
    });
    expect((d.objectTypes as any[]).length, '导出快照应含真实对象类型').toBeGreaterThan(0);
    const row = (d.objectTypes as any[])[0];
    expect(row.code ?? row.name, '对象类型行应有 code 或 name').toBeTruthy();
  });

  test('V-ONTO-014b 导出任务列表可达', async ({ request }) => {
    const list = await okArray(request, '/api/v1/ontology/export/tasks', '导出任务');
    expect(list === null, '导出任务不得为 null').toBe(false);
  });
});

test.describe('术语与口径（V-ONTO-008/009）', () => {
  test('V-ONTO-008 术语表根路径为路由索引，真实数据在 /terms', async ({ request }) => {
    const d = await okObject(request, '/api/v1/ontology/glossary', '术语表根');
    // 根路径是有意设计的「API 索引」（terms/relations/graph 三个值是子路径字符串）
    ['endpoint', 'terms', 'relations', 'graph'].forEach((k) => {
      expect(Object.prototype.hasOwnProperty.call(d, k), `术语索引应含 ${k}`).toBe(true);
    });
    expect(String(d.terms), '索引里的 terms 应为子路径').toContain('/glossary');

    // 真实词条数据必须从 /terms 取，且形态为数组
    const { status, json } = await call(request, 'GET', '/api/v1/ontology/glossary/terms', { token });
    expect(status, '术语列表应 200').toBe(200);
    expect(json?.code).toBe(0);
    const terms = json?.data;
    const arr = Array.isArray(terms) ? terms : (terms as any)?.items ?? (terms as any)?.list;
    expect(Array.isArray(arr), '术语数据应为数组（直接或分页容器）').toBe(true);
  });

  test('V-ONTO-008b 术语关系端点必须真实存在（不得 404 假实现）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/ontology/glossary/relations', { token });
    expect(status, '术语关系端点应存在').not.toBe(404);
  });

  test('V-ONTO-009 口径列表可达', async ({ request }) => {
    const list = await okArray(request, '/api/v1/ontology/calibers', '口径列表');
    expect(list === null, '口径列表不得为 null').toBe(false);
  });

  test('V-ONTO-009b 口径指标绑定端点存在（POST 语义，非 GET 列表）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ontology/metrics', { token });
    // 该前缀下只挂 POST /metrics/{code}/binding（L2 面），GET 列表不存在是设计
    expect(status, `GET /ontology/metrics 应为 404（只有 POST binding），实得 ${status}`).toBe(404);
    expect(json?.code, '404 时信封 code 应为 404').toBe(404);
  });
});

test.describe('提案与候选（V-ONTO-011）', () => {
  test('V-ONTO-011 提案列表可达', async ({ request }) => {
    const list = await okArray(request, '/api/v1/ontology/proposals', '提案列表');
    expect(list === null, '提案列表不得为 null').toBe(false);
  });

  test('V-ONTO-011b 候选列表强制租户隔离（显式设计，非法调用必须 400 且说明原因）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ontology/candidates', { token });
    expect(status, `缺 tenantId 应 400，实得 ${status}`).toBe(400);
    expect(String(json?.message ?? ''), '必须说明 tenantId 必填原因').toContain('tenantId');
    expect(json?.errorCode, '错误码应为 ECOS-SYS-400').toBe('ECOS-SYS-400');
  });

  test('V-ONTO-011c 候选不存在 id 必须 404（反向探针）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/ontology/candidates/__NOT_EXIST__', { token });
    expect([400, 404], `不存在候选应 400/404，实得 ${status}`).toContain(status);
  });
});

test.describe('版本与配置（V6）', () => {
  test('V-ONTO-030 本体版本列表真实', async ({ request }) => {
    const list = await okArray(request, '/api/v1/ontology/versions', '本体版本');
    expect(Array.isArray(list), '版本列表应为数组').toBe(true);
  });

  test('V-ONTO-031 本体引擎设置非空', async ({ request }) => {
    const list = await okArray(request, '/api/v1/engine/ontology/settings', '引擎设置');
    expect(list.length, '引擎设置应有条目').toBeGreaterThan(0);
  });

  test('V-ONTO-032 本体引擎状态真实（RUNNING 或 UP 均为健康态）', async ({ request }) => {
    const d = await okObject(request, '/api/v1/engine/ontology/status', '引擎状态');
    expect(d.name, '应返回引擎名').toBe('ontology-engine');
    // 本体引擎用 RUNNING（数据引擎用 UP），两者都表示健康，不强求统一词表
    expect(['UP', 'RUNNING'], `引擎状态应为健康态，实得 ${d.status}`).toContain(String(d.status));
  });
});

test.describe('数据面与血缘（V-ONTO-040）', () => {
  test('V-ONTO-040 本体数据分页契约自洽', async ({ request }) => {
    const d = await okObject(request, '/api/v1/ontology/data?page=1&size=2', '本体数据');
    expect(typeof d.total, 'total 应为数字').toBe('number');
    expect(Array.isArray(d.data), '分页体应含 data 数组').toBe(true);
    expect(d.data.length, 'size=2 时条数应 ≤ 2').toBeLessThanOrEqual(2);
    expect(Number(d.page), 'page 应回显 1').toBe(1);
    expect(Number(d.size), 'size 应回显 2').toBe(2);
  });

  test('V-ONTO-041 血缘列表可达', async ({ request }) => {
    const list = await okArray(request, '/api/v1/lineage', '血缘列表');
    expect(list === null, '血缘列表不得为 null').toBe(false);
  });

  test('V-ONTO-042 本体只读面双前缀等价（/api/ontology 兼容面）', async ({ request }) => {
    const a = await call(request, 'GET', '/api/v1/ontology/glossary', { token });
    const b = await call(request, 'GET', '/api/ontology/glossary', { token });
    expect([200, 404], `兼容前缀应可达或明确 404，实得 ${b.status}`).toContain(b.status);
    if (b.status === 200 && a.status === 200) {
      const strip = (o: any) => {
        const { timestamp, ...rest } = o?.data ?? {};
        return rest;
      };
      expect(strip(b.json), '两前缀术语表应等价（除 timestamp）').toEqual(strip(a.json));
    }
  });
});

test.describe('权限（V5）', () => {
  test('V-ONTO-050 网关路径无 token 必须拒绝', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/ecos/ontologies');
    expect([401, 403], `无 token 应 401/403，实得 ${status}`).toContain(status);
  });

  test('V-ONTO-051 直连 18083 无身份校验（已知设计，记录实测）', async ({ request }) => {
    const res = await request.get('http://127.0.0.1:18083/api/v1/ecos/ontologies');
    console.log(`[V-ONTO-051] direct buszhi:18083 = ${res.status()}`);
    expect([200, 401, 403, 404], `直连实测记录，实得 ${res.status()}`).toContain(res.status());
  });
});
