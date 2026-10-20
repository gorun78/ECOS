// @vitest-environment node
/**
 * b6EcosFamiliesSingleSource.spec.ts — ecos 域三枚重复族单源化取证 (#145/B6-10)
 *
 * <p>差距台账 §13.2.4 C1 腿的 B6 存量债第十批。改前 `urlFamilyDualFormRatchet` 的 BASELINE 在册三族：
 * `/v1/ecos/ontologies`（`components/ontology-workbench/OntologyDomainPanel.tsx` 的
 * `ONTOLOGY_API` ＋ 同文件 `ENTITY_API` 两枚同值常量，＋ `pages/business-workbench/BusinessWorkbenchLayout.tsx`
 * 的一枚行内腿）、`/v1/ecos/versions`（`services/ontology/ontologyVersionApi.ts` 的私有 `VERSION_BASE`
 * ＋ `pages/knowledge/tabs/DataWorkbenchImportTab.tsx` 的行内腿）、
 * `/v1/ecos/world-model-graph`（`api/monitorClient.ts` 的私有 `WM_BASE` ＋
 * `services/worldModelGraphApi.ts` 的私有 `WMG`）。现每族恰 1 枚导出常量持有族根体 ⇒ dupBoth 9 族。</p>
 *
 * <p>**三族属主落位不是同一套规则**（按实测形状逐条钉，防后继批把某一族当通则）：
 * ① ontologies 两属主都在页面侧，且 `OntologyDomainPanel` 同文件有两枚同值常量——谁当属主都要让另一侧
 * "页面→页面"取常量（分层倒置），故落服务侧 `ontologyModelApi.ts`：该文件 `${BASE}/ontologies` 五枚腿
 * 本就是该端点的正典实现，声明位**净新增** 1 枚；② versions 一枚属主已在服务侧（私有 `VERSION_BASE`），
 * 只改名并 export ⇒ 声明位**不动**、计数不变；③ world-model-graph 两属主分属 api/services，
 * 按 #145/B6-8 ② 判据落 **api 层** `monitorClient.ts`，另一侧 `worldModelGraphApi.ts` 的 `WMG` 声明
 * **整枚删除**（该箱族根 −1）。门面 `services/ontologyApi.ts` 的 `export *` 行不含 URL 体 ⇒
 * 三个页面经门面取值不会造出第二构造方（#145/B6-5 ② / #145/B6-9 ② 判例）。</p>
 *
 * <p>**面值快照 35 条**系 2026-10-20 把 `git archive HEAD ./src` 解出的整棵树与本批工作树
 * 用**同一份解析器**逐行读出（非手抄）：解析器把文件内 const 链（`WM_BASE`/`VERSION_BASE`/`WMG`/
 * `CAUSAL_BASE`）与跨模块属主常量都还原成运行期字符串，因此 `${ONTOLOGY_API}/${ontologyId}/entities`
 * 这类改前形态与改后 `${ECOS_ONTOLOGIES_BASE}/${ontologyId}/entities` 落在同一枚面值上，
 * 等式才叫"面值逐字不变"。**两条口径纪律**（都是实测踩出来的，缺一条守恒就是假的）：
 * ① 别名链 `const CAUSAL_BASE = WM_BASE;` 必须迭代求解，否则 monitorClient 的 `/paths?from=…`
 * 等三枚腿改前隐形、改后可见；② 行尾 `\r` 必须先剥，否则本仓 CRLF 文件上的"整行形状"类正则
 * （`$` 无 `m` 标志只认输入末尾）会在两棵树上表现不一致。
 * 逐箱只允许两处差额且各有名字归属：
 * `ontologyModelApi.ts` +1（净新增属主声明）、`worldModelGraphApi.ts` −1（重复族根声明删除）。
 * 总数 35 守恒 ⇒ 本批没有任何一条腿凭空消失，也没有多出一条改前不存在的请求面。</p>
 *
 * <p>**族作用域口径与棘轮一致**：`inFamily` 只吃"族根本身"或"族根后紧跟斜杠"，
 * 故 `${VERSION_BASE}?domainCode=…` 这类带查询串开头的腿在两棵树里同样不计（改前改后同面，
 * 不影响守恒）；`api/ontologyClient.ts` 的 `/ontologies/ont001/entities` 单属主子路径刻意保持
 * `${API_BASE}` 行内形态——并进去只是对棘轮隐形而不减债（#145/B6-1 ② / #145/B6-9 ④ 判例）。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { ECOS_ONTOLOGIES_BASE } from '../services/ontology/ontologyModelApi';
import { ECOS_VERSIONS_BASE } from '../services/ontology/ontologyVersionApi';
import { ECOS_WORLD_MODEL_GRAPH_BASE } from '../api/monitorClient';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const CANONICAL = 'services/httpClient.ts';

const esc = (s: string): string => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** 三枚属主常量：族根面值 + 属主文件 + 声明位命运。 */
const OWNERS = [
  {
    body: '/api/v1/ecos/ontologies',
    name: 'ECOS_ONTOLOGIES_BASE',
    owner: 'services/ontology/ontologyModelApi.ts',
    value: ECOS_ONTOLOGIES_BASE,
    netNewDecl: true,
  },
  {
    body: '/api/v1/ecos/versions',
    name: 'ECOS_VERSIONS_BASE',
    owner: 'services/ontology/ontologyVersionApi.ts',
    value: ECOS_VERSIONS_BASE,
    netNewDecl: false,
  },
  {
    body: '/api/v1/ecos/world-model-graph',
    name: 'ECOS_WORLD_MODEL_GRAPH_BASE',
    owner: 'api/monitorClient.ts',
    value: ECOS_WORLD_MODEL_GRAPH_BASE,
    netNewDecl: false,
  },
] as const;

const BODIES = OWNERS.map((o) => o.body);

/**
 * 改前逐箱运行期面值（35 条，同一解析器在 `git archive HEAD` 解出的树上实跑读出）。
 * 箱键＝`族根|文件`；只收录该文件在该族作用域内的面值，插值段按字面保留。
 */
const HEAD_FACES: Record<string, readonly string[]> = {
  '/api/v1/ecos/ontologies|services/ontology/ontologyModelApi.ts': [
    '/api/v1/ecos/ontologies/${ontologyId}${path}',
    '/api/v1/ecos/ontologies/ont001/entities',
    '/api/v1/ecos/ontologies',
    '/api/v1/ecos/ontologies',
    '/api/v1/ecos/ontologies/${id}',
    '/api/v1/ecos/ontologies/${id}',
  ],
  '/api/v1/ecos/ontologies|components/ontology-workbench/OntologyDomainPanel.tsx': [
    '/api/v1/ecos/ontologies',
    '/api/v1/ecos/ontologies',
    '/api/v1/ecos/ontologies/${ontologyId}/entities',
    '/api/v1/ecos/ontologies/${editingId}',
    '/api/v1/ecos/ontologies/${deletingId}',
  ],
  '/api/v1/ecos/ontologies|pages/business-workbench/BusinessWorkbenchLayout.tsx': [
    '/api/v1/ecos/ontologies',
  ],
  '/api/v1/ecos/ontologies|api/ontologyClient.ts': [
    '/api/v1/ecos/ontologies/ont001/entities',
  ],
  '/api/v1/ecos/versions|services/ontology/ontologyVersionApi.ts': [
    '/api/v1/ecos/versions',
    '/api/v1/ecos/versions/${encodeURIComponent(id)}',
  ],
  '/api/v1/ecos/versions|pages/knowledge/tabs/DataWorkbenchImportTab.tsx': [
    '/api/v1/ecos/versions',
  ],
  '/api/v1/ecos/world-model-graph|api/monitorClient.ts': [
    '/api/v1/ecos/world-model-graph',
    '/api/v1/ecos/world-model-graph/goals',
    '/api/v1/ecos/world-model-graph/scenarios',
    '/api/v1/ecos/world-model-graph/links',
    '/api/v1/ecos/world-model-graph/causal-graph',
    '/api/v1/ecos/world-model-graph/goals/tree',
    '/api/v1/ecos/world-model-graph/${type}',
    '/api/v1/ecos/world-model-graph/${type}/${id}',
    '/api/v1/ecos/world-model-graph/${type}/${id}',
    '/api/v1/ecos/world-model-graph/compare',
    '/api/v1/ecos/world-model-graph/causal-graph',
    '/api/v1/ecos/world-model-graph/paths?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}',
    '/api/v1/ecos/world-model-graph/compare',
  ],
  '/api/v1/ecos/world-model-graph|services/worldModelGraphApi.ts': [
    '/api/v1/ecos/world-model-graph',
    '/api/v1/ecos/world-model-graph/state',
    '/api/v1/ecos/world-model-graph/scenarios',
    '/api/v1/ecos/world-model-graph/causal-graph',
    '/api/v1/ecos/world-model-graph/scenarios',
    '/api/v1/ecos/world-model-graph/strategy/recommend?goal=${encodeURIComponent(goal)}',
  ],
};

/** 逐箱允许的差额，每条都必须落到具体名字上（无归属的增减即红）。 */
const FACE_ADJUSTMENTS: ReadonlyArray<{
  kind: 'net-new-decl' | 'merged-root-decl';
  body: string;
  file: string;
  name: string;
  why: string;
}> = [
  {
    kind: 'net-new-decl',
    body: '/api/v1/ecos/ontologies',
    file: 'services/ontology/ontologyModelApi.ts',
    name: 'ECOS_ONTOLOGIES_BASE',
    why: '两属主分在两个页面侧文件、该面值改前在本域不存在 ⇒ 属主声明位净新增 +1',
  },
  {
    kind: 'merged-root-decl',
    body: '/api/v1/ecos/world-model-graph',
    file: 'services/worldModelGraphApi.ts',
    name: 'WMG',
    why: '改前该文件自持一枚 `${API_BASE}/v1/ecos/world-model-graph` 声明，本批整枚删除改引属主 ⇒ 该箱 −1，属主 2→1',
  },
];

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
  // 先剥行尾 `\r`：本仓多数文件是 CRLF，`$` 在无 `m` 标志时只认输入末尾 ⇒ 不剥掉的话
  // "整行形状"类判据（下面的同值别名排除）会因一行有没有注释而随机失效，两棵树口径不一。
  return src.split('\n').map((l) => l.replace(/\r$/, '')).filter((l) => !isComment(l));
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

/** 族作用域：族根本身或族根后紧跟斜杠的更细腿（`?`/前缀撞名都不吃，与棘轮同口径）。 */
const inFamily = (v: string): boolean =>
  BODIES.some((b) => v === b || v.startsWith(`${b}/`));

const OWNER_BY_NAME = new Map(OWNERS.map((o) => [o.name, o] as const));

/** `${X}` 插值求值：API_BASE 与已解析常量代入，运行期变量段按字面保留。 */
function evalTemplate(body: string, consts: Record<string, string>): string {
  let out = '';
  let i = 0;
  while (i < body.length) {
    if (body[i] === '\\' && i + 1 < body.length) { out += body[i + 1]; i += 2; continue; }
    if (body[i] === '$' && body[i + 1] === '{') {
      const end = body.indexOf('}', i);
      if (end < 0) { out += body.slice(i); break; }
      const expr = body.slice(i + 2, end);
      const simple = /^[A-Za-z0-9_]+$/.test(expr);
      if (expr === 'API_BASE') out += API_BASE;
      else if (simple && Object.prototype.hasOwnProperty.call(consts, expr)) out += consts[expr];
      else out += '${' + expr + '}';
      i = end + 1;
      continue;
    }
    out += body[i];
    i += 1;
  }
  return out;
}

/**
 * 还原一个文件的常量表：跨模块 import 的属主常量用**运行期真值**代入（本文件顶部真 import），
 * 文件内 const 链（`WM_BASE`/`VERSION_BASE`/`CAUSAL_BASE` 这类）迭代求解到不动点。
 * 属主常量必须是**改后**才存在的名字——改前的树里没有它们，快照因此只能取自私有名，
 * 这正是"属主上收后面值仍逐字相同"的可证之处。
 */
function constsFor(rel: string, src: string): Record<string, string> {
  const consts: Record<string, string> = {};
  const lines = codeLines(src);
  for (const l of lines) {
    if (!l.trimStart().startsWith('import ')) continue;
    for (const [name, fam] of OWNER_BY_NAME) {
      if (new RegExp('(^|[{,\\s])' + name + '\\b').test(l)) consts[name] = fam.value;
    }
  }
  const tmplDecl = /(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`([^`]*)`/;
  const litDecl = /(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*['"]([^'"]*)['"]/;
  // 纯别名声明：`const WM_BASE = ECOS_WORLD_MODEL_GRAPH_BASE;` / `const CAUSAL_BASE = WM_BASE;`
  // 改前的 monitorClient 用「私有名 → 私有名」两级链（`WM_BASE` → `CAUSAL_BASE`）喂出 3 枚腿，
  // 不解析这条链那 3 枚腿在两棵树里口径不一（改前解不出＝隐形、改后解得出＝可见），
  // "总数守恒"会变成解析器形状造出来的假象而不是事实。
  const aliasDecl = /(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+)\s*;?\s*(?:\/\/.*)?$/;
  for (let pass = 0; pass < 8; pass++) {
    let changed = false;
    for (const l of lines) {
      const d = l.match(tmplDecl);
      if (d && !(d[1] in consts)) {
        const deps = [...d[2].matchAll(/\$\{([A-Za-z0-9_]+)(?:\.[^}]*)?\}/g)].map((m) => m[1]);
        if (deps.every((x) => x === 'API_BASE' || x in consts)) {
          consts[d[1]] = evalTemplate(d[2], consts);
          changed = true;
        }
      }
      const t = l.match(litDecl);
      if (t && !(t[1] in consts)) { consts[t[1]] = t[2]; changed = true; }
      const a = l.match(aliasDecl);
      if (a && !(a[1] in consts)) {
        if (a[2] === 'API_BASE') { consts[a[1]] = API_BASE; changed = true; }
        else if (a[2] in consts) { consts[a[1]] = consts[a[2]]; changed = true; }
      }
    }
    if (!changed) break;
  }
  return consts;
}

/**
 * 该文件在本族作用域内的全部运行期面值 = 模板腿 + 裸字符串腿 + 裸名引用腿。
 * 裸名引用腿剔除四类：import 行、属主声明行、已含 `` `${NAME}` `` 的行、同值别名声明行
 * （`const CAUSAL_BASE = ECOS_WORLD_MODEL_GRAPH_BASE;` 只是改名，不是一条请求，计进来会把属主面数虚高）。
 */
function facesOf(rel: string, srcOverride?: string): string[] {
  const src = srcOverride ?? read(rel);
  const consts = constsFor(rel, src);
  const out: string[] = [];
  for (const line of codeLines(src)) {
    const trimmed = line.trimStart();
    for (const m of line.matchAll(/`((?:\\.|(?!`)[^\\])*)`/g)) out.push(evalTemplate(m[1], consts));
    for (const m of line.matchAll(/['"](\/api[^'"]*)['"]/g)) out.push(m[1]);
    if (trimmed.startsWith('import ')) continue;
    for (const [name, fam] of OWNER_BY_NAME) {
      const nameRe = new RegExp('(^|[^A-Za-z0-9_.$])' + name + '\\b');
      if (!nameRe.test(line)) continue;
      if (new RegExp('const\\s+' + name + '\\s*=').test(line)) continue;
      if (new RegExp('\\$\\{' + name + '\\}').test(line)) continue;
      // 同值别名声明行（`const CAUSAL_BASE = ECOS_WORLD_MODEL_GRAPH_BASE; // 单值收敛…`）只是改名，
      // 不是一条请求 ⇒ 剥掉行注释后按整行形状排除，否则属主面数会虚高一条、掩盖真的增减。
      const noTrailingComment = trimmed.replace(/\/\/[^'"]*$/, '').trim();
      if (new RegExp('^const\\s+[A-Za-z0-9_]+\\s*=\\s*' + name + '\\s*;?$').test(noTrailingComment)) continue;
      out.push(fam.body);
    }
  }
  return out.filter(inFamily);
}

/** `${API_BASE}/<族根后缀>` 且紧跟族边界（族内更细的腿与前缀撞名都不是族根构造点）。 */
function familyRootBuilders(src: string, body: string): number {
  const re = new RegExp('`\\$\\{API_BASE\\}' + esc(body.slice('/api'.length)) + '(?![A-Za-z0-9_/-])', 'g');
  let n = 0;
  for (const line of codeLines(src)) n += (line.match(re) ?? []).length;
  return n;
}

const BASE_DECL_RE = /(?:export\s+)?const\s+([A-Z0-9_]+)\s*=\s*`?\$\{([A-Z0-9_]+)\}(\/v1\/[^`'"]*)/;

function privBaseDecls(src: string, suffix: string): string[] {
  return codeLines(src).filter((l) => {
    const m = l.match(BASE_DECL_RE);
    return m !== null && m[2] === 'API_BASE' && m[3] === suffix;
  });
}

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

const BIN_KEYS = Object.keys(HEAD_FACES);
const FILES_IN_BINS = [...new Set(BIN_KEYS.map((k) => k.slice(k.indexOf('|') + 1)))].sort();

describe('b6EcosFamiliesSingleSource — ecos 域三枚重复族单源化取证 (#145/B6-10)', () => {
  it('前提：三枚属主常量运行期面值 = 改前各属主私设基址的族根面值（逐字）', () => {
    expect(API_BASE).toBe('/api');
    expect(ECOS_ONTOLOGIES_BASE).toBe('/api/v1/ecos/ontologies');
    expect(ECOS_VERSIONS_BASE).toBe('/api/v1/ecos/versions');
    expect(ECOS_WORLD_MODEL_GRAPH_BASE).toBe('/api/v1/ecos/world-model-graph');
  });

  it('属主唯一：全 src 生产面每族族根构造方恰 1 个文件 1 条，且是 export 常量；消费侧零私设', () => {
    const prod = prodSources();
    expect(prod.size, '生产面文件数异常 ⇒ 扫描根或测试文件判据漂移，本腿的"唯一"没有意义').toBeGreaterThanOrEqual(600);
    for (const fam of OWNERS) {
      const suffix = fam.body.slice('/api'.length);
      const builders = new Map<string, number>();
      const privateBases = new Map<string, string[]>();
      for (const [rel, src] of prod) {
        const n = familyRootBuilders(src, fam.body);
        if (n > 0) builders.set(rel, n);
        if (rel !== fam.owner) {
          const priv = privBaseDecls(src, suffix);
          if (priv.length > 0) privateBases.set(rel, priv);
        }
      }
      expect([...builders.keys()].sort(), `族 ${fam.body} 仍有 ${builders.size} 个构造方`).toEqual([fam.owner]);
      expect(builders.get(fam.owner), `族 ${fam.body} 属主内出现第二处族根构造`).toBe(1);
      expect([...privateBases.keys()], `族 ${fam.body} 消费侧仍私设基址`).toEqual([]);
      // 声明形状用字符串拼接比对（RegExp 吃反引号/`${` 在本类文件里极易写出语法洞）
      const declNeedle = 'export const ' + fam.name + ' = `' + '${API_BASE}' + suffix + '`;';
      expect(read(fam.owner), `族 ${fam.body} 属主声明形状异常`).toContain(declNeedle);
    }
  });

  it('门面不是第二属主：services/ontologyApi 的 export * 行零面零引用，但页面确实经门面取值', () => {
    const facade = read('services/ontologyApi.ts');
    for (const fam of OWNERS.slice(0, 2)) {
      expect(familyRootBuilders(facade, fam.body)).toBe(0);
      expect(facade).toMatch(new RegExp('export \\* from ["\']\\./ontology/' + fam.owner.split('/').pop()!.replace('.ts', '') + '["\'];'));
    }
    for (const rel of [
      'components/ontology-workbench/OntologyDomainPanel.tsx',
      'pages/business-workbench/BusinessWorkbenchLayout.tsx',
      'pages/knowledge/tabs/DataWorkbenchImportTab.tsx',
    ]) {
      expect(read(rel)).toMatch(/from ['"]\.\.(\/\.\.)*\/services\/ontologyApi['"]/);
    }
    // world-model-graph 属主在 api 层，服务侧直引属主文件（#145/B6-8 ② 同款：services→api 不成环）
    expect(read('services/worldModelGraphApi.ts')).toMatch(/from ['"]\.\.\/api\/monitorClient['"]/);
  });

  it('面值逐字不变：八箱逐箱 = 改前 35 条 ± 两处有名字归属的差额，总数守恒 35', () => {
    let headTotal = 0;
    let gotTotal = 0;
    for (const key of BIN_KEYS) {
      const rel = key.slice(key.indexOf('|') + 1);
      const want = [...HEAD_FACES[key]];
      for (const adj of FACE_ADJUSTMENTS) {
        if (adj.file !== rel || adj.body !== key.slice(0, key.indexOf('|'))) continue;
        if (adj.kind === 'net-new-decl') want.push(adj.body);
        else want.splice(want.indexOf(adj.body), 1);
      }
      const got = facesOf(rel).sort();
      expect(got, `${rel} 在族 ${key.slice(0, key.indexOf('|'))} 的面值分箱漂移`).toEqual(want.sort());
      headTotal += HEAD_FACES[key].length;
      gotTotal += got.length;
    }
    expect(headTotal, '改前快照总面位不是实跑的 35 条 ⇒ 快照被剪过').toBe(35);
    expect(gotTotal, '本批后面值总面位须与改前守恒（±1 的两处差额互相抵掉）').toBe(35);
    expect(FACE_ADJUSTMENTS.length).toBe(2);
    expect(FILES_IN_BINS.length).toBe(8);
  });

  it('可见性：单属主子路径与同族更细的腿刻意保持 `${API_BASE}` 行内形态（族索引面不因本批关掉）', () => {
    expect(facesOf('api/ontologyClient.ts')).toEqual(['/api/v1/ecos/ontologies/ont001/entities']);
    const model = read('services/ontology/ontologyModelApi.ts');
    // 私有 BASE 仍在（该族之外的腿继续用它），族根只此一枚派生态声明
    expect(familyRootBuilders(model, '/api/v1/ecos/ontologies')).toBe(1);
    expect(codeLines(model).filter((l) => l.includes('${BASE}')).length).toBeGreaterThanOrEqual(1);
    const ver = read('services/ontology/ontologyVersionApi.ts');
    expect(constsFor('services/ontology/ontologyVersionApi.ts', ver)['ECOS_VERSIONS_BASE'])
      .toBe('/api/v1/ecos/versions');
    // 列表腿带 `?domainCode=` 直接跟在族根后 ⇒ 与棘轮同口径不计族根，改前后同面
    expect(apiBaseFaceCount(ver, '/api/v1/ecos/versions')).toBe(1);
    const mon = read('api/monitorClient.ts');
    expect(apiBaseFaceCount(mon, '/api/v1/ecos/world-model-graph')).toBe(1);
    expect(codeLines(mon).filter((l) => l.includes('${CAUSAL_BASE}')).length).toBe(3);
  });

  it('棘轮在册性：三族已从 urlFamilyDualFormRatchet 的 BASELINE 剪除，关账后活性改由合成行承证 (#145/B6-10 / #145/B6-15)', () => {
    const ratchet = read('__tests__/urlFamilyDualFormRatchet.test.ts');
    // #145/B6-15 关账 ⇒ BASELINE 是**空对象**，空集从此是目标态而不是失灵态（与知识域门的三重判据同批换承载面）：
    // 行集非空反转为「恰读 0 行」，同形正控改由合成行承证，规模对账两值同为 0。
    const rows = [...ratchet.matchAll(/^ {2}'([^']+)':\s*\[/gm)].map((m) => m[1]);
    expect(rows, 'BASELINE 已于 #145/B6-15 关账为空 ⇒ 任何读出的在册行都是重新引入豁免').toEqual([]);
    const SYNTH = "const BASELINE = Object.freeze({\n  '/v1/fixture/dup': ['a.ts', 'b.ts'],\n});";
    expect([...SYNTH.matchAll(/^ {2}'([^']+)':\s*\[/gm)].map((m) => m[1])).toEqual(['/v1/fixture/dup']);
    expect(SYNTH.includes(`'/v1/fixture/dup': [`), '同形合成探针失灵 ⇒ contains 类判据在此恒真').toBe(true);
    const dupPin = Number((ratchet.match(/expect\(dupBoth\.size[^)]*\)\.toBe\((\d+)\)/) ?? [])[1]);
    const entriesPin = Number((ratchet.match(/expect\(entries\.length\)\.toBe\((\d+)\)/) ?? [])[1]);
    expect(Number.isInteger(dupPin) && Number.isInteger(entriesPin), '棘轮源码里两处规模定点形状变了').toBe(true);
    expect(rows.length).toBe(dupPin);
    expect(entriesPin).toBe(dupPin);
    expect(dupPin).toBe(0);
    // 「本批三族已收口」的正面证据另起：逐族必须在册于 CLOSED_FAMILIES（防收口清单被剪薄）
    for (const fam of OWNERS) {
      const key = fam.body.slice('/api'.length);
      expect(ratchet.includes(`'${key}',`), `CLOSED_FAMILIES 里找不到 ${key} ⇒ 收口清单被剪薄`).toBe(true);
      expect(ratchet.includes(`'${key}': [`), `${key} 已收口，不得回到 BASELINE 豁免位`).toBe(false);
    }
    // 单属主子路径族根从来不在基线，空基线下该形状探针恒假 ⇒ 只对 `rows` 有意义，改由行集判据整体覆盖
    expect(rows.filter((r) => r.startsWith('/v1/ecos/'))).toEqual([]);
  });

  it('判据能变红：私设第二写手、族边界误吃、别名重复计、面值漂移各造违例且被对应腿吃出（防恒绿）', () => {
    const body = '/api/v1/ecos/ontologies';
    const DEFECT = [
      "import { API_BASE } from './httpClient';",
      'const ONTOLOGY_API = `${API_BASE}/v1/ecos/ontologies`;',
      'const u = `${ONTOLOGY_API}/${ontologyId}/entities`;',
      'const c = apiFetch(ECOS_ONTOLOGIES_BASE);',
    ].join('\n');
    expect(familyRootBuilders(DEFECT, body), '族根第二写手判据吃不出违例').toBe(1);
    expect(privBaseDecls(DEFECT, body.slice('/api'.length)).length, '私设声明判据吃不出违例').toBe(1);
    // 常量链解析：私有名喂出来的腿必须还原成同一个面值（否则"改前后同面"这条判据是空转）
    expect(constsFor('fixture.ts', DEFECT)['ONTOLOGY_API']).toBe(body);
    // 同值别名声明不是一条请求 ⇒ 裸名判据不许把它计成族根面（计了会让属主面数虚高、掩盖真增减）
    expect(facesOf('fixture.ts', [
      'const ECOS_WORLD_MODEL_GRAPH_BASE = `${API_BASE}/v1/ecos/world-model-graph`;',
      'const CAUSAL_BASE = ECOS_WORLD_MODEL_GRAPH_BASE; // 单值收敛',
    ].join('\n'))).toEqual(['/api/v1/ecos/world-model-graph']);
    // 两级别名链 + CRLF 必须同时被解析器吃掉：任一侧失灵，monitorClient 的 `CAUSAL_BASE` 三枚腿
    // 就会在两棵树上口径不一（改前解不出＝隐形、改后解得出＝可见）⇒ 守恒变成解析器造的假象
    const CRLF_SRC = [
      'const WM_BASE = `${API_BASE}/v1/ecos/world-model-graph`;',
      'const CAUSAL_BASE = WM_BASE; // 单值收敛',
      'const u = `${CAUSAL_BASE}/paths`;',
    ].join('\r\n');
    expect(constsFor('fixture.ts', CRLF_SRC)['CAUSAL_BASE'], '两级别名链没解析 ⇒ CRLF 或 alias 判据失灵').toBe(
      '/api/v1/ecos/world-model-graph',
    );
    expect(facesOf('fixture.ts', CRLF_SRC)).toEqual([
      '/api/v1/ecos/world-model-graph',
      '/api/v1/ecos/world-model-graph/paths',
    ]);
    // 族边界：更细的腿与前缀撞名都不是族根构造点
    expect(familyRootBuilders('const u = `${API_BASE}/v1/ecos/ontologies/ont001/entities`;', body)).toBe(0);
    expect(familyRootBuilders('const u = `${API_BASE}/v1/ecos/ontologiesX`;', body)).toBe(0);
    expect(familyRootBuilders('// const u = `${API_BASE}/v1/ecos/ontologies`;', body)).toBe(0);
    // 作用域：查询串腿不吃、少一段的撞名不吃
    expect(inFamily('/api/v1/ecos/versions?domainCode=x')).toBe(false);
    expect(inFamily('/api/v1/ecos/versionsX')).toBe(false);
    expect(inFamily('/api/v1/ecos/versions/ver001')).toBe(true);
    // 面值漂移：把属主常量代错一枚（少一段/多一段），还原结果必须落进另一箱 ⇒ 快照腿即红
    expect(evalTemplate('${ECOS_VERSIONS_BASE}/${encodeURIComponent(id)}', { ECOS_VERSIONS_BASE: '/api/v1/ecos/versions' }))
      .toBe('/api/v1/ecos/versions/${encodeURIComponent(id)}');
    expect(inFamily(evalTemplate('${ECOS_VERSIONS_BASE}/x', { ECOS_VERSIONS_BASE: '/api/v1/ecos/versionsX' }))).toBe(false);
    expect(evalTemplate('${ECOS_VERSIONS_BASE}/${id}', { ECOS_VERSIONS_BASE: '/api/v1/ecos/version' }))
      .toBe('/api/v1/ecos/version/${id}');
    expect(inFamily(evalTemplate('${ECOS_VERSIONS_BASE}/${id}', { ECOS_VERSIONS_BASE: '/api/v1/ecos/version' }))).toBe(false);
  });
});

/** 该文件里 `${API_BASE}` 直接派生态落在该族作用域内的条数（可见性腿用）。 */
function apiBaseFaceCount(src: string, body: string): number {
  const re = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;
  let n = 0;
  for (const line of codeLines(src)) for (const m of line.matchAll(re)) {
    const v = `${API_BASE}${m[1]}`;
    if (v === body || v.startsWith(`${body}/`)) n += 1;
  }
  return n;
}
