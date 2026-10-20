// @vitest-environment node
/**
 * `/v1/audit/stats` 族基址单源回归门（#145/B6-13，差距台账 §13.2.4 C1 腿、B6 存量债第十三批）。
 *
 * <p>改前两属主＝`api/securityClient.ts` 的 `fetchAuditStats` 行内腿与 `pages/SecurityAudit.tsx`
 * 自己的直连原语腿（该页刻意不经 `fetchAuditStats`，因为后者在 transport 层把失败吞成零值 ⇒
 * 「今日 0 条审计」假安静）。属主按 import 图裁到 api 层：页面 → `../api` 门面 → `api/securityClient.ts`
 * 不成环，且该文件本就是此端点的正典实现面。门面 `export * from './api/securityClient'` 行不含 URL 体
 * ⇒ 不是第二属主（B6-5 ②／B6-9 ② 判例）。页面改裸引用后，<b>绕开门面的语义一点没动</b>——本门第
 * 「行为保真」腿钉死它仍调 `apiFetchData`、仍不引用 `fetchAuditStats`，否则单源化会顺手把假安静改回来。</p>
 *
 * <p><b>本批固定的第三种形状（与 B6-11/B6-12 的「改名并 export 既有私有常量」不同）</b>：属主声明位
 * 是**净新增**（改前该面值只作为消费腿存在，文件里没有这一行），但面值本来就在这域的在册腿里 ⇒
 * `api/__tests__/apiDomainBaseSingleSource.spec.ts` 的 `OWNER_DECL_ADDITIONS` 台账**不登记**：
 * 那本账只登「面值原本不在本域任何腿上」的声明位（B6-9 的 `DATA_ENGINE_SETTINGS_BASE`），
 * 登记会让它的期望多重集变成 83＋2＝85 而实跑读 84 ⇒ 假红。两把尺子因此对本批给出不同读数，
 * 且都正确：api 域派生态计数 20→20（腿搬成声明）、C1 全域 302→301（页面腿 1→0）、
 * 棘轮 dupBoth 5→4／属主 10→8。本门把这些读数差具名钉住，防止后来者拿其中一个数当"进度"。</p>
 *
 * <p>派生只有一种正确性可证：**运行期面值逐字不变**。本门按常量实际取值还原每个派生点，与改前快照
 * （2 条，2026-10-20 由双模读出器对 `git show HEAD:` 树与工作树各跑一遍、两侧多重集完全一致后落笔）
 * 做排序多重集比对并逐文件核对条数。判据形状沿用前批挣来的七条（剥 import 先于按行扫、绑定行不算腿、
 * 裸引用算腿、族键按归一化判等、模板腿先摘除后再数裸名、属性访问前缀跳过、导入腿判据必须走保留
 * import 原文的 `declLines`），本批另补一条：<b>裸名判据必须有标识符右边界</b>，
 * 否则 `AUDIT_STATS_BASEX` 这类更长标识符会被 `split(name)` 误计成腿。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { AUDIT_STATS_BASE } from '../api/securityClient';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const OWNER = 'api/securityClient.ts';
const CONSUMER = { rel: 'pages/SecurityAudit.tsx', importPath: '../api', name: 'AUDIT_STATS_BASE' };
const SCOPED = [OWNER, CONSUMER.rel];

/** 本批收口的族键（归一化后，与 `urlFamilyDualFormRatchet` 同口径）。 */
const CLOSED_FAM = '/v1/audit/stats';

/** 私设基址判据：引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** ① 整条 import 先剥（含多行花括号体）：只过滤行首会把续行 `NAME,` 吃成假腿。 */
const IMPORT_STMT_RE = /^\s*import\s[\s\S]*?;/gm;
/** ② 绑定行：派生基址声明 / 名别名（只入解析表，不构成腿）。 */
const DERIVED_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`\$\{API_BASE\}([^`]*)`;?\s*$/;
const ALIAS_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+);?\s*$/;
/** ③ 派生腿：`` `${任意常量名}尾串` ``（尾串允许转义字符，不允许裸反引号）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
/** ④ 族根体（属主唯一判据）：裸形态与 `${API_BASE}` 派生态都计。 */
const ROOT_RAW_RE = /["'`]\/api\/v1\/audit\/stats["'`]/g;
const ROOT_DER_RE = /`\$\{API_BASE\}\/v1\/audit\/stats`/g;
/** ⑤ 属主声明形状（改名不回退裸字面量的判据）。 */
const OWNER_DECL_NEEDLE = 'export const AUDIT_STATS_BASE = `' + '${API_BASE}/v1/audit/stats`;';

/** 解析表种子：两枚常量的运行期真实取值（不写死字面串，防止「常量值漂了但快照照登」）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['AUDIT_STATS_BASE', AUDIT_STATS_BASE],
]);

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.replace(IMPORT_STMT_RE, '').replace(/\r/g, '').split('\n').filter((l) => !isComment(l));
}

/** 判据第七条：导入腿判据必须走保留 import 原文的行集（剥 import 的集合里导入恒不可见）。 */
function declLines(src: string): string[] {
  return src.replace(/\r/g, '').split('\n').filter((l) => !isComment(l));
}

/** `${…}` 逐层归一为 `{}`，与棘轮门的族键口径一致（最多 5 层，防嵌套残留）。 */
function normalize(v: string): string {
  let s = v;
  for (let i = 0; i < 5 && /\$\{[^{}]*\}/.test(s); i += 1) s = s.replace(/\$\{[^{}]*\}/g, '{}');
  return s.replace(/\$\{[^{}]*\}/g, '{}');
}

function read(rel: string): string {
  return readFileSync(join(SRC_ROOT, rel), 'utf8');
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
    if (statSync(full).isDirectory()) {
      walk(`${dirRoot}/${entry}`, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(SRC_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
}

/** 族成员判定：剥基址前缀后按归一化族键**判等**（前缀匹配会吃进同根子路径族）。 */
function inClosed(val: string): boolean {
  return normalize(val).replace(/^\/api/, '') === CLOSED_FAM;
}

/** 把派生点还原成运行期面值；绑定行不计，模板腿先从行内摘除后再数裸名引用。 */
function famFaces(rel: string, src?: string): string[] {
  const code = src ?? read(rel);
  const out: string[] = [];
  for (const line of codeLines(code)) {
    if (DERIVED_DECL_RE.test(line) || ALIAS_DECL_RE.test(line)) continue;
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      rest = rest.split(m[0]).join('');
      const base = CONST_VALUES.get(m[1]);
      const val = base === undefined ? `\${${m[1]}}${m[2]}` : base + m[2];
      if (inClosed(val)) out.push(val);
    }
    for (const [name, val] of CONST_VALUES) {
      if (name === 'API_BASE' || !inClosed(val)) continue;
      const parts = rest.split(name);
      for (let i = 0; i < parts.length - 1; i++) {
        const before = parts[i].slice(-1);
        const after = parts[i + 1].slice(0, 1);
        if (before === '{' || before === '.') continue;
        if (/[A-Za-z0-9_$]/.test(after)) continue;
        out.push(val);
      }
    }
  }
  return out.sort();
}

/** 族根体构造点计数：裸形态 + `${API_BASE}` 派生态（属主唯一性判据）。 */
function rootBodySites(code: string): string[] {
  const hits: string[] = [];
  for (const line of codeLines(code)) {
    for (const m of line.matchAll(ROOT_RAW_RE)) hits.push(m[0]);
    for (const m of line.matchAll(ROOT_DER_RE)) hits.push(m[0]);
  }
  return hits;
}

/** 行内 `` `${API_BASE}` `` 站点数（与 C1 门同口径的搬运可见性判据）。 */
function inlineApiBaseSites(code: string): number {
  return codeLines(code).filter((l) => l.includes('${API_BASE}')).length;
}

/**
 * 改前快照（2 条，双模读出器实跑）：属主 1（`fetchAuditStats` 行内腿）+ 消费者 1（页面直连原语腿）。
 * 本族无同根子路径腿，故不需要 B6-12 的第二级前缀快照。
 */
const HEAD_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [OWNER]: ['/api/v1/audit/stats'],
  'pages/SecurityAudit.tsx': ['/api/v1/audit/stats'],
});
const HEAD_TOTAL = 2;

describe('audit/stats 域基址单源 — /v1/audit/stats (#145/B6-13)', () => {
  it('前提：常量运行期面值与 API_BASE 单源取值一致（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(AUDIT_STATS_BASE).toBe('/api/v1/audit/stats');
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（2 条）且逐文件条数对得上', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = famFaces(rel);
      expect(vals, `${rel} 族面值多重集应等于改前快照`).toEqual([...HEAD_FACES[rel]].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_TOTAL);
  });

  it('属主唯一：族根体只允许 securityClient 书写（全仓生产文件扫，裸形态与 ${API_BASE} 派生态都计）', () => {
    const offenders: string[] = [];
    const files = walk('.', []);
    expect(files.length, '扫描根漂移 ⇒ 属面判据会退化成恒真').toBeGreaterThan(600);
    for (const rel of files) {
      if (rel === 'services/httpClient.ts') continue;
      const hits = rootBodySites(read(rel));
      if (hits.length === 0 || rel === OWNER) continue;
      offenders.push(`${rel} -> ${JSON.stringify(hits)}`);
    }
    expect(offenders, '族根体出现第二生产方即红').toEqual([]);
    expect(rootBodySites(read(OWNER)).length, '属主应恰有一枚族根声明').toBe(1);
  });

  it('声明形状不回退：属主以 export 的 ${API_BASE} 派生态书写，且 api 门面重导出行不是第二属主', () => {
    expect(read(OWNER)).toContain(OWNER_DECL_NEEDLE);
    const facade = read('api.ts');
    expect(facade).toContain(`export * from "./api/securityClient";`);
    expect(rootBodySites(facade), '门面重导出行不得含 URL 体').toEqual([]);
    // 净新增声明位但面值原本就在本域腿上 ⇒ api 域台账不登记，改前/改后本域派生态计数不动（20→20）
    expect(inlineApiBaseSites(read(OWNER))).toBe(20);
    expect(inlineApiBaseSites(read(CONSUMER.rel))).toBe(0);
  });

  it('消费方只有 import 腿：零族根体书写、零私设基址、不得自声明同名常量，且 API_BASE 来源归零', () => {
    const { rel, importPath, name } = CONSUMER;
    const code = read(rel);
    expect(rootBodySites(code), `${rel} 仍在书写族根体`).toEqual([]);
    expect(codeLines(code).filter((l) => HARDCODED_RE.test(l)), `${rel} 仍有私设基址`).toEqual([]);
    expect(code).not.toMatch(/\/api\/api\b/);
    const lines = declLines(code);
    const imports = lines.filter((l) => l.trimStart().startsWith('import ') && l.includes(name));
    expect(imports.length, `${rel} 应有且仅有 1 条 ${name} 导入`).toBe(1);
    expect(imports[0].replace(/"/g, "'"), `${rel} 导入路径不是 ${importPath}`).toContain(`from '${importPath}'`);
    expect(
      lines.filter((l) => l.trimStart().startsWith('import ') && /\bAPI_BASE\b/.test(l)),
      '唯一站点已上收，再留 API_BASE 导入即未用来源',
    ).toEqual([]);
    expect(
      codeLines(code).filter((l) => DERIVED_DECL_RE.test(l) && l.includes(name)),
      `${rel} 不得自声明 ${name}`,
    ).toEqual([]);
  });

  it('行为保真：页面仍走自己的直连原语，不得改回吞错回落零值的 fetchAuditStats 门面', () => {
    const code = read(CONSUMER.rel);
    const lines = codeLines(code);
    expect(lines.some((l) => l.includes('apiFetchData<AuditStats>(AUDIT_STATS_BASE)')), '直连原语腿须仍在').toBe(true);
    const useFacade = lines.filter((l) => /\bfetchAuditStats\b/.test(l));
    expect(useFacade, '引用吞错门面会把「今日 0 条审计」假安静改回来').toEqual([]);
    expect(code).toMatch(/async function loadAuditStats/);
  });

  it('判据能变红：第二裸面写手、别名造腿、前缀相似标识符、注释里的端点说明各造违例且被对应腿吃出（防恒绿）', () => {
    const RAW_DUP = ['const a = "/api/v1/audit/stats";', 'fetch(`${API_BASE}/v1/audit/stats`);'].join('\n');
    expect(rootBodySites(RAW_DUP).length).toBe(2);
    expect(rootBodySites('const t = `${API_BASE}/v1/audit/stats/summary`;')).toEqual([]);
    const ALIAS = [
      "import { API_BASE as API_ROOT } from './services/httpClient';",
      'const A = `${API_ROOT}/v1/audit/stats`;',
      'fetch(A);',
    ].join('\n');
    // 本门属面判据只认裸形态与 `${API_BASE}` 派生态两形（名字轴不在本门重复实现）：别名族根对本门隐形，
    // 但会让 `urlFamilyDualFormRatchet` 的 derivedRe（按文件取 `API_BASE as X` 别名 token 集）读出第二属主
    // ⇒ 基线外新重复族即红；该形状本身还被棘轮「扫描面合规」腿禁绝（prod 面零枚 `API_BASE as X`）。
    expect(rootBodySites(ALIAS)).toEqual([]);
    // 裸名右边界：更长标识符不得吃成腿；属性访问位置不得吃成腿
    expect(famFaces('x.ts', 'const u = AUDIT_STATS_BASEX;')).toEqual([]);
    expect(famFaces('x.ts', 'cfg.AUDIT_STATS_BASE = 1;')).toEqual([]);
    // 合规形态：模板腿 + 裸引用各计 1，绑定行不计
    const CLEAN = [
      'export const AUDIT_STATS_BASE = `${API_BASE}/v1/audit/stats`;',
      'return apiFetchData(AUDIT_STATS_BASE);',
      'return apiFetchData(`${AUDIT_STATS_BASE}`);',
    ].join('\n');
    expect(famFaces('x.ts', CLEAN)).toEqual(['/api/v1/audit/stats', '/api/v1/audit/stats']);
    // 注释不计；漂移即红（写错一段就不再是收口族）
    expect(famFaces('x.ts', '// GET /api/v1/audit/stats 只是注释里的端点说明')).toEqual([]);
    expect(famFaces('x.ts', 'const x = apiFetchData(`${API_BASE}/v1/audit/stat`);')).toEqual([]);
    const ONE_LINE_IMPORT = "import { AUDIT_STATS_BASE } from '../api';";
    expect(codeLines(ONE_LINE_IMPORT), '剥 import 后导入腿恒不可见 ⇒ 导入判据必须走 declLines').toEqual(['']);
    expect(declLines(ONE_LINE_IMPORT).length).toBe(1);
  });

  it('快照自身可校验：2 条 = 属主 1 + 消费者 1 且全部等于族根面值（防快照清空成恒真）', () => {
    const flat = SCOPED.flatMap((rel) => [...HEAD_FACES[rel]]);
    expect(flat.length).toBe(HEAD_TOTAL);
    expect(HEAD_FACES[OWNER].length).toBe(1);
    expect(HEAD_FACES[CONSUMER.rel].length).toBe(1);
    for (const v of flat) expect(v).toBe('/api/v1/audit/stats');
    expect(CLOSED_FAM).toBe('/v1/audit/stats');
    expect(SCOPED.length).toBe(2);
  });
});
