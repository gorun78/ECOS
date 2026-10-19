// @vitest-environment node
/**
 * api/ 域基址单源回归门（#143/B5-16，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把 `src/api/` 七个域客户端里代码行的 `"/api…"` 字面量整体改由
 * `API_BASE` 派生。派生本身只有一种正确性可证：**取值逐字不变**。故本门的核心
 * 断言不是「长得对」，而是把每个派生点按 `API_BASE` 的实际值还原成字符串，与改前
 * 快照（83 条，2026-10-19 自 HEAD 实跑读出，非手抄）做多重集比对——多一条、少一条、
 * 差一个斜杠都判红。双前缀（`/api/api`）与漏段（`${API_BASE}` 后不接 `/`）另立判据。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../services/httpClient';

const API_ROOT = fileURLToPath(new URL('..', import.meta.url));

const FILES = [
  'agentClient.ts',
  'dataClient.ts',
  'iamClient.ts',
  'knowledgeClient.ts',
  'monitorClient.ts',
  'ontologyClient.ts',
  'securityClient.ts',
] as const;

/** 私设基址判据：引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** 派生形态：`` `${API_BASE}<path>` ``，捕获组即拼出来的路径段。 */
const DERIVED_RE = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.split('\n').filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(API_ROOT, rel), 'utf8');
}

/** 把派生点还原成运行期字符串面值（`${…}` 插值按字面保留，改前后同一写法）。 */
function derivedValues(src: string): string[] {
  const out: string[] = [];
  for (const line of codeLines(src)) {
    for (const m of line.matchAll(DERIVED_RE)) out.push(`${API_BASE}${m[1]}`);
  }
  return out;
}

/**
 * 改前快照（HEAD 实跑读出，排序后逐项比对）。
 * 来源＝同一批改动的逆投影：把七文件里代码行的 `"/api…"` 字面量原样收录，
 * 其中 `${…}` 为模板插值的字面文本，两侧写法一致才可对齐。
 */
/**
 * 快照以**带引号形态**收录（与源码里的字面量同形，便于人工逐条核对），
 * 下面统一剥引号得到运行期面值。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  '"/api/dq/goal-tracking${qs}"',
  '"/api/v1/abac/policies"',
  '"/api/v1/abac/policies/${id}"',
  '"/api/v1/abac/policies/${id}"',
  '"/api/v1/abac/policies?${params.toString()}"',
  '"/api/v1/agent-mesh"',
  '"/api/v1/agent/call"',
  '"/api/v1/audit/logs?${params.toString()}"',
  '"/api/v1/audit/stats"',
  '"/api/v1/data-masking/apply"',
  '"/api/v1/datanet/assets"',
  '"/api/v1/datanet/catalog"',
  '"/api/v1/datanet/datasource"',
  '"/api/v1/datanet/datasource"',
  '"/api/v1/datanet/metadata"',
  '"/api/v1/datanet/metadata/resources/${active.datasourceId}"',
  '"/api/v1/dq"',
  '"/api/v1/ecos/biz/dashboard"',
  '"/api/v1/ecos/objects"',
  '"/api/v1/ecos/ontologies/ont001/entities"',
  '"/api/v1/ecos/workflows"',
  '"/api/v1/ecos/world-model-graph"',
  '"/api/v1/engine/data/health"',
  '"/api/v1/engine/knowledge/health"',
  '"/api/v1/engine/security/health"',
  '"/api/v1/knowledge"',
  '"/api/v1/knowledge/ecos-graph"',
  '"/api/v1/monitor"',
  '"/api/v1/ontology/entities/${entityType}/instances"',
  '"/api/v1/pareto"',
  '"/api/v1/pipeline/definitions"',
  '"/api/v1/pipeline/executions/${executionId}"',
  '"/api/v1/portal/search?q=${encodeURIComponent(q)}&type=${encodeURIComponent(type)}"',
  '"/api/v1/security-profiles/user/${userId}"',
  '"/api/v1/security-profiles/user/${userId}"',
  '"/api/v1/security/audit/verify-chain"',
  '"/api/v1/security/cls/policies"',
  '"/api/v1/security/cls/policies/${id}"',
  '"/api/v1/security/cls/policies/${id}"',
  '"/api/v1/security/cls/policies?${sp.toString()}"',
  '"/api/v1/security/policy/evaluate"',
  '"/api/v1/security/rls/policies"',
  '"/api/v1/security/rls/policies/${id}"',
  '"/api/v1/security/rls/policies/${id}"',
  '"/api/v1/security/rls/policies?${sp.toString()}"',
  '"/api/v1/system/organizations"',
  '"/api/v1/system/organizations/${id}"',
  '"/api/v1/system/organizations/${id}"',
  '"/api/v1/system/organizations/all"',
  '"/api/v1/system/organizations/tree"',
  '"/api/v1/system/permissions"',
  '"/api/v1/system/permissions"',
  '"/api/v1/system/permissions/${id}"',
  '"/api/v1/system/permissions/${id}"',
  '"/api/v1/system/roles"',
  '"/api/v1/system/roles"',
  '"/api/v1/system/roles/${id}"',
  '"/api/v1/system/roles/${id}"',
  '"/api/v1/system/roles/${roleId}/permissions"',
  '"/api/v1/system/roles/${roleId}/permissions"',
  '"/api/v1/system/tenants"',
  '"/api/v1/system/tenants/${tenantId}"',
  '"/api/v1/system/tenants/${tenantId}"',
  '"/api/v1/system/tenants/${tenantId}/quota"',
  '"/api/v1/system/tenants/${tenantId}/quota"',
  '"/api/v1/system/tenants?${params.toString()}"',
  '"/api/v1/system/users"',
  '"/api/v1/system/users/${id}"',
  '"/api/v1/system/users/${id}"',
  '"/api/v1/system/users/${id}/force-logout"',
  '"/api/v1/system/users/${id}/password"',
  '"/api/v1/system/users/${id}/reset-password"',
  '"/api/v1/system/users/${id}/status"',
  '"/api/v1/system/users/${userId}/organizations"',
  '"/api/v1/system/users/${userId}/roles"',
  '"/api/v1/system/users/${userId}/roles"',
  '"/api/v1/system/users/batch"',
  '"/api/v1/system/users?page=${page}&pageSize=${pageSize}${q}"',
  '"/api/v1/twins/${encodeURIComponent(deviceId)}/command"',
  '"/api/v1/twins/${encodeURIComponent(deviceId)}/status"',
  '"/api/v1/twins/${encodeURIComponent(deviceId)}/telemetry?limit=${limit}"',
  '"/api/v1/twins/devices"',
  '"/api/v1/twins/health"',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

describe('api 域基址单源 — 私设基址清零 (#143/B5-16)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('七文件代码行零私设基址；注释里的端点说明不计（与判据同口径）', () => {
    for (const rel of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('七文件一律从 httpClient 导入 API_BASE（第二处来源即红）', () => {
    for (const rel of FILES) {
      const src = read(rel);
      const imports = codeLines(src).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0]).toContain('from "../services/httpClient"');
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），全目录零 /api/api 双前缀', () => {
    for (const rel of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（83 条，HEAD 实跑读出）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(83);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
  });

  it('快照自身可校验：83 条且全部以 /api/ 开头（防快照被清空成恒真）', () => {
    expect(PRE_BATCH_URL_LITERALS.length).toBe(83);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
  });

  it('判据能变红：违例源命中、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const BASE = '/api/v1/system/users';",
      'return apiFetchData(`/api/v1/audit/stats`);',
      'return apiFetchData("/api/v1/system/roles");',
    ].join('\n');
    const CLEAN = [
      'import { API_BASE, apiFetchData } from "../services/httpClient";',
      'const BASE = `${API_BASE}/v1/system/users`;',
      'return apiFetchData(`${BASE}`);',
      '// 历史 "/api" 字面量只允许留在注释行（判据与门禁同口径）',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual(['/api/v1/system/users']);
  });
});
