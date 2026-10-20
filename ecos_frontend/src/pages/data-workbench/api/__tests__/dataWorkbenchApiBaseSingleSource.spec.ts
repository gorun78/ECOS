// @vitest-environment node
/**
 * pages/data-workbench/api/ 域基址单源回归门（#143/B5-21，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把数据工作台 API 域（8 个 `.ts` 文件）代码行的私设基址整体改由 `API_BASE` 派生。
 * 派生只有一种正确性可证：**运行期面值逐字不变**。故本门不断「长得对」，而是把每个派生点
 * 按 `API_BASE` 的实际值还原成字符串，与改前快照（27 条 = 裸形态 26 + 改前既有派生态 1，
 * 2026-10-19 自 `git show HEAD:` 实跑读出，非手抄）做排序多重集比对——多一条、少一条、
 * 差一个斜杠都判红。</p>
 *
 * <p>判据口径与 `api-base-single-source`、`urlFamilyDualFormRatchet` 一致地含**反引号**：
 * 本批 26 处裸形态里 15 处是反引号模板，只吃引号的"清零"是假绿（D-B5-40 实证过的同一个坑）。
 * 快照里的 `${…}` 插值按字面保留、改前后同一写法，比对的是源码字面形态而非求值结果；
 * `unstructured.ts` 的 folder/files 那条含嵌套模板，两侧正则在同一个内层反引号处同样截断，
 * 故截断本身不影响逐字可比性（与 #143/B5-17 域门同口径，注释已言明）。</p>
 *
 * <p>`_base.ts` 系 #143/B5-8 传输合流批即已从 `API_BASE` 派生四枚域常量，本批不重复计入
 * 派生点数（其 3 条 `${API_BASE}` 属改前既有），只由「整域零私设基址」腿覆盖。</p>
 *
 * <p>**#145/B6-15 同批翻转**：`pipeline.ts` 的 `runs/{runId}` 腿基址上收为 `api/dataClient.ts`
 * 导出的 `ENGINE_DATA_PIPELINE_RUNS_BASE` ⇒ 该行不再是 `` `${API_BASE}…` `` 形态。按**剪腿禁止**
 * 口径（#145/B6-5 ④）判据换成表驱动解析（`SITE_RE` + `CONST_VALUES`），快照 27 条**一条不剪**，
 * 并新增「常量取值逐字 = 改前族根」等价判据与「该腿只准引 api/dataClient」来源判据作根据。
 * 同文件的三枚 `/tasks` 子路径腿各只有本域一个生产方、不在重复面，仍写 `${API_BASE}` 原样
 * 以保住棘轮族面可见性（#145/B6-1/B6-3 口径）。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../../../services/httpClient';
// #145/B6-15：本域 `pipeline.ts` 的 `runs/{runId}` 腿改取属主常量面值。按**剪腿禁止**口径（#145/B6-5 ④），
// 面值解析表必须认这枚常量，否则该腿对本门隐形 ⇒ 多重集少一条、把「形态搬运」误读成「请求消失」。
import { ENGINE_DATA_PIPELINE_RUNS_BASE } from '../../../../api/dataClient';

const DOMAIN_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 本批改动的 5 文件（`_base.ts` 改前已派生，`index.ts`／`mappers.ts` 零基址字面量）。 */
const FILES = [
  'datasource.ts',
  'lineage.ts',
  'metadata.ts',
  'pipeline.ts',
  'unstructured.ts',
] as const;

/** 私设基址判据：引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** 派生形态：`` `${常量}<path>` ``，捕获组即拼出来的路径段（#145/B6-15 起含族单源常量）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;

/** 面值解析表：`API_BASE` 单源 + #145/B6-15 上收到 api 层的管道 runs 族根。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
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
 * 改前裸形态快照（26 条，带定界符原样收录，便于人工逐条核对）：
 * datasource 1 + lineage 1 + metadata 11 + pipeline 4 + unstructured 9。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  "'/api/v1/ecos/data/datasource/preview-schema'",
  "'/api/v1/engine/data/lineage/impact?'",
  '`/api/v1/datanet/metadata/collect-async/${encodeURIComponent(datasourceId)}`',
  '`/api/v1/datanet/metadata/collect-status/${encodeURIComponent(taskId)}`',
  '`/api/v1/datanet/metadata/collect-sync/${encodeURIComponent(datasourceId)}`',
  "'/api/v1/task/list?taskType=METADATA_COLLECT&offset=0&limit=20'",
  '`/api/v1/datanet/metadata/collect-diff/${encodeURIComponent(datasourceId)}?limit=${limit}`',
  '`/api/v1/datanet/metadata/version-history/${encodeURIComponent(datasourceId)}`',
  '`/api/v1/datanet/metadata/version-diff/${encodeURIComponent(datasourceId)}?version=${encodeURIComponent(version)}`',
  '`/api/v1/datanet/metadata/preview/${encodeURIComponent(resourceId)}?limit=${limit}`',
  '`/api/v1/datanet/metadata/fields/${encodeURIComponent(resourceId)}`',
  '`/api/v1/datanet/metadata/strategy/${encodeURIComponent(datasourceId)}`',
  '`/api/v1/datanet/metadata/resources/${encodeURIComponent(datasourceId)}?_=${Date.now()}`',
  "'/api/v1/engine/data/pipeline/tasks?taskType=SYNC'",
  "'/api/v1/engine/data/pipeline/tasks'",
  '`/api/v1/engine/data/pipeline/tasks/${taskId}/run`',
  '`/api/v1/engine/data/pipeline/runs/${encodeURIComponent(runId)}`',
  "'/api/v1/datanet/ingest/targets'",
  "'/api/v1/datanet/ingest/run'",
  '`/api/v1/datanet/ingest/status/${encodeURIComponent(taskId)}`',
  "'/api/v1/datanet/ingest/schedule'",
  '`/api/v1/datanet/ingest/schedule/${encodeURIComponent(datasourceId)}`',
  "'/api/v1/datanet/datalake/status'",
  "'/api/v1/datanet/datalake/init'",
  '`/api/v1/datanet/datalake/unstructured/folder/files?datasourceId=${encodeURIComponent(datasourceId)}${docId ? `',
  "'/api/v1/datanet/datalake/unstructured/collect'",
];

/** 改前既有派生态（#143/B5-14 multipart 上传腿）：本批不动，但必须计入多重集，否则比对少一条。 */
const DERIVED_AT_HEAD: readonly string[] = [
  '/api/v1/datanet/datalake/unstructured/upload',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = [
  ...RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1)),
  ...DERIVED_AT_HEAD,
];

describe('pages/data-workbench/api 域基址单源 — 私设基址清零 (#143/B5-21)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('整域八文件代码行零私设基址，注释里的端点说明不计', () => {
    const tsFiles = readdirSync(DOMAIN_ROOT).filter((f) => f.endsWith('.ts'));
    expect(tsFiles.length).toBe(8);
    for (const rel of tsFiles) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('五文件一律从 httpClient 导入 API_BASE（第二处来源即红）', () => {
    for (const rel of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0]).toContain("from '../../../services/httpClient'");
    }
  });

  it('派生形态唯一：解析表内常量后必接 /（防漏段），全域零 /api/api 双前缀', () => {
    const names = [...CONST_VALUES.keys()];
    const NO_SLASH_RE = new RegExp('`\\$\\{(?:' + names.join('|') + ')\\}(?!/)');
    for (const rel of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => NO_SLASH_RE.test(l));
      expect(bad, `${rel} 存在 \`\${…}\` 基址常量后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('#145/B6-15 族根来源唯一：pipeline.ts 的 runs 腿只准引 api/dataClient，且 API_BASE 不得多于 1 条 (#145/B6-5 ④)', () => {
    const imports = codeLines(read('pipeline.ts')).filter((l) => l.trimStart().startsWith('import '));
    const runsImports = imports.filter((l) => /\bENGINE_DATA_PIPELINE_RUNS_BASE\b/.test(l));
    expect(runsImports.length, 'runs 族根导入必须恰为 1 条（第二处来源即红）').toBe(1);
    expect(runsImports[0]).toContain("from '../../../api/dataClient'");
    // 面值前提：属主常量必须真等于该族根，否则解析表还原出来的面值与运行期不一致，多重集比对失去意义
    expect(ENGINE_DATA_PIPELINE_RUNS_BASE).toBe('/api/v1/engine/data/pipeline/runs');
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（27 条 = 裸形态 26 + 既有派生 1）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(27);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
  });

  it('快照自身可校验：26 裸形态 = 11 单引号 + 15 反引号 + 0 双引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(RAW_PRE_BATCH_URL_LITERALS.length).toBe(26);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(0);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(11);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(15);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
  });

  it('判据能变红：违例源命中（含反引号形态）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const INGEST_BASE = '/api/v1/datanet/ingest/run';",
      'return get(`/api/v1/datanet/metadata/fields/${encodeURIComponent(id)}`);',
      'return post("/api/v1/engine/data/pipeline/tasks", body);',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../../../services/httpClient';",
      'const INGEST_BASE = `${API_BASE}/v1/datanet/ingest/run`;',
      'return get(`${API_BASE}/v1/datanet/metadata/fields/${encodeURIComponent(id)}`);',
      '// GET /api/v1/task/list 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual([
      '/api/v1/datanet/ingest/run',
      '/api/v1/datanet/metadata/fields/${encodeURIComponent(id)}',
    ]);
  });
});
