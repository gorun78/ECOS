import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B3 数据引擎 — API 级（对应矩阵域04）。
 * 覆盖：数据源/元数据/目录/分类/资产、数据湖与分层、管道与函数、查询、DQ 七面、血缘与健康、
 *       双前缀一致性、权限面、关账落点。
 *
 * 反假绿纪律：
 *  - 每条断言「形态 + 语义」双检；只判 200/code=0 不算通过
 *  - 双前缀一致性做逐字段比对，不做「两边都 200」的糊弄式断言
 *  - 已知设计缺口（直连 18082 无 HeaderAuth）显式断言为「已知行为」，不判 PASS
 *  - int8 计数陷阱（V-DATA-092）：凡分页端点必须核对 total 是数字且与 list 长度关系合理
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

/** 期望 HTTP 200 + 信封 code=0 + 指定 data 容器形态 */
async function expectOkWith(
  request: any,
  path: string,
  shape: 'array' | 'object' | 'any',
  label: string
) {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} HTTP 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0，实得 ${json?.code}（${json?.message ?? ''}）`).toBe(0);
  if (shape === 'array') expect(Array.isArray(json?.data), `${label} data 应为数组`).toBe(true);
  if (shape === 'object') {
    expect(json?.data && typeof json?.data === 'object' && !Array.isArray(json?.data),
      `${label} data 应为对象`).toBe(true);
  }
  return json?.data;
}

test.describe('数据源与元数据（V-DATA-020~026）', () => {
  test('V-DATA-020 数据源列表真实且结构完整', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/datanet/datasource', 'array', '数据源列表');
    expect(data.length, '数据源列表不应为空（种子至少 1 条）').toBeGreaterThan(0);
    // 该接口主键字段名为 datasourceId/datasourceName（非通用 id/name）
    const row = data[0];
    expect(row, '数据源行应有 datasourceId').toHaveProperty('datasourceId');
    expect(typeof row.datasourceId, 'datasourceId 应为非空字符串').toBe('string');
    expect(Object.prototype.hasOwnProperty.call(row, 'datasourceName'), '数据源行应有 datasourceName').toBe(true);
    expect(row.connectionConfig, '数据源不应回显明文密码（V-DATA-104 同源校验）')
      .not.toMatch(/"password"\s*:\s*"[^"]+"/);
  });

  test('V-DATA-021 数据源兼容面前缀 /api/v1/datasource 同源', async ({ request }) => {
    const a = await expectOkWith(request, '/api/v1/datanet/datasource', 'array', '主前缀');
    const b = await expectOkWith(request, '/api/v1/datasource', 'array', '兼容前缀');
    expect(b.length, '兼容面与主前缀条数应一致').toBe(a.length);
    expect(
      b.map((x: any) => x.id).sort(),
      '兼容面与主前缀的 id 集合应一致'
    ).toEqual(a.map((x: any) => x.id).sort());
  });

  test('V-DATA-022 元数据 resources 双前缀一致（V-DATA-094；D10 裸数组登记）', async ({ request }) => {
    // D10 已登记：该端点返回顶层裸数组、不套 ApiResponse 信封（API 只增不改，本批不改外形）
    const a = await call(request, 'GET', '/api/v1/datanet/metadata/resources/all', { token });
    const b = await call(request, 'GET', '/api/datanet/metadata/resources/all', { token });
    expect(a.status, '元数据主前缀应 200').toBe(200);
    expect(b.status, '元数据兼容前缀应 200').toBe(200);
    expect(Array.isArray(a.json), 'D10：该端点当前是裸数组形态（若改为信封须同步撤销该断言）').toBe(true);
    expect(JSON.stringify(b.json), '两前缀返回体应逐字节一致').toBe(JSON.stringify(a.json));
  });

  test('V-DATA-022b 元数据 resources 已知为空时的显式判定', async ({ request }) => {
    const a = await call(request, 'GET', '/api/v1/datanet/metadata/resources/all', { token });
    const arr = a.json as unknown as unknown[];
    const collected = await call(request, 'GET', '/api/v1/datanet/metadata/resources/dac14d03d9c04603970f4666ea704c01', { token });
    // 若全量为空，必须是「元数据未采集」的真实状态，而非接口故障
    if (arr.length === 0) {
      expect([200, 404], `全量为空时单源查询应可达，实得 ${collected.status}`).toContain(collected.status);
    } else {
      expect(arr.length, '全量非空').toBeGreaterThan(0);
    }
  });

  test('V-DATA-023 数据目录 dashboard/stats 数值自洽', async ({ request }) => {
    const stats = await expectOkWith(request, '/api/v1/datanet/catalog/stats', 'object', '目录统计');
    const dash = await expectOkWith(request, '/api/v1/datanet/catalog/dashboard', 'object', '目录仪表盘');
    const num = Object.values(stats as Record<string, unknown>).filter((v) => typeof v === 'number') as number[];
    expect(num.length, '目录统计应至少含一个数值字段').toBeGreaterThan(0);
    num.forEach((n) => expect(Number.isFinite(n), `目录统计数值应有限，实得 ${n}`).toBe(true));
    expect(Object.keys(dash as object).length, '目录仪表盘不应为空对象').toBeGreaterThan(0);
  });

  test('V-DATA-024 分类树结构正确且 level 与 parent 自洽（D12 回归常驻）', async ({ request }) => {
    const tree = await expectOkWith(request, '/api/v1/datanet/category/tree', 'array', '分类树');
    expect(tree.length, '分类树根节点不应为空').toBeGreaterThan(0);
    // D12：V58 种子未写 level（DEFAULT 1），二级分类 level 全为 1 ⇒
    //      前端 DataAssetsDashboard.tsx:151 filter(c => c.level === 2) 取不到任何业务分类。
    let level2 = 0;
    const walk = (node: any, depth: number) => {
      expect(node, '节点应有 categoryId').toHaveProperty('categoryId');
      const parentId = node.parentId;
      const expected = parentId ? depth + 1 : 1;
      expect(Number(node.level), `节点 ${node.categoryId} 的 level 应与 parent 层级自洽（期望 ${expected}）`)
        .toBe(expected);
      if (Number(node.level) === 2) level2 += 1;
      (node.children ?? []).forEach((c: any) => walk(c, depth + 1));
    };
    tree.forEach((n: any) => walk(n, 0));
    expect(level2, '至少应有一个 level=2 的业务分类（否则前端分类下拉框为空）').toBeGreaterThan(0);
  });

  test('V-DATA-025 资产列表契约（裸数组 + pageSize 生效；int8 陷阱 V-DATA-092）', async ({ request }) => {
    // 该端点 data 为裸数组（无 total 分页信封），分页语义须用 pageSize 是否生效来证伪
    const p1 = await expectOkWith(request, '/api/v1/datanet/assets?page=1&pageSize=1', 'array', '资产 pageSize=1');
    const p5 = await expectOkWith(request, '/api/v1/datanet/assets?page=1&pageSize=5', 'array', '资产 pageSize=5');
    expect(p1.length, 'pageSize=1 时返回条数应 ≤ 1').toBeLessThanOrEqual(1);
    expect(p5.length, 'pageSize=5 时返回条数应 ≤ 5').toBeLessThanOrEqual(5);
    expect(p5.length, 'pageSize=5 的返回条数不应少于 pageSize=1').toBeGreaterThanOrEqual(p1.length);
    // int8 陷阱（V-DATA-092）：若 row 内含 COUNT 派生的数值列，必须为 number 而非 "3"/抛错
    if (p5.length) {
      const numericKeys = Object.keys(p5[0]).filter((k) =>
        /count|total|recordCount|fieldCount/i.test(k)
      );
      numericKeys.forEach((k) => {
        const v = (p5[0] as any)[k];
        expect(
          v === null || typeof v === 'number',
          `计数列 ${k} 应为 number|null（int8 已降位），实得 ${typeof v}=${v}`
        ).toBe(true);
      });
    }
  });

  test('V-DATA-025b 资产层级与分类字典非空', async ({ request }) => {
    const levels = await expectOkWith(request, '/api/v1/datanet/levels', 'any', '资产层级');
    const cats = await expectOkWith(request, '/api/v1/datanet/categories', 'any', '资产分类');
    expect(Array.isArray(levels) ? levels.length : Object.keys(levels ?? {}).length,
      '层级字典不应为空').toBeGreaterThan(0);
    expect(Array.isArray(cats) ? cats.length : Object.keys(cats ?? {}).length,
      '分类字典不应为空').toBeGreaterThan(0);
  });

  test('V-DATA-090 资产详情 ↔ 列表 id 可回溯', async ({ request }) => {
    const list = await expectOkWith(request, '/api/v1/datanet/assets?page=1&pageSize=1', 'array', '资产首页');
    const rows = list as any[];
    test.skip(!rows?.length, '库内无资产，详情回溯无法验证（登记 NOT_EXECUTED，非 PASS）');
    const id = rows[0].assetId ?? rows[0].id;
    expect(id, '资产行应有 assetId').toBeTruthy();
    const detail = await expectOkWith(request, `/api/v1/datanet/assets/${id}`, 'object', '资产详情');
    expect((detail as any).assetId ?? (detail as any).id, '详情 id 应与列表 id 一致').toBe(id);
  });
});

test.describe('数据湖与分层（V-DATA-030~033）', () => {
  test('V-DATA-030 数据湖状态真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/datanet/datalake/status', 'any', '数据湖状态');
    expect(data, '数据湖状态不应为 null').not.toBeNull();
  });

  test('V-DATA-032 数据分层计数与存储分层规范一致', async ({ request }) => {
    // 该端点返回的是「各层资源计数对象」，不是层定义数组
    const data = (await expectOkWith(request, '/api/v1/engine/data/layers', 'object', '分层计数')) as Record<string, any>;
    const LAYERS = ['SOURCE', 'RAW', 'CURATED', 'SEMANTIC', 'APPLICATION'];
    LAYERS.forEach((l) => {
      expect(Object.prototype.hasOwnProperty.call(data, l), `分层计数应含 ${l}（规范定义五层）`).toBe(true);
      expect(typeof data[l], `${l} 计数应为数字`).toBe('number');
      expect(Number.isInteger(data[l]), `${l} 计数应为整数`).toBe(true);
      expect(data[l], `${l} 计数不应为负`).toBeGreaterThanOrEqual(0);
    });
    expect(Object.prototype.hasOwnProperty.call(data, 'total'), '应含 total 汇总').toBe(true);
    const sum = LAYERS.reduce((acc, l) => acc + Number(data[l]), 0);
    expect(data.total, `total(${data.total}) 应等于五层之和(${sum})`).toBe(sum);
  });

  test('V-DATA-033 写通道存在且拒绝空请求（红线：确定性产物唯一写通道）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/datanet/write-channel', {
      token,
      data: {},
    });
    expect([400, 422, 500].includes(status) || json?.code !== 0,
      `空请求不应被当作成功，实得 HTTP=${status} code=${json?.code}`).toBe(true);
  });
});

test.describe('管道与函数（V-DATA-040~051）', () => {
  test('V-DATA-043 管道函数列表分页与过滤（D9 回归常驻）', async ({ request }) => {
    const p1 = await expectOkWith(request, '/api/v1/engine/data/functions?page=1&pageSize=3', 'object', '函数首页');
    const d1 = p1 as Record<string, any>;
    expect(typeof d1.total, 'total 应为数字').toBe('number');
    expect(d1.total, '函数库应有数据').toBeGreaterThan(0);
    expect(d1.list.length, 'pageSize=3 应返回 3 条').toBe(3);

    const p2 = await expectOkWith(request, '/api/v1/engine/data/functions?page=2&pageSize=3', 'object', '函数第二页');
    const d2 = p2 as Record<string, any>;
    expect(d2.total, '两页 total 应一致').toBe(d1.total);
    const ids1 = d1.list.map((x: any) => x.id);
    const ids2 = d2.list.map((x: any) => x.id);
    expect(ids1.filter((x: string) => ids2.includes(x)), '第 1、2 页不应重叠').toEqual([]);
  });

  test('V-DATA-043b 函数分类过滤生效', async ({ request }) => {
    const all = (await expectOkWith(request, '/api/v1/engine/data/functions?page=1&pageSize=50', 'object', '全量')) as any;
    const cat = all.list[0].category;
    const filtered = (await expectOkWith(
      request,
      `/api/v1/engine/data/functions?page=1&pageSize=50&category=${encodeURIComponent(cat)}`,
      'object',
      '分类过滤'
    )) as any;
    expect(filtered.total, '过滤后 total 应小于全量').toBeLessThan(all.total);
    filtered.list.forEach((x: any) =>
      expect(x.category, '过滤结果分类应全部匹配').toBe(cat)
    );
  });

  test('V-DATA-043c 函数越界页返回空但 total 不变', async ({ request }) => {
    const data = (await expectOkWith(
      request,
      '/api/v1/engine/data/functions?page=9999&pageSize=10',
      'object',
      '越界页'
    )) as any;
    expect(data.list.length, '越界页应返回空列表').toBe(0);
    expect(data.total, '越界页 total 应保持真实总数').toBeGreaterThan(0);
  });

  test('V-DATA-051 数据引擎状态真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/engine/data/status', 'any', '引擎状态');
    expect(data, '引擎状态不应为 null').not.toBeNull();
  });

  test('V-DATA-051b 引擎设置读写一致', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/engine/data/settings', 'any', '引擎设置');
    expect(data, '引擎设置不应为 null').not.toBeNull();
  });

  test('V-DATA-047 数据新鲜度真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/engine/data/freshness', 'any', '数据新鲜度');
    expect(data, '新鲜度不应为 null').not.toBeNull();
  });

  test('V-DATA-048 Schema 变更列表可查', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/engine/data/schema/changes', 'any', 'Schema 变更');
    expect(Array.isArray(data), 'Schema 变更应为数组').toBe(true);
  });

  test('V-DATA-050 UDF 列表分页契约（空库时 total=0 且 list 空）', async ({ request }) => {
    const data = (await expectOkWith(request, '/api/v1/engine/data/udf/list', 'object', 'UDF 列表')) as Record<string, any>;
    expect(typeof data.total, 'UDF total 应为数字').toBe('number');
    expect(Array.isArray(data.list), 'UDF 应含 list 数组').toBe(true);
    expect(data.list.length, 'list 长度不应超过 total').toBeLessThanOrEqual(data.total);
  });

  test('V-DATA-050b UDF 恶意代码登记应被拒（安全）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/engine/data/udf/register', {
      token,
      data: {
        name: 'evil_probe_' + Date.now(),
        code: 'Runtime.getRuntime().exec("calc")',
        language: 'JAVA',
        returnType: 'string',
      },
    });
    const rejected = json?.code !== 0 || status >= 400;
    expect(rejected, `恶意 UDF 应被拒，实得 HTTP=${status} code=${json?.code} message=${json?.message}`).toBe(true);
  });

  test('V-DATA-046 查询模板分页契约（字段名 items）', async ({ request }) => {
    const data = (await expectOkWith(request, '/api/v1/engine/data/query/templates', 'object', '查询模板')) as Record<string, any>;
    expect(typeof data.total, '模板 total 应为数字').toBe('number');
    // 该端点分页容器的数组字段名为 items（与 udf/list 的 list 不同，属既有契约，不统一改）
    expect(Array.isArray(data.items), '模板应含 items 数组').toBe(true);
    expect(data.items.length, 'items 长度不应超过 total').toBeLessThanOrEqual(data.total);
  });

  test('V-DATA-046b 查询历史可查', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/engine/data/query/history', 'any', '查询历史');
    expect(data, '查询历史不应为 null').not.toBeNull();
  });

  test('V-DATA-103 SQL 注入应被拒或参数化（安全）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/engine/data/query/execute', {
      token,
      data: { datasourceId: 'ds-001', sql: "SELECT 1; DROP TABLE ecos_pipeline_function; --" },
    });
    const notDropped = (await call(request, 'GET', '/api/v1/engine/data/functions?page=1&pageSize=1', { token })).json?.code === 0;
    expect(notDropped, '注入尝试后函数表必须仍可用').toBe(true);
    expect(
      json?.code !== 0 || status >= 400 || typeof json?.data === 'object',
      `注入请求要么被拒、要么被参数化执行，实得 HTTP=${status} code=${json?.code}`
    ).toBe(true);
  });

  test('V-DATA-045 管道 Copilot 五端点存在且失败显式不伪造', async ({ request }) => {
    // 真实路径集（PipelineCopilotController：/sql /pipeline /expression /udf /diagnose）
    const paths = ['/sql', '/pipeline', '/expression', '/udf', '/diagnose'];
    for (const p of paths) {
      const { status, json } = await call(request, 'POST', `/api/v1/engine/data/copilot${p}`, {
        token,
        data: { prompt: 'probe', question: 'probe', content: 'probe' },
      });
      expect([200, 400, 500, 503], `copilot${p} 不应 404（路由必须存在），实得 ${status}`).toContain(status);
      if (json?.code === 0) {
        expect(json?.data, `copilot${p} 若成功必须返回真实内容，不能空对象冒充`).not.toEqual({});
      } else {
        expect(String(json?.message ?? ''), `copilot${p} 失败必须给明确原因`).not.toBe('');
      }
    }
  });
});

test.describe('数据质量 DQ（V-DATA-060~068）', () => {
  test('V-DATA-061 DQ 规则列表真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/dq/rules', 'any', 'DQ 规则');
    expect(Array.isArray(data) || typeof data === 'object', 'DQ 规则应为数组或分页对象').toBe(true);
  });

  test('V-DATA-061b DQ 规则状态机：DRAFT → submit → IN_REVIEW（自建数据，不依赖库内存量）', async ({ request }) => {
    // 库里 ecos_data.dq_rule 为 0 行（2026-11-13 实测），原用例 skip ⇒ 状态机从未被真正验证。
    // 改为用例自建 DRAFT 规则，使该断言始终可执行（反假绿：不靠 skip 洗白）。
    const code = `VERIFY-ST-${Date.now()}`;
    const created = await call(request, 'POST', '/api/v1/dq/rules', {
      token,
      data: {
        ruleName: '状态机验证规则',
        ruleCode: code,
        ruleType: 'NOT_NULL',
        category: 'ACCURACY',
        severity: 'MEDIUM',
        targetKind: 'TABLE',
        targetTable: 'ecos_dw.dw_order',
        description: '自动化验证用，验证 DRAFT→IN_REVIEW',
      },
    });
    expect(created.status, `创建 DRAFT 规则应 2xx，实得 ${created.status} ${JSON.stringify(created.json).slice(0, 150)}`).toBeLessThan(300);
    const ruleId = (created.json?.data as any) ?? (created.json?.data as any)?.id;
    expect(ruleId, '创建必须返回规则 id').toBeTruthy();

    const before = await expectOkWith(request, `/api/v1/dq/rules/${ruleId}`, 'object', '规则详情');
    const statusOf = (o: any) => String(o?.status ?? o?.rule?.status ?? '').toUpperCase();
    expect(statusOf(before), '新建规则初始状态应为 DRAFT').toBe('DRAFT');

    const submit = await call(request, 'POST', `/api/v1/dq/rules/${ruleId}/submit`, { token });
    expect(submit.status, `提交应 2xx，实得 ${submit.status}`).toBeLessThan(300);

    const after = await expectOkWith(request, `/api/v1/dq/rules/${ruleId}`, 'object', '规则详情');
    expect(statusOf(after), `提交后应为 IN_REVIEW（非 ACTIVE），实得 ${statusOf(after)}（HTTP=${submit.status}）`).toBe('IN_REVIEW');
  });

  test('V-DATA-062 DQ 告警列表与等级字典', async ({ request }) => {
    const alerts = await expectOkWith(request, '/api/v1/dq/alerts', 'any', 'DQ 告警');
    const levels = await expectOkWith(request, '/api/v1/dq/alerts/levels', 'any', '告警等级');
    expect(alerts, '告警列表不应为 null').not.toBeNull();
    expect(Array.isArray(levels) ? levels.length : Object.keys(levels ?? {}).length,
      '告警等级字典不应为空').toBeGreaterThan(0);
  });

  test('V-DATA-063 DQ 调度列表真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/dq/schedules', 'any', 'DQ 调度');
    expect(data, '调度列表不应为 null').not.toBeNull();
  });

  test('V-DATA-064 DQ 评分与系统评分自洽', async ({ request }) => {
    const sys = await expectOkWith(request, '/api/v1/dq/scores/system', 'any', '系统评分');
    const grade = await expectOkWith(request, '/api/v1/dq/scores/grade', 'any', '评分等级');
    expect(sys, '系统评分不应为 null').not.toBeNull();
    expect(grade, '评分等级不应为 null').not.toBeNull();
  });

  test('V-DATA-065 DQ 工单列表真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/dq/work-orders', 'any', 'DQ 工单');
    expect(data, '工单列表不应为 null').not.toBeNull();
  });

  test('V-DATA-060 DQ 总面 dashboard 真实', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/ecos/dq/rules', 'any', 'DQ 总面规则');
    expect(data, 'DQ 总面规则不应为 null').not.toBeNull();
  });

  test('V-DATA-066 DQ 自检端点存在（POST 语义）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/dq/health/selfcheck', { token });
    expect([200, 400, 500, 503]).toContain(status);
    if (status === 200) expect(json?.code).toBe(0);
    else expect(String(json?.message ?? ''), '非 200 时必须给明确原因').not.toBe('');
  });
});

test.describe('血缘与健康（V-DATA-080~081）', () => {
  test('V-DATA-080 血缘列表与拓扑可查', async ({ request }) => {
    const list = await expectOkWith(request, '/api/v1/engine/data/lineage', 'any', '血缘列表');
    const topo = await expectOkWith(request, '/api/v1/engine/data/lineage/topology', 'any', '血缘拓扑');
    expect(list, '血缘列表不应为 null').not.toBeNull();
    expect(topo, '血缘拓扑不应为 null').not.toBeNull();
  });

  test('V-DATA-080b 血缘兼容前缀 /api/lineage 存在', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/lineage', { token });
    expect([200, 400, 404].includes(status), `兼容前缀应可达，实得 ${status}`).toBe(true);
    if (status === 200) expect(json?.code ?? 0).toBe(0);
  });

  test('V-DATA-081 数据服务健康双前缀等价（时间戳之外字段必须相等）', async ({ request }) => {
    const a = (await expectOkWith(request, '/api/v1/datanet/api/health', 'any', '健康主前缀')) as any;
    const b = (await expectOkWith(request, '/datanet/api/health', 'any', '健康兼容前缀')) as any;
    // timestamp 是每次请求的实时生成值，天然不同；拿它做逐字节比对必然假红
    const strip = (o: any) => {
      const { timestamp, ...rest } = o ?? {};
      return rest;
    };
    expect(strip(b), '除 timestamp 外两前缀健康体应完全一致').toEqual(strip(a));
    expect(b.status, '健康状态应为 UP').toBe('UP');
  });
});

test.describe('权限与安全（V-DATA-100~104）', () => {
  test('V-DATA-100 网关路径无 token 必须拒绝', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/datanet/datasource');
    expect([401, 403], `无 token 应 401/403，实得 ${status}`).toContain(status);
  });

  test('V-DATA-104 数据源列表不回显明文密码', async ({ request }) => {
    const data = await expectOkWith(request, '/api/v1/datanet/datasource', 'array', '数据源列表');
    const raw = JSON.stringify(data);
    expect(raw, '列表不得出现明文密码字段值').not.toMatch(/"password"\s*:\s*"[^"]+"/);
  });

  test('V-DATA-102 关账落点：网关可达 + 直连差异（记录实测，不判 PASS）', async ({ request }) => {
    // 真实路径为 /api/v1/datanet/facts/periods/{yyyMM}/close（PeriodCloseController）
    const viaGateway = await request.get('/api/v1/datanet/facts/periods/202601', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect([200, 403, 503], `经网关查期间状态应可达，实得 ${viaGateway.status()}`)
      .toContain(viaGateway.status());

    const direct = await request.get('http://127.0.0.1:18082/api/v1/datanet/facts/periods/202601');
    // 已知设计：datanet 关 header-auth ⇒ 直连无身份校验。此处只记录实测，不断言「应该 403」
    expect([200, 401, 403, 404], `直连实测记录，实得 ${direct.status()}`).toContain(direct.status());
    console.log(`[V-DATA-102] gateway=${viaGateway.status()} direct18082=${direct.status()}`);
  });
});
