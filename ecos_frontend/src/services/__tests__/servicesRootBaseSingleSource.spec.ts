// @vitest-environment node
/**
 * servicesRootBaseSingleSource.spec.ts — `src/services/` 根域基址单源回归门（#143/B5-18，差距台账 §13.2.4 C1 腿第三批）。
 *
 * <p>本批把 `src/services/` 根十个基线在册文件代码行的私设基址整体改由 `API_BASE` 派生。派生只有
 * 一种正确性可证：**取值逐字不变**。故核心断言不是「长得对」，而是把每个派生点按 `API_BASE` 的实际
 * 值还原成字符串，与改前快照（48 条，2026-10-19 自 HEAD 实跑读出，非手抄）做多重集比对——多一条、
 * 少一条、差一个斜杠都判红。</p>
 *
 * <p>**判据口径（本批实证）**：私设基址有三种定界符形态，HEAD 实扫拆分 = 双引号 16 枚 + 单引号 7 枚
 * + 反引号 25 枚。若按窄口径 `["']` 去扫只能看到 23 枚，剩下 25 枚（占本批过半）会带着「已收口」的
 * 结论留在树里——这正是 #143/B5-17 照登的漏扫形状的放大版。本门因此把形态拆分写成快照自校验断言，
 * 并在 DEFECT 样本里三形态各配一条，使「窄口径化」必然判红。</p>
 *
 * <p>`/api/api` 双前缀腿只对代码行判定：`taskCenter.ts` 头部注释里有一句「避免拼成 /api/api/…」的
 * 历史防错说明（HEAD 既存，非本批新增），整文判定会把一句注释判成违例；注释面另由 `HARDCODED_RE`
 * 的行首剔除口径统一处理，与本门族其余判据一致。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../httpClient';

const SVC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const FILES = [
  'aiworkbenchApi.ts',
  'cognitiveClient.ts',
  'cognitiveEngineApi.ts',
  'dataCatalogClient.ts',
  'gitService.ts',
  'knowledgeNavApi.ts',
  'ontologyClient.ts',
  'ontologyWorkbenchApi.ts',
  'platformApi.ts',
  'taskCenter.ts',
] as const;

/** 私设基址判据：引号/单引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径，含反引号）。 */
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
  return readFileSync(join(SVC_ROOT, rel), 'utf8');
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
 * 改前快照（HEAD 实跑读出，按「文件 × 行序」收录，带各自的定界符形态以便人工逐条核对与形态拆分自校验）。
 * 数组切片剥掉首尾定界符后即运行期面值。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  '"/api/v1/task"',
  '"/api/v1/task"',
  '"/api/v1/system/gateway-diagnostics"',
  '`/api/v1/monitor/alerts?${qs.toString()}`',
  '`/api/v1/monitor/alerts/${encodeURIComponent(id)}/ack`',
  '`/api/v1/monitor/dlq?${qs.toString()}`',
  '`/api/v1/monitor/dlq/${encodeURIComponent(id)}/replay`',
  '"/api/v1/ontology/data"',
  '"/api/v1/ontology/calibers"',
  '"/api/v1/ontology/functions/audit"',
  "'/api/v1/knowledge/nav'",
  '`/api/v1/knowledge/assets/status${qs}`',
  '`/api/v1/ecos/git/status${q(repoId)}`',
  '`/api/v1/ecos/git/commits${q(repoId, { path, limit })}`',
  '`/api/v1/ecos/git/commit${q(repoId)}`',
  '`/api/v1/ecos/git/branches${q(repoId)}`',
  '`/api/v1/ecos/git/diff${q(repoId, { commit1, commit2, path })}`',
  '`/api/v1/ecos/git/tag${q(repoId)}`',
  '`/api/v1/ecos/git/rollback${q(repoId)}`',
  '`/api/v1/ecos/git/branch${q(repoId)}`',
  '`/api/v1/ecos/git/push${q(repoId)}`',
  '`/api/v1/ecos/git/pull${q(repoId)}`',
  '`/api/v1/ecos/git/${action}${q(repoId)}`',
  '"/api/v1/datanet/metadata"',
  '`/api/v1/world-model/state`',
  '`/api/v1/world-model/scenarios`',
  '`/api/v1/world-model/strategy/recommend`',
  '`/api/v1/world-model/causal-graph${q ? "?" + q : ""}`',
  "'/api/agent-mesh/agents'",
  "'/api/agent-mesh/missions'",
  "'/api/agent-mesh/route-intent'",
  "'/api/v1/guardrails/validate'",
  "'/api/v1/guardrails/policies'",
  "'/api/v1/guardrails/policies'",
  '`/api/v1/guardrails/policies/${id}`',
  '"/api/v1/cognitive"',
  '"/api/v1/business/scenarios"',
  '"/api/v1/engine/cognitive"',
  '"/api/v1/world-model"',
  '"/api/v1/aip/pipelines"',
  '`/api/v1/aip/pipelines/${id}`',
  '`/api/v1/aip/pipelines/${id}`',
  '"/api/v1/aip/agents"',
  '"/api/v1/aip/agents"',
  '`/api/v1/aip/agents/${id}`',
  '`/api/v1/aip/agents/${id}`',
  '"/api/v1/aip/guardrails"',
  '"/api/v1/aip/audit-logs"',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

/** 快照规模与三形态拆分（HEAD 实扫读出）。 */
const TOTAL = 48;
const FORM_COUNTS = { dq: 16, sq: 7, bt: 25 } as const;
/** 每文件处数（HEAD 实扫读出，供「漏文件」自检）。 */
const PER_FILE = {
  'aiworkbenchApi.ts': 9,
  'cognitiveClient.ts': 4,
  'cognitiveEngineApi.ts': 11,
  'dataCatalogClient.ts': 1,
  'gitService.ts': 11,
  'knowledgeNavApi.ts': 2,
  'ontologyClient.ts': 2,
  'ontologyWorkbenchApi.ts': 1,
  'platformApi.ts': 5,
  'taskCenter.ts': 2,
} as const;

describe('services 根域基址单源 — 私设基址清零 (#143/B5-18)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('十文件代码行零私设基址；注释里的端点说明不计（与判据同口径）', () => {
    for (const rel of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行私设基址`).toEqual([]);
    }
  });

  it('十文件一律从 ./httpClient 导入 API_BASE（第二处来源即红）', () => {
    for (const rel of FILES) {
      const src = read(rel);
      const imports = codeLines(src).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0]).toMatch(/from ['"]\.\/httpClient['"]/);
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），代码行零 /api/api 双前缀', () => {
    for (const rel of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      const dbl = codeLines(src).filter((l) => /\/api\/api\b/.test(l));
      expect(dbl, `${rel} 代码行出现 /api/api 双前缀`).toEqual([]);
    }
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（48 条，HEAD 实跑读出）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(TOTAL);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
  });

  it('快照自身可校验：条数、三形态拆分与 /api/ 前缀（防快照被清空或窄口径化）', () => {
    expect(PRE_BATCH_URL_LITERALS.length).toBe(TOTAL);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
    const dq = RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length;
    const sq = RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length;
    const bt = RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length;
    expect({ dq, sq, bt }).toEqual(FORM_COUNTS);
    expect(dq + sq + bt).toBe(TOTAL);
    const perFileTotal = Object.values(PER_FILE).reduce((a, b) => a + b, 0);
    expect(Object.keys(PER_FILE).sort()).toEqual([...FILES].sort());
    expect(perFileTotal, '每文件处数之和必须等于快照总数（防漏文件）').toBe(TOTAL);
  });

  it('判据能变红：三形态违例源各命中、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const SQ_BASE = '/api/v1/system/users';",
      'const DQ_BASE = "/api/v1/system/roles";',
      'return apiFetchData(`/api/v1/audit/stats`);',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE, apiFetchData } from './httpClient';",
      'const BASE = `${API_BASE}/v1/system/users`;',
      'return apiFetchData(BASE);',
      '// 历史 "/api" 字面量只允许留在注释行（判据与门禁同口径）',
      '/** 注释里提到 /api/api/… 属防错说明，不计违例 */',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual(['/api/v1/system/users']);
    expect(codeLines(CLEAN).filter((l) => /\/api\/api\b/.test(l))).toEqual([]);
  });
});
