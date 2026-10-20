// @vitest-environment node
/**
 * services/ontology/ 域基址单源回归门（#143/B5-17，差距台账 §13.2.4 C1 腿）。
 *
 * <p>本批把本体服务域（基线在册 8 文件）代码行的私设基址整体改由 `API_BASE` 派生。
 * 派生只有一种正确性可证：**运行期面值逐字不变**。故本门不断「长得对」，而是把每个
 * 派生点按 `API_BASE` 的实际值还原成字符串，与改前快照（17 条，2026-10-19 自 HEAD
 * 实跑读出，非手抄）做排序多重集比对——多一条、少一条、差一个斜杠都判红。</p>
 *
 * <p>判据口径必须与 `api-base-single-source` 一致地含**反引号**：本批初扫只按 `["']`
 * 数出 13 处，按 `["'`]` 复扫才见余下 4 处反引号内联字面量。只吃引号的"清零"是假绿，
 * 故下方 DEFECT 样本专门留一条反引号形态供反向自检。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../../httpClient';

const DOMAIN_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 本批改动的 8 文件（`ontologyExportApi.ts` 系 #143/B5-11f 已派生，不在本批派生点数内）。 */
const FILES = [
  'ontologyDataApi.ts',
  'ontologyDomainApi.ts',
  'ontologyEngineApi.ts',
  'ontologyLineageApi.ts',
  'ontologyMappingApi.ts',
  'ontologyModelApi.ts',
  'ontologyProposalApi.ts',
  'ontologyVersionApi.ts',
] as const;

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
 * 下面统一剥定界符得到运行期面值。来源＝`git show HEAD:` 八文件代码行实跑读出。
 */
const RAW_PRE_BATCH_URL_LITERALS: readonly string[] = [
  '"/api/v1/ontology/data"',
  '"/api/v1/ontology/functions"',
  '"/api/v1/ontology/domains"',
  '"/api/v1/ontology/domains"',
  '`/api/v1/ontology/domains/${encodeURIComponent(code)}`',
  '`/api/v1/ontology/domains/${encodeURIComponent(code)}`',
  '`/api/v1/ontology/objects/${encodeURIComponent(objectId)}/domain`',
  '"/api/v1/engine/ontology"',
  '"/api/v1/lineage"',
  '"/api/v1/ontology/mappings"',
  '"/api/v1/engine/data/layers"',
  '"/api/v1/datanet/metadata/fields"',
  '"/api/v1/ontology/action-types"',
  '"/api/v1/ecos"',
  '"/api/v1/ontology/proposals"',
  '"/api/v1/ecos/versions"',
  '`/api/v1/ontology/versions/diff?v1=${encodeURIComponent(v1Id)}&v2=${encodeURIComponent(v2Id)}`',
];

const PRE_BATCH_URL_LITERALS: readonly string[] = RAW_PRE_BATCH_URL_LITERALS.map((s) => s.slice(1, -1));

/**
 * 后续批**净新增**到本域的 `` `${API_BASE}` `` 属主声明台账（只增需登记，不登记即红）。
 *
 * <p>#145/B6-10 起有 1 条：`/v1/ecos/ontologies` 族根单源上收进 `ontologyModelApi.ts`。
 * 同一批的 `ECOS_VERSIONS_BASE` **不进这本账**——`git show HEAD` 实读该声明位改前就是
 * `const VERSION_BASE = \`\${API_BASE}/v1/ecos/versions\`;`（第 18 行），本批只改名并 export，
 * 面值原本就在 17 条快照里 ⇒ 计数不变。ontologies 那枚则不同：改前本文件只有 `import { API_BASE }`
 * 与 `const BASE = \`\${API_BASE}/v1/ecos\`` 两处派生位，五枚 ontologies 腿一律写
 * `\`\${BASE}/ontologies…\``（私有 BASE 派生态，不在 `DERIVED_RE` 面）⇒ 族根面值
 * `/api/v1/ecos/ontologies` 改前在本域**不存在**，属主上收是净新增声明位。
 * 故快照必须保持 17 条**逐字是改前面值**，新增位单列，两类判据各自成立。</p>
 */
const OWNER_DECL_ADDITIONS: ReadonlyArray<{
  batch: string;
  file: string;
  name: string;
  value: string;
  why: string;
}> = [
  {
    batch: '#145/B6-10',
    file: 'ontologyModelApi.ts',
    name: 'ECOS_ONTOLOGIES_BASE',
    value: '/api/v1/ecos/ontologies',
    why: '族两属主分在两个页面侧文件（OntologyDomainPanel 两枚私有常量 + BusinessWorkbenchLayout 取数腿），改前本域该面值只以 `${BASE}` 私有派生态存在 ⇒ 页面反向引页面不可行，属主按 import 图落服务侧正典实现文件，声明位净新增',
  },
];

describe('services/ontology 域基址单源 — 私设基址清零 (#143/B5-17)', () => {
  it('前提：API_BASE 单源取值 = "/api"（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
  });

  it('整域九文件代码行零私设基址（含已派生的 exportApi），注释里的端点说明不计', () => {
    const tsFiles = readdirSync(DOMAIN_ROOT).filter((f) => f.endsWith('.ts'));
    expect(tsFiles.length).toBe(9);
    for (const rel of tsFiles) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('八文件一律从 httpClient 导入 API_BASE（第二处来源即红）', () => {
    for (const rel of FILES) {
      const imports = codeLines(read(rel)).filter((l) => /\bAPI_BASE\b/.test(l) && l.trimStart().startsWith('import '));
      expect(imports.length, `${rel} 应有且仅有 1 条 API_BASE 导入`).toBe(1);
      expect(imports[0]).toContain('from "../httpClient"');
    }
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /（防漏段），全域零 /api/api 双前缀', () => {
    for (const rel of FILES) {
      const src = read(rel);
      const bad = codeLines(src).filter((l) => /`\$\{API_BASE\}(?!\/)/.test(l));
      expect(bad, `${rel} 存在 \`\${API_BASE}\` 后不接斜杠的派生`).toEqual([]);
      expect(src).not.toMatch(/\/api\/api\b/);
    }
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照 + 后续批属主声明台账（17 + 1 条）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(18);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS, ...OWNER_DECL_ADDITIONS.map((o) => o.value)].sort());
  });

  it('#145/B6-10 属主声明台账：本域新增的 ${API_BASE} 声明位逐条登记且面值可追到属主常量', () => {
    // 快照 17 条是 **#143/B5-17 改前**实跑读出的消费腿面值，代表「派生不该改变运行期面值」这条不变式。
    // 后续 B6 批把某个族的**属主常量上收进本域文件**时，声明位可能是**净新增**（不是搬运），
    // 混进快照会让「改前根本不存在这条腿」失真；单列台账才能同时保住两条判据：
    //   ① 快照 17 条逐字不动（谁把消费腿面值改了即红）；
    //   ② 新增声明位逐条登记（谁再加一枚未登记的 `${API_BASE}` 声明即红，防属主面悄悄膨胀）。
    expect(OWNER_DECL_ADDITIONS.length).toBe(1);
    for (const o of OWNER_DECL_ADDITIONS) {
      const src = read(o.file);
      expect(derivedValues(src), `${o.file} 找不到登记的属主声明面值`).toContain(o.value);
      // 声明形状用**字符串拼接**比对（不用 RegExp：反引号/`${` 的正则转义在本类文件里极易写出语法洞）
      const declNeedle = 'export const ' + o.name + ' = `' + '${API_BASE}' + o.value.slice('/api'.length) + '`;';
      expect(src, `${o.file} 的 ${o.name} 声明形状异常（必须写 API_BASE 派生态、不得回退裸字面量）`).toContain(declNeedle);
      expect(FILES).toContain(o.file);
    }
    // 反向自检：判据本身能变红（防台账退化成空数组时的恒真绿）。
    expect(derivedValues(read('ontologyModelApi.ts'))).not.toContain('/api/v1/fixture/not-declared');
    expect(OWNER_DECL_ADDITIONS.every((o) => o.value.startsWith('/api/'))).toBe(true);
  });

  it('快照自身可校验：17 条 = 13 引号 + 4 反引号且全部以 /api/ 开头（防快照清空成恒真）', () => {
    expect(PRE_BATCH_URL_LITERALS.length).toBe(17);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('"')).length).toBe(13);
    expect(RAW_PRE_BATCH_URL_LITERALS.filter((s) => s.startsWith('`')).length).toBe(4);
    for (const v of PRE_BATCH_URL_LITERALS) expect(v.startsWith('/api/')).toBe(true);
  });

  it('判据能变红：违例源命中（含反引号形态）、合规源不命中（防恒绿）', () => {
    const DEFECT = [
      'const MAPPING_BASE = "/api/v1/ontology/mappings";',
      'const ONTOLOGY_ENGINE_BASE = \'/api/v1/engine/ontology\';',
      'return apiFetchData(`/api/v1/lineage`);',
    ].join('\n');
    const CLEAN = [
      'import { API_BASE } from "../httpClient";',
      'const MAPPING_BASE = `${API_BASE}/v1/ontology/mappings`;',
      'return apiFetchData(`${API_BASE}/v1/lineage/${encodeURIComponent(id)}`);',
      '// 后端 @RequestMapping("/api/v1/ontology") 只是注释里的端点说明',
    ].join('\n');
    expect(codeLines(DEFECT).filter((l) => HARDCODED_RE.test(l)).length).toBe(3);
    expect(codeLines(CLEAN).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(derivedValues(DEFECT)).toEqual([]);
    expect(derivedValues(CLEAN)).toEqual([
      '/api/v1/ontology/mappings',
      '/api/v1/lineage/${encodeURIComponent(id)}',
    ]);
  });
});
