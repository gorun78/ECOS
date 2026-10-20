// @vitest-environment node
/**
 * AIP 域基址单源回归门（#145/B6-3，差距台账 §13.2.4 C1 腿、B6 存量债第三批）。
 *
 * <p>本批把 AIP 域三族——`/v1/aip/agents`（2 属主）、`/v1/aip/agents/{}`（2 属主）、
 * `/v1/aip/audit-logs`（2 属主）——的 **8** 处构造点收口为 `pages/aiworkbench/api.ts` **导出**的
 * `AIP_AGENTS_BASE` + `AIP_AUDIT_LOGS_BASE` 双常量，`services/aiworkbenchApi.ts` 改为 import。
 * 属主落在页面 api 层而非服务层，是因为 `services/aiworkbenchApi.ts` 本来就 import
 * `pages/aiworkbench/api`（既有四个 fetch 函数），反向放常量会成环——方向由 import 图决定，
 * 不是「单源必须在服务层」的教条（B6-1/B6-2 的服务层属主各自成立）。</p>
 *
 * <p>派生只有一种正确性可证：**运行期面值逐字不变**。故本门用常量解析器把每个派生点还原成
 * 字符串面值，与改前快照（8 条，2026-10-20 自 `git show HEAD:` 双模实跑读出，非手抄）做排序
 * 多重集比对，并逐文件核对条数。族成员判定取**归一化族键**（`${…}` → `{}`）而非前缀匹配：
 * `/v1/aip/agents/templates`、`/v1/aip/agents/instantiate` 与本批收口的族同根但各只有属主一个
 * 生产方，按 #145/B6-1 口径**刻意不并进常量**（并进去只会让两族对棘轮隐形、不减一分债），
 * 故必须被排除在族集合之外——本门有一条专门断言钉住这两条腿仍写在原处可见。</p>
 *
 * <p><b>判据形状的两条教训（B6-2 教训的续集，都有对应反向用例）</b>：
 * ① 绑定行（`` const X = `${API_BASE}…` ``、`const X = Y;`）不是腿，否则属主自己的声明会把根族点数抬高一；
 * ② **多行 import 的续行不是腿**——本批消费方的 import 是花括号多行体，只按「行首是 import」过滤会留下
 * `  AIP_AGENTS_BASE,` 这种续行，被裸名计数吃成假腿（双模跑出来 10 对 8 才暴露）。判据必须先整条剥 import
 * 语句再按行扫。这两条都属**判据缺陷**，单模自证（只看工作树自洽）发现不了。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { AIP_AGENTS_BASE, AIP_AUDIT_LOGS_BASE } from '../pages/aiworkbench/api';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const OWNER = 'pages/aiworkbench/api.ts';
const CONSUMER = 'services/aiworkbenchApi.ts';

/** 本批收口的三个族键（归一化后，与 `urlFamilyDualFormRatchet` 同口径）。 */
const CLOSED_FAMS = ['/v1/aip/agents', '/v1/aip/agents/{}', '/v1/aip/audit-logs'];

/** 刻意不合流的同根子路径族（单属主、不在重复面；本批必须保持可见）。 */
const KEPT_SUBPATH_LEGAL = [
  '`${API_BASE}/v1/aip/agents/templates`',
  '`${API_BASE}/v1/aip/agents/instantiate`',
];

/** 私设基址判据：引号/反引号后紧跟 `/api`（与 `api-base-single-source` 同口径）。 */
const HARDCODED_RE = /["'`]\/api\b/;

/** ① 整条 import 先剥（含多行花括号体）：只过滤行首会把续行 `NAME,` 吃成假腿。 */
const IMPORT_STMT_RE = /^\s*import\s[\s\S]*?;/gm;
/** ② 绑定行：派生基址声明 / 名别名（二者只入解析表，不构成腿）。 */
const DERIVED_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`\$\{API_BASE\}([^`]*)`;?\s*$/;
const ALIAS_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+);?\s*$/;
/** ③ 派生腿：`` `${任意常量名}尾串` ``（尾串允许转义字符，不允许裸反引号）。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
/** ④ 消费方从属主 import 单源的那一条（同时是解析表的种子）。 */
const IMPORT_BASE_RE = /import\s*\{([^}]*)\}\s*from\s*["'][^"']*pages\/aiworkbench\/api["']/s;
/**
 * ⑤ 族体构造点：裸形态或 `${API_BASE}` 派生态写出收口族（属主唯一性判据用）。
 * 终止符只认引号/反引号或 `/${`（插值段 = `{}` 族），因此 `/v1/aip/agents/templates`
 * 这类**同根子路径族**不会被子串吃掉——与 ③ 的族键口径一致，属前缀判据的同一课。
 */
const FAMILY_RAW_RE = /["'`]\/api\/v1\/aip\/(?:agents|audit-logs)(?:["'`]|\/\$\{)/g;
const FAMILY_DERIVED_RE = /`\$\{API_BASE\}\/v1\/aip\/(?:agents|audit-logs)(?:["'`]|\/\$\{)/g;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.replace(IMPORT_STMT_RE, '').split(/\r?\n/).filter((l) => !isComment(l));
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
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx')
  );
}

function walk(dir: string, out: string[]): string[] {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      walk(full, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      const rel = relative(SRC_ROOT, full).split(sep).join('/');
      if (!isTestFile(rel)) out.push(rel);
    }
  }
  return out;
}

/** 属主文件里的常量取值（消费方解析 `${AIP_*}` 时以此为种子，B6-2 同款坑）。 */
function ownerDeclMap(): Map<string, string> {
  const map = new Map<string, string>([['API_BASE', API_BASE]]);
  for (const l of codeLines(read(OWNER))) {
    const d = l.match(DERIVED_DECL_RE);
    if (d) { map.set(d[1], API_BASE + d[2]); continue; }
    const a = l.match(ALIAS_DECL_RE);
    if (a && map.has(a[2])) map.set(a[1], map.get(a[2])!);
  }
  return map;
}

function baseMap(rel: string, decls: Map<string, string>): Map<string, string> {
  const code = read(rel);
  const map = new Map<string, string>([['API_BASE', API_BASE]]);
  if (rel !== OWNER) {
    const m = code.match(IMPORT_BASE_RE);
    for (const raw of m ? m[1].split(',') : []) {
      const name = raw.trim().split(/\s+as\s+/)[0];
      if (decls.has(name)) map.set(name, decls.get(name)!);
    }
  }
  for (const l of codeLines(code)) {
    const d = l.match(DERIVED_DECL_RE);
    if (d) { map.set(d[1], API_BASE + d[2]); continue; }
    const a = l.match(ALIAS_DECL_RE);
    if (a && map.has(a[2])) map.set(a[1], map.get(a[2])!);
  }
  return map;
}

/** 族面值 = 归一化后落在 CLOSED_FAMS 的三个族键之一（同根子路径族因此被排除）。 */
function inClosed(val: string): boolean {
  const body = normalize(val).replace(/^\/api/, '');
  return CLOSED_FAMS.includes(body);
}

/** 把派生点还原成运行期面值；绑定行不计，模板腿先从行内摘除后再数裸名引用。 */
function famFaces(rel: string, decls: Map<string, string>, src?: string): string[] {
  const code = src ?? read(rel);
  const bm = baseMap(rel, decls);
  const out: string[] = [];
  for (const line of codeLines(code)) {
    if (DERIVED_DECL_RE.test(line) || ALIAS_DECL_RE.test(line)) continue;
    let rest = line;
    for (const m of line.matchAll(SITE_RE)) {
      rest = rest.split(m[0]).join('');
      const val = bm.has(m[1]) ? bm.get(m[1])! + m[2] : `\${${m[1]}}${m[2]}`;
      if (inClosed(val)) out.push(val);
    }
    for (const [name, val] of bm) {
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

/** 族体构造点计数：裸形态 + `${API_BASE}` 派生态（属主唯一性判据）。 */
function familyBodySites(code: string): string[] {
  const hits: string[] = [];
  for (const line of codeLines(code)) {
    for (const m of line.matchAll(FAMILY_RAW_RE)) hits.push(m[0]);
    for (const m of line.matchAll(FAMILY_DERIVED_RE)) hits.push(m[0]);
  }
  return hits;
}

/**
 * 改前裸/派生形态面值快照（8 条，实跑 `git show HEAD:` 双模读出）：
 * 属主 3 条（集合 1 + 详情 1 + 审计 1）、服务层 5 条（集合 2 + 详情 2 + 审计 1）。
 * `${…}` 插值按字面保留，改前后同一写法。
 */
const HEAD_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  'pages/aiworkbench/api.ts': [
    '/api/v1/aip/agents',
    '/api/v1/aip/agents/${encodeURIComponent(id)}',
    '/api/v1/aip/audit-logs',
  ],
  'services/aiworkbenchApi.ts': [
    '/api/v1/aip/agents',
    '/api/v1/aip/agents',
    '/api/v1/aip/agents/${id}',
    '/api/v1/aip/agents/${id}',
    '/api/v1/aip/audit-logs',
  ],
});
const HEAD_TOTAL = 8;

describe('AIP 域基址单源 — /v1/aip/agents + /v1/aip/audit-logs (#145/B6-3)', () => {
  it('前提：两枚常量的运行期面值与 API_BASE 单源取值一致（派生即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(AIP_AGENTS_BASE).toBe('/api/v1/aip/agents');
    expect(AIP_AUDIT_LOGS_BASE).toBe('/api/v1/aip/audit-logs');
  });

  it('面值逐字不变：还原后的 URL 多重集 = 改前快照（8 条）且逐文件条数对得上', () => {
    const decls = ownerDeclMap();
    let total = 0;
    for (const rel of [OWNER, CONSUMER]) {
      const vals = famFaces(rel, decls);
      expect(vals, `${rel} 族面值多重集应等于改前快照`).toEqual([...HEAD_FACES[rel]].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_TOTAL);
  });

  it('全仓属主唯一：收口族的族体只允许属主一人书写（裸形态与 ${API_BASE} 派生态都计）', () => {
    const offenders: string[] = [];
    for (const rel of walk(SRC_ROOT, [])) {
      if (rel === 'services/httpClient.ts') continue;
      const hits = familyBodySites(read(rel));
      if (hits.length === 0) continue;
      if (rel !== OWNER) offenders.push(`${rel} -> ${JSON.stringify(hits)}`);
    }
    expect(offenders, '收口族出现第二生产方即红').toEqual([]);
    expect(familyBodySites(read(OWNER)).length, '属主侧恰有两枚基址声明').toBe(2);
  });

  it('消费方只有 import 腿：服务层零族体书写、零私设基址，且不得自声明同名常量', () => {
    const code = read(CONSUMER);
    expect(code).toMatch(IMPORT_BASE_RE);
    expect(/AIP_AGENTS_BASE/.test(code)).toBe(true);
    expect(/AIP_AUDIT_LOGS_BASE/.test(code)).toBe(true);
    expect(familyBodySites(code)).toEqual([]);
    expect(codeLines(code).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    expect(code).not.toMatch(/\/api\/api\b/);
    const selfDecl = codeLines(code).filter((l) => DERIVED_DECL_RE.test(l) && /AIP_/.test(l));
    expect(selfDecl, '服务层不得再自声明 AIP 基址常量').toEqual([]);
  });

  it('同根子路径族保持可见：templates / instantiate 两条腿仍写在属主的 ${API_BASE} 形态（未隐形化）', () => {
    const ownerLines = codeLines(read(OWNER));
    for (const leg of KEPT_SUBPATH_LEGAL) {
      expect(ownerLines.some((l) => l.includes(leg)), `${leg} 应仍在属主文件里`).toBe(true);
    }
  });

  it('判据能变红：重新声明族体、或把绑定行/多行 import 续行当腿，都必须被本门判出来', () => {
    const decls = ownerDeclMap();
    // a) 第二生产方重新裸拼族体 ⇒ 属主唯一判据必须命中
    const REDECLARE = ['const u = `${API_BASE}/v1/aip/agents`;', 'fetch(u);'].join('\n');
    expect(familyBodySites(REDECLARE)).toEqual(['`${API_BASE}/v1/aip/agents`']);
    // b) 合规形态（import + 裸引用 + 模板腿各一）⇒ 还原出 2 条腿，且多行 import 的续行不计为腿
    const CLEAN = [
      'import {',
      '  AIP_AGENTS_BASE,',
      '  AIP_AUDIT_LOGS_BASE,',
      '} from "../pages/aiworkbench/api";',
      'const list = await apiFetchData(AIP_AGENTS_BASE);',
      'await apiFetchData(`${AIP_AGENTS_BASE}/${id}`);',
      'await apiFetchData(AIP_AUDIT_LOGS_BASE);',
    ].join('\n');
    expect(famFaces(CONSUMER, decls, CLEAN)).toEqual([
      '/api/v1/aip/agents',
      '/api/v1/aip/agents/${id}',
      '/api/v1/aip/audit-logs',
    ]);
    // c) 同根子路径族不得被算进收口族（族键判定而非前缀判定），且注释不计
    const SUBPATH = [
      'const t = await apiFetchData(`${API_BASE}/v1/aip/agents/templates`);',
      '// GET /api/v1/aip/agents 只是注释里的端点说明',
      'const d = await apiFetchData(`${AIP_AGENTS_BASE}/${encodeURIComponent(agentId)}/errors`);',
    ].join('\n');
    expect(famFaces(CONSUMER, decls, SUBPATH)).toEqual([]);
    // 属主唯一判据同口径：子路径族（templates 尾串）不吃成收口族腿
    expect(familyBodySites(SUBPATH.split('\n')[0])).toEqual([]);
    // 插值尾串 = `{}` 族，属收口面，必须仍能被判出来（防止收紧终止符后判据吃空）
    expect(familyBodySites('const u = await apiFetchData(`${API_BASE}/v1/aip/agents/${id}`);')).toEqual([
      '`${API_BASE}/v1/aip/agents/${',
    ]);
    expect(codeLines(SUBPATH).filter((l) => HARDCODED_RE.test(l))).toEqual([]);
    // d) 漂移即红：把族体写错一个字母就不再是收口族（防「判据吃空还当守恒」）
    const DRIFT = 'const x = await apiFetchData(`${API_BASE}/v1/aip/agent`);';
    expect(famFaces(CONSUMER, decls, DRIFT)).toEqual([]);
    expect(familyBodySites(DRIFT)).toEqual([]);
  });

  it('快照自身可校验：8 条 = 属主 3 + 服务层 5 且全部以 /api/v1/aip/ 开头（防快照清空成恒真）', () => {
    const flat = [...HEAD_FACES[OWNER], ...HEAD_FACES[CONSUMER]];
    expect(flat.length).toBe(HEAD_TOTAL);
    expect(HEAD_FACES[OWNER].length).toBe(3);
    expect(HEAD_FACES[CONSUMER].length).toBe(5);
    for (const v of flat) expect(v.startsWith('/api/v1/aip/')).toBe(true);
    for (const fam of CLOSED_FAMS) expect(fam.startsWith('/v1/')).toBe(true);
  });
});
