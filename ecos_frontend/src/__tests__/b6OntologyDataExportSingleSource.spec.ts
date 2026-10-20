// @vitest-environment node
/**
 * ontology 域 `/v1/ontology/data` + `/v1/ontology/export` 基址单源回归门（#145/B6-12，
 * 差距台账 §13.2.4 C1 腿、B6 存量债第十二批）。
 *
 * <p>本批把两族的各 2 属主收口为两枚**导出**常量：`services/ontology/ontologyDataApi.ts` 的
 * `ONTOLOGY_DATA_BASE` 与 `services/ontology/ontologyExportApi.ts` 的 `ONTOLOGY_EXPORT_BASE`。
 * 形状与 B6-4 同型但更省：两枚都是**把改前的文件内私有常量改名并加 export**（`DATA_BASE`／
 * `EXPORT_BASE`，同一声明位、面值逐字不变），既删掉第二属主的书写点，又不产生净新增属主枚 ⇒
 * 不需要 B6-9 ③ 那种 `OWNER_DECL_ADDITIONS` 台账。</p>
 *
 * <p>属主仍按 import 图落服务侧：`ontologyWorkbenchApi.ts`（`/v1/ontology/data` 的第二生产方）
 * 与 `pages/knowledge/services/overview.ts`（`/v1/ontology/export` 的第二生产方）反向取分册常量
 * 不成环——前者与本属主同域、后者系 B6-4 已确立的「页面侧深引属主常量」通路。</p>
 *
 * <p>派生只有一种正确性可证：<b>运行期面值逐字不变</b>。本门按两级快照做证：
 * ① <b>族根本体</b>4 条（属主各 1 枚裸 POST 站 + 消费者各 1 条）；
 * ② <b>同根子路径腿</b>合计 15 条（属主 7+6、消费者各 1），因为改名会把子路径腿一起搬到
 * `` `${ONTOLOGY_*_BASE}/…` `` 形态，只钉族根会让"顺手改了子路径面值"读成绿。
 * 两组快照均由双模读出器对 `git show HEAD:` 与工作树各跑一遍、两侧多重集逐文件完全一致后才落笔
 * （实跑读出：8/7/1/1 含属主声明行，剥掉声明行即 7/6/1/1），非手抄。</p>
 *
 * <p>与棘轮账的对应关系（本批口径，勿读成进度）：两族退出 dupBoth（7→5）、BASELINE 属主枚 14→10，
 * 但两枚属主声明仍是 `` `${API_BASE}/v1/…` `` 派生态 ⇒ 族索引面 253 不降；裸引用腿不带 URL 体，
 * 对棘轮的 `familiesOf`/`DER_RE` 隐形，这正是"属主面真的收掉了、可见性没被关掉"的形状差。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { ONTOLOGY_DATA_BASE } from '../services/ontology/ontologyDataApi';
import { ONTOLOGY_EXPORT_BASE } from '../services/ontology/ontologyExportApi';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 两枚属主（族根体唯一书写点）与两枚消费者（只有 import 腿 + 裸引用）。 */
const DATA_OWNER = 'services/ontology/ontologyDataApi.ts';
const EXPORT_OWNER = 'services/ontology/ontologyExportApi.ts';
const CONSUMERS: ReadonlyArray<{ rel: string; importPath: string; name: string }> = [
  { rel: 'services/ontologyWorkbenchApi.ts', importPath: './ontology/ontologyDataApi', name: 'ONTOLOGY_DATA_BASE' },
  { rel: 'pages/knowledge/services/overview.ts', importPath: '../../../services/ontology/ontologyExportApi', name: 'ONTOLOGY_EXPORT_BASE' },
];
const OWNERS: ReadonlyArray<{ rel: string; name: string; legacy: string }> = [
  { rel: DATA_OWNER, name: 'ONTOLOGY_DATA_BASE', legacy: 'DATA_BASE' },
  { rel: EXPORT_OWNER, name: 'ONTOLOGY_EXPORT_BASE', legacy: 'EXPORT_BASE' },
];
const SCOPED = [...OWNERS.map((o) => o.rel), ...CONSUMERS.map((c) => c.rel)];

/** 本批收口的两族族键（归一化后，与 `urlFamilyDualFormRatchet` 同口径）。 */
const CLOSED_FAMS = ['/v1/ontology/data', '/v1/ontology/export'];

/** 私设基址判据：引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** ① 整条 import 先剥（含多行花括号体）：只过滤行首会把续行 `NAME,` 吃成假腿。 */
const IMPORT_STMT_RE = /^\s*import\s[\s\S]*?;/gm;
/** ② 绑定行：派生基址声明 / 名别名（只入解析表，不构成腿）。 */
const DERIVED_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`\$\{API_BASE\}([^`]*)`;?\s*$/;
const ALIAS_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+);?\s*$/;
/** ③ 派生腿：`` `${任意常量名}尾串` ``（尾串允许转义字符，不允许裸反引号）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
/** ④ 族根体（属主唯一判据）：定界符紧接族根，子路径与插值尾巴都不算族根。 */
const ROOT_RAW_RE = /["'`]\/api\/v1\/ontology\/(?:data|export)["'`]/g;
const ROOT_DER_RE = /`\$\{API_BASE\}\/v1\/ontology\/(?:data|export)`/g;

/** 解析表种子：两枚常量的运行期真实取值（不写死字面串，防止「常量值漂了但快照照登」）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['ONTOLOGY_DATA_BASE', ONTOLOGY_DATA_BASE],
  ['ONTOLOGY_EXPORT_BASE', ONTOLOGY_EXPORT_BASE],
]);

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.replace(IMPORT_STMT_RE, '').split(/\r?\n/).filter((l) => !isComment(l));
}

/**
 * 剥 import 的行集不能用来验导入腿（B6-4 规则 ⑦ 沿用）：导入面判据走本函数（只剔注释、保留 import 原文）。
 */
function declLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
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
  return CLOSED_FAMS.includes(normalize(val).replace(/^\/api/, ''));
}

/** 同根子路径判定：`/v1/ontology/data…` 或 `/v1/ontology/export…` 打头（子路径守恒腿用）。 */
function inScope(val: string): boolean {
  return /\/v1\/ontology\/(?:data|export)\b/.test(val);
}

/**
 * 把派生点还原成运行期面值；绑定行不计，模板腿先从行内摘除后再数裸名引用。
 * `member` 决定按族根判等（① 级快照）还是按同根前缀（② 级快照）。
 */
function faces(rel: string, member: (v: string) => boolean, src?: string): string[] {
  const code = src ?? read(rel);
  const out: string[] = [];
  for (const line of codeLines(code)) {
    if (DERIVED_DECL_RE.test(line) || ALIAS_DECL_RE.test(line)) continue;
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      rest = rest.split(m[0]).join('');
      const base = CONST_VALUES.get(m[1]);
      const val = base === undefined ? `\${${m[1]}}${m[2]}` : base + m[2];
      if (member(val)) out.push(val);
    }
    for (const [name, val] of CONST_VALUES) {
      if (name === 'API_BASE' || !member(val)) continue;
      const parts = rest.split(name);
      for (let i = 0; i < parts.length - 1; i++) {
        const before = parts[i].slice(-1);
        if (before === '{' || before === '.') continue;
        out.push(val);
      }
    }
  }
  return out.sort();
}

const famFaces = (rel: string, src?: string) => faces(rel, inClosed, src);
const scopedFaces = (rel: string, src?: string) => faces(rel, inScope, src);

/** 族根体构造点计数：裸形态 + `${API_BASE}` 派生态（属主唯一性判据）。 */
function rootBodySites(code: string): string[] {
  const hits: string[] = [];
  for (const line of codeLines(code)) {
    for (const m of line.matchAll(ROOT_RAW_RE)) hits.push(m[0]);
    for (const m of line.matchAll(ROOT_DER_RE)) hits.push(m[0]);
  }
  return hits;
}

/**
 * 改前快照 ①（族根本体，4 条）：属主各 1（各自 POST 站的裸引用）+ 消费者各 1（改前为
 * `` `${API_BASE}/v1/ontology/{data,export}` `` 直写腿）。双模读出器实跑两侧一致。
 */
const HEAD_ROOT_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [DATA_OWNER]: ['/api/v1/ontology/data'],
  [EXPORT_OWNER]: ['/api/v1/ontology/export'],
  'services/ontologyWorkbenchApi.ts': ['/api/v1/ontology/data'],
  'pages/knowledge/services/overview.ts': ['/api/v1/ontology/export'],
});
const HEAD_ROOT_TOTAL = 4;

/**
 * 改前快照 ②（含同根子路径腿，15 条，`${…}` 插值按字面保留）：改名把属主文件内每一腿都搬到
 * `` `${ONTOLOGY_*_BASE}/…` `` 形态，面值必须逐字不动——本组即"请求面一条没少、一处没改"的正证。
 */
const HEAD_SCOPED_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [DATA_OWNER]: [
    '/api/v1/ontology/data',
    '/api/v1/ontology/data${query}',
    '/api/v1/ontology/data/${id}',
    '/api/v1/ontology/data/${id}',
    '/api/v1/ontology/data/${id}',
    '/api/v1/ontology/data/objects',
    '/api/v1/ontology/data?objectTypeId=${encodeURIComponent(objectTypeId)}',
  ],
  [EXPORT_OWNER]: [
    '/api/v1/ontology/export',
    '/api/v1/ontology/export/${id}',
    '/api/v1/ontology/export/${id}',
    '/api/v1/ontology/export/${id}/download',
    '/api/v1/ontology/export/${id}/download',
    '/api/v1/ontology/export/tasks${query}',
  ],
  'services/ontologyWorkbenchApi.ts': ['/api/v1/ontology/data'],
  'pages/knowledge/services/overview.ts': ['/api/v1/ontology/export'],
});
const HEAD_SCOPED_TOTAL = 15;

describe('ontology 域基址单源 — /v1/ontology/data + /v1/ontology/export (#145/B6-12)', () => {
  it('前提：两枚常量的运行期面值与 API_BASE 单源取值一致（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(ONTOLOGY_DATA_BASE).toBe('/api/v1/ontology/data');
    expect(ONTOLOGY_EXPORT_BASE).toBe('/api/v1/ontology/export');
  });

  it('面值逐字不变 ①：族根本体多重集 = 改前快照（4 条）且逐文件条数对得上', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = famFaces(rel);
      expect(vals, `${rel} 族根本体面值多重集应等于改前快照`).toEqual([...HEAD_ROOT_FACES[rel]].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_ROOT_TOTAL);
  });

  it('面值逐字不变 ②：含同根子路径腿的多重集 = 改前快照（15 条，属主 7+6、消费者各 1）', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = scopedFaces(rel);
      expect(vals, `${rel} 同根面值多重集应等于改前快照（改名不得动任何一条腿的面值）`).toEqual([
        ...HEAD_SCOPED_FACES[rel],
      ].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_SCOPED_TOTAL);
  });

  it('属主唯一：两族族根体只允许各自属主一人书写（全仓生产文件扫，裸形态与 ${API_BASE} 派生态都计）', () => {
    const owners = new Set(OWNERS.map((o) => o.rel));
    const offenders: string[] = [];
    for (const rel of walk('.', [])) {
      if (rel === 'services/httpClient.ts') continue;
      const hits = rootBodySites(read(rel));
      if (hits.length === 0 || owners.has(rel)) continue;
      offenders.push(`${rel} -> ${JSON.stringify(hits)}`);
    }
    expect(offenders, '族根体出现第二生产方即红').toEqual([]);
    for (const { rel } of OWNERS) {
      expect(rootBodySites(read(rel)).length, `${rel} 应恰有一枚族根声明`).toBe(1);
    }
  });

  it('改名而非新增：属主声明仍是 ${API_BASE} 派生态（族面 253 不降的根据），旧私有名代码行零残留', () => {
    for (const { rel, name, legacy } of OWNERS) {
      const code = read(rel);
      const decl = `export const ${name} = \`\${API_BASE}${
        (name === 'ONTOLOGY_DATA_BASE' ? '/v1/ontology/data' : '/v1/ontology/export')
      }\`;`;
      expect(code, `${rel} 的 ${name} 声明形状异常（不得回退裸字面量，否则该族从棘轮族面凭空消失）`).toContain(decl);
      const legacyHits = codeLines(code).filter((l) => new RegExp(`\\b${legacy}\\b`).test(l));
      expect(legacyHits, `${rel} 代码行仍残留旧私有名 ${legacy}`).toEqual([]);
    }
  });

  it('消费方只有 import 腿：零族根体书写、零私设基址、不得自声明同名常量，且导入路径随目录深度正确', () => {
    for (const { rel, importPath, name } of CONSUMERS) {
      const code = read(rel);
      const lines = codeLines(code);
      expect(rootBodySites(code), `${rel} 仍在书写族根体`).toEqual([]);
      expect(lines.filter((l) => HARDCODED_RE.test(l)), `${rel} 仍有私设基址`).toEqual([]);
      expect(code).not.toMatch(/\/api\/api\b/);
      const imports = declLines(code).filter((l) => l.trimStart().startsWith('import ') && l.includes(name));
      expect(imports.length, `${rel} 应有且仅有 1 条 ${name} 导入`).toBe(1);
      expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
        new RegExp(`from '${importPath}'|from "${importPath}"`),
      );
      const selfDecl = lines.filter((l) => DERIVED_DECL_RE.test(l) && l.includes(name));
      expect(selfDecl, `${rel} 不得自声明 ${name}`).toEqual([]);
    }
  });

  it('ontologyWorkbenchApi 收口后不留第二条基址来源（API_BASE 面归零 ⇒ 只剩族导入）', () => {
    const code = read('services/ontologyWorkbenchApi.ts');
    const lines = declLines(code);
    const apiImports = lines.filter((l) => l.trimStart().startsWith('import ') && /\bAPI_BASE\b/.test(l));
    expect(apiImports, '本文件唯一站点已上收，再留 API_BASE 导入即未用来源').toEqual([]);
    expect(codeLines(code).filter((l) => /\bAPI_BASE\b/.test(l)), '代码行不得再出现 API_BASE').toEqual([]);
    expect(lines.filter((l) => l.trimStart().startsWith('import ') && l.includes('ONTOLOGY_DATA_BASE')).length).toBe(1);
  });

  it('判据能变红：重写族根体、把绑定行或多行 import 续行当腿、把子路径腿读成族根，都必须被判出来', () => {
    // a) 第二生产方重新裸拼族根 ⇒ 属主唯一判据必须命中；子路径形态不得命中
    const REDECLARE = ['const u = `${API_BASE}/v1/ontology/export`;', 'fetch(`${API_BASE}/v1/ontology/data/${id}`);'].join('\n');
    expect(rootBodySites(REDECLARE)).toEqual(['`${API_BASE}/v1/ontology/export`']);
    const RAW_DUP = ['const a = "/api/v1/ontology/data";', "const b = '/api/v1/ontology/export';"].join('\n');
    expect(rootBodySites(RAW_DUP).length).toBe(2);
    expect(rootBodySites('const t = `${API_BASE}/v1/ontology/data/objects`;')).toEqual([]);
    // b) 合规形态：多行 import 续行不计腿、绑定行不计腿、裸引用与模板腿各计 1
    const CLEAN = [
      'import {',
      '  ONTOLOGY_DATA_BASE,',
      '  ONTOLOGY_EXPORT_BASE,',
      '} from "../services/ontology/ontologyExportApi";',
      'const list = await apiFetchData(ONTOLOGY_EXPORT_BASE);',
      'await apiFetchData(`${ONTOLOGY_DATA_BASE}/${id}`);',
      'await apiFetchData(ONTOLOGY_DATA_BASE, { method: "POST" });',
    ].join('\n');
    expect(famFaces('x.ts', CLEAN)).toEqual(['/api/v1/ontology/data', '/api/v1/ontology/export']);
    expect(scopedFaces('x.ts', CLEAN)).toEqual([
      '/api/v1/ontology/data',
      '/api/v1/ontology/data/${id}',
      '/api/v1/ontology/export',
    ]);
    // c) 面值漂移必须读成不等（防"改名时顺手改了一段路径"逃过 ① 级快照）
    expect(scopedFaces('x.ts', 'const t = await apiFetchData(`${ONTOLOGY_DATA_BASE}/${ident}`);')).toEqual([
      '/api/v1/ontology/data/${ident}',
    ]);
    // d) 属性访问位置不得吃成裸引用腿；注释不计
    expect(famFaces('x.ts', 'cfg.ONTOLOGY_DATA_BASE = 1;')).toEqual([]);
    expect(codeLines('// GET /api/v1/ontology/data 只是注释里的端点说明').filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    // e) 规则 ⑦ 反证：单行 import 在剥 import 的行集里恒不可见 ⇒ 导入腿判据必须走 declLines
    const ONE_LINE_IMPORT = "import { ONTOLOGY_DATA_BASE } from './ontology/ontologyDataApi';";
    expect(codeLines(ONE_LINE_IMPORT), '剥 import 后只剩一个空行，导入腿在该行集恒不可见').toEqual(['']);
    expect(declLines(ONE_LINE_IMPORT).length).toBe(1);
  });

  it('快照自身可校验：4 = 属主 2 + 消费者 2、15 = 属主 7+6 + 消费者 2 且全部以 /api/v1/ontology/ 开头', () => {
    const flatRoot = SCOPED.flatMap((rel) => [...HEAD_ROOT_FACES[rel]]);
    const flatScoped = SCOPED.flatMap((rel) => [...HEAD_SCOPED_FACES[rel]]);
    expect(flatRoot.length).toBe(HEAD_ROOT_TOTAL);
    expect(flatScoped.length).toBe(HEAD_SCOPED_TOTAL);
    expect(OWNERS.reduce((n, o) => n + HEAD_ROOT_FACES[o.rel].length, 0)).toBe(2);
    expect(CONSUMERS.reduce((n, c) => n + HEAD_ROOT_FACES[c.rel].length, 0)).toBe(2);
    expect(HEAD_SCOPED_FACES[DATA_OWNER].length).toBe(7);
    expect(HEAD_SCOPED_FACES[EXPORT_OWNER].length).toBe(6);
    for (const v of [...flatRoot, ...flatScoped]) expect(v.startsWith('/api/v1/ontology/')).toBe(true);
    expect(CLOSED_FAMS.every((f) => f.startsWith('/v1/ontology/'))).toBe(true);
    expect(SCOPED.length).toBe(4);
    // ① 级是 ② 级的子集：族根本体的每一条都必须在同根面里出现（防两表各写各的、其中一张被清空）
    for (const rel of SCOPED) {
      for (const v of HEAD_ROOT_FACES[rel]) expect(HEAD_SCOPED_FACES[rel]).toContain(v);
    }
  });
});
