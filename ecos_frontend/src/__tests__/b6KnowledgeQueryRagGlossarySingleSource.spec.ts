// @vitest-environment node
/**
 * 知识域三族基址单源守恒门（#145/B6-6，差距台账 §13.2.4 B6 腿）。
 *
 * <p>本批把棘轮基线里知识域的三个两属主族收口成常量单源：`/v1/knowledge/query` 与
 * `/v1/knowledge/rag` 落在同族原服务文件 `services/knowledgeQueryApi.ts`（导出
 * `KNOWLEDGE_QUERY_BASE` / `KNOWLEDGE_RAG_BASE`），`/v1/ontology/glossary` 落在
 * `services/glossary.ts`（该文件头注自认「术语唯一服务出口」，把原本的私有 `GLOSSARY_BASE`
 * 改名并 export 为 `ONTOLOGY_GLOSSARY_BASE`）。三个族的第二个属主都是
 * `pages/knowledge/services/wiki.ts`，本批改成 import 取值（dupBoth 21 → 18、属主 42 → 36）。</p>
 *
 * <p>**属主层位判据**（沿用 #145/B6-4 口径：属主 = 该族原服务拆分文件，不是门面）：
 * `knowledgeQueryApi.ts` 只 import `./httpClient`、`glossary.ts` 只 import `./httpClient` + 类型，
 * 两者都不依赖页面层 ⇒ wiki 引它们不成环；反向把常量放页面层会让 services 层倒过来依赖 pages。</p>
 *
 * <p>**本批的守恒形状与 B6-1~B6-5 都不同，必须分两面判**：query/rag 两族改前**根本没有常量**
 * （两个属主各写一枚 `` `${API_BASE}/v1/knowledge/rag` `` 裸派生态），收口时新建 2 枚属主声明；
 * glossary 族改前有 2 枚族根声明（`glossary.ts` 私有 + wiki 私有），本批删 wiki 那枚、
 * 把 `glossary.ts` 那枚改名导出。于是族根**声明面** 2 → 3、**消费者腿面** 16 → 16，
 * 总量 18 → 19 看着像"多了一条请求"。只比总量会把「声明位搬移」读成「请求面变化」，
 * 故本门把两面拆开各钉：消费面按多重集逐字比（16 = 16），声明面按清单钉死
 * （改前 2 枚同面值 glossary 族根 → 改后 query/rag/glossary 各 1 枚）。</p>
 *
 * <p>减法按**文件整条扣**（wiki 13 → 12），不按面值筛：被删那枚声明的面值
 * `/api/v1/ontology/glossary` 在 `glossary.ts` 的声明里同值仍在，按值筛会一次吃掉 2 条＝凭空多扣 1。
 * `${…}` 插值一律按字面保留、不归一（本域历史上 `${id}` 与 `${encodeURIComponent(id)}` 两种写法共存，
 * 归一会把「顺手改了转义」读成绿）。</p>
 *
 * <p>判据形状（都配反向用例）：<b>⑧ 属主声明行自身的名字 token 不算第二条腿</b>
 * （该行的模板腿已给出一条面值，再按裸名计即双计）；<b>裸引用算腿</b>（本域三枚常量都不在
 * 消费者文件里声明，不存在声明/使用双计，与 #145/B6-3 的「自声明常量不计裸引用」是两种前提）；
 * <b>族内判定用边界式</b> `v === FAM || v.startsWith(FAM + '/') || v.startsWith(FAM + '$')` —
 * 第三个分支专为属主 `request()` 那条 `` `${ONTOLOGY_GLOSSARY_BASE}${path}` ``（源码面在基址后紧跟
 * 插值而非斜杠，运行期 path 必带前导 `/`），纯 `startsWith(FAM)` 会把同根异族
 * `…/glossary-base/x` 误计进来（#145/B6-1 已实证过一次匹配边界缺陷）。</p>
 *
 * <p>两处判据缺陷在本门起草时自捕（非产品代码，记此防复发）：<b>A. 私设基址判据必须按族取范围</b>
 * ——写成"三族合并正则"后，判 glossary 族时会把 `knowledgeQueryApi.ts` 自己的 query/rag 属主声明
 * 读成私设族根（试跑前自查即红）；改为「常量名 → 该族路径体」一对一取值。<b>B. 双前缀判据不得
 * 走族内过滤后的面</b>——`/api/api/v1/knowledge/rag` 本就不落三族，过滤后必为 `[]`，
 * `expect(...).toEqual([])` 恒真是假判据；解析器因此增设 `off` 箱（已知常量的还原值但不在本族），
 * 双前缀腿改判 `off`，既证明该串确实被还原出来、又证明族边界放过它。与 D-B5-40 同形：
 * 「看着在判、其实在放过」多出在**匹配边界**与**过滤后再断言空**。</p>
 *
 * <p>与棘轮的分工：收口后 `` `${ONTOLOGY_GLOSSARY_BASE}/terms/${id}` `` 与裸引用对 RAW_RE/DER_RE 隐形，
 * dupBoth 下降即本门存在的理由——只靠棘轮会把「删掉重复声明」记成进度、把「请求面被吃掉」漏掉。
 * 同根子路径族 `/v1/knowledge/compliance-rules`（RULES_BASE）与 nav 两族本批**不动**：前者是 B6-7
 * 议题（`RuleRepoTable.tsx` 那条跨行模板腿在 #143/B5-26 门里另有形状判据），后者各只有 1 个生产方，
 * 按 B6-1 口径并进去只会让族对棘轮隐形而不减少任何债。三族族根声明都写成
 * `` `${API_BASE}/v1/…` `` 裸形态（不是常量互拼），族面 256 因此一分不动、第二生产方出现即红。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { KNOWLEDGE_QUERY_BASE, KNOWLEDGE_RAG_BASE } from '../services/knowledgeQueryApi';
import { ONTOLOGY_GLOSSARY_BASE } from '../services/glossary';

// 本门位于 `src/__tests__/`，到 `src/` 的相对深度是一档
const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));

const Q = '/api/v1/knowledge/query';
const R = '/api/v1/knowledge/rag';
const G = '/api/v1/ontology/glossary';
/** 三个族键（族体内不含 `${…}`，键即面值本身）。 */
const FAMILIES: readonly string[] = [Q, R, G];

const KQ_FILE = 'services/knowledgeQueryApi.ts';
const GL_FILE = 'services/glossary.ts';
const WIKI_FILE = 'pages/knowledge/services/wiki.ts';
/** 本批三个生产文件（族体内只有这三处出现）。 */
const FILES: readonly string[] = [KQ_FILE, GL_FILE, WIKI_FILE];

/**
 * 属主常量 → { 声明文件, import 时的模块名, 该族路径体（不含 `/api`） }。
 * `famPath` 是「私设族根」判据的**唯一范围**：合并三族成一个正则会吃到别的属主自己的声明（见文件头缺陷 A）。
 */
const OWNERS: ReadonlyMap<string, { file: string; module: string; famPath: string }> = new Map([
  ['KNOWLEDGE_QUERY_BASE', { file: KQ_FILE, module: 'knowledgeQueryApi', famPath: '/v1/knowledge/query' }],
  ['KNOWLEDGE_RAG_BASE', { file: KQ_FILE, module: 'knowledgeQueryApi', famPath: '/v1/knowledge/rag' }],
  ['ONTOLOGY_GLOSSARY_BASE', { file: GL_FILE, module: 'glossary', famPath: '/v1/ontology/glossary' }],
]);
const OWNER_NAMES = [...OWNERS.keys()];

/** 工作树每文件应还原出的族腿条数（实跑读出）：属主 5 + 属主 2 + wiki 12 = 19。 */
const WT_SITES_PER_FILE: Readonly<Record<string, number>> = {
  [KQ_FILE]: 5,
  [GL_FILE]: 2,
  [WIKI_FILE]: 12,
};

const HARDCODED_RE = /["'`]\/api\b/;
/** ⑧ 属主声明行（名字白名单式，绝不写成 `const X =` 通用形——`const resp = await …` 会误判成声明行而漏计裸引用腿）。 */
const OWNER_DECL_RE = new RegExp(String.raw`^\s*(?:export\s+)?const\s+(?:${OWNER_NAMES.join('|')})\b`);
/** 本地私设族根声明（`` const X = `${API_BASE}<该族路径体>` ``，wiki 侧必须归零的那一类）。 */
const LOCAL_BASE_DECL_RE = /^\s*(?:export\s+)?const\s+[A-Za-z0-9_]+\s*=\s*`\$\{API_BASE\}([^`]*)`;/;
const SITE_RE = new RegExp(
  '`\\$\\{(' + [...OWNER_NAMES, 'API_BASE'].sort((a, b) => b.length - a.length).join('|') + ')' +
    '\\}((?:\\\\.|(?!`)[^\\\\])*)`',
  'g',
);
const NO_SLASH_AFTER_BASE = /`\$\{API_BASE\}(?!\/)/;
const DOUBLE_PREFIX_RE = /\/api\/api\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

/** 只去注释（导入面／声明面判据用，规则 ⑦：剥掉 import 的行集不能拿来断导入腿）。 */
function codeLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
}

/** 剥掉整条 import 语句（含多行花括号体）后再按行判腿，防续行被吃成假腿。 */
function stripImports(src: string): string[] {
  return src.replace(/^\s*import\s[\s\S]*?;/gm, '').split(/\r?\n/).filter((l) => !isComment(l));
}

function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, ...rel.split('/')), 'utf8');
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

function walk(dirRoot: string, out: string[]): string[] {
  for (const entry of readdirSync(join(SRC_ROOT, dirRoot))) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(SRC_ROOT, dirRoot, entry);
    if (statSync(full).isDirectory()) walk(`${dirRoot}/${entry}`, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(SRC_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
}

/** 面值解析表：`API_BASE` 单源 + 三枚族单源常量（真 import 进来，面值即运行期取值）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['KNOWLEDGE_QUERY_BASE', KNOWLEDGE_QUERY_BASE],
  ['KNOWLEDGE_RAG_BASE', KNOWLEDGE_RAG_BASE],
  ['ONTOLOGY_GLOSSARY_BASE', ONTOLOGY_GLOSSARY_BASE],
]);

/**
 * 族内判定（**边界式**，不是裸前缀）：第三个分支收属主 `request()` 那条
 * `` `${ONTOLOGY_GLOSSARY_BASE}${path}` ``（基址后紧跟插值），同时放过同根异族 `…/glossary-base/x`。
 */
function inFamily(val: string): boolean {
  return FAMILIES.some((f) => val === f || val.startsWith(`${f}/`) || val.startsWith(`${f}$`));
}

interface FaceBins {
  /** 族根声明位（本批收口造成的 2→3 变化全在这里） */
  decl: string[];
  /** 消费者腿（改前后必须逐字相等的 16 条在这里） */
  consumer: string[];
  /** 已知常量的还原值但不落三族（畸形双前缀、子族、跨族拼串在这里现形，见文件头缺陷 B） */
  off: string[];
}

/**
 * 还原整文件面，按「族根声明位 / 消费者腿 / 族外」分箱。
 * 顺序即判据：先摘模板腿（同行的裸名才是另一条腿）→ 声明行的名字 token 不再计（⑧）→
 * import 语句整条先剥（⑦，否则 import 行读成一次请求）。
 */
function resolveFaces(src: string): FaceBins {
  const bins: FaceBins = { decl: [], consumer: [], off: [] };
  for (const line of stripImports(src)) {
    const isDeclLine = OWNER_DECL_RE.test(line);
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      const base = CONST_VALUES.get(m[1]);
      if (base !== undefined) {
        const val = base + m[2];
        if (inFamily(val)) (isDeclLine ? bins.decl : bins.consumer).push(val);
        else bins.off.push(val);
      }
      rest = rest.split(m[0]).join('');
    }
    if (isDeclLine) continue;
    for (const name of OWNER_NAMES) {
      const cnt = rest.split(name).length - 1;
      for (let i = 0; i < cnt; i += 1) bins.consumer.push(CONST_VALUES.get(name)!);
    }
  }
  return bins;
}

/** 族内两面合并后的有序面值多重集（守恒腿用）。 */
function faces(src: string): string[] {
  const { decl, consumer } = resolveFaces(src);
  return [...decl, ...consumer].sort();
}

/**
 * 改前快照（HEAD 实跑读出，18 条 = 3／2／13，`${…}` 按字面保留）：
 * `knowledgeQueryApi.ts` 无常量、3 枚消费者裸派生态（query×2 + rag×1）；
 * `glossary.ts` 私有族根声明 1 + `request()` 的 `${GLOSSARY_BASE}${path}` 1；
 * `wiki.ts` 私有族根声明 1 + 12 枚消费者腿（query 1、rag 2、glossary 子路径 9）。
 * 声明/消费分箱与 `resolveFaces` 同一判据（族根声明行归 decl）。
 */
const HEAD_FACES: Readonly<Record<string, { decl: readonly string[]; consumer: readonly string[] }>> = {
  [KQ_FILE]: { decl: [], consumer: [Q, Q, R] },
  [GL_FILE]: { decl: [G], consumer: [`${G}$\{path}`] },
  [WIKI_FILE]: {
    decl: [G],
    consumer: [
      Q,
      R,
      R,
      `${G}/terms`,
      `${G}/terms`,
      `${G}/terms`,
      `${G}/terms/$\{id}`,
      `${G}/terms/$\{id}`,
      `${G}/terms/$\{id}`,
      `${G}/terms/$\{id}`,
      `${G}/terms?$\{qs.toString()}`,
      `${G}/terms?$\{q}`,
    ],
  },
};

const HEAD_DECL_TOTAL = 2;
const HEAD_CONSUMER_TOTAL = 16;
const WT_DECL_TOTAL = 3;

describe('知识域 query/rag/glossary 三族基址单源 — 面值守恒 (#145/B6-6)', () => {
  it('前提：API_BASE = "/api" 且三枚属主常量面值 = 改前各族基址（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(KNOWLEDGE_QUERY_BASE).toBe(Q);
    expect(KNOWLEDGE_RAG_BASE).toBe(R);
    expect(ONTOLOGY_GLOSSARY_BASE).toBe(G);
  });

  it('属主唯一：全 src 生产面里含三族族体的代码行只有三枚属主声明（注释里的端点说明不计）', () => {
    const hits: string[] = [];
    for (const rel of walk('.', [])) {
      if (rel === 'services/httpClient.ts') continue;
      for (const line of codeLines(read(rel))) {
        for (const fam of ['v1/knowledge/query', 'v1/knowledge/rag', 'v1/ontology/glossary']) {
          if (line.includes(fam)) hits.push(`${rel} — ${line.trim()}`);
        }
      }
    }
    expect(hits.sort(), `族体字面量散点未收口：\n${hits.join('\n')}`).toEqual([
      `${GL_FILE} — export const ONTOLOGY_GLOSSARY_BASE = \`\${API_BASE}/v1/ontology/glossary\`;`,
      `${KQ_FILE} — export const KNOWLEDGE_QUERY_BASE = \`\${API_BASE}/v1/knowledge/query\`;`,
      `${KQ_FILE} — export const KNOWLEDGE_RAG_BASE = \`\${API_BASE}/v1/knowledge/rag\`;`,
    ]);
  });

  it('三文件代码行零私设基址（注释里的 /api 端点说明不计）', () => {
    for (const rel of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('来源唯一：三枚属主声明各恰 1 行、非属主文件零本地族根声明、wiki 三枚常量各从属主 import 恰 1 条', () => {
    for (const [name, owner] of OWNERS) {
      expect(
        codeLines(read(owner.file)).filter((l) => new RegExp(String.raw`^\s*(?:export\s+)?const\s+${name}\b`).test(l)).length,
        `${name} 的属主声明必须恰 1 行`,
      ).toBe(1);
      for (const rel of FILES) {
        if (rel === owner.file) continue;
        // 判据范围只取**本族**路径体（缺陷 A：合并三族正则会吃到别族的属主声明）
        const decls = codeLines(read(rel)).filter((l) => {
          const m = l.match(LOCAL_BASE_DECL_RE);
          return m !== null && m[1] === owner.famPath;
        });
        expect(decls, `${rel} 不得再私设 ${owner.famPath} 族根`).toEqual([]);
        const imports = codeLines(read(rel)).filter((l) => l.trimStart().startsWith('import ') && l.includes(name));
        if (rel === WIKI_FILE) {
          expect(imports.length, `${rel} 只能有一条 ${name} 导入腿`).toBe(1);
          expect(imports[0], `${rel} 的 ${name} 未取自该族属主`).toMatch(
            new RegExp(`from '[^']*services/${owner.module}';`),
          );
        } else {
          expect(imports.length, `${rel} 与本族无关，出现 ${name} 即第二个属主`).toBe(0);
        }
      }
    }
    // wiki 侧旧名必须彻底消失（留着即双来源：一枚 import + 一枚本地常量）
    expect(codeLines(read(WIKI_FILE)).filter((l) => /\bGLOSSARY_BASE\b/.test(l) && !l.includes('ONTOLOGY_GLOSSARY_BASE')).length).toBe(0);
  });

  it('面值逐字不变（消费面）：工作树 16 条消费者腿 = 改前 16 条，且逐文件条数吻合（wiki 13→12 按文件整条扣）', () => {
    const wtConsumer: string[] = [];
    for (const rel of FILES) {
      const { decl, consumer } = resolveFaces(read(rel));
      expect(decl.length + consumer.length, `${rel} 族腿条数不符`).toBe(WT_SITES_PER_FILE[rel]);
      wtConsumer.push(...consumer);
    }
    const headConsumer = Object.values(HEAD_FACES).flatMap((v) => [...v.consumer]);
    expect(headConsumer.length).toBe(HEAD_CONSUMER_TOTAL);
    expect(wtConsumer.length).toBe(HEAD_CONSUMER_TOTAL);
    expect([...wtConsumer].sort()).toEqual([...headConsumer].sort());
  });

  it('面值逐字不变（声明面）：改前 2 枚同值 glossary 族根 → 改后 query/rag/glossary 各 1 枚（总量 18→19 的 +1 全在这里）', () => {
    const headDecl = Object.values(HEAD_FACES).flatMap((v) => [...v.decl]).sort();
    const wtDecl: string[] = [];
    for (const rel of FILES) wtDecl.push(...resolveFaces(read(rel)).decl);
    expect(headDecl).toEqual([G, G]);
    expect(headDecl.length).toBe(HEAD_DECL_TOTAL);
    expect([...wtDecl].sort(), '族根声明面必须恰是 query/rag/glossary 各一枚').toEqual([Q, R, G].sort());
    expect(wtDecl.length).toBe(WT_DECL_TOTAL);
    // 两面对上账：18 = 2 + 16，19 = 3 + 16 ⇒ 差额恰为声明位搬移，请求面一分未动
    expect(HEAD_DECL_TOTAL + HEAD_CONSUMER_TOTAL).toBe(18);
    expect(WT_DECL_TOTAL + HEAD_CONSUMER_TOTAL).toBe(19);
    expect(FILES.reduce((n, rel) => n + faces(read(rel)).length, 0)).toBe(19);
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /，且三文件族外还原值零 /api/api 双前缀', () => {
    const bad: string[] = [];
    for (const rel of FILES) {
      for (const line of codeLines(read(rel))) {
        if (NO_SLASH_AFTER_BASE.test(line)) bad.push(`${rel}: ${line.trim()}`);
      }
      const { off } = resolveFaces(read(rel));
      for (const v of off) if (DOUBLE_PREFIX_RE.test(v)) bad.push(`${rel}: 还原值 ${v}`);
    }
    expect(bad, `派生态畸形：\n${bad.join('\n')}`).toEqual([]);
  });

  it('判据能变红：声明行不双计(⑧)、裸引用计腿、族边界不吃同根异族、`${path}` 形变体计腿、双前缀只在族外箱现形（防恒绿）', () => {
    const EMPTY: FaceBins = { decl: [], consumer: [], off: [] };
    // ⑧ 属主声明行 = 1 条 decl 腿，名字 token 不再造第二条
    expect(resolveFaces('export const KNOWLEDGE_RAG_BASE = `${API_BASE}/v1/knowledge/rag`;')).toEqual({
      decl: [R],
      consumer: [],
      off: [],
    });
    // 同一行「模板腿 + 裸名」= 两条腿
    expect(resolveFaces('  const u = [`${KNOWLEDGE_QUERY_BASE}/x`, KNOWLEDGE_QUERY_BASE];')).toEqual({
      decl: [],
      consumer: [`${Q}/x`, Q],
      off: [],
    });
    // 裸引用（消费者侧形态）计一条腿；import 行不计（⑦）
    expect(resolveFaces('  await apiFetchData(ONTOLOGY_GLOSSARY_BASE);')).toEqual({ decl: [], consumer: [G], off: [] });
    expect(resolveFaces("import { ONTOLOGY_GLOSSARY_BASE } from '../../../services/glossary';")).toEqual(EMPTY);
    // 族边界：同根异族不得混计（`-base` 后缀族 / 完全无关族）
    expect(inFamily('/api/v1/ontology/glossary-base/terms')).toBe(false);
    expect(inFamily(`${G}/terms`)).toBe(true);
    expect(inFamily(`${G}$\{path}`)).toBe(true);
    expect(resolveFaces('  const u = `${ONTOLOGY_GLOSSARY_BASE}${path}`;').consumer).toEqual([`${G}$\{path}`]);
    // 未知常量不吃（防把任意 `${x}` 吃成 URL）
    expect(resolveFaces('  const q = `${SOME_UNKNOWN_BASE}/v1/knowledge/query`;')).toEqual(EMPTY);
    // `const resp = await …` 不是声明行：通用 `const X =` 形会把同行裸引用腿吃掉（本门曾实测到的判据缺陷形状）
    expect(resolveFaces('  const resp = await rawAnonUrlFetch(KNOWLEDGE_RAG_BASE, { method: "POST" });').consumer).toEqual([R]);
    // 双前缀畸形（B5-26 实证过的自伤形状）在源码串面看不见：族内过滤后必为 []（＝假判据形状，缺陷 B），
    // 真正抓到它的是族外箱 ⇒ 本条同时证明「off 有内容」而非「faces 恒空」
    const DOUBLE = '  const p = `${API_BASE}/api/v1/knowledge/rag`;';
    expect(HARDCODED_RE.test(DOUBLE)).toBe(false);
    expect(faces(DOUBLE)).toEqual([]);
    expect(resolveFaces(DOUBLE)).toEqual({ decl: [], consumer: [], off: [`${API_BASE}/api/v1/knowledge/rag`] });
    expect(DOUBLE_PREFIX_RE.test(CONST_VALUES.get('API_BASE')! + '/api/v1/knowledge/rag')).toBe(true);
    // 私设基址形态必须被清零腿判出
    expect(codeLines('  await fetch("/api/v1/knowledge/query");').filter((l) => HARDCODED_RE.test(l)).length).toBe(1);
    // 缺陷 A 的反向用例：合并三族正则会把别族属主声明读成违例（本门不采用该形状）
    const OTHER_OWNER_DECL = 'export const KNOWLEDGE_QUERY_BASE = `${API_BASE}/v1/knowledge/query`;';
    const m = OTHER_OWNER_DECL.match(LOCAL_BASE_DECL_RE);
    expect(m?.[1]).toBe('/v1/knowledge/query');
    expect(m?.[1] === '/v1/ontology/glossary').toBe(false);
  });

  it('快照自身可校验：18 条按 3／2／13 分布、声明面 2 条消费面 16 条且全部落三族内（防快照清空成恒真）', () => {
    expect(
      Object.entries(HEAD_FACES)
        .map(([k, v]) => `${k}:${v.decl.length + v.consumer.length}`)
        .sort(),
    ).toEqual([
      'pages/knowledge/services/wiki.ts:13',
      'services/glossary.ts:2',
      'services/knowledgeQueryApi.ts:3',
    ]);
    const all = Object.values(HEAD_FACES).flatMap((v) => [...v.decl, ...v.consumer]);
    expect(all.length).toBe(18);
    for (const v of all) expect(inFamily(v)).toBe(true);
    // 快照必须是多重集而非去重集合（有重复腿），否则清空成一组唯一值也能骗过守恒腿
    expect(new Set(all).size, '快照必须有重复腿').toBeLessThan(all.length);
    expect(Object.values(WT_SITES_PER_FILE).reduce((a, b) => a + b, 0)).toBe(19);
    // 四个族的腿都必须还在（glossary 子路径 9 条是 wiki 的主体，掉一条即说明减法筛到了别的族）
    expect(all.filter((v) => v.startsWith(`${G}/`)).length).toBe(9);
    expect(all.filter((v) => v === Q).length).toBe(3);
    expect(all.filter((v) => v === R).length).toBe(3);
  });
});
