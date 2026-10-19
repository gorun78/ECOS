// @vitest-environment node
/**
 * pages/data-workbench/ 页面域基址单源回归门（#143/B5-22，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把数据工作台**页面侧**九文件（pipeline-editor 七个 + pipelineDebugApi.ts +
 * DataEngineConfigPanel.tsx）代码行的 16 处私设基址改由 `API_BASE` 派生，至此
 * `pages/data-workbench` 整域 78 个生产文件零私设基址（api/ 子域 8 文件系 #143/B5-21，
 * `_base.ts` 三枚常量系 #143/B5-8，`tabs/connections/InlineSqlConsole.tsx` 一枚系 #143/B5-11h）。
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
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../../services/httpClient';

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
  'DataEngineConfigPanel.tsx': 4,
};

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
    for (const m of line.matchAll(DERIVED_RE)) out.push(`${API_BASE}${m[1]}`);
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

  it('整域 78 个生产文件代码行零私设基址（递归扫，注释里的端点说明不计）', () => {
    const prodFiles = walk(DOMAIN_ROOT, []);
    expect(prodFiles.length).toBe(78);
    for (const rel of prodFiles) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('九文件一律从 httpClient 导入 API_BASE（第二处来源即红），且相对路径随目录深度正确', () => {
    for (const { rel, importPath } of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0]).toContain(`from '${importPath}'`);
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），九文件零 /api/api 双前缀', () => {
    for (const { rel } of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 16 条 URL 多重集 = 改前快照，且逐文件条数吻合', () => {
    const all: string[] = [];
    for (const { rel } of FILES) {
      const vals = derivedValues(read(rel));
      expect(vals.length, `${rel} 派生态条数不符`).toBe(SITES_PER_FILE[rel]);
      all.push(...vals);
    }
    expect(all.length).toBe(16);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
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
  });
});
