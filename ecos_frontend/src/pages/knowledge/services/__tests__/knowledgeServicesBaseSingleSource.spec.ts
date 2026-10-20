// @vitest-environment node
/**
 * pages/knowledge/services/ 域基址单源回归门（#143/B5-20，差距台账 §13.2.4 C1 腿 注 ㉚）。
 *
 * <p>本批把知识服务域（C1 基线在册 5 文件）代码行的私设基址整体改由 `API_BASE` 派生。
 * 派生只有一种正确性可证：**运行期面值逐字不变**。故本门不断「长得对」，而是把每个
 * 派生点按 `API_BASE` 的实际值还原成字符串，与改前快照（32 条，2026-10-19 自 HEAD
 * 实跑读出，非手抄）做排序多重集比对——多一条、少一条、差一个斜杠都判红。</p>
 *
 * <p>形态口径与 `urlFamilyDualFormRatchet` 对齐：改前 32 处 = 单引号 25 + 反引号 7
 * （双引号 0）。只按 `["']` 数会漏掉 7 处反引号内联字面量＝假绿，故快照逐条保留定界符
 * 并在「快照自身可校验」腿里按形态计数；DEFECT 样本也专门留一条反引号形态做反向自检。</p>
 *
 * <p>本批刻意把每处改写成 `` `${API_BASE}/v1/…` `` 全形态（而非收敛到文件内已有的
 * `KB_V1`／`GRAPH_BASE` 常量）：族键由路径串决定，改写成常量会让该族从双形态棘轮的
 * 两种形态同时消失，属主面凭空变薄＝把「重复可见性」关掉。走 `${API_BASE}` 派生态
 * 则族不变、只由裸形态搬到派生态，棘轮读到的重复面严格不减少。</p>
 *
 * <p>**与本域族根在 #145/B6-1 被单源化不矛盾，须分清两件事**：上段禁的是「C1 转换批顺手把
 * 多属主写成各自常量」——那会同时关掉可见性又不减少属主，是假绿；B6-1 做的是「把 5 个属主的族根
 * 声明删掉、改 `import` 同一个导出常量」——属主面真的从 7 降到 1（棘轮 dupBoth 30→29 即其记账），
 * 面值不变性则由新门 `src/__tests__/b6KnowledgeFamilySingleSource.spec.ts` 按常量链解析另证。
 * 故本门的守恒判据自 B6-1 起要减去那 5 条**在册收口项**（族根声明不再是本域 `${API_BASE}` 派生态），
 * 减法按面值筛除并断言条数恰 5——列错一条即红，不留"数变了但没人解释"的缝。</p>
 *
 * <p>#145/B6-4 同一形状再来一次：`overview.ts` 的两条 `/v1/ontology/mappings` 腿（读+写）上收至
 * 属主 `services/ontology/ontologyMappingApi.ts` 导出的 `ONTOLOGY_MAPPINGS_BASE`，本域书写面再减
 * 2 条**在册收口项**（27−2=25），仍按面值筛除并自检恰 2 条。两批减法合计必须能对上 32→25，
 * 且剩余 11 条 KB 子族腿不许动——动了说明减法筛到了别的族。</p>
 *
 * <p>#145/B6-6 第三次同形减法，但**一批跨三族**：`wiki.ts` 的 glossary 族根声明 1 枚上收至
 * `services/glossary.ts`（导出 `ONTOLOGY_GLOSSARY_BASE`）、rag 直写腿 2 枚与 query 直写腿 1 枚上收至
 * `services/knowledgeQueryApi.ts` 的两枚导出常量 ⇒ 本域 `${API_BASE}` 面 25−4=**21**。因跨族，
 * 减法登记改成「面值 → 应扣条数」成对自检（1/2/1），不再用单值常量；扣错族或把子族一起筛走都会
 * 让总数仍为 21 而明细不对，故同时保留「KB 子族腿 11→8」这条边界自检。
 * 注意本门判据只看**本域书写面**：收口后 `` `${ONTOLOGY_GLOSSARY_BASE}/terms/${id}` `` 这类腿
 * 对 DERIVED_RE 隐形，"请求面一条没少"由 `src/__tests__/b6KnowledgeQueryRagGlossarySingleSource.spec.ts`
 * 按常量链解析另证（HEAD 16 条消费者腿逐字守恒）。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../../../services/httpClient';

const DOMAIN_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 本批改动的 5 文件（`govern.ts` 系 2026-10-10 W206/C188 已派生，不在本批派生点数内）。 */
const FILES = ['assets.ts', 'extract.ts', 'graph.ts', 'overview.ts', 'wiki.ts'] as const;

/** 域内全部生产文件（整域清零腿的扫描面；实测 9 个）。 */
const TS_FILES = readdirSync(DOMAIN_ROOT).filter((f) => f.endsWith('.ts'));

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

/** 把派生点还原成运行期字符串面值（`${…}` 插值按字面保留，改前后同一写法）。 */
function derivedValues(src: string): string[] {
  const out: string[] = [];
  for (const line of codeLines(src)) {
    for (const m of line.matchAll(DERIVED_RE)) out.push(`${API_BASE}${m[1]}`);
  }
  return out;
}

/**
 * 改前快照：以**带引号/带反引号形态**收录（与源码字面量同形，便于人工逐条核对），
 * 下面统一剥定界符得到运行期面值。来源＝`git show HEAD:` 五文件代码行实跑读出，
 * 按 assets(3) → extract(8) → graph(3) → overview(10) → wiki(8) 顺序。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  "'/api/v1/knowledge'",
  "`/api/v1/catalog/assets/${encodeURIComponent(assetId)}/auto-classify`",
  "`/api/v1/catalog/assets/${encodeURIComponent(assetId)}/auto-classify`",
  "'/api/v1/knowledge'",
  "`/api/v1/lineage/impact?startNode=${encodeURIComponent(startNode)}`",
  "'/api/v1/lineage/parse'",
  "'/api/v1/integration/metadata'",
  "'/api/v1/integration/logs'",
  "'/api/v1/integration/metadata/drift'",
  "'/api/v1/integration/metadata'",
  "`/api/v1/integration/metadata/drift${q}`",
  "'/api/v1/knowledge'",
  "`/api/v1/knowledge/graph/search?q=${encodeURIComponent(query)}`",
  "'/api/v1/knowledge/graph/path'",
  "'/api/v1/knowledge'",
  "'/api/v1/knowledge/stats'",
  "'/api/v1/knowledge/stats'",
  "'/api/v1/ontology/entity-mappings'",
  "'/api/v1/ontology/mappings'",
  "'/api/v1/ontology/mappings'",
  "'/api/v1/ontology/export'",
  "'/api/v1/knowledge/sync'",
  "'/api/v1/cognitive/config'",
  "'/api/v1/cognitive/config'",
  "'/api/v1/knowledge'",
  "'/api/v1/ontology/glossary'",
  "'/api/v1/knowledge/compliance-rules'",
  "'/api/v1/knowledge/rag'",
  "'/api/v1/knowledge/rag'",
  "'/api/v1/knowledge/query'",
  "`/api/v1/knowledge/nav/products?${params.toString()}`",
  "`/api/v1/knowledge/nav/categories?${params.toString()}`",
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

/**
 * #145/B6-1 在册收口项：五文件（assets/extract/graph/overview/wiki）各自的**族根声明**
 * `const KB_V1|GRAPH_BASE|KNOWLEDGE_BASE = '/api/v1/knowledge'` 已删掉、改成从
 * `api/knowledgeClient` 导入导出常量 ⇒ 这 5 条面值不再出现在本域 `${API_BASE}` 派生态面。
 * 判据按面值筛除后必须恰剩 27 条（32−5），多减/少减即红。
 */
const CONSOLIDATED_FAM_ROOT = '/api/v1/knowledge';
const EXPECTED_AFTER_B61 = PRE_BATCH_URL_LITERALS.filter((v) => v !== CONSOLIDATED_FAM_ROOT);

/**
 * #145/B6-4 在册收口项：本域 `overview.ts` 的两条 `` `${API_BASE}/v1/ontology/mappings` ``
 * （fetchOntologyMappings / saveOntologyMappings）改为 import 族单源 `ONTOLOGY_MAPPINGS_BASE`
 * 裸引用 ⇒ 这两枚面值**离开本域书写面**（族根声明只在属主 `services/ontology/ontologyMappingApi.ts`
 * 一处）。与 B6-1 同一减法形状：按面值筛除并断言条数恰 2（27−2=25），列错一条即红。
 * 「裸引用腿仍在原位、面值一字未改」不在本门证明范围，由 `src/__tests__/b6OntologyFamilySingleSource.spec.ts`
 * 按 HEAD 快照守恒承证（本门只证「本域派生态面没有偷偷少请求」）。
 */
const CONSOLIDATED_B64_FAM_ROOT = '/api/v1/ontology/mappings';
const EXPECTED_AFTER_B64 = EXPECTED_AFTER_B61.filter((v) => v !== CONSOLIDATED_B64_FAM_ROOT);

/**
 * #145/B6-6 在册收口项：本域 `wiki.ts` 的 4 枚 `` `${API_BASE}…` `` 站点上收至三族属主常量
 * （glossary 族根声明 1 → `services/glossary.ts` 的 `ONTOLOGY_GLOSSARY_BASE`；rag 直写腿 2 与
 * query 直写腿 1 → `services/knowledgeQueryApi.ts` 的两枚导出常量的裸引用）。减法形状与前两批不同：
 * **同一批跨三个族**，故按「面值 → 应扣条数」成对登记并逐值自检（1/2/1，合计 4），
 * 只写一个总数会让"扣错族"读成绿。25 − 4 = **21**（本批改后实跑读出）。
 * 「16 条消费者腿面值逐字未变」不在本门证明范围——本门只看**本域 `${API_BASE}` 派生态书写面**，
 * 常量化后的腿对 DERIVED_RE 隐形，由 `src/__tests__/b6KnowledgeQueryRagGlossarySingleSource.spec.ts`
 * 按常量链解析承证。</p>
 *
 * <p>#145/B6-12 第四次同形减法（单族单腿）：`overview.ts` 的 `exportOntology` 那条
 * `` `${API_BASE}/v1/ontology/export` `` 直写腿改引属主 `services/ontology/ontologyExportApi.ts`
 * 导出的 `ONTOLOGY_EXPORT_BASE` ⇒ 本域派生态面 21−1=**20**。减法仍按面值筛除并自检恰 1 条，
 * KB 子族 8 条边界自检不动（本批没碰 KB 族）。属主侧 8 条腿与另一生产方
 * `services/ontologyWorkbenchApi.ts` 的守恒，由 `src/__tests__/b6OntologyDataExportSingleSource.spec.ts`
 * 按 HEAD 快照承证——本门只看本域书写面。</p>
 */
const CONSOLIDATED_B66_VALUES: ReadonlyArray<{ val: string; legs: number }> = [
  { val: '/api/v1/ontology/glossary', legs: 1 },
  { val: `${CONSOLIDATED_FAM_ROOT}/rag`, legs: 2 },
  { val: `${CONSOLIDATED_FAM_ROOT}/query`, legs: 1 },
];
const EXPECTED_AFTER_B66 = EXPECTED_AFTER_B64.filter(
  (v) => !CONSOLIDATED_B66_VALUES.some((c) => c.val === v),
);

/**
 * #145/B6-12 第四次同形减法（单族单腿）：本域 `overview.ts` 的 `exportOntology` 那条
 * `` `${API_BASE}/v1/ontology/export` `` 直写腿，改为 import 属主
 * `services/ontology/ontologyExportApi.ts` 导出的 `ONTOLOGY_EXPORT_BASE` 裸引用 ⇒ 该面值离开本域
 * 书写面（族根声明只在属主一处）。减法形状与 B6-4 相同：按面值筛除并自检恰 1 条，21 − 1 = **20**。
 * 「裸引用腿面值一字未改」不在本门证明范围，由 `src/__tests__/b6OntologyDataExportSingleSource.spec.ts`
 * 按 HEAD 快照守恒承证。
 */
const CONSOLIDATED_B612_FAM_ROOT = '/api/v1/ontology/export';
const EXPECTED_AFTER_B612 = EXPECTED_AFTER_B66.filter((v) => v !== CONSOLIDATED_B612_FAM_ROOT);

describe('pages/knowledge/services 域基址单源 — 私设基址清零 (#143/B5-20)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('整域九文件代码行零私设基址（含本批未动的 govern/cognitiveAsset/kbFetch/knowledgeApi），注释里的端点说明不计', () => {
    expect(TS_FILES.length).toBe(9);
    for (const rel of TS_FILES) {
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

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），全域零 /api/api 双前缀', () => {
    for (const rel of TS_FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照减去 5 条 B6-1 + 2 条 B6-4 + 4 条 B6-6 + 1 条 B6-12 在册收口项（32−5−2−4−1=20，改后实跑读出）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(20);
    expect([...all].sort()).toEqual([...EXPECTED_AFTER_B612].sort());
  });

  it('快照自身可校验：32 条 = 25 单引号 + 7 反引号 + 0 双引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(PRE_BATCH_URL_LITERALS.length).toBe(32);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith("'")).length).toBe(25);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(7);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(0);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
    // B6-1 减法腿自检：筛掉的必须恰是 5 条族根（少筛/多筛都会让上一条腿假绿或假红）
    expect(PRE_BATCH_URL_LITERALS.filter((v) => v === CONSOLIDATED_FAM_ROOT).length).toBe(5);
    expect(EXPECTED_AFTER_B61.length).toBe(27);
    // B6-4 减法腿自检：筛掉的必须恰是 2 条 `/v1/ontology/mappings` 族根本体（overview.ts 读写各一条）
    expect(EXPECTED_AFTER_B61.filter((v) => v === CONSOLIDATED_B64_FAM_ROOT).length).toBe(2);
    expect(EXPECTED_AFTER_B64.length).toBe(25);
    // B6-6 减法腿自检：三个族各按登记条数扣（1/2/1），扣完必须恰剩 21 且这 4 枚面值在本域面里归零
    for (const { val, legs } of CONSOLIDATED_B66_VALUES) {
      expect(EXPECTED_AFTER_B64.filter((v) => v === val).length, `${val} 应扣条数不符`).toBe(legs);
      expect(EXPECTED_AFTER_B66.filter((v) => v === val), `${val} 不得还留在本域派生态面`).toEqual([]);
    }
    expect(CONSOLIDATED_B66_VALUES.reduce((n, c) => n + c.legs, 0)).toBe(4);
    expect(EXPECTED_AFTER_B66.length).toBe(21);
    // B6-12 减法腿自检：筛掉的必须恰是 1 条 `/v1/ontology/export` 族根本体（overview.ts exportOntology），
    // 且扣完该面值在本域面里归零——KB 子族计数不受影响（下一段照旧钉 8 条）
    expect(EXPECTED_AFTER_B66.filter((v) => v === CONSOLIDATED_B612_FAM_ROOT).length).toBe(1);
    expect(EXPECTED_AFTER_B612.filter((v) => v === CONSOLIDATED_B612_FAM_ROOT)).toEqual([]);
    expect(EXPECTED_AFTER_B612.length).toBe(20);
    // 减法后剩余面里仍有 8 条 KB **子族**腿（compliance-rules／stats×2／sync／graph×2／nav×2，
    // 即 11 条减去 B6-6 收走的 rag 2 + query 1），证明三批只动登记过的族，没顺手收别的族
    expect(EXPECTED_AFTER_B64.filter((v) => v.startsWith(`${CONSOLIDATED_FAM_ROOT}/`)).length).toBe(11);
    expect(EXPECTED_AFTER_B66.filter((v) => v.startsWith(`${CONSOLIDATED_FAM_ROOT}/`)).length).toBe(8);
  });

  it('判据能变红：违例源命中（含反引号形态）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "const KB_V1 = '/api/v1/knowledge';",
      'const RULES_BASE = "/api/v1/knowledge/compliance-rules";',
      'return apiFetchData(`/api/v1/lineage/parse`);',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE } from '../../../services/httpClient';",
      'const KB_V1 = `${API_BASE}/v1/knowledge`;',
      'return apiFetchData(`${API_BASE}/v1/lineage/parse`);',
      '  // 后端 KnowledgeController @RequestMapping("/api/v1/knowledge") 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual(['/api/v1/knowledge', '/api/v1/lineage/parse']);
  });
});
