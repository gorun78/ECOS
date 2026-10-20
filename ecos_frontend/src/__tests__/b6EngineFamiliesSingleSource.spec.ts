// @vitest-environment node
/**
 * b6EngineFamiliesSingleSource.spec.ts — 引擎域三枚重复族单源化取证 (#145/B6-9)
 *
 * <p>差距台账 §13.2.4 C1 腿的 B6 存量债第九批。改前 `urlFamilyDualFormRatchet` 的 BASELINE 在册三族：
 * `/v1/engine/cognitive`（2 属主：`services/cognitiveClient.ts` 的 `COGNITIVE_ENGINE_BASE` ＋
 * `pages/MonitoringCenter.tsx` 的 `ENGINES` 行内腿）、`/v1/engine/ontology`（2 属主：
 * `services/ontology/ontologyEngineApi.ts` 的**私有** `ENGINE_BASE` ＋ 同页 `ENGINES` 行内腿）、
 * `/v1/engine/data/settings`（2 属主：`pages/EngineMonitor.tsx` 的 `ENGINE_META.data.configPath` ＋
 * `pages/data-workbench/DataEngineConfigPanel.tsx` 的批量保存腿）。现每族恰 1 枚属主常量持有族根体，
 * 另侧改成引用 ⇒ dupBoth 15 → 12。</p>
 *
 * <p>**三族属主落位不是同一套规则**（本文件按实测形状逐条钉，防后继批把某一族的判据当通则）：
 * ① cognitive/ontology 的属主就是**原属主之一**且已在分层底部（services 域客户端），页面经
 * `services/cognitiveClient` 直取、经门面 `services/ontologyApi` 取值——门面那行 `export *` 不含 URL 体
 * ⇒ **门面不是第二属主**（#145/B6-5 ② 别名重导出同款）；② settings 两属主分属**两个页面**，谁当属主
 * 都要让另一个页面反向引页面（分层倒置），且改前本域根本没有该面值 ⇒ 单源只能落到 `api/dataClient.ts`，
 * 声明位**净新增** 1 枚（与 #145/B6-8 的"改名升导出、位上不动"是两种形状，账要分开记）。</p>
 *
 * <p>**只收族根、子路径不并进常量**：`/settings/groups`、`/settings/defaults`、`/settings/refresh`
 * 各只有 DataEngineConfigPanel 一个生产方、从来不在重复面；合并只会让三枚族体对棘轮 `familiesOf` 隐形
 * 而不减少任何债（#145/B6-1 文件头 ② / #145/B6-3 ③ 判例）。同理 MonitoringCenter 其余四枚引擎腿
 * （security/data/knowledge/ai）原样保持 `${API_BASE}`。后果：本批 dupBoth 降 3，但**族索引面 253 不降**。</p>
 *
 * <p>面值快照 42 条系 2026-10-20 从 `git show HEAD:` 解出的同一棵树逐行读出（非手抄），另 1 枚
 * `DATA_ENGINE_SETTINGS_BASE` 声明按净新增单列在 `NEW_DECL_FACES`——把净新增混进"改前快照"会让
 * 一条改前不存在的腿看着像守恒，把快照剪短则会让"少一条腿"看着像进度（#145/B6-7 同一纪律）。
 * 落点在快照里写成**空箱** `'…settings|api/dataClient.ts': []`：改前该文件对该族零面是事实，
 * 净新增只由 `netNewDecl` 这一条受名字归属的加项接住，谁再加第二枚未登记声明即红。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { COGNITIVE_ENGINE_BASE } from '../services/cognitiveClient';
import { ONTOLOGY_ENGINE_BASE } from '../services/ontology/ontologyEngineApi';
import { DATA_ENGINE_SETTINGS_BASE } from '../api/dataClient';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const CANONICAL = 'services/httpClient.ts';

const esc = (s: string): string => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** 三族族根面值：改前由各属主各自拼出，运行期与单源常量逐字相同。 */
const FAMS = [
  {
    body: '/api/v1/engine/ontology',
    constName: 'ONTOLOGY_ENGINE_BASE',
    owner: 'services/ontology/ontologyEngineApi.ts',
    netNewDecl: false,
    legsInOwner: 25,
  },
  {
    body: '/api/v1/engine/cognitive',
    constName: 'COGNITIVE_ENGINE_BASE',
    owner: 'services/cognitiveClient.ts',
    netNewDecl: false,
    legsInOwner: 8,
  },
  {
    body: '/api/v1/engine/data/settings',
    constName: 'DATA_ENGINE_SETTINGS_BASE',
    owner: 'api/dataClient.ts',
    netNewDecl: true,
    legsInOwner: 0,
  },
] as const;

/** 消费/属主文件全集（本批动过的 + 该族其余在册腿主，快照按 `族|文件` 分箱）。 */
const HEAD_FACES: Record<string, readonly string[]> = {
  '/api/v1/engine/ontology|services/ontology/ontologyEngineApi.ts': [
    '/api/v1/engine/ontology',
    '/api/v1/engine/ontology/health',
    '/api/v1/engine/ontology/status',
    '/api/v1/engine/ontology/start',
    '/api/v1/engine/ontology/stop',
    '/api/v1/engine/ontology/settings',
    '/api/v1/engine/ontology/settings/defaults',
    '/api/v1/engine/ontology/settings',
    '/api/v1/engine/ontology/settings/refresh',
    '/api/v1/engine/ontology/workflow/definitions?pageSize=${pageSize}',
    '/api/v1/engine/ontology/workflow/definitions',
    '/api/v1/engine/ontology/workflow/instances?limit=${limit}',
    '/api/v1/engine/ontology/workflow/instances',
    '/api/v1/engine/ontology/workflow/instances/${id}',
    '/api/v1/engine/ontology/workflow/instances/${id}/approve',
    '/api/v1/engine/ontology/workflow/instances/${id}/reject',
    '/api/v1/engine/ontology/graph/${ontologyId}',
    '/api/v1/engine/ontology/graph/full',
    '/api/v1/engine/ontology/graph/trace/${nodeId}',
    '/api/v1/engine/ontology/copilot/entity',
    '/api/v1/engine/ontology/copilot/relation',
    '/api/v1/engine/ontology/copilot/validate',
    '/api/v1/engine/ontology/copilot/import',
    '/api/v1/engine/ontology/git/commit/${ontologyId}',
    '/api/v1/engine/ontology/git/pull/${ontologyId}',
    '/api/v1/engine/ontology/git/load',
  ],
  '/api/v1/engine/ontology|pages/MonitoringCenter.tsx': ['/api/v1/engine/ontology'],
  '/api/v1/engine/cognitive|services/cognitiveClient.ts': ['/api/v1/engine/cognitive'],
  '/api/v1/engine/cognitive|services/cognitiveEngineApi.ts': [
    '/api/v1/engine/cognitive/health',
    '/api/v1/engine/cognitive/status',
    '/api/v1/engine/cognitive/start',
    '/api/v1/engine/cognitive/stop',
    '/api/v1/engine/cognitive/config',
    '/api/v1/engine/cognitive/config',
    '/api/v1/engine/cognitive/start',
    '/api/v1/engine/cognitive/start',
  ],
  '/api/v1/engine/cognitive|pages/MonitoringCenter.tsx': ['/api/v1/engine/cognitive'],
  '/api/v1/engine/data/settings|api/dataClient.ts': [],
  '/api/v1/engine/data/settings|pages/EngineMonitor.tsx': ['/api/v1/engine/data/settings'],
  '/api/v1/engine/data/settings|pages/data-workbench/DataEngineConfigPanel.tsx': [
    '/api/v1/engine/data/settings/groups',
    '/api/v1/engine/data/settings/defaults',
    '/api/v1/engine/data/settings',
    '/api/v1/engine/data/settings/refresh',
  ],
};

/** 本批净新增的属主声明面值（改前面值为**不存在**，故不入 HEAD 快照，单列归属）。 */
const NEW_DECL_FACES: readonly { body: string; file: string; name: string }[] = [
  { body: '/api/v1/engine/data/settings', file: 'api/dataClient.ts', name: 'DATA_ENGINE_SETTINGS_BASE' },
];

/** 纯 re-export 门面：必须 0 面（门面行不含 URL 体 ⇒ 不是第二属主），但仍要真指到属主模块。 */
const FACADES = [
  { rel: 'services/ontologyApi.ts', ownerModule: './ontology/ontologyEngineApi' },
  { rel: 'api.ts', ownerModule: './api/dataClient' },
] as const;

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

/** 本族是否在面值作用域内：族根本身或族内更细的腿（排除 `/engine/ontologyX` 这类前缀假命中）。 */
const inFamily = (v: string, body: string): boolean => v === body || v.startsWith(`${body}/`);

/** `${API_BASE}/…` 还原面值。 */
function apiBaseFaces(src: string, body: string): string[] {
  const re = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;
  const out: string[] = [];
  for (const line of codeLines(src)) for (const m of line.matchAll(re)) {
    const v = `${API_BASE}${m[1]}`;
    if (inFamily(v, body)) out.push(v);
  }
  return out;
}

/** `${属主常量}/…` 消费腿还原面值（插值段按字面保留，与改前同一写法）。 */
function constLegFaces(src: string, constName: string, body: string): string[] {
  const re = new RegExp('`\\$\\{' + constName + '\\}((?:\\\\.|(?!`)[^\\\\])*)`', 'g');
  const out: string[] = [];
  for (const line of codeLines(src)) for (const m of line.matchAll(re)) out.push(`${body}${m[1]}`);
  return out;
}

/**
 * 裸名引用腿（`apiBase: ONTOLOGY_ENGINE_BASE`、`configPath: DATA_ENGINE_SETTINGS_BASE` 这类不带模板的
 * 取值）＝族根面值本身。剔除三类噪声：import 行、属主声明行、已在 `constLegFaces` 计过的模板行
 * ⇒ 三条各有实测出处，缺一条就漏计或多计。
 */
function bareUseFaces(src: string, constName: string, body: string): string[] {
  const nameRe = new RegExp('(^|[^A-Za-z0-9_.$])' + constName + '\\b');
  const tmplRe = new RegExp('\\$\\{' + constName + '\\}');
  const declRe = new RegExp('const\\s+' + constName + '\\s*=');
  const out: string[] = [];
  for (const line of codeLines(src)) {
    if (line.trim().startsWith('import ')) continue;
    if (declRe.test(line)) continue;
    if (tmplRe.test(line)) continue;
    if (nameRe.test(line)) out.push(body);
  }
  return out;
}

const allFaces = (src: string, fam: (typeof FAMS)[number]): string[] => [
  ...apiBaseFaces(src, fam.body),
  ...constLegFaces(src, fam.constName, fam.body),
  ...bareUseFaces(src, fam.constName, fam.body),
];

/**
 * 族根本体构造点：`${API_BASE}/<族根后缀>` 且紧跟族边界（族内更细的腿与前缀撞名都不吃）。
 *
 * <p>入参是**运行期面值**（带 `/api`），正则吃的是**源码形态**（`${API_BASE}` 之后的后缀段），
 * 二者差一个 `API_BASE` 前缀——这一步只用 `slice` 剥前缀，且 `API_BASE === '/api'` 已被前提腿钉死。
 * 本函数初版直接把面值拼进正则 ⇒ 恒 0 命中，"属主唯一"退化成空面判据（红色才暴露，正是这条腿
 * 存在的理由）；末路 fixture 把"吃不出违例"钉成断言，防同类恒 0 再溜过去。</p>
 */
function familyRootBuilders(src: string, body: string): number {
  const re = new RegExp('`\\$\\{API_BASE\\}' + esc(body.slice('/api'.length)) + '(?![A-Za-z0-9_/-])', 'g');
  let n = 0;
  for (const line of codeLines(src)) n += (line.match(re) ?? []).length;
  return n;
}

const BASE_DECL_RE = /(?:export\s+)?const\s+([A-Z0-9_]+)\s*=\s*`?\$\{([A-Z0-9_]+)\}(\/v1\/[^`'"]*)/;

/** 生产面一次读入并按文件缓存（三族共用同一棵树，重复 stat/read 只会把门禁拖到超时判读区）。 */
let prodCache: Map<string, string> | null = null;
function prodSources(): Map<string, string> {
  if (prodCache !== null) return prodCache;
  const files: string[] = [];
  walk(SRC_ROOT, files);
  const m = new Map<string, string>();
  for (const abs of files) {
    const rel = relOf(abs);
    if (rel === CANONICAL || isTestFile(rel)) continue;
    m.set(rel, readFileSync(abs, 'utf8'));
  }
  prodCache = m;
  return m;
}

describe('b6EngineFamiliesSingleSource — 引擎域三枚重复族单源化取证 (#145/B6-9)', () => {
  it('前提：三枚属主常量的运行期面值 = 改前各属主私设基址的族根面值（逐字）', () => {
    expect(API_BASE).toBe('/api');
    expect(ONTOLOGY_ENGINE_BASE).toBe('/api/v1/engine/ontology');
    expect(COGNITIVE_ENGINE_BASE).toBe('/api/v1/engine/cognitive');
    expect(DATA_ENGINE_SETTINGS_BASE).toBe('/api/v1/engine/data/settings');
  });

  it('属主唯一：全 src 生产面每族族根构造方恰 1 个文件 1 条，且是 export 常量；消费侧零私设', () => {
    const prod = prodSources();
    // 扫描根自检：面一旦缩水（目录改名/判据漂成只见子集），"唯一构造方"会退化成恒真
    expect(prod.size, '生产面文件数异常 ⇒ 扫描根或测试文件判据漂移，本腿的"唯一"没有意义').toBeGreaterThanOrEqual(600);
    for (const fam of FAMS) {
      const builders = new Map<string, number>();
      const privateBases = new Map<string, string[]>();
      for (const [rel, src] of prod) {
        const n = familyRootBuilders(src, fam.body);
        if (n > 0) builders.set(rel, n);
        if (rel !== fam.owner) {
          const priv = codeLines(src).filter((l) => {
            const m = l.match(BASE_DECL_RE);
            return m !== null && m[2] === 'API_BASE' && m[3] === fam.body.slice('/api'.length);
          });
          if (priv.length > 0) privateBases.set(rel, priv);
        }
      }
      expect([...builders.keys()].sort(), `族 ${fam.body} 仍有 ${builders.size} 个构造方`).toEqual([fam.owner]);
      expect(builders.get(fam.owner), `族 ${fam.body} 属主内出现第二处族根构造`).toBe(1);
      expect([...privateBases.keys()], `族 ${fam.body} 消费侧仍私设基址`).toEqual([]);
      const decl = codeLines(read(fam.owner)).filter(
        (l) => l.trim() === `export const ${fam.constName} = \`${'$'}{API_BASE}${fam.body.slice('/api'.length)}\`;`,
      );
      expect(decl.length, `族 ${fam.body} 属主声明形状异常`).toBe(1);
    }
  });

  it('门面不是第二属主：两个 re-export 入口零面零引用，但仍真指到属主模块（否则取值断链）', () => {
    const ontologyFacade = read('services/ontologyApi.ts');
    expect(familyRootBuilders(ontologyFacade, '/api/v1/engine/ontology')).toBe(0);
    expect(bareUseFaces(ontologyFacade, 'ONTOLOGY_ENGINE_BASE', '/api/v1/engine/ontology')).toEqual([]);
    expect(ontologyFacade).toMatch(/export \* from ["']\.\/ontology\/ontologyEngineApi["'];/);
    const apiFacade = read('api.ts');
    expect(familyRootBuilders(apiFacade, '/api/v1/engine/data/settings')).toBe(0);
    expect(bareUseFaces(apiFacade, 'DATA_ENGINE_SETTINGS_BASE', '/api/v1/engine/data/settings')).toEqual([]);
    expect(apiFacade).toMatch(/export \* from ["']\.\/api\/dataClient["'];/);
    // 页面侧取值确实经门面那一跳（属主文件直引会绕开兼容入口，属另一类形状）
    expect(read('pages/MonitoringCenter.tsx')).toMatch(/from ['"]\.\.\/services\/ontologyApi['"]/);
    expect(read('pages/EngineMonitor.tsx')).toMatch(/from ['"]\.\.\/api['"]/);
  });

  it('面值逐字不变：三族逐文件还原面值 = 改前 42 条快照 + 净新增 1 枚属主声明（多一条少一条都红）', () => {
    let headTotal = 0;
    for (const fam of FAMS) {
      const got: string[] = [];
      const keys = Object.keys(HEAD_FACES).filter((k) => k.startsWith(`${fam.body}|`));
      for (const key of keys) {
        const rel = key.slice(fam.body.length + 1);
        const faces = allFaces(read(rel), fam);
        const want = [...HEAD_FACES[key]];
        if (fam.netNewDecl && rel === fam.owner) want.push(fam.body);
        expect(faces.sort(), `${rel} 族 ${fam.body} 面值分箱漂移`).toEqual(want.sort());
        got.push(...faces);
        headTotal += want.length;
      }
      // 净新增声明必须只加在属主文件那一个箱子里
      const newDecl = NEW_DECL_FACES.filter((n) => n.body === fam.body);
      expect(newDecl.length).toBe(fam.netNewDecl ? 1 : 0);
      if (fam.netNewDecl) {
        expect(got.includes(fam.body), '净新增属主声明未出现在面值面里').toBe(true);
      }
    }
    expect(headTotal, '三族总面位（改前 42 ＋ 净新增 1）与快照台账不符').toBe(43);
  });

  it('属主内腿数守恒：ontology 25 枚、cognitive 8 枚全走单源常量，裸 `${API_BASE}` 面只剩声明位', () => {
    const onto = read('services/ontology/ontologyEngineApi.ts');
    expect(constLegFaces(onto, 'ONTOLOGY_ENGINE_BASE', '/api/v1/engine/ontology').length).toBe(25);
    expect(apiBaseFaces(onto, '/api/v1/engine/ontology')).toEqual(['/api/v1/engine/ontology']);
    const cog = read('services/cognitiveEngineApi.ts');
    expect(constLegFaces(cog, 'COGNITIVE_ENGINE_BASE', '/api/v1/engine/cognitive').length).toBe(8);
    expect(apiBaseFaces(cog, '/api/v1/engine/cognitive')).toEqual([]);
  });

  it('可见性：子路径与单属主引擎腿刻意保持 `${API_BASE}` 原样（族索引面不因本批关掉）', () => {
    const panel = read('pages/data-workbench/DataEngineConfigPanel.tsx');
    // 改前这 4 枚全是 `${API_BASE}` 行内腿；改后族根那一枚换成裸名引用 ⇒ 行内面只剩三枚子路径
    expect(apiBaseFaces(panel, '/api/v1/engine/data/settings').sort()).toEqual([
      '/api/v1/engine/data/settings/defaults',
      '/api/v1/engine/data/settings/groups',
      '/api/v1/engine/data/settings/refresh',
    ]);
    expect(bareUseFaces(panel, 'DATA_ENGINE_SETTINGS_BASE', '/api/v1/engine/data/settings').length).toBe(1);
    const center = read('pages/MonitoringCenter.tsx');
    // security/data/knowledge/ai 四枚各只有一个生产方，本批判据不许把它们并进常量（并进＝关掉可见性而不减债）
    expect(apiBaseFaces(center, '/api/v1/engine/security')).toHaveLength(1);
    expect(apiBaseFaces(center, '/api/v1/engine/data')).toHaveLength(1);
    expect(apiBaseFaces(center, '/api/v1/engine/knowledge')).toHaveLength(1);
    expect(apiBaseFaces(center, '/api/v1/engine/ai')).toHaveLength(1);
    expect(bareUseFaces(center, 'ONTOLOGY_ENGINE_BASE', '/api/v1/engine/ontology').length).toBe(1);
    expect(bareUseFaces(center, 'COGNITIVE_ENGINE_BASE', '/api/v1/engine/cognitive').length).toBe(1);
  });

  it('棘轮在册性：三族已从 urlFamilyDualFormRatchet 的 BASELINE 剪除，且两处定点与解析行集互相对得上', () => {
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
    expect(rows.filter((r) => r.startsWith('/v1/engine/cognitive') || r === '/v1/engine/ontology' || r === '/v1/engine/data/settings')).toEqual([]);
    // 反向探针：本批刻意留在册的单属主族根构造点，改前也在 BASELINE 之外的必须仍然不在（防探针形状漂成恒真）
    expect(probe('/v1/engine/ontology')).toBe(false);
  });

  it('判据能变红：私设第二写手、族边界误吃、裸引用漏计、面值漂移各造违例且被对应腿吃出（防恒绿）', () => {
    const DEFECT = [
      "import { API_BASE } from './httpClient';",
      'const ONTOLOGY_ENGINE_BASE = `${API_BASE}/v1/engine/ontology`;',
      'const u = `${ONTOLOGY_ENGINE_BASE}/health`;',
      'const c: EngineDef[] = [{ apiBase: ONTOLOGY_ENGINE_BASE }];',
    ].join('\n');
    const body = '/api/v1/engine/ontology';
    expect(familyRootBuilders(DEFECT, body), '族根第二写手判据吃不出违例').toBe(1);
    expect(constLegFaces(DEFECT, 'ONTOLOGY_ENGINE_BASE', body), '常量消费腿判据吃不出违例').toEqual([`${body}/health`]);
    expect(bareUseFaces(DEFECT, 'ONTOLOGY_ENGINE_BASE', body), '裸名引用腿判据吃不出违例').toEqual([body]);
    // 声明行不得被裸引用判据重复计（否则面值多重集恒偏高＝本批"少一条腿"看不出来）
    expect(bareUseFaces('export const ONTOLOGY_ENGINE_BASE = `${API_BASE}/v1/engine/ontology`;', 'ONTOLOGY_ENGINE_BASE', body)).toEqual([]);
    // import 行同理（两条腿都会把同一个名字再吃一次）
    expect(bareUseFaces("import { ONTOLOGY_ENGINE_BASE } from './x';", 'ONTOLOGY_ENGINE_BASE', body)).toEqual([]);
    // 族边界：族内更细的腿与前缀撞名都不是族根构造点
    expect(familyRootBuilders('const u = `${API_BASE}/v1/engine/ontology/health`;', body)).toBe(0);
    expect(familyRootBuilders('const u = `${API_BASE}/v1/engine/ontologyX`;', body)).toBe(0);
    expect(familyRootBuilders('// const u = `${API_BASE}/v1/engine/ontology`;', body)).toBe(0);
    // 子路径族根同样不吃（settings 批的假红就长这样）
    expect(familyRootBuilders('const u = `${API_BASE}/v1/engine/data/settings/groups`;', '/api/v1/engine/data/settings')).toBe(0);
    // 私设判据与属主声明必须能分开：私有 const 撞族根体＝违例，导出单源＝唯一合法位
    const privProbe = (src: string, suffix: string): string[] =>
      codeLines(src).filter((l) => {
        const m = l.match(BASE_DECL_RE);
        return m !== null && m[2] === 'API_BASE' && m[3] === suffix;
      });
    // 私设判据只认"声明形态"、不认名字：`export const` 单源声明同样命中 ⇒ 属主靠**路径豁免**
    // 而不是名字豁免（与 #145/B6-8 同形）。把豁免写成名字匹配会让任何改名都悄悄脱离判据。
    expect(privProbe('const ENGINE_BASE = `${API_BASE}/v1/engine/ontology`;', '/v1/engine/ontology').length).toBe(1);
    expect(privProbe('export const ONTOLOGY_ENGINE_BASE = `${API_BASE}/v1/engine/ontology`;', '/v1/engine/ontology').length).toBe(1);
    expect(privProbe('const OTHER = `${API_BASE}/v1/engine/ontology/health`;', '/v1/engine/ontology')).toEqual([]);
    // 面值漂移：把族根写成少一段必须还原成另一枚面值 ⇒ 快照腿即红
    expect(constLegFaces('const u = `${ONTOLOGY_ENGINE_BASE}/graph`;', 'ONTOLOGY_ENGINE_BASE', body)).toEqual([`${body}/graph`]);
    expect(apiBaseFaces('const u = `${API_BASE}/v1/engine/ontologys`;', body)).toEqual([]);
    // inFamily 作用域：`/v1/engine/data` 不得被 settings 族吃掉，反之族根撞名不吃更细族
    expect(apiBaseFaces('const u = `${API_BASE}/v1/engine/data`;', '/api/v1/engine/data/settings')).toEqual([]);
    expect(apiBaseFaces('const u = `${API_BASE}/v1/engine/data/settings`;', '/api/v1/engine/data')).toEqual([
      '/api/v1/engine/data/settings',
    ]);
  });
});
