// @vitest-environment node
/**
 * pages/ 场景域基址单源回归门（#143/B5-23，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把「场景域页面」八个文件的 17 处代码行私设基址改由 `API_BASE` 派生：
 * `pages/scenario/` 五个（CognitionPanel 3 + OverrideTab 2 + ReplayTab 2 + CounterfactualTab 1 +
 * MentalReviewTab 1）、`pages/workbench/` 两个（index 1 + [scenarioId]/index 1）、
 * 根侧 `ScenarioManagementView.tsx` 6。至此 `pages/scenario` 整域 **15** 个生产文件与
 * `pages/workbench` 整域 **20** 个生产文件零私设基址（后者 `NewScenarioModal.tsx` 一枚派生态
 * 系 #143/B5-11h 既有，非本批）。</p>
 *
 * <p>派生只有一种正确性可证：**运行期面值逐字不变**。故本门把每个派生点按 `API_BASE` 实际值
 * 还原成字符串，与改前快照（17 条，2026-10-19 自 `git show HEAD:` 实跑读出，非手抄）做排序
 * 多重集比对，并逐文件核对条数——多一条、少一条、差一个斜杠都判红。八文件改前**零**派生态，
 * 故快照即 17 条裸形态、无补集（与 #143/B5-21 需补一条既有 upload 腿相反，与 #143/B5-22 同形）。</p>
 *
 * <p>判据含**反引号**：本批 17 处里 **11** 处是模板串（全部含 `${encodeURIComponent(…)}` 一类插值），
 * 只吃引号的"清零"是假绿（D-B5-40 实证过的同一个坑）。快照里的 `${…}` 按字面保留、改前后同一写法，
 * 比对的是源码字面形态而非求值结果。</p>
 *
 * <p>本域另有跨族意义：`/v1/cognitive/beliefs?domain={}`、`/v1/workspace/scenarios`、
 * `/v1/workspace/scenarios/{}` 三族的裸面属主在本批后归零，只认裸字面量的旧判据对它们更瞎
 * ——由 `urlFamilyDualFormRatchet` 按实测重钉，不在本门重复断言。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../services/httpClient';

const PAGES_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 本批改动的 8 文件 + 各自到 httpClient 的正确相对路径（目录深度不同，写错即编译不过）。 */
const FILES: ReadonlyArray<{ rel: string; importPath: string }> = [
  { rel: 'scenario/CognitionPanel.tsx', importPath: "../../services/httpClient" },
  { rel: 'scenario/OverrideTab.tsx', importPath: "../../services/httpClient" },
  { rel: 'scenario/ReplayTab.tsx', importPath: "../../services/httpClient" },
  { rel: 'scenario/CounterfactualTab.tsx', importPath: "../../services/httpClient" },
  { rel: 'scenario/MentalReviewTab.tsx', importPath: "../../services/httpClient" },
  { rel: 'workbench/index.tsx', importPath: "../../services/httpClient" },
  { rel: 'workbench/[scenarioId]/index.tsx', importPath: "../../../services/httpClient" },
  { rel: 'ScenarioManagementView.tsx', importPath: "../services/httpClient" },
];

/** 本批每文件应还原出的派生态条数（实跑读出，防「剪一处漏一处」）。 */
const SITES_PER_FILE: Readonly<Record<string, number>> = {
  'scenario/CognitionPanel.tsx': 3,
  'scenario/OverrideTab.tsx': 2,
  'scenario/ReplayTab.tsx': 2,
  'scenario/CounterfactualTab.tsx': 1,
  'scenario/MentalReviewTab.tsx': 1,
  'workbench/index.tsx': 1,
  'workbench/[scenarioId]/index.tsx': 1,
  'ScenarioManagementView.tsx': 6,
};

/** 本批闭合的两个目录根与其生产文件数（实跑读出，扫描根漂移成空集即红）。 */
const DOMAIN_ROOTS: ReadonlyArray<{ dir: string; files: number }> = [
  { dir: 'scenario', files: 15 },
  { dir: 'workbench', files: 20 },
];

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
  return readFileSync(join(PAGES_ROOT, rel), 'utf8');
}

/** 测试面豁免口径与双形态棘轮门一致：`__tests__`/`test` 目录与 `*.test.*`/`*.spec.*` 不算生产文件。 */
function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx')
  );
}

function walk(dir: string, out: string[]): string[] {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      walk(full, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(PAGES_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
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
 * 改前裸形态快照（17 条，带定界符原样收录，便于人工逐条核对）：
 * CognitionPanel 3 + OverrideTab 2 + ReplayTab 2 + CounterfactualTab 1 + MentalReviewTab 1 +
 * workbench/index 1 + workbench/[scenarioId]/index 1 + ScenarioManagementView 6。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  '`/api/v1/workspace/scenarios/${scenario.id}/runs?limit=20`',
  "'/api/v1/cognitive/models'",
  '`/api/v1/workspace/scenarios/${scenario.id}/runs`',
  '`/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}`',
  '`/api/v1/cognitive/beliefs/${encodeURIComponent(variable.trim())}/override`',
  '`/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}`',
  '`/api/v1/cognitive/beliefs/${encodeURIComponent(variableValue)}/${encodeURIComponent(version.trim())}/replay?${params.toString()}`',
  "'/api/v1/cognitive/counterfactual'",
  '`/api/v1/cognitive/mental-reviews?${params.toString()}`',
  "'/api/v1/workspace/scenarios'",
  '`/api/v1/workspace/scenarios/${scenarioId}`',
  "'/api/v1/workspace/scenarios'",
  "'/api/v1/ontology/proposals'",
  '`/api/v1/ontology/proposals/${id}/approve`',
  '`/api/v1/ontology/proposals/${id}/reject`',
  '`/api/v1/workspace/scenarios/${wizardScenarioId}`',
  "'/api/v1/workspace/scenarios'",
];

describe('pages/ 场景域基址单源 — 私设基址清零 (#143/B5-23)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('两个目录根 35 个生产文件 + 根侧场景管理页代码行零私设基址，注释里的端点说明不计', () => {
    for (const { dir, files } of DOMAIN_ROOTS) {
      const rels = walk(join(PAGES_ROOT, dir), []);
      expect(rels.length, `${dir}/ 生产文件数应实跑等于 ${files}（扫描根漂移即红）`).toBe(files);
      for (const rel of rels) {
        const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
        expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
      }
    }
    const smv = codeLines(read('ScenarioManagementView.tsx')).filter((l) => HARDCODED_RE.test(l));
    expect(smv, 'ScenarioManagementView.tsx 仍有私设基址').toEqual([]);
  });

  it('八文件一律从 httpClient 导入 API_BASE（第二处来源即红）', () => {
    for (const { rel, importPath } of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0]).toContain(`from '${importPath}'`);
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），八文件零 /api/api 双前缀', () => {
    for (const { rel } of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（17 条）且逐文件条数对得上', () => {
    const all: string[] = [];
    for (const { rel } of FILES) {
      const vals = derivedValues(read(rel));
      expect(vals.length, `${rel} 派生态条数`).toBe(SITES_PER_FILE[rel]);
      all.push(...vals);
    }
    expect(all.length).toBe(17);
    expect([...all].sort()).toEqual([...RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1))].sort());
  });

  it('快照自身可校验：17 条 = 6 单引号 + 11 反引号 + 0 双引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(RAW_PRE_BATCH_URL_LITERALS.length).toBe(17);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(0);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(6);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(11);
    for (const v of RAW_PRE_BATCH_URL_LITERALS) expect(v.slice(1, -1).startsWith('/api/')).toBe(true);
  });

  it('判据能变红：违例源命中（含反引号形态）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const d = await apiFetchData<ScenarioSummaryVO[]>('/api/v1/workspace/scenarios');",
      'await apiFetchData(`/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}`);',
      'const r = await callJsonWithTrace("/api/v1/ontology/proposals", { method: \'POST\' });',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../../services/httpClient';",
      "const d = await apiFetchData<ScenarioSummaryVO[]>(`${API_BASE}/v1/workspace/scenarios`);",
      'await apiFetchData(`${API_BASE}/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}`);',
      '// GET /api/v1/workspace/scenarios 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual([
      '/api/v1/workspace/scenarios',
      '/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}',
    ]);
  });
});
