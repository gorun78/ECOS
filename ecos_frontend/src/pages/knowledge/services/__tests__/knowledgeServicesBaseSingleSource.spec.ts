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

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照减去 5 条 B6-1 在册收口项（32−5=27，HEAD 实跑读出）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(27);
    expect([...all].sort()).toEqual([...EXPECTED_AFTER_B61].sort());
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
    // 减法后剩余面里仍有 11 条 KB **子族**腿（compliance-rules／rag／query／stats／sync／nav／graph），
    // 证明本批只动族根、没顺手收别的族（子族面一变即说明减法筛多了）
    expect(EXPECTED_AFTER_B61.filter((v) => v.startsWith(`${CONSOLIDATED_FAM_ROOT}/`)).length).toBe(11);
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
