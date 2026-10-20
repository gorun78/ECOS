// @vitest-environment node
/**
 * ontology 域基址单源回归门（#145/B6-4，差距台账 §13.2.4 C1 腿、B6 存量债第四批）。
 *
 * <p>本批把 `/v1/ontology/mappings`（3 属主）与 `/v1/ontology/proposals`（3 属主）两族的
 * **7** 处构造点收口为两枚**导出**常量：`services/ontology/ontologyMappingApi.ts` 的
 * `ONTOLOGY_MAPPINGS_BASE` 与 `services/ontology/ontologyProposalApi.ts` 的
 * `ONTOLOGY_PROPOSALS_BASE`（各自把改前的文件内私有 `MAPPING_BASE` / `PROPOSAL_BASE` 上收，
 * 声明形态逐字未动，只加 `export` 并改名）。</p>
 *
 * <p>属主落在 `services/ontology/*` 分册而非页面 api 层，仍由 import 图决定：两分册只依赖
 * `../../api` + `../httpClient` + `../../types/ontology`，四枚消费者（两页面、一页面服务、
 * 一页面 api 层）反向取它不成环；`services/ontologyApi.ts` 是纯 re-export 兼容入口，不持有 URL 体，
 * 故不做属主也不做中转（中转会让族体在两个文件里各写一次）。</p>
 *
 * <p>派生只有一种正确性可证：**运行期面值逐字不变**。本门把每个派生点（模板腿 + 裸引用）按常量
 * 实际取值还原成字符串，与改前快照（7 条，2026-10-20 由双模读出器对 `git show HEAD:` 与工作树
 * 各跑一遍、两侧多重集完全一致后才落笔，非手抄）做排序多重集比对并逐文件核对条数。</p>
 *
 * <p>判据形状沿用前三批挣来的六条：① 绑定行不算腿（属主声明自身即面值来源，否则每属主多计 1）；
 * ② 裸引用算腿（本批属主的 POST 站就是裸引用，改前改后各 1 条，两侧同判据才可比）；
 * ③ 族成员按**归一化族键**判等而非前缀匹配，否则 `/v1/ontology/mappings/objects`、
 * `/v1/ontology/proposals/{}/approve` 这类同根子路径会被前缀吃进收口族；
 * ④ 消费者解析表以属主声明为种子；⑤ 先整条剥 import 语句再按行扫，多行花括号体的续行不是腿；
 * ⑥ 模板腿先从行文本摘除后再数裸名，属性访问前缀（`x.NAME`）跳过；
 * ⑦ 本批新踩并钉死：<b>剥了 import 的行集不能用来验导入腿</b>——规则 ⑤ 为了不把多行 import 的
 * 续行吃成假腿，是把整条 import 语句删掉的，若拿同一行集去断「导入腿恰 1 条」必然恒 0（首跑即红
 * 才暴露）。故导入面判据另走 `declLines`（只剔注释、保留 import 原文），两套行集各有用途、不许混用。</p>
 *
 * <p><b>本批新钉的一条口径差</b>：属主唯一判据只看**族根体**（`/v1/ontology/mappings` 或
 * `/v1/ontology/proposals` 后紧跟定界符），子路径腿不计为族根违例——因为按 #145/B6-1 口径
 * 子路径族刻意保持 `${API_BASE}` 原样以保住棘轮可见性（`ScenarioManagementView` 的 approve/reject
 * 与 `pages/aiworkbench/api.ts` 的 execute 三腿都属此类，本门第「同根子路径族保持可见」腿钉死它们
 * 仍在原处可读）。把子路径也当成族根违例，会逼后续批把它们并进常量，那是不减债的隐形化。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { ONTOLOGY_MAPPINGS_BASE } from '../services/ontology/ontologyMappingApi';
import { ONTOLOGY_PROPOSALS_BASE } from '../services/ontology/ontologyProposalApi';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 两枚属主（族根体唯一书写点）与四枚消费者（只有 import 腿）。 */
const MAPPING_OWNER = 'services/ontology/ontologyMappingApi.ts';
const PROPOSAL_OWNER = 'services/ontology/ontologyProposalApi.ts';
const CONSUMERS: ReadonlyArray<{ rel: string; importPath: string; name: string }> = [
  { rel: 'pages/knowledge/pages/ExtractionPage.tsx', importPath: '../../../services/ontology/ontologyMappingApi', name: 'ONTOLOGY_MAPPINGS_BASE' },
  { rel: 'pages/knowledge/services/overview.ts', importPath: '../../../services/ontology/ontologyMappingApi', name: 'ONTOLOGY_MAPPINGS_BASE' },
  { rel: 'pages/ScenarioManagementView.tsx', importPath: '../services/ontology/ontologyProposalApi', name: 'ONTOLOGY_PROPOSALS_BASE' },
  { rel: 'pages/aiworkbench/api.ts', importPath: '../../services/ontology/ontologyProposalApi', name: 'ONTOLOGY_PROPOSALS_BASE' },
];
const OWNERS: ReadonlyArray<{ rel: string; name: string }> = [
  { rel: MAPPING_OWNER, name: 'ONTOLOGY_MAPPINGS_BASE' },
  { rel: PROPOSAL_OWNER, name: 'ONTOLOGY_PROPOSALS_BASE' },
];
const SCOPED = [...OWNERS.map((o) => o.rel), ...CONSUMERS.map((c) => c.rel)];

/** 本批收口的两族族键（归一化后，与 `urlFamilyDualFormRatchet` 同口径）。 */
const CLOSED_FAMS = ['/v1/ontology/mappings', '/v1/ontology/proposals'];

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
const ROOT_RAW_RE = /["'`]\/api\/v1\/ontology\/(?:mappings|proposals)["'`]/g;
const ROOT_DER_RE = /`\$\{API_BASE\}\/v1\/ontology\/(?:mappings|proposals)`/g;

/** 解析表种子：三枚常量的运行期真实取值（不写死字面串，防止「常量值漂了但快照照登」）。 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['ONTOLOGY_MAPPINGS_BASE', ONTOLOGY_MAPPINGS_BASE],
  ['ONTOLOGY_PROPOSALS_BASE', ONTOLOGY_PROPOSALS_BASE],
]);

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.replace(IMPORT_STMT_RE, '').split(/\r?\n/).filter((l) => !isComment(l));
}

/**
 * 判据形状第七条（本批新踩）：**剥 import 的 codeLines 不能用来验导入腿**——规则 ⑤ 为了不把
 * 多行 import 的续行吃成假腿，是把整条 import 语句删掉的，导入行在 codeLines 面恒为 0 条。
 * 故「导入腿恰 1 条 / 零第二条来源」这类判据必须走本函数（只剔注释、保留 import 原文）。
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
        if (before === '{' || before === '.') continue;
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

/**
 * 改前快照（7 条，双模读出器实跑，`${…}` 插值按字面保留）：
 * 属主各 1（各自 POST 站的裸引用）+ 消费者 5（ExtractionPage 1 + overview 2 + SMV 1 + 页面 api 1）。
 */
const HEAD_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [MAPPING_OWNER]: ['/api/v1/ontology/mappings'],
  [PROPOSAL_OWNER]: ['/api/v1/ontology/proposals'],
  'pages/knowledge/pages/ExtractionPage.tsx': ['/api/v1/ontology/mappings'],
  'pages/knowledge/services/overview.ts': ['/api/v1/ontology/mappings', '/api/v1/ontology/mappings'],
  'pages/ScenarioManagementView.tsx': ['/api/v1/ontology/proposals'],
  'pages/aiworkbench/api.ts': ['/api/v1/ontology/proposals'],
});
const HEAD_TOTAL = 7;

/** 刻意不合流的同根子路径腿（单族多属主不在本批重复面，保持 `${API_BASE}` 形态以保住棘轮可见性）。 */
const KEPT_SUBPATH_LEGS = [
  '`${API_BASE}/v1/ontology/proposals/${id}/approve`',
  '`${API_BASE}/v1/ontology/proposals/${id}/reject`',
  '`${API_BASE}/v1/ontology/proposals/${encodeURIComponent(id)}/execute`',
];

describe('ontology 域基址单源 — /v1/ontology/mappings + /v1/ontology/proposals (#145/B6-4)', () => {
  it('前提：两枚常量的运行期面值与 API_BASE 单源取值一致（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(ONTOLOGY_MAPPINGS_BASE).toBe('/api/v1/ontology/mappings');
    expect(ONTOLOGY_PROPOSALS_BASE).toBe('/api/v1/ontology/proposals');
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（7 条）且逐文件条数对得上', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = famFaces(rel);
      expect(vals, `${rel} 族面值多重集应等于改前快照`).toEqual([...HEAD_FACES[rel]].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_TOTAL);
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

  it('ExtractionPage 上收到族单源后不得留第二条基址来源（API_BASE 导入 0 条 ⇒ 族导入恰 1 条）', () => {
    const lines = declLines(read('pages/knowledge/pages/ExtractionPage.tsx'));
    const apiImports = lines.filter((l) => l.trimStart().startsWith('import ') && /\bAPI_BASE\b/.test(l));
    expect(apiImports, '本文件唯一站点已上收，再留 API_BASE 导入即未用来源').toEqual([]);
    expect(lines.filter((l) => l.trimStart().startsWith('import ') && l.includes('ONTOLOGY_MAPPINGS_BASE')).length).toBe(1);
  });

  it('同根子路径族保持可见：approve / reject / execute 三腿仍写在 ${API_BASE} 形态（未隐形化）', () => {
    const lines = ['pages/ScenarioManagementView.tsx', 'pages/aiworkbench/api.ts'].flatMap((rel) => codeLines(read(rel)));
    for (const leg of KEPT_SUBPATH_LEGS) {
      expect(lines.some((l) => l.includes(leg)), `${leg} 应仍在原处可读`).toBe(true);
    }
  });

  it('判据能变红：重写族根体、把绑定行或多行 import 续行当腿、把子路径族吃进收口族，都必须被判出来', () => {
    // a) 第二生产方重新裸拼族根 ⇒ 属主唯一判据必须命中；子路径形态不得命中
    const REDECLARE = ['const u = `${API_BASE}/v1/ontology/proposals`;', 'fetch(`${API_BASE}/v1/ontology/proposals/${id}/approve`);'].join('\n');
    expect(rootBodySites(REDECLARE)).toEqual(['`${API_BASE}/v1/ontology/proposals`']);
    const RAW_DUP = ['const a = "/api/v1/ontology/mappings";', 'const b = \'/api/v1/ontology/proposals\';'].join('\n');
    expect(rootBodySites(RAW_DUP).length).toBe(2);
    expect(rootBodySites('const t = `${API_BASE}/v1/ontology/mappings/objects`;')).toEqual([]);
    // b) 合规形态：多行 import 续行不计腿、绑定行不计腿、裸引用与模板腿各计 1
    const CLEAN = [
      'import {',
      '  ONTOLOGY_MAPPINGS_BASE,',
      '  ONTOLOGY_PROPOSALS_BASE,',
      '} from "../services/ontology/ontologyProposalApi";',
      'const list = await apiFetchData(ONTOLOGY_MAPPINGS_BASE);',
      'await apiFetchData(`${ONTOLOGY_PROPOSALS_BASE}/${id}`);',
      'await apiFetchData(ONTOLOGY_PROPOSALS_BASE, { method: "POST" });',
    ].join('\n');
    expect(famFaces('x.tsx', CLEAN)).toEqual([
      '/api/v1/ontology/mappings',
      '/api/v1/ontology/proposals',
    ]);
    // c) 同根子路径族不得被算进收口族（族键判等而非前缀匹配），且注释不计
    const SUBPATH = [
      'const t = await apiFetchData(`${ONTOLOGY_MAPPINGS_BASE}/validate`);',
      '// POST /api/v1/ontology/proposals 只是注释里的端点说明',
      'const q = await apiFetchData(`${ONTOLOGY_MAPPINGS_BASE}${query}`);',
    ].join('\n');
    expect(famFaces('x.tsx', SUBPATH)).toEqual([]);
    expect(codeLines(SUBPATH).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    // d) 漂移即红：族根体写错一段就不再是收口族（防判据吃空还当守恒）
    expect(famFaces('x.tsx', 'const x = await apiFetchData(`${API_BASE}/v1/ontology/mapping`);')).toEqual([]);
    expect(rootBodySites('const x = await apiFetchData(`${API_BASE}/v1/ontology/mapping`);')).toEqual([]);
    // e) 属性访问位置不得吃成裸引用腿
    expect(famFaces('x.tsx', 'cfg.ONTOLOGY_MAPPINGS_BASE = 1;')).toEqual([]);
    // f) 规则 ⑦ 反证：单行 import 在剥 import 的行集里恒不可见 ⇒ 导入腿判据必须走 declLines
    const ONE_LINE_IMPORT = "import { ONTOLOGY_MAPPINGS_BASE } from '../services/ontology/ontologyMappingApi';";
    expect(codeLines(ONE_LINE_IMPORT), '剥 import 后只剩一个空行，导入腿在该行集恒不可见').toEqual(['']);
    expect(declLines(ONE_LINE_IMPORT).length).toBe(1);
  });

  it('快照自身可校验：7 条 = 属主 2 + 消费者 5 且全部以 /api/v1/ontology/ 开头（防快照清空成恒真）', () => {
    const flat = SCOPED.flatMap((rel) => [...HEAD_FACES[rel]]);
    expect(flat.length).toBe(HEAD_TOTAL);
    expect(OWNERS.reduce((n, o) => n + HEAD_FACES[o.rel].length, 0)).toBe(2);
    expect(CONSUMERS.reduce((n, c) => n + HEAD_FACES[c.rel].length, 0)).toBe(5);
    for (const v of flat) expect(v.startsWith('/api/v1/ontology/')).toBe(true);
    expect(CLOSED_FAMS.every((f) => f.startsWith('/v1/ontology/'))).toBe(true);
    expect(SCOPED.length).toBe(6);
  });
});
