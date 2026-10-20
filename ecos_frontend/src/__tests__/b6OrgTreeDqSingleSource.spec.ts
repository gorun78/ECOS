// @vitest-environment node
/**
 * 重复族 `/v1/system/organizations/tree` + `/v1/dq` 基址单源回归门（#145/B6-14，
 * 差距台账 §13.2.4 C1 腿、B6 存量债第十四批）。
 *
 * <p>两族改前各有 2 枚属主（棘轮 dupBoth 面 4 族里的两族）。两族的收口形状**不同型**，本门因此有两套判据：</p>
 *
 * <p>① `/v1/system/organizations/tree`＝**内联腿提为声明**（第三形状，与 B6-13 ② 同判）：改前
 * `api/iamClient.ts` 与 `pages/WorldModelViewer.tsx` 各自把整条 URL 内联写进 `apiFetchData` 调用；
 * 改后属主侧新增 `` SYSTEM_ORGANIZATIONS_TREE_URL `` 声明、页面侧改成裸名取值。族根本体面值条数
 * **一字未动**（两侧各 1 条），只是书写权从两方收给一方。</p>
 *
 * <p>② `/v1/dq`＝**私有改名 + 域别名重导出**（本批新形状：别名层）：属主把改前文件内私有
 * `DQ_BASE` 改名并加 `export`（同一声明位、面值逐字不变），页面侧 `pages/data-quality/api/_base.ts`
 * 的 `DQ_GOV_BASE` 从 `` `${API_BASE}/v1/dq` `` 改成**不含 URL 体的别名重导出** `= DQ_GOVERNANCE_BASE`。
 * 之所以保留别名而不把 31 条腿逐个改引属主常量：该文件是本域共享端点常量层，`rules.ts`(10) +
 * `scores-schedules-alerts.ts`(21) 共 31 条腿全部经 `./_base` 取基址，逐个改引会把一次"收书写权"
 * 放大成 31 处 diff，且导入面（`./_base`）改变后 B5-26 的门禁形状也得跟着重写——收益为零、风险为正。
 * 代价是别名与属主的等价性**不再是源码字面可比**，故本门用**运行期对拍**补上：
 * `expect(DQ_GOV_BASE).toBe(DQ_GOVERNANCE_BASE)`（两枚都从真实模块 import，非写死字面串）。
 * 第二项代价是既有判据的形状：「消费方不得自声明同名常量」原按"行内出现该名字"判，在别名重导出行的
 * 右半（正是属主名）上必然误报——首轮实跑 `1 failed | 9 passed` 即此，改按**绑定名**判（`boundName`）后
 * 别名形状通过、真自声明仍能被抓，两条 fixture 一起钉进「判据能变红」腿。</p>
 *
 * <p>面值守恒按两级快照做证，两组均由双模读出器对 `git show HEAD:` 与工作树各跑同一解析器、
 * 逐文件多重集完全一致后才落笔（实跑读出：① 级 0/1/0/0/0/1、② 级 8/1/0/10/21/1），非手抄：
 * ①族根本体 2 条、②同根子路径合计 41 条。dq 域的 ① 级恒 0 是该族**固有形状**而非漏扫——
 * 族根 `DQ_BASE`/`DQ_GOV_BASE` 在全仓从不单独成面（每一腿都带子路径），故本批 dq 的正证全在 ② 级，
 * 属主唯一性另由族根构造点计数（`rootBodySites`）钉死。读出器 `identical` 标志为 false 是预期的：
 * 它比的是 root/scoped/rootBody/decl 四个面，root 与 scoped 两侧逐文件全等，
 * rootBody（4→2）与 decl 行文本正是本批要改的东西。</p>
 *
 * <p>与棘轮账的对应关系（本批口径，勿读成进度）：两族退出 dupBoth（4→2）、BASELINE 属主枚 8→4，
 * derSites 284→282（−2＝`_base.ts` 别名化 1 + `WorldModelViewer.tsx` 裸名化 1；两枚属主声明都是
 * `` `${API_BASE}/v1/…` `` 派生态 ⇒ 净增 0），族索引面 253 不降。C1 侧 `derTotal` 301→299 与本批
 * 棘轮差额同值——两口径这次恰好同面（两族族体全在 `/v1/` 内且无跨行站），B6-7 已实证过不同值，不得当通则。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { DQ_GOVERNANCE_BASE } from '../api/dataClient';
import { SYSTEM_ORGANIZATIONS_TREE_URL } from '../api/iamClient';
import { DQ_GOV_BASE, DQ_LEGACY_BASE } from '../pages/data-quality/api/_base';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 两枚属主（族根体唯一书写点）。 */
const DQ_OWNER = 'api/dataClient.ts';
const TREE_OWNER = 'api/iamClient.ts';
/** 消费面：别名层 1 枚 + 别名下游 2 枚 + 页面直取 1 枚。 */
const CONSUMERS: ReadonlyArray<{ rel: string; importPath: string; name: string }> = [
  { rel: 'pages/data-quality/api/_base.ts', importPath: '../../../api/dataClient', name: 'DQ_GOVERNANCE_BASE' },
  { rel: 'pages/data-quality/api/rules.ts', importPath: './_base', name: 'DQ_GOV_BASE' },
  { rel: 'pages/data-quality/api/scores-schedules-alerts.ts', importPath: './_base', name: 'DQ_GOV_BASE' },
  { rel: 'pages/WorldModelViewer.tsx', importPath: '../api/iamClient', name: 'SYSTEM_ORGANIZATIONS_TREE_URL' },
];
const OWNERS: ReadonlyArray<{ rel: string; name: string; root: string; legacy?: string }> = [
  { rel: DQ_OWNER, name: 'DQ_GOVERNANCE_BASE', root: '/v1/dq', legacy: 'DQ_BASE' },
  { rel: TREE_OWNER, name: 'SYSTEM_ORGANIZATIONS_TREE_URL', root: '/v1/system/organizations/tree' },
];
const SCOPED = [...OWNERS.map((o) => o.rel), ...CONSUMERS.map((c) => c.rel)];

/** 本批收口的两族族键（归一化后，与 `urlFamilyDualFormRatchet` 同口径）。 */
const CLOSED_FAMS = ['/v1/dq', '/v1/system/organizations/tree'];

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
const ROOT_RAW_RE = /["'`]\/api\/v1\/(?:dq|system\/organizations\/tree)["'`]/g;
const ROOT_DER_RE = /`\$\{API_BASE\}\/v1\/(?:dq|system\/organizations\/tree)`/g;

/**
 * 解析表种子：本批涉及的每一枚常量的**运行期真实取值**（不写死字面串，防止「常量值漂了但快照照登」）。
 * `DQ_GOV_BASE` 是别名层成员——它的值必须来自真实模块求值，否则别名与属主的等价性就退化成两条字面串巧合。
 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['DQ_GOVERNANCE_BASE', DQ_GOVERNANCE_BASE],
  ['DQ_GOV_BASE', DQ_GOV_BASE],
  ['DQ_LEGACY_BASE', DQ_LEGACY_BASE],
  ['SYSTEM_ORGANIZATIONS_TREE_URL', SYSTEM_ORGANIZATIONS_TREE_URL],
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

/** 族成员判定：剥基址前缀后按归一化族键**判等**（前缀匹配会吃进同根子路径族，含 `/v1/ecos/dq`）。 */
function inClosed(val: string): boolean {
  return CLOSED_FAMS.includes(normalize(val).replace(/^\/api/, ''));
}

/** 同根子路径判定：`/v1/dq…` 或 `/v1/system/organizations/tree…` 打头（子路径守恒腿用）。 */
function inScope(val: string): boolean {
  return /\/v1\/(?:dq|system\/organizations\/tree)\b/.test(val);
}

/**
 * 把派生点还原成运行期面值；绑定行不计，模板腿先从行内摘除后再数裸名引用。
 * 裸名引用必须同时避开**前向属性位**（`{` / `.`）与**后向标识符字节**，否则 `DQ_GOV_BASEX` 会被切成一条假腿。
 * `member` 决定按族根判等（① 级快照）还是按同根前缀（② 级快照）。
 */
function faces(
  rel: string,
  member: (v: string) => boolean,
  src?: string,
  table?: ReadonlyMap<string, string>,
): string[] {
  const code = src ?? read(rel);
  const useTable = table ?? CONST_VALUES;
  const out: string[] = [];
  for (const line of codeLines(code)) {
    if (DERIVED_DECL_RE.test(line) || ALIAS_DECL_RE.test(line)) continue;
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      rest = rest.split(m[0]).join('');
      const base = useTable.get(m[1]);
      const val = base === undefined ? `\${${m[1]}}${m[2]}` : base + m[2];
      if (member(val)) out.push(val);
    }
    for (const [name, val] of useTable) {
      if (name === 'API_BASE' || !member(val)) continue;
      const parts = rest.split(name);
      for (let i = 0; i < parts.length - 1; i++) {
        if (parts[i].slice(-1) === '{' || parts[i].slice(-1) === '.') continue;
        if (/[A-Za-z0-9_$]/.test(parts[i + 1].charAt(0))) continue;
        out.push(val);
      }
    }
  }
  return out.sort();
}

/**
 * 绑定行上的**被绑定标识符**（派生声明或名别名各取捕获组 1）。
 * 「消费方不得自声明同名常量」必须按绑定名判、不能按"行内是否出现该名字"判：
 * 别名重导出行 `export const DQ_GOV_BASE = DQ_GOVERNANCE_BASE;` 的右半正是属主名，
 * 用包含判据会把这条**本批要的正形**读成自声明（实测首轮即误报，此处即那一次的固化）。
 */
function boundName(line: string): string | undefined {
  return DERIVED_DECL_RE.exec(line)?.[1] ?? ALIAS_DECL_RE.exec(line)?.[1];
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
 * 改前快照 ①（族根本体 2 条，双模实跑两侧一致）：org-tree 族两侧各 1 枚整 URL 内联腿；
 * dq 域四文件全为 0——该族族根从不单独成面（每条腿都带子路径），本批正证全在 ② 级。
 */
const HEAD_ROOT_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [DQ_OWNER]: [],
  [TREE_OWNER]: ['/api/v1/system/organizations/tree'],
  'pages/data-quality/api/_base.ts': [],
  'pages/data-quality/api/rules.ts': [],
  'pages/data-quality/api/scores-schedules-alerts.ts': [],
  'pages/WorldModelViewer.tsx': ['/api/v1/system/organizations/tree'],
});
const HEAD_ROOT_TOTAL = 2;

/**
 * 改前快照 ②（含同根子路径腿，41 条，`${…}` 插值按字面保留）：改名/别名化会把属主与别名下游每一腿
 * 都搬到 `` `${…}/…` `` 形态，面值必须逐字不动——本组即「请求面一条没少、一处没改」的正证。
 */
const HEAD_SCOPED_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [DQ_OWNER]: [
    '/api/v1/dq/${type}',
    '/api/v1/dq/${type}/${id}',
    '/api/v1/dq/${type}/${id}',
    '/api/v1/dq/check',
    '/api/v1/dq/dashboard',
    '/api/v1/dq/issues',
    '/api/v1/dq/issues/${issueId}',
    '/api/v1/dq/rules',
  ],
  [TREE_OWNER]: ['/api/v1/system/organizations/tree'],
  'pages/data-quality/api/_base.ts': [],
  'pages/data-quality/api/rules.ts': [
    '/api/v1/dq/health/selfcheck',
    '/api/v1/dq/rules',
    '/api/v1/dq/rules',
    '/api/v1/dq/rules${qs ? ',
    '/api/v1/dq/rules/${id}',
    '/api/v1/dq/rules/${id}',
    '/api/v1/dq/rules/${id}',
    '/api/v1/dq/rules/${id}/${action}',
    '/api/v1/dq/rules/${id}/versions',
    '/api/v1/dq/rules/dimension-registry',
  ],
  'pages/data-quality/api/scores-schedules-alerts.ts': [
    '/api/v1/dq/alerts${qs ? ',
    '/api/v1/dq/alerts/${id}/ack',
    '/api/v1/dq/alerts/${id}/resolve',
    '/api/v1/dq/reports${qs ? ',
    '/api/v1/dq/reports/${id}/html',
    '/api/v1/dq/reports/generate${qs}',
    '/api/v1/dq/schedules',
    '/api/v1/dq/schedules',
    '/api/v1/dq/schedules/${id}',
    '/api/v1/dq/schedules/${id}',
    '/api/v1/dq/schedules/${scheduleId}/trigger',
    '/api/v1/dq/scores${qs}',
    '/api/v1/dq/scores/grade${qs}',
    '/api/v1/dq/scores/recompute${qs}',
    '/api/v1/dq/scores/system',
    '/api/v1/dq/scores/trend${qs}',
    '/api/v1/dq/work-orders${qs ? ',
    '/api/v1/dq/work-orders/${id}',
    '/api/v1/dq/work-orders/${id}/${action}',
    '/api/v1/dq/work-orders/${id}/assign?to=${encodeURIComponent(to)}',
    '/api/v1/dq/work-orders/rank${qs}',
  ],
  'pages/WorldModelViewer.tsx': ['/api/v1/system/organizations/tree'],
});
const HEAD_SCOPED_TOTAL = 41;

describe('重复族 /v1/system/organizations/tree + /v1/dq 基址单源 — (#145/B6-14)', () => {
  it('前提：四枚常量的运行期面值与 API_BASE 单源取值一致；别名层与属主取值逐字相等', () => {
    expect(API_BASE).toBe('/api');
    expect(DQ_GOVERNANCE_BASE).toBe('/api/v1/dq');
    expect(SYSTEM_ORGANIZATIONS_TREE_URL).toBe('/api/v1/system/organizations/tree');
    // 别名层唯一可信的等价性证明就是运行期对拍：源码面已经不含 URL 体，字面串比对无从下手
    expect(DQ_GOV_BASE).toBe(DQ_GOVERNANCE_BASE);
    // legacy 前缀不得被顺手改成治理族根（`/v1/ecos/dq` 与 `/v1/dq` 是两个族）
    expect(DQ_LEGACY_BASE).toBe('/api/v1/ecos/dq');
    expect(normalize(DQ_LEGACY_BASE).replace(/^\/api/, '')).not.toBe('/v1/dq');
  });

  it('面值逐字不变 ①：族根本体多重集 = 改前快照（2 条，dq 域四文件恒 0 是该族固有形状）', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = famFaces(rel);
      expect(vals, `${rel} 族根本体面值多重集应等于改前快照`).toEqual([...HEAD_ROOT_FACES[rel]].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_ROOT_TOTAL);
    for (const rel of [DQ_OWNER, ...CONSUMERS.slice(0, 3).map((c) => c.rel)]) {
      expect(famFaces(rel), `${rel} 若出现族根本体面值，说明有人新增了裸族根请求腿`).toEqual([]);
    }
  });

  it('面值逐字不变 ②：含同根子路径腿的多重集 = 改前快照（41 条 = 属主 8+1、别名层 0、下游 10+21、页面 1）', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = scopedFaces(rel);
      expect(vals, `${rel} 同根面值多重集应等于改前快照（改名/别名化不得动任何一条腿的面值）`).toEqual([
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
    expect(offenders, '族根体出现第二生产方即红（含 `_base.ts` 别名回退成派生态）').toEqual([]);
    for (const { rel } of OWNERS) {
      expect(rootBodySites(read(rel)).length, `${rel} 应恰有一枚族根声明`).toBe(1);
    }
  });

  it('形状归因：dq 属主是私有改名（旧私有名零残留），org-tree 属主是内联腿提为声明（消费腿改裸名）', () => {
    const dq = read(DQ_OWNER);
    expect(dq).toContain('export const DQ_GOVERNANCE_BASE = `${API_BASE}/v1/dq`;');
    expect(codeLines(dq).filter((l) => /\bDQ_BASE\b/.test(l)), 'api/dataClient.ts 代码行仍残留旧私有名 DQ_BASE').toEqual([]);
    // 改名不得改成裸字面量：那会让该族从棘轮 DER_RE 面里凭空消失（可见性被关掉、不是债还掉）
    expect(codeLines(dq).some((l) => /^\s*export const DQ_GOVERNANCE_BASE = ["']\/api/.test(l))).toBe(false);

    const tree = read(TREE_OWNER);
    expect(tree).toContain('export const SYSTEM_ORGANIZATIONS_TREE_URL = `${API_BASE}/v1/system/organizations/tree`;');
    const treeOwnerLines = codeLines(tree);
    expect(treeOwnerLines.filter((l) => /apiFetchData<any>\(`\$\{API_BASE\}\/v1\/system\/organizations\/tree`/.test(l)), '属主自己的取数腿必须改引声明常量（声明与内联并存＝本批没收到位）').toEqual([]);
    expect(treeOwnerLines.filter((l) => /\bSYSTEM_ORGANIZATIONS_TREE_URL\b/.test(l) && /apiFetchData/.test(l)).length, '属主内应恰好出现一次裸名消费腿').toBe(1);
  });

  it('别名层收口：_base.ts 的 DQ_GOV_BASE 必须是不含 URL 体的名别名，且不得自声明同名派生态', () => {
    const code = read('pages/data-quality/api/_base.ts');
    expect(code).toContain('export const DQ_GOV_BASE = DQ_GOVERNANCE_BASE;');
    expect(codeLines(code).filter((l) => DERIVED_DECL_RE.test(l) && l.includes('DQ_GOV_BASE')), '别名回退成 ${API_BASE} 派生态即双属主复发').toEqual([]);
    expect(codeLines(code).filter((l) => HARDCODED_RE.test(l)), '_base.ts 仍有私设基址').toEqual([]);
    // 同文件另一枚 legacy 常量必须仍是派生态（本批不动它，防止"顺手改成裸字面量"把 C1 基线打红）
    expect(codeLines(code).some((l) => /^\s*export const DQ_LEGACY_BASE = `\$\{API_BASE\}\/v1\/ecos\/dq`;?$/.test(l))).toBe(true);
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
      const selfDecl = lines.filter((l) => boundName(l) === name);
      expect(selfDecl, `${rel} 不得自声明 ${name}`).toEqual([]);
    }
  });

  it('WorldModelViewer 双来源排查：该页 org-tree 腿只剩族单源，API_BASE 只服务于 users 腿（不得整删导入）', () => {
    const code = read('pages/WorldModelViewer.tsx');
    const lines = declLines(code);
    expect(rootBodySites(code)).toEqual([]);
    expect(
      codeLines(code).some((l) => l.includes('SYSTEM_ORGANIZATIONS_TREE_URL') && /apiFetchData/.test(l)),
      '该页 org-tree 腿必须改成裸名取值',
    ).toBe(true);
    expect(lines.filter((l) => l.trimStart().startsWith('import ') && /\bAPI_BASE\b/.test(l)).length, 'users 腿仍在位 ⇒ API_BASE 导入应恰 1 条').toBe(1);
    expect(codeLines(code).filter((l) => /\/v1\/system\/users/.test(l) && HARDCODED_RE.test(l)), 'users 腿不得夹带裸基址').toEqual([]);
  });

  it('判据能变红：重写族根体、把绑定行或多行 import 续行当腿、把子路径腿读成族根、别名值漂，都必须被判出来', () => {
    // a) 第二生产方重新裸拼族根 ⇒ 属主唯一判据必须命中；子路径形态不得命中
    const REDECLARE = [
      'const u = `${API_BASE}/v1/system/organizations/tree`;',
      'fetch(`${API_BASE}/v1/dq/rules/${id}`);',
    ].join('\n');
    expect(rootBodySites(REDECLARE)).toEqual(['`${API_BASE}/v1/system/organizations/tree`']);
    const RAW_DUP = ['const a = "/api/v1/dq";', "const b = '/api/v1/system/organizations/tree';"].join('\n');
    expect(rootBodySites(RAW_DUP).length).toBe(2);
    expect(rootBodySites('const t = `${API_BASE}/v1/dq/rules`;')).toEqual([]);
    // b) 别名值漂了必须读成「面值不等于快照」，而不是"照样匹配常量名"：同一腿源码不变，只换解析表
    const LEG = 'const t = await apiFetchData(`${DQ_GOV_BASE}/rules`);';
    expect(scopedFaces('x.ts', LEG)).toEqual(['/api/v1/dq/rules']);
    expect([...HEAD_SCOPED_FACES['pages/data-quality/api/rules.ts']]).toContain('/api/v1/dq/rules');
    const DRIFT = new Map([...CONST_VALUES].map(([k, v]) => [k, k === 'DQ_GOV_BASE' ? '/api/v1/dq-x' : v]));
    const drifted = faces('x.ts', (v) => v.startsWith('/api/v1/dq'), LEG, DRIFT);
    expect(drifted).toEqual(['/api/v1/dq-x/rules']);
    expect(HEAD_SCOPED_FACES['pages/data-quality/api/rules.ts']).not.toContain(drifted[0]);
    // c) 合规形态：多行 import 续行不计腿、绑定行不计腿、模板腿与裸引用各计 1
    const CLEAN = [
      'import {',
      '  DQ_GOV_BASE,',
      '  DQ_LEGACY_BASE,',
      '} from "./_base";',
      'const alias = DQ_GOV_BASE;',
      'const list = await apiFetchData(`${DQ_GOV_BASE}/rules${qs ? `?${qs}` : ""}`);',
      'await apiFetchData(SYSTEM_ORGANIZATIONS_TREE_URL, { method: "POST" });',
      'const legacy = `${DQ_LEGACY_BASE}/rules`;',
    ].join('\n');
    expect(famFaces('x.ts', CLEAN)).toEqual(['/api/v1/system/organizations/tree']);
    expect(scopedFaces('x.ts', CLEAN)).toEqual([
      '/api/v1/dq/rules${qs ? ',
      '/api/v1/system/organizations/tree',
    ]);
    // d) 属性访问位与后缀标识符不得吃成裸引用腿；注释与 import 原文不得造腿
    expect(famFaces('x.ts', 'cfg.DQ_GOV_BASE = 1;')).toEqual([]);
    expect(famFaces('x.ts', 'const DQ_GOV_BASEX = 1;')).toEqual([]);
    expect(codeLines('// GET /api/v1/dq 只是注释里的端点说明').filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    // f) 自声明判据按**绑定名**判：别名重导出行不是自声明（首轮误报固化），真自声明必须被抓
    expect(boundName('export const DQ_GOV_BASE = DQ_GOVERNANCE_BASE;')).toBe('DQ_GOV_BASE');
    expect(boundName('export const DQ_GOVERNANCE_BASE = `${API_BASE}/v1/dq`;')).toBe('DQ_GOVERNANCE_BASE');
    expect(boundName('const x = DQ_GOV_BASE;')).toBe('x');
    // e) 规则 ⑦ 反证：单行 import 在剥 import 的行集里恒不可见 ⇒ 导入腿判据必须走 declLines
    const ONE_LINE_IMPORT = "import { DQ_GOVERNANCE_BASE } from '../../../api/dataClient';";
    expect(codeLines(ONE_LINE_IMPORT), '剥 import 后只剩一个空行，导入腿在该行集恒不可见').toEqual(['']);
    expect(declLines(ONE_LINE_IMPORT).length).toBe(1);
  });

  it('快照自身可校验：2 = 属主 1 + 页面 1、41 = 属主 8+1 + 别名 0 + 下游 10+21 + 页面 1 且全部以 /api/v1/ 开头', () => {
    const flatRoot = SCOPED.flatMap((rel) => [...HEAD_ROOT_FACES[rel]]);
    const flatScoped = SCOPED.flatMap((rel) => [...HEAD_SCOPED_FACES[rel]]);
    expect(flatRoot.length).toBe(HEAD_ROOT_TOTAL);
    expect(flatScoped.length).toBe(HEAD_SCOPED_TOTAL);
    expect(SCOPED.length).toBe(6);
    expect(HEAD_SCOPED_FACES[DQ_OWNER].length).toBe(8);
    expect(HEAD_SCOPED_FACES[TREE_OWNER].length).toBe(1);
    expect(HEAD_SCOPED_FACES['pages/data-quality/api/_base.ts'].length).toBe(0);
    expect(HEAD_SCOPED_FACES['pages/data-quality/api/rules.ts'].length).toBe(10);
    expect(HEAD_SCOPED_FACES['pages/data-quality/api/scores-schedules-alerts.ts'].length).toBe(21);
    expect(HEAD_SCOPED_FACES['pages/WorldModelViewer.tsx'].length).toBe(1);
    for (const v of [...flatRoot, ...flatScoped]) expect(v.startsWith('/api/v1/')).toBe(true);
    expect(CLOSED_FAMS.every((f) => f.startsWith('/v1/'))).toBe(true);
    // ① 级是 ② 级的子集：族根本体的每一条都必须在同根面里出现（防两表各写各的、其中一张被清空）
    for (const rel of SCOPED) {
      for (const v of HEAD_ROOT_FACES[rel]) expect(HEAD_SCOPED_FACES[rel]).toContain(v);
    }
    // 别名层条数为 0 必须是「面值真的不在这文件」而非「快照漏登」：该文件运行期仍导出族根面值
    expect(famFaces('pages/data-quality/api/_base.ts')).toEqual([]);
    expect(DQ_GOV_BASE).toBe('/api/v1/dq');
  });
});
