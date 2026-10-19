// @vitest-environment node
/**
 * 工作台散点批基址单源回归门（#143/B5-24，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把 4 个在册文件的代码行私设基址 **11 处**（逐文件 4 + 2 + 2 + 3）改由 `API_BASE`
 * 派生，形态拆分 = **单引号 6 + 反引号 3 + 双引号 2**（逐条取自 `git show HEAD:` 实跑读出）。
 * 与前六批不同，本批四个落点不共属一个目录域，故本门是**批级文件集门 + 两个整目录闭合门**的合体：
 * `pages/business-workbench`（38 生产文件）与 `components/ontology-workbench`（14 生产文件）
 * 自此整域零私设基址；`pages/aiworkbench` 仍有 `api.ts` 27 处未在册⇒该域本批不宣称闭合。</p>
 *
 * <p>派生只有一种正确性可证：**运行期面值逐字不变**。故本门把每个派生点按 `API_BASE` 实际值
 * 还原成字符串，与改前快照做排序多重集比对，并逐文件核对条数——多一条、少一条、差一个斜杠都判红。</p>
 *
 * <p>判据含**双引号与反引号**：本批 11 处里 2 处是双引号（`TelemetryViewer.tsx` 的两条
 * `apiFetchData<HealthData>("/api/telemetry/health")` 型）、3 处是模板串。只吃单引号的"清零"是假绿
 * （D-B5-40 系实证过的同一个坑）。</p>
 *
 * <p>本批另一特色：`TelemetryViewer.tsx` 三条腿是**无版本段**的 `/api/telemetry/…`（不带 `v1/`），
 * 双形态棘轮门按 `b.startsWith('/v1/')` 收族，故这三条本就不在族面内；但 C1 判据
 * `["'`]/api\b` 对它们可见⇒仍属必须收口的私设基址。域门的多重集与自检对这类腿同样逐字比。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 整域闭合的两个目录 + 各自生产文件数（实跑读出）。 */
const CLOSED_DIRS: ReadonlyArray<{ dir: string; files: number }> = [
  { dir: 'pages/business-workbench', files: 38 },
  { dir: 'components/ontology-workbench', files: 14 },
];

/** 本批改动的 4 文件 + 各自到 httpClient 的正确相对路径（目录深度不同，写错即编译不过）。 */
const FILES: ReadonlyArray<{ rel: string; importPath: string }> = [
  { rel: 'pages/business-workbench/BusinessWorkbenchLayout.tsx', importPath: '../../services/httpClient' },
  { rel: 'components/ontology-workbench/OntologyDomainPanel.tsx', importPath: '../../services/httpClient' },
  { rel: 'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts', importPath: '../../../services/httpClient' },
  { rel: 'pages/TelemetryViewer.tsx', importPath: '../services/httpClient' },
];

/** 本批每文件应还原出的派生态条数（实跑读出，防「剪一处漏一处」）。 */
const SITES_PER_FILE: Readonly<Record<string, number>> = {
  'pages/business-workbench/BusinessWorkbenchLayout.tsx': 4,
  'components/ontology-workbench/OntologyDomainPanel.tsx': 2,
  'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts': 2,
  'pages/TelemetryViewer.tsx': 3,
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
  return readFileSync(join(SRC_ROOT, rel), 'utf8');
}

/** 测试面豁免口径与棘轮门一致：`__tests__` 目录与 `*.test.*`/`*.spec.*` 不算生产文件。 */
function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx')
  );
}

function walk(dirRoot: string, out: string[]): string[] {
  for (const entry of readdirSync(join(SRC_ROOT, dirRoot))) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(SRC_ROOT, dirRoot, entry);
    if (statSync(full).isDirectory()) {
      walk(`${dirRoot}/${entry}`, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(SRC_ROOT, full).split(sep).join('/');
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
 * 改前裸形态快照（11 条，带定界符原样收录，`git show HEAD:` 实跑读出，非手抄）：
 * BusinessWorkbenchLayout 4 + OntologyDomainPanel 2 + useLogicPipelineRun 2 + TelemetryViewer 3。
 * 其中 `/api/v1/ecos/ontologies` 一条快照里出现 3 次（两文件各自私设 + 同文件两枚常量），
 * 多重集比对因此对「同族多属主」这一 B6 存量债的当前点位也逐字钉死。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  "'/api/v1/ecos/ontologies'",
  "'/api/v1/ecos/relationships'",
  "'/api/v1/ecos/actions'",
  "'/api/v1/ecos/domains'",
  "'/api/v1/ecos/ontologies'",
  "'/api/v1/ecos/ontologies'",
  '`/api/v1/aip/studio/pipelines/${encodeURIComponent(selectedPipeline.id)}/execute`',
  '`/api/v1/aip/studio/pipelines/${encodeURIComponent(execId)}/executions`',
  '"/api/telemetry/health"',
  '"/api/telemetry/traces?limit=20"',
  '`/api/telemetry/traces/${traceId}`',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

describe('工作台散点批基址单源 — 私设基址清零 (#143/B5-24)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('两整域 38+14 个生产文件 + 两散点文件代码行零私设基址（递归扫，注释里的端点说明不计）', () => {
    for (const { dir, files } of CLOSED_DIRS) {
      const prodFiles = walk(dir, []);
      expect(prodFiles.length, `${dir} 生产文件数与本批实跑读数不符（扫描面漂移即红）`).toBe(files);
      for (const rel of prodFiles) {
        const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
        expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
      }
    }
    for (const { rel } of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `本批文件 ${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('四文件一律从 httpClient 导入 API_BASE（第二处来源即红），且相对路径随目录深度正确', () => {
    for (const { rel, importPath } of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      // 两种引号风格都要吃：`TelemetryViewer.tsx` 全文件用双引号，写死单引号会把该文件判成
      // 「没有正确的导入」＝假红；反向只查 `services/httpClient` 子串则放过错误的目录深度。
      expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
        new RegExp(`from '${importPath}'|from "${importPath}"`),
      );
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），四文件零 /api/api 双前缀', () => {
    for (const { rel } of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 11 条 URL 多重集 = 改前快照，且逐文件条数吻合（4/2/2/3）', () => {
    const all: string[] = [];
    for (const { rel } of FILES) {
      const vals = derivedValues(read(rel));
      expect(vals.length, `${rel} 派生态条数不符`).toBe(SITES_PER_FILE[rel]);
      all.push(...vals);
    }
    expect(all.length).toBe(11);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
  });

  it('快照自身可校验：11 条 = 6 单引号 + 3 反引号 + 2 双引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(RAW_PRE_BATCH_URL_LITERALS.length).toBe(11);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(6);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(3);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(2);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
  });

  it('判据能变红：违例源命中（含双引号与反引号形态）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      'const detail = await apiFetchData<TraceDetail>(`/api/telemetry/traces/${traceId}`);',
      'apiFetchData<HealthData>("/api/telemetry/health"),',
      "const ONTOLOGY_API = '/api/v1/ecos/ontologies';",
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../services/httpClient';",
      'const ONTOLOGY_API = `${API_BASE}/v1/ecos/ontologies`;',
      'const detail = await apiFetchData<TraceDetail>(`${API_BASE}/telemetry/traces/${traceId}`);',
      '// GET /api/v1/ecos/relationships 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual([
      '/api/v1/ecos/ontologies',
      '/api/telemetry/traces/${traceId}',
    ]);
  });
});
