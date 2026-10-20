// @vitest-environment node
/**
 * b6DatanetMetadataSingleSource.spec.ts — 重复族 `/v1/datanet/metadata` 单源化取证 (#145/B6-8)
 *
 * <p>差距台账 §13.2.4 C1 腿的 B6 存量债第八批。改前 `urlFamilyDualFormRatchet` 的 BASELINE 里有两枚
 * datanet 元数据族：`/v1/datanet/metadata`（2 属主：`api/dataClient.ts` 的 `DATANET_META` ＋
 * `services/dataCatalogClient.ts` 的 `DATANET_BASE`）与 `/v1/datanet/metadata/preview/{}?limit={}`
 * （2 属主：`pages/data-workbench/api/metadata.ts` ＋ `services/datanetMetadataApi.ts`）。现由
 * `api/dataClient.ts` 导出一枚 `DATANET_METADATA_BASE` 单源持有（B6-1 判例：族根常量属主＝api 层域客户端），
 * 另两文件改成裸名引用。</p>
 *
 * <p>**属主为什么不能落 `services/dataCatalogClient.ts`**：该文件本来就值导入 `../api` 门面取
 * `apiFetchData`，而 `src/api.ts` 又 re-export `api/dataClient.ts`。单源常量若放服务侧、让 api 层反向引它，
 * 即成 `api.ts → dataClient → dataCatalogClient → api.ts` 循环。导入方向只能是 services/页面 → api。</p>
 *
 * <p>**preview 族只动一腿即闭合（本批口径必须说清，防后继误读成"漏收"）**：页面门面 `metadata.ts:235`
 * 一字未动 ⇒ 改后它是该族**唯一**属主，从 dupBoth 面退出但**仍留在族索引里**，再冒出第二个裸面写手照样变红。
 * 页面门面既不升属主、也不为收口新增一条 页面→api 值导入：`state-boundary` 门把页面对 api/services 的
 * 值导入逐文件登记在册，无必要不扩容；它自己的 10 条 datanet 腿仍由 #143/B5-21 域门 27 条多重集守恒。</p>
 *
 * <p>**三处"看着像漏收"的形状是刻意选择，逐条落判据**：① 属主 `dataClient.ts:66` 的
 * `${API_BASE}/v1/datanet/metadata/resources/${id}` 保持裸派生态，故属主文件内部混用单源常量与裸面**不是遗漏**
 * ——B6 只按 dupBoth 属主数记账，动一枚在册的单属主裸面＝把可见性关掉而不减债（B6-1 判例）；
 * ② `…/resources/all` 一族改前有 `datanetMetadataApi.ts:27` 与 `dataCatalogClient.ts:55` 两写手，
 * 后者早已是 `${DATANET_BASE}` 派生态（对族面口径隐形），本批把前者上收后该族在生产面**整体退出索引**
 * ——这与 B6-2/B6-6 登记过的"族面下降＝可见性变化非新增重复"同形， dupBoth 的账不受影响；
 * ③ 族根本体的构造点判据必须带**族边界**负向前查，否则 `/v1/datanet/metadata/preview` 这类族内腿
 * 会被误吃成族根第二构造方＝本批第一条腿假红，末腿已用 fixture 把该误吃钉成"能变红"。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { DATANET_METADATA_BASE } from '../api/dataClient';
import { KNOWLEDGE_BASE } from '../api/knowledgeClient';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const CANONICAL = 'services/httpClient.ts';

/** 族根面值：改前由各属主私设常量各自拼出，运行期与本单源逐字相同。 */
const FAM = '/api/v1/datanet/metadata';
const OWNER = 'api/dataClient.ts';

/** 单源导入腿判据按解析后的说明符比（两消费文件各自的引号风格不同，风格不属于本批债）。 */
const IMPORT_RE = /^import \{ DATANET_METADATA_BASE \} from ['"]\.\.\/api\/dataClient['"];/;

const CONSUMERS = [
  { rel: 'services/dataCatalogClient.ts' },
  { rel: 'services/datanetMetadataApi.ts' },
] as const;

/** 页面门面文件：本批刻意未动，但它必须仍是 preview 族的唯一在册裸面属主（见「可见性」腿）。 */
const PAGE_FACADE = 'pages/data-workbench/api/metadata.ts';

/**
 * 改前 5 条面值快照（2026-10-20 自 `git show HEAD:` 逐行读出，非手抄；面值即改前各文件拼出的串）：
 * `dataCatalogClient.ts` 3（`DATANET_BASE` 消费腿）＋ `datanetMetadataApi.ts` 2（裸派生态）。
 * 页面门面 `metadata.ts` 的 10 条本批一字未动，由 #143/B5-21 域门 27 条多重集继续守恒。
 */
const HEAD_FACES: Record<string, readonly string[]> = {
  'services/dataCatalogClient.ts': [
    '/api/v1/datanet/metadata/resources/all',
    '/api/v1/datanet/metadata/fields/${resourceId}',
    '/api/v1/datanet/metadata/preview/${resourceId}?limit=${limit}',
  ],
  'services/datanetMetadataApi.ts': [
    '/api/v1/datanet/metadata/resources/all',
    '/api/v1/datanet/metadata/preview/${resourceId}?limit=${limit}',
  ],
};

/** 与棘轮门同口径的注释/测试文件判据（面必须同一，否则两门对同一面各说各话）。 */
function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}
function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}
function codeLines(src: string): string[] {
  return src.split('\n').filter((l) => !isComment(l));
}
function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, rel), 'utf8');
}
function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}
const relOf = (abs: string): string => relative(SRC_ROOT, abs).split(sep).join('/');

/** 单源常量消费腿还原成运行期面值（`${…}` 插值按字面保留，与改前同一写法）。 */
function baseFaces(src: string): string[] {
  const re = /`\$\{DATANET_METADATA_BASE\}((?:\\.|(?!`)[^\\])*)`/g;
  const out: string[] = [];
  for (const line of codeLines(src)) for (const m of line.matchAll(re)) out.push(`${FAM}${m[1]}`);
  return out;
}

/** 裸派生态消费腿（本批刻意未动的在册单属主腿）同样还原，供面值多重集比对。 */
function apiBaseFaces(src: string): string[] {
  const re = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;
  const out: string[] = [];
  for (const line of codeLines(src)) for (const m of line.matchAll(re)) out.push(`${API_BASE}${m[1]}`);
  return out.filter((v) => v.startsWith('/api/v1/datanet/metadata'));
}

/** 族根本体构造点：`${API_BASE}/v1/datanet/metadata` 且紧跟族边界（不吃族内更细的腿）。 */
function familyRootBuilders(src: string): number {
  const re = /`\$\{API_BASE\}\/v1\/datanet\/metadata(?!(?:[A-Za-z0-9_/-]))/g;
  let n = 0;
  for (const line of codeLines(src)) n += (line.match(re) ?? []).length;
  return n;
}

const BASE_DECL_RE = /(?:export\s+)?const\s+([A-Z0-9_]+)\s*=\s*`?\$\{([A-Z0-9_]+)\}(\/v1\/[^`'"]*)/;

describe('b6DatanetMetadataSingleSource — 重复族 /v1/datanet/metadata 单源化取证 (#145/B6-8)', () => {
  it('前提：单源常量的运行期面值 = 改前各属主私设基址的面值（派生化即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(DATANET_METADATA_BASE).toBe(FAM);
    // 同一 API_BASE 上另一枚在册族根未被本批波及
    expect(KNOWLEDGE_BASE).toBe('/api/v1/knowledge');
  });

  it('属主唯一：全 src 生产面构造族根本体的文件只有 api/dataClient.ts 一个、1 条，且是导出常量', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const builders = new Map<string, number>();
    for (const abs of files) {
      const rel = relOf(abs);
      if (rel === CANONICAL || isTestFile(rel)) continue;
      const n = familyRootBuilders(readFileSync(abs, 'utf8'));
      if (n > 0) builders.set(rel, n);
    }
    expect([...builders.keys()].sort(), `族 /v1/datanet/metadata 仍有 ${builders.size} 个构造方`).toEqual([OWNER]);
    expect(builders.get(OWNER)).toBe(1);
    const decl = codeLines(read(OWNER)).filter((l) => l.trim() === 'export const DATANET_METADATA_BASE = `${API_BASE}/v1/datanet/metadata`;');
    expect(decl.length).toBe(1);
    // 属主自己的消费腿必须走单源常量（写回裸面即本批判据漏网）
    expect(baseFaces(read(OWNER)).length, '属主内部 6 条 datanet 腿未全走单源常量').toBe(6);
  });

  it('消费方两文件：各恰 1 条单源导入且零私设本族基址、零未用 API_BASE', () => {
    for (const { rel } of CONSUMERS) {
      const src = read(rel);
      const lines = codeLines(src);
      const imports = lines.filter((l) => /^\s*import\s.*\bDATANET_METADATA_BASE\b/.test(l)).map((l) => l.trim());
      expect(imports.length, `${rel} 应有且仅有 1 条单源导入`).toBe(1);
      expect(IMPORT_RE.test(imports[0]), `${rel} 导入腿不是单源那一跳: ${imports[0]}`).toBe(true);
      const privateBase = lines.filter((l) => {
        const m = l.match(BASE_DECL_RE);
        return m !== null && m[2] === 'API_BASE' && m[3] === '/v1/datanet/metadata';
      });
      expect(privateBase, `${rel} 不得再私设本族基址`).toEqual([]);
      // API_BASE 用途的唯一合法形态＝该文件仍在册的裸面腿；用途归零则连导入也必须一起清
      const usesApiBase = lines.filter((l) => /\bAPI_BASE\b/.test(l) && !l.trim().startsWith('import '));
      const bareLegs = apiBaseFaces(src).length;
      expect(usesApiBase.length, `${rel} 的 API_BASE 用途数须等于仍在册的裸面腿数`).toBe(bareLegs);
      if (bareLegs === 0) {
        expect(lines.filter((l) => /API_BASE/.test(l)), `${rel} 零用途却残留 API_BASE（导入未清即未用死导入）`).toEqual([]);
      }
    }
  });

  it('面值逐字不变：两消费文件还原出的 datanet 面值多重集 = 改前 5 条快照（多一条少一条都红）', () => {
    const got: string[] = [];
    for (const { rel } of CONSUMERS) {
      const src = read(rel);
      got.push(...baseFaces(src), ...apiBaseFaces(src));
    }
    const head = Object.values(HEAD_FACES).flat();
    expect(got.length, `datanet 面值点位数变了（改前 ${head.length}）`).toBe(head.length);
    expect([...got].sort()).toEqual([...head].sort());
    // 逐文件分箱：减法只按文件核对，防止"总数对得上但腿换了文件"
    for (const { rel } of CONSUMERS) {
      const src = read(rel);
      expect([...baseFaces(src), ...apiBaseFaces(src)].sort(), `${rel} 面值分箱漂移`).toEqual([...HEAD_FACES[rel]].sort());
    }
  });

  it('可见性：preview 族与 resources 族改后各有 1 枚在册裸面属主（族退出 dupBoth 不等于族从口径消失）', () => {
    // 页面门面这 10 条本批一字未动，preview 族靠它当唯一属主留在族索引里
    const facade = apiBaseFaces(read(PAGE_FACADE));
    expect(facade.length).toBe(10);
    expect(facade.filter((v) => v === '/api/v1/datanet/metadata/preview/${encodeURIComponent(resourceId)}?limit=${limit}')).toEqual([
      '/api/v1/datanet/metadata/preview/${encodeURIComponent(resourceId)}?limit=${limit}',
    ]);
    expect(facade.filter((v) => v === '/api/v1/datanet/metadata/resources/${encodeURIComponent(datasourceId)}?_=${Date.now()}')).toHaveLength(1);
    // 属主文件里的裸 `${API_BASE}` 面必须恰是两枚：在册单属主裸面腿（resources/{id}，:66）＋ 族根声明
    // 本身（:302）——按源码文档序读出的就是这个次序。前者还在＝族面没被本批关掉，是本腿真正要证的可见性
    expect(apiBaseFaces(read(OWNER)), '属主裸面腿形状变了（声明腿或在册腿被动过）').toEqual([
      '/api/v1/datanet/metadata/resources/${active.datasourceId}',
      FAM,
    ]);
  });

  it('棘轮在册性：两枚 datanet 族已随本批从 urlFamilyDualFormRatchet 的 BASELINE 剪除（不留陈旧豁免）', () => {
    const ratchet = read('__tests__/urlFamilyDualFormRatchet.test.ts');
    const rows = [...ratchet.matchAll(/^ {2}'([^']+)':\s*\[/gm)].map((m) => m[1]);
    expect(rows.length, 'BASELINE 行集解析为空 ⇒ contains 类探针在此恒真，本腿失去意义').toBeGreaterThan(0);
    const probe = (name: string) => ratchet.includes(`'${name}': [`);
    expect(probe(rows[0]), '同形探针连在册行都判不出 ⇒ 探针形状与 BASELINE 书写形状已脱钩').toBe(true);
    const dupPin = Number((ratchet.match(/expect\(dupBoth\.size\)\.toBe\((\d+)\)/) ?? [])[1]);
    const entriesPin = Number((ratchet.match(/expect\(entries\.length\)\.toBe\((\d+)\)/) ?? [])[1]);
    expect(Number.isInteger(dupPin) && Number.isInteger(entriesPin), '棘轮源码里两处规模定点形状变了').toBe(true);
    expect(rows.length).toBe(dupPin);
    expect(entriesPin).toBe(dupPin);
    expect(rows.filter((r) => r.startsWith('/v1/datanet/metadata'))).toEqual([]);
  });

  it('判据能变红：私设基址、族根第二写手、面值漂移、边界误吃各造违例且必被对应腿吃出（防恒绿）', () => {
    const DEFECT = [
      "import { API_BASE } from './httpClient';",
      'const DATANET_BASE = `${API_BASE}/v1/datanet/metadata`;',
      'const url = `${DATANET_METADATA_BASE}/preview/${id}`;',
    ].join('\n');
    const lines = codeLines(DEFECT);
    expect(lines.filter((l) => {
      const m = l.match(BASE_DECL_RE);
      return m !== null && m[2] === 'API_BASE' && m[3] === '/v1/datanet/metadata';
    }).length, '私设基址判据吃不出违例').toBe(1);
    expect(familyRootBuilders(DEFECT), '族根第二写手判据吃不出违例').toBe(1);
    // 面值漂移：`/metadata/` 写成 `/meta/` 必须还原成另一枚面值 ⇒ 快照腿即红
    expect(baseFaces('const u = `${DATANET_METADATA_BASE}/meta/${id}`;')).toEqual([`${FAM}/meta/\${id}`]);
    // 无插值消费腿（单写常量）也必须落进还原面，否则「属主只声明不消费」或裸引用漏判
    expect(baseFaces('const u = `${DATANET_METADATA_BASE}`;')).toEqual([FAM]);
    // 属主声明行本身不被消费腿判据误吃（它用的是 API_BASE 而非族常量）
    expect(baseFaces('export const DATANET_METADATA_BASE = `${API_BASE}/v1/datanet/metadata`;')).toEqual([]);
    // 族边界负向前查：族内更细的腿不是族根构造点（吃进即本批第一条腿假红）
    expect(familyRootBuilders('const u = `${API_BASE}/v1/datanet/metadata/preview/1`;')).toBe(0);
    expect(familyRootBuilders('const u = `${API_BASE}/v1/datanet/metadataX`;')).toBe(0);
    // 注释面不吃（与棘轮同口径）
    expect(familyRootBuilders('// const u = `${API_BASE}/v1/datanet/metadata`;')).toBe(0);
    expect(apiBaseFaces("  const u = `${API_BASE}/v1/datanet/metadata/collect-async/${id}`;")).toEqual([
      '/api/v1/datanet/metadata/collect-async/${id}',
    ]);
    expect(apiBaseFaces("  const u = `${API_BASE}/v1/other/thing`;")).toEqual([]);
  });
});
