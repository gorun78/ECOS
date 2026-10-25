// @vitest-environment node
/**
 * 工作台散点批基址单源回归门（#143/B5-24，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把 4 个在册文件的代码行私设基址 **11 处**（逐文件 4 + 2 + 2 + 3）改由 `API_BASE`
 * 派生，形态拆分 = **单引号 6 + 反引号 3 + 双引号 2**（逐条取自 `git show HEAD:` 实跑读出）。
 * 与前六批不同，本批四个落点不共属一个目录域，故本门是**批级文件集门 + 两个整目录闭合门**的合体：
 * `pages/business-workbench`（37 生产文件）与 `components/ontology-workbench`（14 生产文件）
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
 *
 * <p>**#145/B6-10 对本门的影响（必读，否则后批会把 8 条当成'快照被改小'）**：
 * `/v1/ecos/ontologies` 族根构造点上收为 `services/ontology/ontologyModelApi.ts` 的
 * `ECOS_ONTOLOGIES_BASE`，本门两文件（`BusinessWorkbenchLayout` 1 枚 ＋ `OntologyDomainPanel` 2 枚
 * 同值常量）共 3 条派生态改为引属主常量 ⇒ 派生面 11→8。快照**保持 11 条逐字是改前面值**，
 * 上收的 3 条改由两条独立判据承证：① 面值等价（运行期 import 属主常量 = 快照里那条面值）；
 * ② 本门四文件代码行对 `/v1/ecos/ontologies` 字面量零命中（谁回退成私设派生即红）。
 * 属主唯一性（全仓该族只有一处族根构造点）不在本门证明范围内，由同批新增的
 * `b6EcosFamiliesSingleSource.spec.ts` 承证。
 * 另须记清消费点数与改前构造点数**不是 1:1**：`OntologyDomainPanel` 改前 2 枚常量喂 5 枚腿，
 * 改后是 0 派生态 + 5 枚属主常量消费点 ⇒ 本门按两张表各自钉死，不做加减等式。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { ECOS_ONTOLOGIES_BASE } from '../services/ontologyApi';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 整域闭合的两个目录 + 各自生产文件数（实跑读出）。 */
const CLOSED_DIRS: ReadonlyArray<{ dir: string; files: number }> = [
  // #142/B4-3e-76 同批摘除 `pages/business-workbench/LucideIcon.tsx` 页内桥（git rm，LucideIcon 过渡桥下线）
  // ⇒ 38→37（实扫读出，非手算）。
  { dir: 'pages/business-workbench', files: 37 },
  { dir: 'components/ontology-workbench', files: 14 },
];

/** 本批改动的 4 文件 + 各自到 httpClient 的正确相对路径（目录深度不同，写错即编译不过）。 */
const FILES: ReadonlyArray<{ rel: string; importPath: string }> = [
  { rel: 'pages/business-workbench/BusinessWorkbenchLayout.tsx', importPath: '../../services/httpClient' },
  { rel: 'components/ontology-workbench/OntologyDomainPanel.tsx', importPath: '../../services/httpClient' },
  { rel: 'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts', importPath: '../../../services/httpClient' },
  { rel: 'pages/TelemetryViewer.tsx', importPath: '../services/httpClient' },
];

/** 本批每文件应还原出的派生态条数（实跑读出，防「剪一处漏一处」）——**改前**口径。 */
const SITES_PER_FILE: Readonly<Record<string, number>> = {
  'pages/business-workbench/BusinessWorkbenchLayout.tsx': 4,
  'components/ontology-workbench/OntologyDomainPanel.tsx': 2,
  'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts': 2,
  'pages/TelemetryViewer.tsx': 3,
};

/** #145/B6-10 属主上收后，同四文件的 `` `${API_BASE}` `` 派生态条数（实测读出）。 */
const DERIVED_AFTER_B6_10: Readonly<Record<string, number>> = {
  'pages/business-workbench/BusinessWorkbenchLayout.tsx': 3,
  'components/ontology-workbench/OntologyDomainPanel.tsx': 0,
  'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts': 2,
  'pages/TelemetryViewer.tsx': 3,
};

/**
 * #145/B6-10 属主常量在本门文件面的消费点数（实测读出，含模板串与裸引用两种写法）。
 * 与 {@link SITES_PER_FILE} **不做加减等式**：改前 `OntologyDomainPanel` 的 2 枚同值常量喂 5 枚腿，
 * 上收后派生态 0 枚、消费点 5 枚，两个数各自钉死才有意义。
 */
const OWNER_USE_PER_FILE: Readonly<Record<string, number>> = {
  'pages/business-workbench/BusinessWorkbenchLayout.tsx': 1,
  'components/ontology-workbench/OntologyDomainPanel.tsx': 5,
};

/** 本门快照里被上收的族根面值（出现次数由下方 `PRE_BATCH` 计数钉死为 3）。 */
const UPLIFTED_FAMILY_FACE = '/api/v1/ecos/ontologies';

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

  it('两整域 37+14 个生产文件 + 两散点文件代码行零私设基址（递归扫，注释里的端点说明不计）', () => {
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

  it('四文件基址来源唯一：API_BASE 导入按文件钉死（B6-10 后 OntologyDomainPanel 归零），属主常量 import 恰 1 条', () => {
    // `OntologyDomainPanel.tsx` 的 API_BASE 导入数 1→0 是本批上收的直接后果：该文件改后**没有任何**
    // `${API_BASE}` 派生态，族根一律取属主常量 ⇒ 继续要求它「有 1 条 API_BASE 导入」会变成
    // 「逼着文件留一枚未使用的 import」，那是假绿而不是判据。改而钉两条：基址来源归零 + 属主常量入面。
    const API_BASE_IMPORTS: Readonly<Record<string, number>> = {
      'pages/business-workbench/BusinessWorkbenchLayout.tsx': 1,
      'components/ontology-workbench/OntologyDomainPanel.tsx': 0,
      'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts': 1,
      'pages/TelemetryViewer.tsx': 1,
    };
    for (const { rel, importPath } of FILES) {
      const lines = codeLines(read(rel));
      const imports = lines.filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 的 API_BASE 导入条数与本批实跑不符`).toBe(API_BASE_IMPORTS[rel]);
      // 两种引号风格都要吃：`TelemetryViewer.tsx` 全文件用双引号，写死单引号会把该文件判成
      // 「没有正确的导入」＝假红；反向只查 `services/httpClient` 子串则放过错误的目录深度。
      if (API_BASE_IMPORTS[rel] === 1) {
        expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
          new RegExp(`from '${importPath}'|from "${importPath}"`),
        );
      }
      if (OWNER_USE_PER_FILE[rel] !== undefined) {
        const familyImports = lines.filter((l) => /\bECOS_ONTOLOGIES_BASE\b/.test(l) && l.trimStart().startsWith('import '));
        expect(familyImports.length, `${rel} 应有且仅有 1 条属主常量导入`).toBe(1);
        expect(familyImports[0], `${rel} 的属主常量不经门面取值`).toContain("from '../../services/ontologyApi'");
      }
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

  it('面值逐字不变：派生态 8 条 = 改前 11 条 − 上收的 3 条同族面值，且逐文件条数吻合（3/0/2/3）', () => {
    const all: string[] = [];
    for (const { rel } of FILES) {
      const vals = derivedValues(read(rel));
      expect(vals.length, `${rel} 派生态条数不符`).toBe(DERIVED_AFTER_B6_10[rel]);
      all.push(...vals);
    }
    // 上收的三条只能是 ontologies 族根那一枚面值，出现次数逐字钉死为 3（1 枚页面腿 + 2 枚同文件常量）
    const uplifted = PRE_BATCH_URL_LITERALS.filter((v) => v === UPLIFTED_FAMILY_FACE);
    expect(uplifted.length).toBe(3);
    expect([...all].sort()).toEqual(PRE_BATCH_URL_LITERALS.filter((v) => v !== UPLIFTED_FAMILY_FACE).sort());
    // 上收后本门四文件不得再出现该族根面值的任何私设写法（引号/反引号/派生形态一律算违例）
    for (const { rel } of FILES) {
      const leaks = codeLines(read(rel)).filter((l) => l.includes(UPLIFTED_FAMILY_FACE));
      expect(leaks, `${rel} 回退成私设基址：\n${leaks.join('\n')}`).toEqual([]);
    }
    for (const [rel, n] of Object.entries(OWNER_USE_PER_FILE)) {
      const uses = codeLines(read(rel)).filter(
        (l) => l.includes('ECOS_ONTOLOGIES_BASE') && !l.trimStart().startsWith('import '),
      );
      expect(uses.length, `${rel} 属主常量消费点数不符`).toBe(n);
    }
  });

  it('#145/B6-10 面值等价：运行期 import 的属主常量 = 快照里那条族根面值（上收不改一个字符）', () => {
    expect(ECOS_ONTOLOGIES_BASE).toBe(UPLIFTED_FAMILY_FACE);
    // 反向自检：等价判据对同族其他腿不许成立（防把整族面值都当成族根）
    expect(ECOS_ONTOLOGIES_BASE).not.toBe('/api/v1/ecos/ontologies/ont001/entities');
    // 净减数守恒：本门派生态总数只减掉上收那 3 条，别的一条也不许悄悄消失
    const sum = (t: Readonly<Record<string, number>>) => Object.values(t).reduce((a, b) => a + b, 0);
    expect(sum(SITES_PER_FILE) - sum(DERIVED_AFTER_B6_10)).toBe(3);
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
