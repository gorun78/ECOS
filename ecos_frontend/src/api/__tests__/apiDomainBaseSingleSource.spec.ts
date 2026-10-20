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

/**
 * 后续批**净新增**到本域的 `` `${API_BASE}` `` 属主声明台账（只增需登记，不登记即红）。
 *
 * <p>#145/B6-9 起有 1 条：`/v1/engine/data/settings` 族根单源上收进 `api/dataClient.ts`。
 * 前面的 B6 批都不进这本账，原因各不相同且都必须记清，否则后批会误以为"属主上收必然 +1"：
 * B6-1/B6-5/B6-8 落在本域的 `KNOWLEDGE_BASE`/`PIPELINE_DEFINITIONS_BASE`/`DATANET_METADATA_BASE`
 * 三枚只是**同文件私有常量改名并导出**（同一声明位，面值原本就在 83 条快照里）⇒ 计数不变；
 * B6-2~B6-7 的属主全在 services/ 或 pages/ 域，不进本域扫描面。
 * 本条是唯一"该面值原本不在本域任何腿上"的上收（改前两枚消费腿分属两个页面），
 * 故快照必须保持 83 条**逐字是改前面值**，新增位单列，两类判据各自成立。</p>
 *
 * <p>#145/B6-15 起有第 2 条：`/v1/engine/data/pipeline/runs` 族根单源上收进同一 `dataClient.ts`。
 * 同 B6-9 形状（净新增声明位，非改名）：改前两枚轮询腿分属 `pages/data-workbench/api/pipeline.ts`
 * 与 `pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx`，本域从未持该面值 ⇒ 台账
 * 1→2 条、面值多重集 84→85 条。B6-14 的 `DQ_GOVERNANCE_BASE` 不进这本账（私有 `DQ_BASE` 改名并
 * export，同一声明位、面值原本就在 83 条里），与 B6-1/B6-5/B6-8 同因。</p>
 */
const OWNER_DECL_ADDITIONS: ReadonlyArray<{
  batch: string;
  file: string;
  name: string;
  value: string;
  why: string;
}> = [
  {
    batch: '#145/B6-9',
    file: 'dataClient.ts',
    name: 'DATA_ENGINE_SETTINGS_BASE',
    value: '/api/v1/engine/data/settings',
    why: '族两属主分属两个页面（EngineMonitor configPath + DataEngineConfigPanel 保存腿），改前本域无该面值 ⇒ 属主按 import 图落 api 层，声明位净新增',
  },
  {
    batch: '#145/B6-15',
    file: 'dataClient.ts',
    name: 'ENGINE_DATA_PIPELINE_RUNS_BASE',
    value: '/api/v1/engine/data/pipeline/runs',
    why: '族两属主分属域内 api 层与组件层（pipeline.ts 轮询腿 + PipelineExecutionMonitor 轮询腿），任选其一为属主都会让另一枚反向引页面 ⇒ 按 import 图落 api 层，声明位净新增',
  },
];

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

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照 + 后续批属主声明台账（83 + 2 条）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(85);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS, ...OWNER_DECL_ADDITIONS.map((o) => o.value)].sort());
  });

  it('属主声明台账（#145/B6-9／#145/B6-15）：本域新增的 ${API_BASE} 声明位逐条登记且面值可追到属主常量', () => {
    // 快照 83 条是 **#143/B5-16 改前**实跑读出的消费腿面值，代表「派生不该改变运行期面值」这条不变式。
    // 后续 B6 批把某个族的**属主常量上收进本域文件**时，声明位可能是**净新增**（不是搬运），
    // 混进快照会让「改前根本不存在这条腿」失真；单列台账才能同时保住两条判据：
    //   ① 快照 83 条逐字不动（谁把消费腿面值改了即红）；
    //   ② 新增声明位逐条登记（谁再加一枚未登记的 `${API_BASE}` 声明即红，防属主面悄悄膨胀）。
    expect(OWNER_DECL_ADDITIONS.length).toBe(2);
    for (const o of OWNER_DECL_ADDITIONS) {
      const src = read(o.file);
      expect(derivedValues(src), `${o.file} 找不到登记的属主声明面值`).toContain(o.value);
      // 声明形状用**字符串拼接**比对（不用 RegExp：反引号/`${` 的正则转义在本文件模板串里极易写出语法洞）
      const declNeedle = 'export const ' + o.name + ' = `' + '${API_BASE}' + o.value.slice('/api'.length) + '`;';
      expect(src, `${o.file} 的 ${o.name} 声明形状异常（必须写 API_BASE 派生态、不得回退裸字面量）`).toContain(declNeedle);
      expect(FILES).toContain(o.file);
    }
    // 反向自检：台账面若被清空，上一条 `derivedValues(...).toContain` 便无证据 ⇒ 用一条恒不成立的取值
    // 证明判据本身能变红（防台账退化成空数组时的恒真绿）。
    expect(derivedValues(read('dataClient.ts'))).not.toContain('/api/v1/fixture/not-declared');
    expect(OWNER_DECL_ADDITIONS.every((o) => o.value.startsWith('/api/'))).toBe(true);
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
