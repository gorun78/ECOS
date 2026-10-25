// @vitest-environment node
/**
 * pages/data-workbench/ 页面域基址单源回归门（#143/B5-22，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把数据工作台**页面侧**九文件（pipeline-editor 七个 + pipelineDebugApi.ts +
 * DataEngineConfigPanel.tsx）代码行的 16 处私设基址改由 `API_BASE` 派生，至此
 * `pages/data-workbench` 整域 78 个生产文件零私设基址（api/ 子域 8 文件系 #143/B5-21，
 * `_base.ts` 三枚常量系 #143/B5-8，`tabs/connections/InlineSqlConsole.tsx` 一枚系 #143/B5-11h）。
 * **#142/B4-3e-76 面收缩 78→77**：域内原 LucideIcon.tsx 过渡桥随本批桥下线被删，生产文件面减一枚。</p>
 * 派生只有一种正确性可证：**运行期面值逐字不变**。故本门把每个派生点按 `API_BASE` 实际值
 * 还原成字符串，与改前快照（16 条，2026-10-19 实跑读出，非手抄）做排序多重集比对，
 * 并逐文件核对条数（1+3+1+1+1+1+2+2+4）——多一条、少一条、差一个斜杠都判红。</p>
 *
 * <p>本批九文件改前**零** `API_BASE` 派生态（与 #143/B5-21 的 api/ 子域不同，那里有一条
 * multipart 上传腿既已派生、必须计入多重集），故快照即 16 条裸形态，无补集。</p>
 *
 * <p>判据含**反引号**：本批 16 处里 6 处是模板串（含 `${encodeURIComponent(…)}` 插值），
 * 只吃引号的"清零"是假绿（D-B5-40 实证过的同一个坑）。快照里的 `${…}` 按字面保留，
 * 改前后同一写法，比对的是源码字面形态而非求值结果。</p>
 *
 * <p>**#145/B6-9 属主上收后的同批翻转**（本门是兄弟门耦合规则的第 N 次兑现：任何逐文件枚举
 * 基址形态的门，族上收时必红或经注释假绿，属主批必须同批改钉）：`DataEngineConfigPanel.tsx`
 * 的 PUT 保存腿改成引 `api/dataClient.ts` 导出的 `DATA_ENGINE_SETTINGS_BASE` ⇒ 该行不再是
 * `` `${API_BASE}…` `` 形态，`derivedValues` 吃不到。本门的处理是**快照不剪腿**、判据从
 * 派生态面换成**面值面**（模板腿 + 常量使用腿两形态都接住，import 行与注释行不算），
 * 并新增一条「常量取值逐字 = 改前那枚裸字面量」的等价判据作为唯一根据。
 * 剪腿会让「少一条请求」看着像进度（与 #145/B6-7 的 `faceValues` 同一口径）；
 * 本域其余三枚子路径腿（`/groups`、`/defaults`、`/refresh`）各只有本页一个生产方、
 * 不在重复面，按 #145/B6-1/B6-3 口径保持 `${API_BASE}` 原样以保住棘轮族面可见性。</p>
 *
 * <p>**#145/B6-15 同批二次翻转**（本域第二批上收，形状与 B6-9 **不同**）：
 * `pipeline-editor/PipelineExecutionMonitor.tsx` 的轮询腿上收为 `api/dataClient.ts` 的
 * `ENGINE_DATA_PIPELINE_RUNS_BASE`，写成 `` `${常量}/${encodeURIComponent(rid)}` ``——
 * **带尾段插值**而非裸引用，B6-9 那套裸名判据吃不到它 ⇒ 解析表 `CONST_VALUES` 必须认这枚常量，
 * 快照 16 条与 `SITES_PER_FILE` 逐文件条数**一条不剪**。该文件是全域唯一一枚基址腿，上收后
 * `API_BASE` 导入归零 ⇒ 原「九文件一律导入 API_BASE」判据若不改写会**误报成违例**，
 * 现按已测口径改为「至多 1 条 + 零来源必须恰有一条族单源导入腿」，并用 `movedOf` 反向钉住
 * 上收面恰好是两个已上收文件（少一个＝有文件被静默改成零来源，多一个＝扫描根漂了）。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../../services/httpClient';
import { DATA_ENGINE_SETTINGS_BASE, ENGINE_DATA_PIPELINE_RUNS_BASE } from '../../../api/dataClient';

const DOMAIN_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 本批改动的 9 文件 + 各自到 httpClient 的正确相对路径（目录深度不同，写错即编译不过）。 */
const FILES: ReadonlyArray<{ rel: string; importPath: string }> = [
  { rel: 'pipeline-editor/CopilotPanel.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipeline-editor/GitCommitDialog.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipeline-editor/GitVersionPanel.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipeline-editor/NodePalette.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipeline-editor/PipelineExecutionMonitor.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipeline-editor/PropertyPanel.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipeline-editor/UdfBuilderPanel.tsx', importPath: "../../../services/httpClient" },
  { rel: 'pipelineDebugApi.ts', importPath: "../../services/httpClient" },
  { rel: 'DataEngineConfigPanel.tsx', importPath: "../../services/httpClient" },
];

/** 本批每文件应还原出的派生态条数（实跑读出，防「剪一处漏一处」）。 */
const SITES_PER_FILE: Readonly<Record<string, number>> = {
  'pipeline-editor/CopilotPanel.tsx': 1,
  'pipeline-editor/GitCommitDialog.tsx': 3,
  'pipeline-editor/GitVersionPanel.tsx': 1,
  'pipeline-editor/NodePalette.tsx': 1,
  'pipeline-editor/PipelineExecutionMonitor.tsx': 1,
  'pipeline-editor/PropertyPanel.tsx': 1,
  'pipeline-editor/UdfBuilderPanel.tsx': 2,
  'pipelineDebugApi.ts': 2,
  // 4 枚里 1 枚（PUT 保存腿）系 #145/B6-9 上收为族单源常量 `DATA_ENGINE_SETTINGS_BASE`，
  // 面值由 faceValues 的常量表接住 ⇒ 条数判据仍取**改前腿数**，剪腿会让「少一条腿」看着像进度
  'DataEngineConfigPanel.tsx': 4,
};

/** 私设基址判据：引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** 派生形态：`` `${常量}<path>` ``，捕获组即拼出来的路径段（#145/B6-15 起含第二枚族单源常量）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;

/**
 * 面值解析表：`API_BASE` 单源 + 本域两枚已上收的族根常量。
 *
 * <p>#145/B6-15 的 `ENGINE_DATA_PIPELINE_RUNS_BASE` 与 B6-9 那枚的**形态不同**：B6-9 的保存腿是
 * **裸引用**（`apiFetchData(DATA_ENGINE_SETTINGS_BASE, …)`，整条面值即常量本身），由 `FAM_SITES`
 * 的裸名判据接住；本批的轮询腿写成 `` `${ENGINE_DATA_PIPELINE_RUNS_BASE}/${encodeURIComponent(rid)}` ``
 * ——族根后面还挂尾段插值，裸名判据对它隐形（前一位是 `$`，不算独立引用），只有解析表能还原出
 * 快照里那条面值。两种形态各有一条判据，任何一枚被写错都会少计一条 ⇒ 快照 16 条不许剪腿。</p>
 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['DATA_ENGINE_SETTINGS_BASE', DATA_ENGINE_SETTINGS_BASE],
  ['ENGINE_DATA_PIPELINE_RUNS_BASE', ENGINE_DATA_PIPELINE_RUNS_BASE],
]);

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.split('\n').filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(DOMAIN_ROOT, rel), 'utf8');
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
      const rel = relative(DOMAIN_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
}

/** 把派生点还原成运行期字符串面值（`${…}` 插值按字面保留，改前后同一写法）。 */
function derivedValues(src: string): string[] {
  const out: string[] = [];
  for (const line of codeLines(src)) {
    for (const m of line.matchAll(SITE_RE)) {
      const base = CONST_VALUES.get(m[1]);
      if (base !== undefined) out.push(base + m[2]);
    }
  }
  return out;
}

/**
 * 族单源常量表（#145/B6-9 起）：本域某枚腿的基址被上收为其他文件的导出常量后，
 * 页面里不再是 `` `${API_BASE}…` `` 形态 ⇒ `derivedValues` 吃不到它。判据按表驱动换成
 * **面值面**（模板腿 + 常量使用腿两种形态都接住），快照**不剪腿**：剪一条会让「少一条请求」
 * 看着像进度（#145/B6-7 同一口径）。后继批再上收一枚只需加一行。
 */
const FAM_SITE_RE = /(?:^|[^A-Za-z0-9_.$])DATA_ENGINE_SETTINGS_BASE\b/;
const FAM_SITES: ReadonlyArray<{ value: string; re: RegExp }> = [
  { value: DATA_ENGINE_SETTINGS_BASE, re: FAM_SITE_RE },
];

/**
 * 族单源导入名 = 解析表里除 `API_BASE` 之外的成员（由 `CONST_VALUES` 派生，不设第二份清单）。
 * 裸引用判据 `FAM_SITES` 只管**独立引用**形态（`apiFetchData(X, …)`），带尾段插值的
 * `` `${X}/…` `` 形态由解析表接住，两条腿各管一种形状。
 */
const FAM_IMPORT_NAMES: readonly string[] = [...CONST_VALUES.keys()].filter((k) => k !== 'API_BASE');

/** 逐字排除 import 行（导入腿只是取名，不构成一条 URL 站点）。 */
function nonImportCodeLines(src: string): string[] {
  return codeLines(src).filter((l) => !l.trimStart().startsWith('import '));
}

/** 面值面 = 派生态还原值 + 属主常量使用腿的取值。 */
function faceValues(src: string): string[] {
  const out = derivedValues(src);
  for (const line of nonImportCodeLines(src)) {
    for (const f of FAM_SITES) if (f.re.test(line)) out.push(f.value);
  }
  return out;
}

/**
 * 改前裸形态快照（16 条，带定界符原样收录，便于人工逐条核对）：
 * CopilotPanel 1 + GitCommitDialog 3 + GitVersionPanel 1 + NodePalette 1 +
 * PipelineExecutionMonitor 1 + PropertyPanel 1 + UdfBuilderPanel 2 +
 * pipelineDebugApi 2 + DataEngineConfigPanel 4。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  "'/api/v1/engine/data/copilot/chat'",
  '`/api/v1/engine/data/pipeline/git/branches?pipelineId=${encodeURIComponent(pipelineId)}`',
  '`/api/v1/engine/data/pipeline/tasks/${encodeURIComponent(pipelineId)}/git/status`',
  '`/api/v1/engine/data/pipeline/tasks/${encodeURIComponent(pipelineId)}/git/commit`',
  '`/api/v1/engine/data/pipeline/git/versions/${encodeURIComponent(pipelineId)}`',
  "'/api/v1/pipeline/node-types'",
  '`/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}`',
  "'/api/v1/engine/data/udf/list'",
  "'/api/v1/engine/data/udf/convert'",
  "`/api/v1/engine/data/udf/test`",
  "'/api/v1/pipeline/debug/sessions'",
  "'/api/v1/pipeline/debug/executions'",
  "'/api/v1/engine/data/settings/groups'",
  "'/api/v1/engine/data/settings/defaults'",
  "'/api/v1/engine/data/settings'",
  "'/api/v1/engine/data/settings/refresh'",
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

describe('pages/data-workbench 页面域基址单源 — 私设基址清零 (#143/B5-22)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('整域 77 个生产文件代码行零私设基址（递归扫，注释里的端点说明不计；78→77 系 #142/B4-3e-76 删 LucideIcon 桥）', () => {
    const prodFiles = walk(DOMAIN_ROOT, []);
    expect(prodFiles.length, '生产文件面漂移：新增/删除样本文件须同步本门判据').toBe(77);
    for (const rel of prodFiles) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('九文件至多 1 条 API_BASE 导入且来源为 httpClient（相对路径随目录深度正确）；已零 API_BASE 来源的文件必须恰有一条族单源导入腿 (#143/B5-22 / #145/B6-15)', () => {
    for (const { rel, importPath } of FILES) {
      const imports = codeLines(read(rel)).filter((l) => l.trimStart().startsWith('import '));
      const apiImports = imports.filter((l) => /\bAPI_BASE\b/.test(l));
      expect(apiImports.length, `${rel} 的 API_BASE 导入不得多于 1 条（第二处来源即红）`).toBeLessThanOrEqual(1);
      if (apiImports.length === 1) expect(apiImports[0]).toContain(`from '${importPath}'`);
      for (const name of FAM_IMPORT_NAMES) {
        const famImports = imports.filter((l) => l.includes(name));
        expect(famImports.length, `${rel} 的 ${name} 导入不得多于 1 条`).toBeLessThanOrEqual(1);
      }
      // 零 API_BASE 来源只有一种合法成因：该文件所有基址腿都上收到了族单源常量
      if (apiImports.length === 0) {
        const famTotal = imports.filter((l) => FAM_IMPORT_NAMES.some((n) => l.includes(n))).length;
        expect(famTotal, `${rel} 已无 API_BASE 导入 ⇒ 必须恰有一条族单源导入腿`).toBe(1);
      }
    }
    // 上收面必须**恰好**是对应文件：多一个说明扫描根漂了，少一个说明有文件被静默改成零来源（假绿）
    const movedOf = (name: string) =>
      FILES.filter(({ rel }) =>
        codeLines(read(rel)).some((l) => l.trimStart().startsWith('import ') && l.includes(name))
      ).map((f) => f.rel);
    expect(movedOf('DATA_ENGINE_SETTINGS_BASE')).toEqual(['DataEngineConfigPanel.tsx']);
    expect(movedOf('ENGINE_DATA_PIPELINE_RUNS_BASE')).toEqual(['pipeline-editor/PipelineExecutionMonitor.tsx']);
  });

  it('派生形态唯一：解析表内常量后必接 /（防漏段），九文件零 /api/api 双前缀', () => {
    const NO_SLASH_RE = new RegExp('`\\$\\{(?:' + [...CONST_VALUES.keys()].join('|') + ')\\}(?!/)');
    for (const { rel } of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => NO_SLASH_RE.test(l));
      expect(bad, `${rel} 存在 \`\${…}\` 基址常量后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 16 条 URL 多重集 = 改前快照，且逐文件条数吻合', () => {
    const all: string[] = [];
    for (const { rel } of FILES) {
      const vals = faceValues(read(rel));
      expect(vals.length, `${rel} 面值面条数不符`).toBe(SITES_PER_FILE[rel]);
      all.push(...vals);
    }
    expect(all.length).toBe(16);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
  });

  it('#145/B6-9 属主常量面值等价：DATA_ENGINE_SETTINGS_BASE 取值 = 改前那枚裸字面量', () => {
    // 这条是本门允许快照不剪腿的**唯一根据**：常量必须逐字等于改前面值，否则「面值不变」是假的。
    expect(DATA_ENGINE_SETTINGS_BASE).toBe('/api/v1/engine/data/settings');
    // 且该页确实只剩这一枚常量使用腿（保存腿），三枚子路径腿仍写 `${API_BASE}` 原样
    const src = read('DataEngineConfigPanel.tsx');
    expect(derivedValues(src).length).toBe(3);
    expect(faceValues(src).length).toBe(4);
    expect(nonImportCodeLines(src).filter((l) => FAM_SITE_RE.test(l)).length).toBe(1);
  });

  it('#145/B6-15 属主常量面值等价：ENGINE_DATA_PIPELINE_RUNS_BASE 取值 = 改前那枚族根，轮询腿带插值尾段仍计 1 条', () => {
    // 同上：等价性是「16 条快照不剪腿」的唯一根据。这枚常量拼出来的是**含插值尾段**的完整腿面值，
    // 与改前 `${API_BASE}/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}` 逐字相同。
    expect(ENGINE_DATA_PIPELINE_RUNS_BASE).toBe('/api/v1/engine/data/pipeline/runs');
    const src = read('pipeline-editor/PipelineExecutionMonitor.tsx');
    expect(derivedValues(src)).toEqual(['/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}']);
    expect(faceValues(src).length).toBe(1);
    // 该文件已把唯一一枚基址腿上收 ⇒ 全文件不得再有任何 `/api` 裸面或 API_BASE 派生
    expect(codeLines(src).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(codeLines(src).filter((l) => /\bAPI_BASE\b/.test(l))).toEqual([]);
  });

  it('快照自身可校验：16 条 = 10 单引号 + 6 反引号 + 0 双引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(RAW_PRE_BATCH_URL_LITERALS.length).toBe(16);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(0);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(10);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(6);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
  });

  it('判据能变红：违例源命中（含反引号形态）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const DBG_BASE = '/api/v1/pipeline/debug/sessions';",
      'const url = `/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}`;',
      'await apiFetchData("/api/v1/engine/data/settings/refresh", { method: \'POST\' });',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../../services/httpClient';",
      'const DBG_BASE = `${API_BASE}/v1/pipeline/debug/sessions`;',
      'const url = `${API_BASE}/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}`;',
      '// GET /api/v1/engine/data/settings 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual([
      '/api/v1/pipeline/debug/sessions',
      '/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}',
    ]);
    // 面值面自身的形状自检（#145/B6-9）：常量**使用腿**计 1 条、**import 腿**与注释行都不计
    const FAM = [
      "import { DATA_ENGINE_SETTINGS_BASE } from '../../api';",
      "await apiFetchData(DATA_ENGINE_SETTINGS_BASE, { method: 'PUT' });",
      '// 注释里的 DATA_ENGINE_SETTINGS_BASE 不算腿',
    ].join('\n');
    expect(faceValues(FAM)).toEqual(['/api/v1/engine/data/settings']);
    expect(derivedValues(FAM)).toEqual([]);
  });
});
