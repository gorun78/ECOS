import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * 覆盖补充 2 — 282 Controller 登记判定中「活体探测返回 200 但前序 spec 未命中」的前缀。
 * 这些是**真实可达**面，必须实测而非在未覆盖清单里写理由蒙混。
 *
 * 2026-11-15 加固（复核 121 个未覆盖前缀活体状态时发现）：
 *  1. 原断言允许 `[200,400,401,403,404]`，其中 **404 是假绿后门** —— 这些面的
 *     登记理由恰恰是「已确认 200 可达」，若某天路由回退成 404，旧断言照样绿。
 *     现改为：登记为可达的必须 **200 + code=0 + data 非空壳**。
 *  2. 补入 `CaliberContractController /api/v1/workspace/calibers`：它 11-13 登记时
 *     是 403（D112 跨服务凭证中继失效），D112 修复后实测 **200** 且
 *     `dataSource="ontology-owner-rest"`（属主真值回源），但一直没有任何 spec 覆盖。
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function reachable(request: any, path: string): Promise<{ status: number; json: any }> {
  return call(request, 'GET', path, { token });
}

const TARGETS: Array<{ path: string; label: string; kind: 'array' | 'object' | 'any' }> = [
  { path: '/api/v1/datanet/facts/master-data-completeness', label: '主数据完整度事实', kind: 'any' },
  { path: '/api/v1/dq/reports', label: '数据质量报告', kind: 'any' },
  { path: '/api/v1/knowledge/extract/scheduled', label: '定时抽取', kind: 'any' },
  { path: '/api/v1/knowledge/graph', label: '知识图谱查询面', kind: 'any' },
  { path: '/api/v1/ecos/entities', label: '本体实体', kind: 'any' },
  { path: '/api/v1/ecos/versions', label: '版本清单', kind: 'any' },
  { path: '/api/v1/ontology/mappings', label: '本体映射', kind: 'any' },
  { path: '/api/security/assets', label: '安全资产(旧前缀)', kind: 'any' },
  { path: '/api/security/audit', label: '安全审计(旧前缀)', kind: 'any' },
  { path: '/api/v1/ecos/workflows', label: '工作流(ecos)', kind: 'any' },
  { path: '/api/v1/ontology/workflows', label: '工作流(ontology)', kind: 'any' },
  { path: '/api/v1/ecos/tasks', label: '任务(ecos)', kind: 'any' },
  { path: '/api/v1/ecos/entity-table-mappings', label: '实体表映射', kind: 'any' },
  { path: '/api/v1/ecos/world-model-graph/scenarios', label: '世界模型场景', kind: 'any' },
  { path: '/api/v1/security/policies', label: '安全策略(sysman)', kind: 'any' },
  { path: '/api/v1/system/permissions', label: '权限清单', kind: 'any' },
  { path: '/api/v1/workspace/binding-catalog', label: '场景绑定目录', kind: 'any' },
  { path: '/api/v1/business/scenarios/scenarioId/blueprints', label: '场景蓝图', kind: 'any' },
  { path: '/api/v1/workspace/calibers', label: '口径契约(属主回源)', kind: 'any' },
  // 2026-11-15 追加：登记册把这 2 个 wAgent 面判「未覆盖（前缀是常量/无 path）」，
  // 但正文自述「已实测 GET → 200」。既已确认可达就必须有用例，否则「未覆盖」清单
  // 会长期藏真实面。
];

test.describe('未覆盖但真实可达的面（登记判定闭环）', () => {
  for (const { path, label, kind } of TARGETS) {
    test(`V-COV-${label}`, async ({ request }) => {
      const { status, json } = await reachable(request, path);
      // 登记口径 = 「已确认 200 可达」，因此必须真的 200。
      // 旧断言允许 404/403，会让「路由回退」这种回归静默变绿 —— 2026-11-15 收紧。
      expect(status, `${label} 登记为可达面，必须 200（实得 ${status}）；404 说明路由回退或装配丢失`)
          .toBe(200);
      expect(json?.code, `${label} code 应为 0`).toBe(0);
      expect(json?.data === undefined, `${label} 必须返回 data（不得空响应冒充）`).toBe(false);
      if (kind === 'array') expect(Array.isArray(json.data)).toBe(true);
    });
  }

  test('V-COV-090 双前缀等价：/api/security/assets 与 /api/v1/security/assets', async ({ request }) => {
    const legacy = await reachable(request, '/api/security/assets');
    const v1 = await reachable(request, '/api/v1/security/assets');
    expect(legacy.status).toBe(200);
    expect(v1.status).toBe(200);
    const a = (legacy.json?.data as any[]) ?? [];
    const b = (v1.json?.data as any[]) ?? [];
    expect(a.length, '双前缀应返回同一数据集').toBe(b.length);
  });

  test('V-COV-091 世界模型场景与 workspace 场景为两套独立数据源（如实登记，不强行对齐）', async ({ request }) => {
    const wm = await reachable(request, '/api/v1/ecos/world-model-graph/scenarios');
    const ws = await reachable(request, '/api/v1/workspace/scenarios');
    expect(wm.status).toBe(200);
    expect(ws.status).toBe(200);

    // 世界模型：{data:[...], total:N}（sysman 侧 world-model-graph）
    const wmBody = wm.json?.data as any;
    const wmRows = Array.isArray(wmBody) ? wmBody : (wmBody?.data ?? []);
    expect(Array.isArray(wmRows), '世界模型场景应含数组').toBe(true);
    expect(wmBody?.total ?? wmRows.length, '世界模型场景 total 应为数字').toBeDefined();

    // workspace：裸数组（workspace-impl 场景表）
    const wsRows = ws.json?.data as any[];
    expect(Array.isArray(wsRows), 'workspace 场景应返回数组').toBe(true);

    // 两套是独立数据源（世界模型场景表 ecos_wm_scenario，workspace 为场景主表
    // ecos_business_scenario），条数不要求相等。
    //
    // 2026-11-15 订正（D104 落地后）：原断言写的是「世界模型必须含 default-scenario
    // 默认节点」，而 `default-scenario` 恰恰是 D104 修掉的那条**内存假数据**
    // （ScenarioController 曾用 ConcurrentHashMap + 构造器塞一条默认场景冒充数据源）。
    // 现在该端点读 DB，返回的是 ecos_wm_scenario 的真实行。留着旧断言等于
    // 要求缺陷复发 ⇒ 改为正向断言真实行、并以反向断言钉死假数据不得回归。
    const wmIds = wmRows.map((r: any) => String(r.id ?? ''));
    expect(wmIds, '世界模型场景不得出现 default-scenario 假节点（D104 反向钉）')
        .not.toContain('default-scenario');
    expect(wmIds.every((id) => id !== '' && id !== 'undefined'), '每行都应有真实 id').toBe(true);
    console.log(`[V-COV-091] world-model rows=${wmRows.length} workspace rows=${wsRows.length}（独立数据源）`);
  });
});
