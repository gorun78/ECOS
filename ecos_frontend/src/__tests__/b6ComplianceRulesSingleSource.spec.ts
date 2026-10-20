// @vitest-environment node
/**
 * 知识域规则仓族基址单源守恒门（#145/B6-7，差距台账 §13.2.4 B6 腿）。
 *
 * <p>本批把棘轮基线里最后一个知识域重复族 `/v1/knowledge/compliance-rules` 收口成常量单源：
 * 改前两枚属主 — `pages/knowledge/services/wiki.ts` 的私有 `const RULES_BASE`（9 枚消费腿）与
 * `pages/knowledge/pages/components/govern/RuleRepoTable.tsx` 的 5 枚内联
 * `` `${API_BASE}/v1/knowledge/compliance-rules…` `` 腿。单源＝把 wiki 那枚**改名并 export** 为
 * `COMPLIANCE_RULES_BASE`（B6-6 判例：属主＝该族原服务拆分文件），页面侧改成 import
 * （dupBoth 18 → 17、属主 36 → 34）。</p>
 *
 * <p>**属主层位判据**（沿用 #145/B6-4/B6-6 口径：属主 = 该族原服务文件，门面不是属主）：
 * wiki.ts 本来就持有该族声明、且只 import `api` + `services/*` + 类型，不依赖任何页面组件 ⇒
 * RuleRepoTable 引它方向是"页面引服务"、不成环。未上收到 `api/knowledgeClient.ts` 是因为
 * `api/__tests__/apiDomainBaseSingleSource.spec.ts` 逐文件钉 api 域书写面，横插一枚常量会改兄弟门面值。</p>
 *
 * <p>**本批守恒形状与 B6-1~B6-6 都不同**：声明位 **1 → 1**（既不是 B6-1~B6-5 的「N 枚留 1 枚」，
 * 也不是 B6-6 的「新增 2 枚」），消费腿 **13 → 13** 逐字不变 ⇒ 面总量 **15 → 15 一分未动**。
 * 属主从 2 降到 1 全靠页面侧 5 枚内联腿改引常量。所以本门的判据强度不在"扣了几条"，
 * 而在"换了写法之后运行期串完全没变"。</p>
 *
 * <p>**跨行模板腿是本批独有形状**（全仓唯一一枚）：`RuleRepoTable.tsx:72` 那条列表腿
 * `` `${COMPLIANCE_RULES_BASE}?${ …跨 4 行… }`.replace('?&', '?') `` 的行级正吃不进换行
 * （#143/B5-26 门已为此单独设腿）。本门因此把面拆成 **decl / consumer / multi / off** 四箱：
 * 开口行按左半还原成 `…compliance-rules?${` 归 `multi`，后 4 行按快照逐字比；
 * 并且**开口行绝不进裸名计数**——朴素裸名计数会把这一行的 `${COMPLIANCE_RULES_BASE}` 吃成一条
 * 裸引用腿（实测虚增 1 条，是本批起草时自捕的判据缺陷 C），虚增正好会把"面值不变"读成绿。</p>
 *
 * <p>三处判据缺陷形状都在反向用例里钉死：<b>A. 私设基址判据按族取范围</b>（拿整段路径体相等判，
 * 不吃别族声明）；<b>B. 双前缀判据不得走族内过滤后的面</b>（`/api/api/v1/…` 不落本族，
 * `expect(faces).toEqual([])` 恒真＝假判据，必须判 `off` 箱有内容）；<b>C. 跨行开口行的裸名双计</b>
 * （见上段）。与 D-B5-40 同形：「看着在判、其实在放过」多出在<b>匹配边界</b>与<b>过滤后再断言空</b>。</p>
 *
 * <p>与棘轮的分工：本批收口后，棘轮族面第一次降两枚（256→254）——掉的不是本族，而是只由
 * RuleRepoTable 一家书写的 `/…/{}` 与 `/…?{}` 两枚同根子族（经属主常量拼出的腿对
 * `familiesOf` 隐形）。那属可见性变化、不是债多还两族，账只记 dupBoth 18→17 与属主 36→34；
 * 隐形带来的"请求面是否被吃掉"只能由本门这条 15→15 逐字守恒证明。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { COMPLIANCE_RULES_BASE } from '../pages/knowledge/services/wiki';

// 本门位于 `src/__tests__/`，到 `src/` 的相对深度是一档
const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));

const C = '/api/v1/knowledge/compliance-rules';
/** 族路径体（不含 `/api`）——私设族根判据的唯一范围（缺陷 A）。 */
const FAM_PATH = '/v1/knowledge/compliance-rules';
const OWNER = 'COMPLIANCE_RULES_BASE';
const OLD_OWNER = 'RULES_BASE';
const WIKI_FILE = 'pages/knowledge/services/wiki.ts';
const PAGE_FILE = 'pages/knowledge/pages/components/govern/RuleRepoTable.tsx';
/** 本批两个生产文件（族体内只有这两处出现）。 */
const FILES: readonly string[] = [WIKI_FILE, PAGE_FILE];
const OWNER_NAMES: readonly string[] = [OWNER];

/**
 * 工作树每文件应还原出的族腿条数（探针实跑读出）：wiki 1 声明 + 9 消费 = 10；
 * 页面 4 行内消费 + 1 跨行开口 = 5。改前后逐字同值 ⇒ 面总量 15 → 15。
 */
const WT_SITES_PER_FILE: Readonly<Record<string, number>> = {
  [WIKI_FILE]: 10,
  [PAGE_FILE]: 5,
};

const HARDCODED_RE = /["'`]\/api\b/;
/** ⑧ 属主声明行（名字白名单式，绝不写成 `const X =` 通用形——`const resp = await …` 会误判成声明行而漏计裸引用腿）。 */
const OWNER_DECL_RE = new RegExp(String.raw`^\s*(?:export\s+)?const\s+${OWNER}\b`);
/** 本地私设族根声明（`` const X = `${API_BASE}<路径体>` ``，页面侧必须归零的那一类）。 */
const LOCAL_BASE_DECL_RE = /^\s*(?:export\s+)?const\s+[A-Za-z0-9_]+\s*=\s*`\$\{API_BASE\}([^`]*)`;/;
const SITE_RE = new RegExp(
  '`\\$\\{(' + [...OWNER_NAMES, 'API_BASE'].sort((a, b) => b.length - a.length).join('|') + ')' +
    '\\}((?:\\\\.|(?!`)[^\\\\])*)`',
  'g',
);
/** 跨行模板开口行：反引号不配对，本行只到左半为止（缺陷 C 的处置入口）。 */
const OPENER_RE = new RegExp(
  '`\\$\\{(' + [...OWNER_NAMES, 'API_BASE'].sort((a, b) => b.length - a.length).join('|') + ')\\}([^`]*)$',
);
const NO_SLASH_AFTER_BASE = /`\$\{API_BASE\}(?!\/)/;
const DOUBLE_PREFIX_RE = /\/api\/api\b/;
/** 旧名必须彻底消失；`\b` 不会在 `COMPLIANCE_RULES_BASE` 里匹配到 `RULES_BASE`（前置 `_` 非边界）。 */
const OLD_NAME_RE = new RegExp(String.raw`\b${OLD_OWNER}\b`);
/** ⑤ 旧名 `\b` 自证：新名内含旧名子串，但不得被旧名判据吃到（否则"归零"腿恒真）。 */
const NEW_NAME_LINE = 'const COMPLIANCE_RULES_BASE = `${API_BASE}/v1/knowledge/compliance-rules`;';

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

/** 面值解析表：`API_BASE` 单源 + 属主常量（真 import 进来，面值即运行期取值）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  [OWNER, COMPLIANCE_RULES_BASE],
]);

/** 族内判定（**边界式**，不是裸前缀）：`?` 分支收列表腿，`$` 分支收 `${OWNER}${path}` 形变体。 */
function inFamily(val: string): boolean {
  return val === C || val.startsWith(`${C}/`) || val.startsWith(`${C}?`) || val.startsWith(`${C}$`);
}

/** 行内反引号是否不配对（不配对＝模板字面量跨到后续行，本行只能按左半还原）。 */
function hasOpenBacktick(line: string): boolean {
  let n = 0;
  for (let i = 0; i < line.length; i += 1) {
    if (line[i] === '`' && line[i - 1] !== '\\') n += 1;
  }
  return n % 2 === 1;
}

interface FaceBins {
  /** 族根声明位（本批 1→1，属主声明位不增不减） */
  decl: string[];
  /** 行内消费者腿（改前后必须逐字相等的 13 条在这里） */
  consumer: string[];
  /** 跨行模板开口的左半（全仓唯一一枚，本批新增形状） */
  multi: string[];
  /** 已知常量的还原值但不落本族（wiki 的 nav 两枚、畸形双前缀在这里现形，见文件头缺陷 B） */
  off: string[];
}

/**
 * 还原整文件面，按「族根声明位 / 行内消费腿 / 跨行开口 / 族外」分箱。
 * 顺序即判据：跨行开口行先单独还原并跳过裸名计数（缺陷 C）→ 再摘行内模板腿（同行的裸名才是另一条腿）
 * → 声明行的名字 token 不再计（⑧）→ import 语句整条先剥（⑦，否则 import 行读成一次请求）。
 */
function resolveFaces(src: string): FaceBins {
  const bins: FaceBins = { decl: [], consumer: [], multi: [], off: [] };
  for (const line of stripImports(src)) {
    if (hasOpenBacktick(line)) {
      const m = line.match(OPENER_RE);
      const base = m ? CONST_VALUES.get(m[1]) : undefined;
      if (m && base !== undefined) {
        const val = base + m[2];
        (inFamily(val) ? bins.multi : bins.off).push(val);
      }
      continue;
    }
    const isDeclLine = OWNER_DECL_RE.test(line);
    let rest = line;
    for (const match of line.matchAll(SITE_RE)) {
      const base = CONST_VALUES.get(match[1]);
      if (base !== undefined) {
        const val = base + match[2];
        if (inFamily(val)) (isDeclLine ? bins.decl : bins.consumer).push(val);
        else bins.off.push(val);
      }
      rest = rest.split(match[0]).join('');
    }
    if (isDeclLine) continue;
    for (const name of OWNER_NAMES) {
      const cnt = rest.split(name).length - 1;
      for (let i = 0; i < cnt; i += 1) bins.consumer.push(CONST_VALUES.get(name)!);
    }
  }
  return bins;
}

/** 族内三面合并后的有序面值多重集（守恒腿用）。 */
function faces(src: string): string[] {
  const { decl, consumer, multi } = resolveFaces(src);
  return [...decl, ...consumer, ...multi].sort();
}

/**
 * 改前快照（HEAD 探针实跑读出，15 条 = wiki 10／页面 5，`${…}` 按字面保留）：
 * wiki 私有族根声明 1 + 9 枚消费腿（`?${query}`×2、裸引用×3、`/${id}`×2、`/${ruleId}/versions`×2）；
 * 页面 4 枚行内腿 + 1 枚跨行开口（左半 `…compliance-rules?${`）。
 * 分箱与 `resolveFaces` 同一判据（族根声明行归 decl）。
 */
const HEAD_FACES: Readonly<Record<string, { decl: readonly string[]; consumer: readonly string[]; multi: readonly string[] }>> = {
  [WIKI_FILE]: {
    decl: [C],
    consumer: [
      `${C}?$\{query}`,
      C,
      `${C}?$\{query}`,
      C,
      C,
      `${C}/$\{id}`,
      `${C}/$\{id}`,
      `${C}/$\{ruleId}/versions`,
      `${C}/$\{ruleId}/versions`,
    ],
    multi: [],
  },
  [PAGE_FILE]: {
    decl: [],
    consumer: [C, `${C}/$\{editingId}`, `${C}/$\{r.id}`, `${C}/$\{r.id}`],
    multi: [`${C}?$\{`],
  },
};

const HEAD_DECL_TOTAL = 1;
const HEAD_CONSUMER_TOTAL = 13;
const HEAD_MULTI_TOTAL = 1;

/**
 * 跨行腿整块快照（HEAD 探针实跑读出，5 行）：只有首行的基址写法变了，
 * 后 4 行（插值体 + `.replace('?&', '?')` 收尾）必须逐字未动。
 */
const HEAD_MULTILINE_BLOCK: readonly string[] = [
  '        `${API_BASE}/v1/knowledge/compliance-rules?${',
  '          (keywords ? `keyword=${encodeURIComponent(keywords)}` : \'\') +',
  "          (keywords && domainFilter ? '&' : '') +",
  '          (domainFilter ? `domain=${encodeURIComponent(domainFilter)}` : \'\')',
  "        }`.replace('?&', '?'),",
];

/** 工作树首行（改后写法）＝快照首行把内联基址换成属主常量（needle 含插值的收尾 `}`，少一枚即拼出坏串）。 */
const WT_MULTILINE_FIRST = HEAD_MULTILINE_BLOCK[0].replace('`${API_BASE}/v1/knowledge/compliance-rules', '`${COMPLIANCE_RULES_BASE}');

describe('知识域 compliance-rules 族基址单源 — 面值守恒 (#145/B6-7)', () => {
  it('前提：API_BASE = "/api"、属主常量面值 = 改前族基址，且声明写成 `${API_BASE}` 裸派生态（族面对棘轮可见的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(COMPLIANCE_RULES_BASE).toBe(C);
    const declLines = codeLines(read(WIKI_FILE)).filter((l) => OWNER_DECL_RE.test(l));
    expect(declLines.length).toBe(1);
    expect(declLines[0]).toBe('export const COMPLIANCE_RULES_BASE = `${API_BASE}/v1/knowledge/compliance-rules`;');
  });

  it('属主唯一：全 src 生产面里含该族族体的代码行只剩 1 枚（= wiki 声明行；页面侧 5 枚内联腿已全部改引常量）', () => {
    const hits: string[] = [];
    for (const rel of walk('.', [])) {
      if (rel === 'services/httpClient.ts') continue;
      for (const line of codeLines(read(rel))) {
        if (line.includes(FAM_PATH)) hits.push(`${rel} — ${line.trim()}`);
      }
    }
    expect(hits.sort(), `族体字面量散点未收口：\n${hits.join('\n')}`).toEqual([
      `${WIKI_FILE} — export const COMPLIANCE_RULES_BASE = \`\${API_BASE}/v1/knowledge/compliance-rules\`;`,
    ]);
  });

  it('两文件代码行零私设基址（注释里的 /api 端点说明不计）', () => {
    for (const rel of FILES) {
      const hits = codeLines(read(rel)).filter((l) => HARDCODED_RE.test(l));
      expect(hits, `${rel} 仍有 ${hits.length} 处代码行 "/api 字面量`).toEqual([]);
    }
  });

  it('来源唯一：wiki 声明恰 1 行、页面零本地族根声明、页面导入腿恰 1 条且取自该族属主、旧名 RULES_BASE 全面归零', () => {
    expect(
      codeLines(read(WIKI_FILE)).filter((l) => new RegExp(String.raw`^\s*(?:export\s+)?const\s+${OWNER}\b`).test(l)).length,
      `${OWNER} 的属主声明必须恰 1 行`,
    ).toBe(1);
    const pageDecls = codeLines(read(PAGE_FILE)).filter((l) => {
      const m = l.match(LOCAL_BASE_DECL_RE);
      return m !== null && m[1] === FAM_PATH;
    });
    expect(pageDecls, `${PAGE_FILE} 不得再私设 ${FAM_PATH} 族根`).toEqual([]);
    const imports = codeLines(read(PAGE_FILE)).filter((l) => l.trimStart().startsWith('import ') && l.includes(OWNER));
    expect(imports.length, `${PAGE_FILE} 只能有一条 ${OWNER} 导入腿`).toBe(1);
    expect(imports[0], `${PAGE_FILE} 的 ${OWNER} 未取自该族属主`).toMatch(/from '[^']*services\/wiki';/);
    // 旧名归零（缺陷 ⑤ 自证：`\b` 不吃新名内含的 `RULES_BASE` 子串，所以这条不是恒真）
    for (const rel of FILES) {
      expect(codeLines(read(rel)).filter((l) => OLD_NAME_RE.test(l)), `${rel} 旧名 ${OLD_OWNER} 未归零`).toEqual([]);
    }
    expect(OLD_NAME_RE.test(NEW_NAME_LINE)).toBe(false);
    expect(NEW_NAME_LINE.includes(OLD_OWNER)).toBe(true);
  });

  it('面值逐字不变（消费面 13 = 13）：工作树行内消费腿与改前多重集逐字相等，且逐文件条数吻合（10／5 含跨行一枚）', () => {
    const wtConsumer: string[] = [];
    for (const rel of FILES) {
      const { decl, consumer, multi } = resolveFaces(read(rel));
      expect(decl.length + consumer.length + multi.length, `${rel} 族腿条数不符`).toBe(WT_SITES_PER_FILE[rel]);
      wtConsumer.push(...consumer);
    }
    const headConsumer = Object.values(HEAD_FACES).flatMap((v) => [...v.consumer]);
    expect(headConsumer.length).toBe(HEAD_CONSUMER_TOTAL);
    expect(wtConsumer.length).toBe(HEAD_CONSUMER_TOTAL);
    expect([...wtConsumer].sort()).toEqual([...headConsumer].sort());
  });

  it('面值逐字不变（声明面 1 = 1）：属主声明位不增不减（与 B6-6 的"新增 2 枚声明"形状相对），面总量 15→15', () => {
    const headDecl = Object.values(HEAD_FACES).flatMap((v) => [...v.decl]).sort();
    const wtDecl: string[] = [];
    for (const rel of FILES) wtDecl.push(...resolveFaces(read(rel)).decl);
    expect(headDecl, '改前族根声明恰 1 枚（wiki 私有 RULES_BASE）').toEqual([C]);
    expect(headDecl.length).toBe(HEAD_DECL_TOTAL);
    expect([...wtDecl].sort(), '改后族根声明必须恰是 1 枚且同值').toEqual([C]);
    expect(wtDecl.length).toBe(HEAD_DECL_TOTAL);
    // 三面合计：15 = 1 + 13 + 1，改前后同账 ⇒ 差额零，本批纯写法收口
    expect(HEAD_DECL_TOTAL + HEAD_CONSUMER_TOTAL + HEAD_MULTI_TOTAL).toBe(15);
    expect(
      FILES.reduce((n, rel) => {
        const b = resolveFaces(read(rel));
        return n + b.decl.length + b.consumer.length + b.multi.length;
      }, 0),
    ).toBe(15);
    expect(FILES.reduce((n, rel) => n + faces(read(rel)).length, 0)).toBe(15);
  });

  it('跨行腿守恒：左半还原值改前后逐字相等 + 后 4 行插值体一字未动（全仓唯一一枚跨行站点）', () => {
    const lines = stripImports(read(PAGE_FILE));
    const i = lines.indexOf(WT_MULTILINE_FIRST);
    expect(i, `页面侧跨行列表腿首行必须恰在位（改后写法）`).toBeGreaterThanOrEqual(0);
    expect(lines.filter((l) => l === WT_MULTILINE_FIRST).length, '跨行开口首行必须恰 1 条').toBe(1);
    expect(lines.slice(i + 1, i + HEAD_MULTILINE_BLOCK.length), '跨行腿后 4 行插值体必须与改前逐字相同').toEqual(
      HEAD_MULTILINE_BLOCK.slice(1),
    );
    // 整块（含首行）换写法后必须还原成与改前逐字相同的串——只允许基址写法变，插值体不许动
    expect(WT_MULTILINE_FIRST.replace('`${COMPLIANCE_RULES_BASE}', C)).toBe(
      HEAD_MULTILINE_BLOCK[0].replace('`${API_BASE}/v1/knowledge/compliance-rules', C),
    );
    const { multi } = resolveFaces(read(PAGE_FILE));
    expect(multi, '跨行开口腿必须落在 multi 箱且恰 1 条').toEqual([`${C}?$\{`]);
    expect(multi.length).toBe(HEAD_MULTI_TOTAL);
  });

  it('派生形态唯一：`${API_BASE}` 后必接 /，族外箱内容可枚举（nav 两枚）且零 /api/api 双前缀', () => {
    const bad: string[] = [];
    for (const rel of FILES) {
      for (const line of codeLines(read(rel))) {
        if (NO_SLASH_AFTER_BASE.test(line)) bad.push(`${rel}: ${line.trim()}`);
      }
      const { off } = resolveFaces(read(rel));
      for (const v of off) if (DOUBLE_PREFIX_RE.test(v)) bad.push(`${rel}: 还原值 ${v}`);
    }
    expect(bad, `派生态畸形：\n${bad.join('\n')}`).toEqual([]);
    // 缺陷 B 的反证：off 箱必须真吃到东西（wiki 的 nav 两枚同根异族），否则「族外零违例」是恒真空话
    const wikiOff = resolveFaces(read(WIKI_FILE)).off;
    expect([...wikiOff].sort()).toEqual([
      '/api/v1/knowledge/nav/categories?$\{params.toString()}',
      '/api/v1/knowledge/nav/products?$\{params.toString()}',
    ]);
    expect(resolveFaces(read(PAGE_FILE)).off).toEqual([]);
  });

  it('判据能变红：声明行不双计(⑧)、裸引用计腿、跨行开口不吃裸名(⑤)、族边界不吃同根异族、双前缀只在族外箱现形（防恒绿）', () => {
    const EMPTY: FaceBins = { decl: [], consumer: [], multi: [], off: [] };
    // ⑧ 属主声明行 = 1 条 decl 腿，名字 token 不再造第二条
    expect(resolveFaces(NEW_NAME_LINE)).toEqual({ decl: [C], consumer: [], multi: [], off: [] });
    // 同一行「模板腿 + 裸名」= 两条腿
    expect(resolveFaces('  const u = [`${COMPLIANCE_RULES_BASE}/x`, COMPLIANCE_RULES_BASE];')).toEqual({
      decl: [],
      consumer: [`${C}/x`, C],
      multi: [],
      off: [],
    });
    // 裸引用（消费者侧形态）计一条腿；import 行不计（⑦）
    expect(resolveFaces('  await apiFetchData<void>(COMPLIANCE_RULES_BASE, { method: "DELETE" });')).toEqual({
      decl: [],
      consumer: [C],
      multi: [],
      off: [],
    });
    expect(resolveFaces("import { COMPLIANCE_RULES_BASE } from '../../../services/wiki';")).toEqual(EMPTY);
    // ⑤ 跨行开口行：只归 multi，绝不进裸名计数（朴素裸名计数会在这里虚增 1 条＝本批实测到的判据缺陷）
    expect(resolveFaces(WT_MULTILINE_FIRST)).toEqual({ decl: [], consumer: [], multi: [`${C}?$\{`], off: [] });
    expect(resolveFaces(WT_MULTILINE_FIRST).consumer.length, '开口行裸名虚增自检').toBe(0);
    // 族边界：同根异族不得混计（`-sets` 后缀族 / 完全无关族），`?` 与 `${` 形变体必须收进来
    expect(inFamily('/api/v1/knowledge/compliance-rulesets')).toBe(false);
    expect(inFamily('/api/v1/knowledge/nav/products')).toBe(false);
    expect(inFamily(`${C}/$\{id}`)).toBe(true);
    expect(inFamily(`${C}?$\{q}`)).toBe(true);
    expect(inFamily(`${C}$\{path}`)).toBe(true);
    // 未知常量不吃（防把任意 `${x}` 吃成 URL）
    expect(resolveFaces('  const q = `${SOME_UNKNOWN_BASE}/v1/knowledge/compliance-rules`;')).toEqual(EMPTY);
    // `const resp = await …` 不是声明行：通用 `const X =` 形会把同行裸引用腿吃掉（本门曾实测到的判据缺陷形状）
    expect(resolveFaces('  const resp = await apiFetchData(COMPLIANCE_RULES_BASE);').consumer).toEqual([C]);
    // 双前缀畸形（B5-26 实证过的自伤形状）在族内面看不见：过滤后必为 []（＝假判据形状，缺陷 B），
    // 真正抓到它的是族外箱 ⇒ 本条同时证明「off 有内容」而非「faces 恒空」
    const DOUBLE = '  const p = `${API_BASE}/api/v1/knowledge/compliance-rules`;';
    expect(HARDCODED_RE.test(DOUBLE)).toBe(false);
    expect(faces(DOUBLE)).toEqual([]);
    expect(resolveFaces(DOUBLE)).toEqual({ decl: [], consumer: [], multi: [], off: [`${API_BASE}/api/v1/knowledge/compliance-rules`] });
    expect(DOUBLE_PREFIX_RE.test(CONST_VALUES.get('API_BASE')! + '/api/v1/knowledge/compliance-rules')).toBe(true);
    // 私设基址形态必须被清零腿判出
    expect(codeLines('  await fetch("/api/v1/knowledge/compliance-rules");').filter((l) => HARDCODED_RE.test(l)).length).toBe(1);
    // 缺陷 A 的反向用例：按族取范围才不误食（本族声明命中、nav 族根不命中）
    const own = 'const LOCAL = `${API_BASE}/v1/knowledge/compliance-rules`;'.match(LOCAL_BASE_DECL_RE);
    const nav = 'const NAV = `${API_BASE}/v1/knowledge/nav`;'.match(LOCAL_BASE_DECL_RE);
    expect(own?.[1]).toBe(FAM_PATH);
    expect(nav?.[1] === FAM_PATH).toBe(false);
  });

  it('快照自身可校验：15 条按 10／5 分布、三面 1/13/1 且全部落本族内、必须有重复腿（防快照清空成恒真）', () => {
    expect(
      Object.entries(HEAD_FACES)
        .map(([k, v]) => `${k}:${v.decl.length + v.consumer.length + v.multi.length}`)
        .sort(),
    ).toEqual([
      'pages/knowledge/pages/components/govern/RuleRepoTable.tsx:5',
      'pages/knowledge/services/wiki.ts:10',
    ]);
    const all = Object.values(HEAD_FACES).flatMap((v) => [...v.decl, ...v.consumer, ...v.multi]);
    expect(all.length).toBe(15);
    for (const v of all) expect(inFamily(v)).toBe(true);
    expect(new Set(all).size, '快照必须有重复腿').toBeLessThan(all.length);
    expect(Object.values(WT_SITES_PER_FILE).reduce((a, b) => a + b, 0)).toBe(15);
    // 形状分布：裸基址 5 枚（1 枚属主声明 + 4 枚裸引用/空尾巴腿）、`?${query}` 2 枚、`/${id}` 2 枚、
    // `/${ruleId}/versions` 2 枚、`/${editingId}` 1 枚、`/${r.id}` 2 枚、跨行左半 1 枚
    // ⇒ 合计 15，掉任一形状即说明快照被筛错
    expect(all.filter((v) => v === C).length).toBe(5);
    expect(all.filter((v) => v === `${C}?$\{query}`).length).toBe(2);
    expect(all.filter((v) => v === `${C}/$\{id}`).length).toBe(2);
    expect(all.filter((v) => v === `${C}/$\{ruleId}/versions`).length).toBe(2);
    expect(all.filter((v) => v === `${C}/$\{editingId}`).length).toBe(1);
    expect(all.filter((v) => v === `${C}/$\{r.id}`).length).toBe(2);
    expect(all.filter((v) => v === `${C}?$\{`).length).toBe(1);
    // 快照首行必须能还原成改前写法（防止日后有人顺手把快照里的改前串也"现代化"了）
    expect(HEAD_MULTILINE_BLOCK[0]).toContain('${API_BASE}');
    expect(HEAD_MULTILINE_BLOCK.length).toBe(5);
    expect(WT_MULTILINE_FIRST).toContain('${COMPLIANCE_RULES_BASE}');
  });
});
