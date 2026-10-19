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

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（17 条，HEAD 实跑读出）', () => {
    const all: string[] = [];
    for (const rel of FILES) all.push(...derivedValues(read(rel)));
    expect(all.length).toBe(17);
    expect([...all].sort()).toEqual([...PRE_BATCH_URL_LITERALS].sort());
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
      'const ENGINE_BASE = \'/api/v1/engine/ontology\';',
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
