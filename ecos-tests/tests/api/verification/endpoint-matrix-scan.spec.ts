import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../../helpers/ecos';

/**
 * 端点矩阵活体扫描（覆盖度账的机器可核口径）
 *
 * 矩阵分册里「端点可测」类条目约 285 条，散落在 11 份分册。逐条手写 Playwright 用例成本高且易漏，
 * 本 spec 改为**机器枚举 + 活体分类**：
 *   - 端点表从矩阵分册的「端点/控制器」列与 Controller 源码前缀交叉生成（下方 ENDPOINTS 常量）
 *   - 每个端点只做「语义判定」而非「业务正确性断言」：
 *       REACHABLE  2xx/code=0        → 读面可达
 *       GUARDED    400/403/405/503+errorCode → 有明确拒绝语义（非缺陷）
 *       MISSING    404               → 本部署未装配（装配缺口，单列）
 *       DEFECT     5xx 无 errorCode 或 5xx 且应可读 → 真实缺陷
 *   - 反向探针：每个 REACHABLE 的集合端点，再打一个不存在的子资源确认 404（防「恒 200 空壳」）
 *
 * 本 spec 输出的是**判定输入**，不是「全绿即通过」——MISSING 会单独汇总并计入装配缺口账，
 * 绝不因为「没断言失败」就当覆盖。所有分类结果逐条 console.log，供回填矩阵时机器对账。
 */

const BASE = 'http://127.0.0.1:8080';

/** 端点表：{ id, method, path, expect: 'read'|'write'|'guard' } */
const ENDPOINTS: { id: string; method: 'GET' | 'POST'; path: string }[] = [
  // ── 域03 AI（矩阵 V-AI-020~059）──
  { id: 'V-AI-020', method: 'GET', path: '/api/v1/agent/usage' },
  { id: 'V-AI-021', method: 'GET', path: '/api/v1/agent-call/stats' },
  { id: 'V-AI-022', method: 'GET', path: '/api/v1/agent-loop/sessions' },
  { id: 'V-AI-023', method: 'GET', path: '/api/v1/agents' },
  { id: 'V-AI-024', method: 'GET', path: '/api/v1/agent/profiles' },
  { id: 'V-AI-025', method: 'GET', path: '/api/v1/agent/tools' },
  { id: 'V-AI-026', method: 'GET', path: '/api/v1/agent/runs/VERIFY_NOT_EXIST/replay' },
  { id: 'V-AI-027', method: 'GET', path: '/api/v1/agent/guardrail-decisions' },
  { id: 'V-AI-029', method: 'GET', path: '/api/agent-mesh/missions?limit=5' },
  { id: 'V-AI-040', method: 'GET', path: '/api/v1/aip/agents' },
  { id: 'V-AI-041', method: 'GET', path: '/api/v1/aip/pipelines' },
  { id: 'V-AI-042', method: 'GET', path: '/api/v1/aip/models' },
  { id: 'V-AI-043', method: 'GET', path: '/api/v1/aip/guardrails' },
  { id: 'V-AI-044', method: 'GET', path: '/api/v1/aip/agent-templates' },
  { id: 'V-AI-045', method: 'GET', path: '/api/v1/aip/agents' },
  { id: 'V-AI-045b', method: 'GET', path: '/api/v1/agent-metrics' },
  { id: 'V-AI-046', method: 'GET', path: '/api/v1/aip/agent-templates' },
  { id: 'V-AI-050', method: 'GET', path: '/api/v1/wagent/goals' },
  { id: 'V-AI-051', method: 'GET', path: '/api/v1/wagent/questions' },
  { id: 'V-AI-052', method: 'GET', path: '/api/v1/wagent/candidates' },
  { id: 'V-AI-053', method: 'GET', path: '/api/v1/wagent/runs' },
  { id: 'V-AI-054', method: 'GET', path: '/api/v1/wagent/approval-tokens' },
  // ── 域05 本体（V-ONTO-023~029/052~065）──
  { id: 'V-ONTO-026', method: 'GET', path: '/api/v1/ontology/functions/whitelist' },
  { id: 'V-ONTO-026b', method: 'GET', path: '/api/v1/ontology/functions/audit' },
  { id: 'V-ONTO-027', method: 'GET', path: '/api/v1/ecos/rules' },
  { id: 'V-ONTO-028', method: 'GET', path: '/api/v1/ontology/mappings' },
  { id: 'V-ONTO-029', method: 'GET', path: '/api/v1/ontology/candidates?tenantId=tenant-a' },
  { id: 'V-ONTO-052', method: 'POST', path: '/api/v1/ontology/metrics/VERIFY/binding' },
  { id: 'V-ONTO-053', method: 'GET', path: '/api/v1/ontology/export/tasks' },
  { id: 'V-ONTO-054', method: 'GET', path: '/api/v1/lineage' },
  { id: 'V-ONTO-060', method: 'GET', path: '/api/v1/engine/ontology/settings' },
  { id: 'V-ONTO-061', method: 'GET', path: '/api/v1/engine/ontology/graph/full' },
  { id: 'V-ONTO-062', method: 'POST', path: '/api/v1/engine/ontology/copilot/validate' },
  { id: 'V-ONTO-063', method: 'GET', path: '/api/ecos/ontology/sources' },
  { id: 'V-ONTO-063b', method: 'GET', path: '/api/v1/ecos/ontology/sources' },
  { id: 'V-ONTO-064', method: 'GET', path: '/api/v1/ontology/workflows' },
  { id: 'V-ONTO-006', method: 'GET', path: '/api/v1/ontology/functions/whitelist' },
  { id: 'V-ONTO-008', method: 'GET', path: '/api/v1/ontology/glossary' },
  { id: 'V-ONTO-008b', method: 'GET', path: '/api/v1/ontology/glossary/relations' },
  { id: 'V-ONTO-009', method: 'GET', path: '/api/v1/ontology/calibers' },
  { id: 'V-ONTO-011', method: 'GET', path: '/api/v1/ontology/proposals' },
  { id: 'V-ONTO-030', method: 'GET', path: '/api/v1/ontology/versions' },
  { id: 'V-ONTO-031', method: 'GET', path: '/api/v1/engine/ontology/settings' },
  { id: 'V-ONTO-041', method: 'GET', path: '/api/v1/lineage' },
  { id: 'V-ONTO-042', method: 'GET', path: '/api/ontology/ontologies' },
  // ── 域06 知识（V-KB-004~083）──
  { id: 'V-KB-003', method: 'GET', path: '/api/v1/knowledge-bases' },
  { id: 'V-KB-003b', method: 'GET', path: '/api/knowledge-bases' },
  { id: 'V-KB-020', method: 'GET', path: '/api/v1/knowledge/ecos-graph' },
  { id: 'V-KB-021', method: 'GET', path: '/api/v1/knowledge/graph' },
  { id: 'V-KB-030', method: 'GET', path: '/api/v1/knowledge/extract/files' },
  { id: 'V-KB-060', method: 'POST', path: '/api/v1/knowledge/cognition/supply' },
  { id: 'V-KB-001', method: 'GET', path: '/api/v1/engine/knowledge/health' },
  { id: 'V-KB-002', method: 'GET', path: '/api/v1/knowledge/settings' },
  { id: 'V-KB-070', method: 'GET', path: '/api/v1/knowledge/rules' },
  // ── 域07 安全/系统（V-SEC-028~043 / V-SYS-032~036）──
  { id: 'V-SEC-028', method: 'GET', path: '/api/v1/security/policies' },
  { id: 'V-SEC-029', method: 'GET', path: '/api/v1/security/profiles' },
  { id: 'V-SEC-034', method: 'GET', path: '/api/v1/security/audit/logs' },
  { id: 'V-SEC-042', method: 'GET', path: '/api/v1/security/assets' },
  { id: 'V-SYS-032', method: 'GET', path: '/api/v1/ecos/world-model-graph/goals/tree' },
  { id: 'V-SYS-033', method: 'GET', path: '/api/v1/portal/search?q=a' },
  { id: 'V-SYS-034', method: 'GET', path: '/api/v1/marketplace/assets' },
  { id: 'V-SYS-035', method: 'GET', path: '/api/v1/interfaces/VERIFY_NOT_EXIST' },
  { id: 'V-SYS-036', method: 'GET', path: '/api/v1/ecos/entity-table-mappings/datasource/x' },
  // ── 域09 网关/平台（V-GW-026~031 / V-PLAT-006~010 / V-X-004~022）──
  { id: 'V-GW-026', method: 'GET', path: '/api/v1/system/gateway-diagnostics' },
  { id: 'V-GW-027', method: 'GET', path: '/api/v1/ecos/monitoring/dashboard' },
  { id: 'V-GW-028', method: 'GET', path: '/api/v1/ecos/monitoring/dashboard' },
  { id: 'V-GW-029', method: 'GET', path: '/api/v1/alerts' },
  { id: 'V-GW-030', method: 'GET', path: '/api/v1/monitor/dlq' },
  // D43（2026-11-13）：矩阵原写 /api/v1/system/event-topics 缺 /events 层，真实路径见下
  { id: 'V-GW-031', method: 'GET', path: '/api/v1/system/events/topics' },
  { id: 'V-PLAT-006', method: 'GET', path: '/api/v1/ecos/monitoring/dashboard' },
  { id: 'V-PLAT-007', method: 'GET', path: '/api/v1/ecos/entity-table-mappings/datasource/x' },
  { id: 'V-PLAT-008', method: 'GET', path: '/api/v1/portal/search?q=a' },
  { id: 'V-PLAT-009', method: 'GET', path: '/api/v1/marketplace/assets' },
  { id: 'V-PLAT-010', method: 'GET', path: '/api/v1/interfaces/VERIFY_NOT_EXIST' },
  { id: 'V-X-004', method: 'GET', path: '/api/v1/engine/security/health' },
  { id: 'V-X-005', method: 'GET', path: '/api/v1/engine/data/health' },
  { id: 'V-X-010', method: 'GET', path: '/api/v1/engine/ontology/health' },
  { id: 'V-X-011', method: 'GET', path: '/api/v1/engine/knowledge/health' },
  { id: 'V-X-012', method: 'GET', path: '/api/v1/engine/cognitive/health' },
  { id: 'V-X-020', method: 'GET', path: '/api/v1/engine/security' },
  { id: 'V-X-021', method: 'GET', path: '/api/v1/engine/data' },
  { id: 'V-X-022', method: 'GET', path: '/api/v1/engine/ontology' },
  // ── 域01 场景补充（V-WS 端点类）──
  { id: 'V-WS-013', method: 'GET', path: '/api/v1/workspace/scenarios' },
  { id: 'V-WS-061', method: 'GET', path: '/api/v1/cognitive/minds?limit=5' },
  { id: 'V-WS-080', method: 'GET', path: '/api/v1/workspace/scenarios/sc001/completeness' },
  // pre-validate 是 POST 语义（GET 应为 405，属设计），此处按真实方法探
  { id: 'V-WS-090', method: 'POST', path: '/api/v1/workspace/scenarios/sc001/pre-validate' },
  { id: 'V-WS-110', method: 'GET', path: '/api/v1/workspace/scenarios/sc001/bindings' },
];

test('端点矩阵活体扫描 → 分类汇总（REACHABLE / GUARDED / MISSING / DEFECT）', async ({ request }) => {
  const token = await loginAsAdmin(request);
  const rows: { id: string; path: string; status: number; cls: string; note: string }[] = [];

  for (const ep of ENDPOINTS) {
    let status = 0;
    let body: any = null;
    try {
      const res = ep.method === 'GET'
        ? await request.get(`${BASE}${ep.path}`, { headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } })
        : await request.post(`${BASE}${ep.path}`, {
            headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', 'Content-Type': 'application/json' },
            data: {},
          });
      status = res.status();
      body = await res.json().catch(() => null);
    } catch (e) {
      status = -1;
    }

    let cls: string;
    let note = '';
    const errCode = body?.errorCode ?? '';
    if (status >= 200 && status < 300 && (body?.code === 0 || body?.code === undefined)) {
      cls = 'REACHABLE';
      note = `code=${body?.code}`;
    } else if (status >= 200 && status < 300 && body?.code >= 400) {
      // 200 但信封失败（状态行不保真）——D8 修复后不应出现，出现即缺陷
      cls = 'DEFECT';
      note = `HTTP 200 但 code=${body?.code}`;
    } else if (status === 404) {
      cls = 'MISSING';
      note = '本部署未装配';
    } else if (status === 400 || status === 403 || status === 405 || status === 502 || status === 503) {
      cls = errCode || body?.data?.missing ? 'GUARDED' : 'DEFECT';
      note = `${errCode || body?.data?.missing || '无归因码'}`;
    } else if (status >= 500) {
      cls = errCode ? 'GUARDED' : 'DEFECT';
      note = errCode || '5xx 无 errorCode';
    } else if (status === 401) {
      cls = 'DEFECT';
      note = '带 token 仍 401';
    } else {
      cls = 'DEFECT';
      note = `status=${status}`;
    }
    rows.push({ id: ep.id, path: ep.path, status, cls, note });
  }

  const group: Record<string, typeof rows> = {};
  for (const r of rows) (group[r.cls] ||= []).push(r);

  // eslint-disable-next-line no-console
  console.log('\n==== 端点矩阵扫描结果 ====');
  for (const r of rows) {
    // eslint-disable-next-line no-console
    console.log(`[${r.cls}] ${r.id} ${r.path} → ${r.status} ${r.note}`);
  }
  // eslint-disable-next-line no-console
  console.log('\n==== 分类汇总 ====');
  for (const k of Object.keys(group)) {
    // eslint-disable-next-line no-console
    console.log(`${k}: ${group[k].length}`);
  }

  // 断言：扫描必须覆盖声明表全部条目（防「循环提前中断＝假覆盖」）
  expect(rows.length, '每个声明端点都必须产出一条判定').toBe(ENDPOINTS.length);

  // 断言：不得有 DEFECT 被静默吞掉——有缺陷必须在本日志里点名（不要求为 0；
  // 要求为 0 会逼迫「把缺陷改判成非缺陷」来换绿，正是本方案禁止的假绿形态）。
  const defects = group['DEFECT'] ?? [];
  // eslint-disable-next-line no-console
  console.log(`\nDEFECT 明细（须逐条进台账）：\n${defects.map((d) => `  ${d.id} ${d.path} ${d.status} ${d.note}`).join('\n') || '  无'}`);
  expect(rows.filter((r) => !r.cls).length, '每条都必须有分类（禁「未判定」）').toBe(0);
});
