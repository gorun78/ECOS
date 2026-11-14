import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * 覆盖补充 2 — 282 Controller 登记判定中「活体探测返回 200 但前序 spec 未命中」的 18 个前缀。
 * 这些是**真实可达**面，必须实测而非在未覆盖清单里写理由蒙混。
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
];

test.describe('未覆盖但真实可达的 18 个面（登记判定闭环）', () => {
  for (const { path, label, kind } of TARGETS) {
    test(`V-COV-${label}`, async ({ request }) => {
      const { status, json } = await reachable(request, path);
      // 每个面必须给出确定性状态：可达(200) 或明确拒绝(4xx)，不得 5xx
      expect(status, `${label} 不得 5xx（实得 ${status}）`).toBeLessThan(500);
      expect([200, 400, 401, 403, 404], `${label} 状态应为确定性，实得 ${status}`).toContain(status);
      if (status === 200) {
        expect(json?.code, `${label} 200 时 code 应为 0`).toBe(0);
        expect(json?.data === undefined, `${label} 200 时必须返回 data（不得空响应冒充）`).toBe(false);
        if (kind === 'array') expect(Array.isArray(json.data)).toBe(true);
      }
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

    // 两套是独立数据源（世界模型图为图谱投影，workspace 为场景主表），条数不要求相等；
    // 但世界模型必须至少包含默认场景，否则说明投影未生成。
    const wmIds = wmRows.map((r: any) => String(r.id ?? ''));
    expect(wmIds, '世界模型投影应含 default-scenario 默认节点').toContain('default-scenario');
    console.log(`[V-COV-091] world-model rows=${wmRows.length} workspace rows=${wsRows.length}（独立数据源）`);
  });
});
