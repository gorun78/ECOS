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
 *
 * <p>**#142/B4-3e-47 同批改面值（本门唯一一次「面值合法变更」）**：`datasource.ts` 的
 * `preview-schema` 腿原打 `/api/v1/ecos/data/datasource/preview-schema`——全仓零承流的
 * **幻影路径**，改前它进快照只证明「形态是派生的」，不证明「请求有人接」。本批把该腿改指
 * data-engine 属主端点 `/api/v1/datanet/datasource/preview-schema`（族根取 `api/dataClient`
 * 的 `DATANET_DATASOURCE_BASE`）。按**剪腿禁止**口径：幻影条目不从历史上抹掉，而是搬进
 * `RETIRED_PHANTOM_URL_LITERALS` 并新增「退役面值再出现即红」的判据腿——幻影复活与请求消失
 * 是两种必须各自能变红的失败。同时 `API_BASE` 导入腿改表驱动：`datasource.ts` 期望 **0**
 * （该文件已无 `${API_BASE}` 站点），其余四文件仍期望 1。</p>
 *
 * <p>**#142/B4-3e-68（D-B4-79 收口同批换口径）**：`_base.ts` 的 `DATANET_DS` 原为不含 `/api`
 * 前缀的 dev-only 第二属主（生产 BFF 面打不通），按 B6-5 的**别名重导出**同法退役本地声明——
 * `_base.ts` 只剩一行 `export { DATANET_DATASOURCE_BASE as DATANET_DS }`（同 `PIPELINE_DEFS`
 * 判例，B6-5 守恒门判据形状批上已钉死），六腿引用名逐字不变、运行期 URL 由
 * `/datanet/datasource…` 改为 `/api/v1/datanet/datasource…`（后端 `DataSourceController`
 * 类级映射双路径 `{/api/v1/datanet/datasource, /datanet/datasource}` 均承流、后者存量不动，
 * 面值变更属「同一端点换承流轨道」、逐腿端点与动词不变）。本门口径三连改：
 * ① `CONST_VALUES` 再认 `DATANET_DS` 别名（值＝属主族根），**四条反引号模板腿**（`` `${id}/test` ``／
 *  `` `${id}` `×2` ``／`/test`）首次进面值多重集 ⇒ 改后期望 **32**（27 − 幻影 1 + 换入 1 +
 * 净增 1 + D79 四条模板腿 4）；另两条<b>裸引用腿</b>（列表 GET＋创建 POST，`get/post(DATANET_DS, …)`
 * 无插值段）对 `SITE_RE` 按口径不可见，其承流凭据由新增的 **D-B4-79 别名腿**案钉死
 * （域门面恰好一行重导出＋全域零本地 `const DATANET_DS` 声明＋运行期面值 = 属主族根），不另补面值；
 * ② 基址后必接斜杠腿无需豁免：`DATANET_DS` 的全部模板站点后均接 `/`（裸引用腿不在该判据面里）；
 * ③ `datasource.ts` 的 `DATANET_DATASOURCE_BASE` 导入仍恰 1 条（既有案不动），
 * 面值表自检案把 D79 四条腿一并纳入差集闭合。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../../../services/httpClient';
// #145/B6-15：本域 `pipeline.ts` 的 `runs/{runId}` 腿改取属主常量面值。按**剪腿禁止**口径（#145/B6-5 ④），
// 面值解析表必须认这枚常量，否则该腿对本门隐形 ⇒ 多重集少一条、把「形态搬运」误读成「请求消失」。
// #142/B4-3e-47：同表再认 `DATANET_DATASOURCE_BASE`（`datasource.ts` 的预览腿族根），理由同上。
import { ENGINE_DATA_PIPELINE_RUNS_BASE, DATANET_DATASOURCE_BASE } from '../../../../api/dataClient';
import { DATANET_DS } from '../_base';

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

/** 面值解析表：`API_BASE` 单源 + 上收到 api 层的族根。
 *  #145/B6-15 认 `ENGINE_DATA_PIPELINE_RUNS_BASE`；#142/B4-3e-47 认 `DATANET_DATASOURCE_BASE`；
 *  #142/B4-3e-68 再认域别名 `DATANET_DS`（B6-5 同法重导出，值＝属主族根；六条 CRUD/测试腿里的
 *  四条模板站点由此进入面值多重集——不吃它们就把「形态搬运」误读成「请求消失」，同 B6-5 ④ 失面判例）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['ENGINE_DATA_PIPELINE_RUNS_BASE', ENGINE_DATA_PIPELINE_RUNS_BASE],
  ['DATANET_DATASOURCE_BASE', DATANET_DATASOURCE_BASE],
  ['DATANET_DS', DATANET_DATASOURCE_BASE],
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
 * 改前裸形态快照（25 条，带定界符原样收录，便于人工逐条核对）：
 * datasource 0 + lineage 1 + metadata 11 + pipeline 4 + unstructured 9。
 *
 * <p>**#142/B4-3e-47**：原 datasource 那条（零承流幻影 `/api/v1/ecos/data/datasource/preview-schema`）
 * 不在这里了——它不是「被删」，而是搬进下方 `RETIRED_PHANTOM_URL_LITERALS`，仍计入改前全集，
 * 但从「合法面值」降为「只准存在过、不准再出现」。留在本表等于给幻影永久发通行证。</p>
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
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

/**
 * #142/B4-3e-47 退役幻影面值（改前在历史上、改后任何代码行再出现即红）。
 *
 * <p>该路径全仓零承流：后端 Controller、网关重写表、BFF 路由三面均无匹配，前端打过去只
 * 得到「成功但读空」的假象（差距 D-B4-76）。改前它进快照只证明「形态是 `API_BASE` 派生的」，
 * 从不证明「请求有人接」——把它留在合法面里，等于永久给幻影发通行证。</p>
 *
 * <p>它仍计入 `PRE_BATCH_URL_LITERALS`（改前全集一条不剪），只是从「合法面值」降为
 * 「已知存在过的面值」；改后期望集通过 `EXPECTED_URL_LITERALS` 把它换掉。</p>
 */
const RETIRED_PHANTOM_URL_LITERALS: readonly string[] = [
  '/api/v1/ecos/data/datasource/preview-schema',
];

/** 改前全集（27 条 = 裸形态 25 + 退役幻影 1 + 改前既有派生态 1）：面值逐字取自 `git show HEAD:`。 */
const PRE_BATCH_URL_LITERALS: readonly string[] = [
  ...RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1)),
  ...RETIRED_PHANTOM_URL_LITERALS,
  ...DERIVED_AT_HEAD,
];

/**
 * #142/B4-3e-47 本批合法新增面值：预览腿改指 data-engine 属主端点
 * （`DataSourceController#previewSchema` 挂 `/api/v1/datanet/datasource`，族根＝
 * `api/dataClient` 的 `DATANET_DATASOURCE_BASE`）。
 */
const ADDED_AT_BATCH_URL_LITERALS: readonly string[] = [
  '/api/v1/datanet/datasource/preview-schema',
];

/**
 * #142/B4-3e-64 之后批次合法新增的面值（闭集棘轮：新增一枚须在这里点名，只增不静默）。
 *
 * <p>本域在 B5-21 收口之后仍会继续长出口，这不可免——可免的是「长出来的面值没在这儿留下痕迹」。
 * 因此把「本批换一条」与「后续批次净增」分成两张表：前者是 B5-21 改面的守恒证据（剪腿与加腿
 * 一一对应，数量守恒），后者是净增登记（枚数独立钉值，多一枚少一枚都红）。</p>
 *
 * <p>`supported-types` 的属主＝data-engine 承流面 `DataSourceController#supportedTypes`
 * （族根同样取 `api/dataClient` 的 `DATANET_DATASOURCE_BASE`，不另立基址）。</p>
 */
const ADDED_BY_LATER_BATCHES_URL_LITERALS: readonly string[] = [
  '/api/v1/datanet/datasource/supported-types',
];

/**
 * #142/B4-3e-68 D-B4-79 收口本批合法新增面值：`_base.DATANET_DS` 声明退役为属主常量的
 * 别名重导出（B6-5 同法）后，`datasource.ts` 四条**反引号模板腿**首次进入本门面值面
 * （改前它们打 dev-only 的 `/datanet/datasource…`，不含 `/api` 前缀 ⇒ `HARDCODED_RE`/
 * `SITE_RE` 双口径都吃不到，门对它们长期失明，正是 D-B4-79 的病灶形状）。
 * 四条面值 = 属主族根 × {`` `${id}/test` ``、`` `${id}` ``（PUT/DELETE 两腿同值）、`/test`}。
 * 另两条<b>裸引用腿</b>（列表 GET ＋ 创建 POST，`get/post(DATANET_DS, …)` 无插值段）对
 * `SITE_RE` 按口径不可见——其承流凭据是 `DATANET_DS` 别名腿（见「D-B4-79 别名腿」案），
 * 不在此处补记面值，避免另一个口径外的面值表。
 */
const ADDED_BY_D79_URL_LITERALS: readonly string[] = [
  '/api/v1/datanet/datasource/${id}/test',
  '/api/v1/datanet/datasource/${id}',
  '/api/v1/datanet/datasource/${id}',
  '/api/v1/datanet/datasource/test',
];

/** 改后期望多重集：27 条改前全集 − 退役幻影 1 ＋ B5-47 换入 1 ＋ 后续批次净增 1 ＋ D-B4-79 四条模板腿 ＝ 32。 */
const EXPECTED_URL_LITERALS: readonly string[] = [
  ...PRE_BATCH_URL_LITERALS.filter((v) => !RETIRED_PHANTOM_URL_LITERALS.includes(v)),
  ...ADDED_AT_BATCH_URL_LITERALS,
  ...ADDED_BY_LATER_BATCHES_URL_LITERALS,
  ...ADDED_BY_D79_URL_LITERALS,
];

/** 域内全部 `.ts`（含 `_base.ts`/`index.ts`/`mappers.ts`）：幻影复活判据扫这个全集。 */
function allDomainTsFiles(): string[] {
  return readdirSync(DOMAIN_ROOT).filter((f) => f.endsWith('.ts'));
}

describe('pages/data-workbench/api 域基址单源 — 私设基址清零 (#143/B5-21)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('前提：DATANET_DATASOURCE_BASE 面值 = "/api/v1/datanet/datasource"（属主族根，预览腿面值的根据）', () => {
    expect(DATANET_DATASOURCE_BASE).toBe('/api/v1/datanet/datasource');
  });

  it('整域八文件代码行零私设基址，注释里的端点说明不计', () => {
    const tsFiles = allDomainTsFiles();
    expect(tsFiles.length).toBe(8);
    for (const rel of tsFiles) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('#142/B4-3e-47 幻影不复活：退役面值在域内任何代码行（含注释外的字符串）出现即红', () => {
    for (const phantom of RETIRED_PHANTOM_URL_LITERALS) {
      for (const rel of allDomainTsFiles()) {
        const hits = codeLines(read(rel)).filter((l) => l.includes(phantom));
        expect(hits, `${rel} 仍引用零承流幻影路径 ${phantom}`).toEqual([]);
      }
    }
  });

  it('#142/B4-3e-47 预览腿族根来源唯一：datasource.ts 只准从 api/dataClient 引 DATANET_DATASOURCE_BASE', () => {
    const imports = codeLines(read('datasource.ts')).filter((l) => l.trimStart().startsWith('import '));
    const hits = imports.filter((l) => /\bDATANET_DATASOURCE_BASE\b/.test(l));
    expect(hits.length, 'DATANET_DATASOURCE_BASE 导入必须恰为 1 条（第二处来源即红）').toBe(1);
    expect(hits[0]).toContain("from '../../../api/dataClient'");
  });

  it('五文件从 httpClient 导入 API_BASE：datasource.ts 已零站点故期望 0，其余四文件仍 1（表驱动）', () => {
    const EXPECTED_API_BASE_IMPORTS: ReadonlyMap<string, number> = new Map([
      ['datasource.ts', 0],
      ['lineage.ts', 1],
      ['metadata.ts', 1],
      ['pipeline.ts', 1],
      ['unstructured.ts', 1],
    ]);
    for (const rel of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 的 API_BASE 导入条数与预期不符`).toBe(EXPECTED_API_BASE_IMPORTS.get(rel));
      if (imports.length > 0) expect(imports[0]).toContain("from '../../../services/httpClient'");
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

  it('面值不变量：还原后的 URL 多重集守恒（#142/B4-3e-68 起 32 条 = 改前 27 − 幻影 1 + 属主 1 + 净增 1 + D-B4-79 四条模板腿 4）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length, `实读 ${all.length} 条：${all.sort().join(' | ')}`).toBe(32);
    expect([...all].sort()).toEqual([...EXPECTED_URL_LITERALS].sort());
  });

  it('快照自身可校验：25 裸形态 = 10 单引号 + 15 反引号 + 0 双引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(RAW_PRE_BATCH_URL_LITERALS.length).toBe(25);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(0);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(10);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(15);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
  });

  it('改表面值表自身可校验：幻影与新增互不重叠、各自确在/不在改前全集（防 EXPECTED 被手改歪）', () => {
    expect(RETIRED_PHANTOM_URL_LITERALS.length).toBe(1);
    expect(ADDED_AT_BATCH_URL_LITERALS.length).toBe(1);
    expect(ADDED_BY_LATER_BATCHES_URL_LITERALS.length).toBe(1);
    expect(ADDED_BY_D79_URL_LITERALS.length).toBe(4);
    expect(PRE_BATCH_URL_LITERALS.length).toBe(27);
    for (const v of RETIRED_PHANTOM_URL_LITERALS) expect(PRE_BATCH_URL_LITERALS).toContain(v);
    for (const v of [...ADDED_AT_BATCH_URL_LITERALS, ...ADDED_BY_LATER_BATCHES_URL_LITERALS, ...ADDED_BY_D79_URL_LITERALS]) {
      expect(PRE_BATCH_URL_LITERALS).not.toContain(v);
      expect(RETIRED_PHANTOM_URL_LITERALS).not.toContain(v);
    }
    // 换一条 + 净增一条 + D79 四条模板腿：差集恰为这几条，任何其它漂移都在这里变红
    expect(EXPECTED_URL_LITERALS.length).toBe(
      PRE_BATCH_URL_LITERALS.length + ADDED_BY_LATER_BATCHES_URL_LITERALS.length + ADDED_BY_D79_URL_LITERALS.length,
    );
    const removed = PRE_BATCH_URL_LITERALS.filter((v) => !EXPECTED_URL_LITERALS.includes(v)).sort();
    const added = EXPECTED_URL_LITERALS.filter((v) => !PRE_BATCH_URL_LITERALS.includes(v)).sort();
    expect(removed).toEqual([...RETIRED_PHANTOM_URL_LITERALS].sort());
    expect(added).toEqual(
      [...ADDED_AT_BATCH_URL_LITERALS, ...ADDED_BY_LATER_BATCHES_URL_LITERALS, ...ADDED_BY_D79_URL_LITERALS].sort(),
    );
  });

  it('#142/B4-3e-68 D-B4-79 别名腿：DATANET_DS 恰 1 行重导出且零本地声明，运行期面值 = 属主族根', () => {
    // _base.ts 代码行：DATANET_DS 只准以下列别名重导出形态出现，第二处声明即红
    // （本仓文件走 CRLF，先 .trimEnd() 剥 \r；测试自身/file 都是 eq 判定，剥掉不放松任何东西）
    const aliasLines = codeLines(read('_base.ts'))
      .filter((l) => /\bDATANET_DS\b/.test(l))
      .map((l) => l.trimEnd());
    expect(aliasLines).toEqual([
      "export { DATANET_DATASOURCE_BASE as DATANET_DS } from '../../../api/dataClient';",
    ]);
    // 域内任何代码行不得再有另一枚同族基址的本地 const 声明
    for (const rel of allDomainTsFiles()) {
      const hits = codeLines(read(rel))
        .filter((l) => /\bconst\s+DATANET_DS\b/.test(l))
        .map((l) => l.trimEnd());
      expect(hits, `${rel} 仍有 DATANET_DS 的本地声明（第二属主复形）`).toEqual([]);
    }
    // 运行期面值：域门面导出的 DATANET_DS 必须与属主族根同源同值
    expect(DATANET_DS).toBe(DATANET_DATASOURCE_BASE);
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
    // 幻影复活判据本身要能红：把退役面值写回代码行必须被吃到，写进注释则按口径不吃
    const PHANTOM_RETURNED = "  const url = '/api/v1/ecos/data/datasource/preview-schema';";
    expect(codeLines(PHANTOM_RETURNED).some((l) => l.includes(RETIRED_PHANTOM_URL_LITERALS[0]))).toBe(true);
    expect(codeLines('  // 改前打的是 /api/v1/ecos/data/datasource/preview-schema').length).toBe(0);
    // 属主腿判据本身要能红：派生态还原出的是属主面值，不是幻影面值
    expect(derivedValues('return post(`${DATANET_DATASOURCE_BASE}/preview-schema`, dto);'))
      .toEqual([ADDED_AT_BATCH_URL_LITERALS[0]]);
  });
});
