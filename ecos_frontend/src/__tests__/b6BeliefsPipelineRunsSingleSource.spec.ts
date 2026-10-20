// @vitest-environment node
/**
 * 重复族 `/v1/cognitive/beliefs?domain={}` + `/v1/engine/data/pipeline/runs/{}` 基址单源回归门
 * （#145/B6-15，差距台账 §13.2.4 C1 腿、B6 存量债**最后一批＝关账批**）。
 *
 * <p>本批把棘轮 dupBoth 面的最后 2 族 / 4 属主收口，BASELINE 从此为空对象（30 族全清）。
 * 两族形状相同（各 2 枚页面侧写手 → 1 枚服务/api 层属主 + 2 条 import 腿），但族键形状不同：</p>
 *
 * <p>① `/v1/cognitive/beliefs?domain={}`＝**族键带查询串插值**。改前两枚页面腿把整条
 * `` `${API_BASE}/v1/cognitive/beliefs?domain=${encodeURIComponent(…)}` `` 行内写出，
 * 族键由棘轮按「面值归一化」读成 `beliefs?domain={}` 而非 `beliefs`——**属主常量只收族根**
 * `` `${API_BASE}/v1/cognitive/beliefs` ``，`?domain=` 尾巴刻意留在消费方行内。这不是没收干净：
 * 把查询串并进常量会得到 `/v1/cognitive/beliefs?domain=` 这种「带半条查询串的基址」，
 * 它是另一枚族键的字面量、会让后续批的同根判据失去可比性（同 #145/B6-1 ② 的子路径口径）。</p>
 *
 * <p>② `/v1/engine/data/pipeline/runs/{}`＝**族键带路径插值**，且改后与改前逐字同面：
 * 两枚消费腿都写成 `` `${ENGINE_DATA_PIPELINE_RUNS_BASE}/${encodeURIComponent(…)}` ``，
 * 归一化后仍是 `runs/{}`。同族无单属主子路径腿需要照 ① 保持行内可见性——本族收的即全部两腿。</p>
 *
 * <p>③ **刻意留在行内的两枚子路径腿**（`beliefs/{variable}/override`、
 * `beliefs/{variable}/{version}/replay?…`）：各只有一个生产方，属另一枚族键，
 * 并进常量等于把该族从棘轮族索引面（253）里关掉而不减债（#145/B6-9 ④ 裁定，本门 ② 级快照把它俩钉在面上）。</p>
 *
 * <p>④ 属主落位由 import 图裁决（沿用 #145/B6-9 ①／B6-10 ①／B6-11 ①／B6-14 ①）：
 * beliefs 两枚写手都是页面，任选其一为属主都会让另一枚「页面→页面」取常量 ⇒ 落 `services/cognitiveClient.ts`；
 * runs 一枚在域内 api 层、一枚在组件层 ⇒ 落 `api/dataClient.ts`（同段已册 `PIPELINE_DEFINITIONS_BASE` 先例）。</p>
 *
 * <p>面值守恒按两级快照做证，两组均由同一解析器对 `git archive HEAD` 解出的树与本工作树各跑一遍
 * 后才落笔（实跑读出 ① 级 1/1/0/0/1/1＝4、② 级 0/2/2/0/1/1＝6，两侧逐文件完全一致），非手抄。
 * 族根声明构造点另由全仓 walk 双模读出：HEAD＝0 枚、WT＝2 枚（两属主各 1，均为净新增枚），
 * 与 derSites 282→280 / derTotal 299→297 的「删 4 枚行内派生态 + 增 2 枚族根声明 ⇒ 净 −2」对得上。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { COGNITIVE_BASE, COGNITIVE_BELIEFS_BASE } from '../services/cognitiveClient';
import { ENGINE_DATA_PIPELINE_RUNS_BASE } from '../api/dataClient';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const BELIEFS_OWNER = 'services/cognitiveClient.ts';
const RUNS_OWNER = 'api/dataClient.ts';

/** 四枚消费腿：两条页面列表腿 + 两条轮询腿；导入路径按目录深度各不相同，必须逐条钉住。 */
const CONSUMERS: ReadonlyArray<{ rel: string; importPath: string; name: string }> = [
  { rel: 'pages/scenario/OverrideTab.tsx', importPath: '../../services/cognitiveClient', name: 'COGNITIVE_BELIEFS_BASE' },
  { rel: 'pages/scenario/ReplayTab.tsx', importPath: '../../services/cognitiveClient', name: 'COGNITIVE_BELIEFS_BASE' },
  { rel: 'pages/data-workbench/api/pipeline.ts', importPath: '../../../api/dataClient', name: 'ENGINE_DATA_PIPELINE_RUNS_BASE' },
  // 组件层经 `src/api.ts` 门面取值（先例 `pages/data-engine/DataEngineConfigPanel.tsx`）
  { rel: 'pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx', importPath: '../../../api', name: 'ENGINE_DATA_PIPELINE_RUNS_BASE' },
];

const OWNERS: ReadonlyArray<{ rel: string; name: string; root: string }> = [
  { rel: BELIEFS_OWNER, name: 'COGNITIVE_BELIEFS_BASE', root: '/v1/cognitive/beliefs' },
  { rel: RUNS_OWNER, name: 'ENGINE_DATA_PIPELINE_RUNS_BASE', root: '/v1/engine/data/pipeline/runs' },
];
const SCOPED = [...OWNERS.map((o) => o.rel), ...CONSUMERS.map((c) => c.rel)];

/** 本批收口的两枚族键（归一化后，与 `urlFamilyDualFormRatchet` 逐字同形）。 */
const CLOSED_FAMS = ['/v1/cognitive/beliefs?domain={}', '/v1/engine/data/pipeline/runs/{}'];
/**
 * 族键字面前缀＝族键在第一个 `{}` 之前的整段（`beliefs?domain=` / `runs/`）。
 * 「书写族根本体」必须按**族键前缀**判、不能按族根判：`/v1/cognitive/beliefs/` 打头的
 * override／replay 两条单属主腿是 ③ 裁定刻意留在行内的另一枚族键，按族根判会把它们误计成第二属主。
 */
const KEY_PREFIXES = CLOSED_FAMS.map((f) => f.slice(0, f.indexOf('{}')));

const HARDCODED_RE = /["'`]\/api\b/;
/** ① 整条 import 先剥（含多行花括号体）：只过滤行首会把续行 `NAME,` 吃成假腿。 */
const IMPORT_STMT_RE = /^\s*import\s[\s\S]*?;/gm;
/** ② 绑定行：派生基址声明 / 名别名（只入解析表，不构成腿）。 */
const DERIVED_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`\$\{API_BASE\}([^`]*)`;?\s*$/;
const ALIAS_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+);?\s*$/;
/** ③ 派生腿：`` `${任意常量名}尾串` ``。 */
const SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
/** ④ 族根声明（定界符收尾）：`${API_BASE}/v1/cognitive` 与 `/v1/cognitive/beliefs` 是两枚不同族根。 */
const ROOT_DECL_RE = new RegExp('`\\$\\{API_BASE\\}(?:' + OWNERS.map((o) => escRe(o.root)).join('|') + ')`', 'g');
/** ⑤ 从基址直接拼出族键字面前缀＝「书写该族根体」，属主唯一判据的攻击面（裸形态与派生态都计）。 */
const ROOT_BODY_RE = new RegExp(
  '(?:["\'`]\\/api|`\\$\\{API_BASE\\})(?:' + KEY_PREFIXES.map((p) => escRe(p)).join('|') + ')',
  'g',
);

function escRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * 解析表种子：本批每一枚常量的**运行期真实取值**（不写死字面串）。
 * 两枚族根常量若面值漂了，①②两级快照会整列读空——读空在本门＝红而不是绿，故必须走真实 import。
 */
const CONST_VALUES: ReadonlyMap<string, string> = new Map([
  ['API_BASE', API_BASE],
  ['COGNITIVE_BASE', COGNITIVE_BASE],
  ['COGNITIVE_BELIEFS_BASE', COGNITIVE_BELIEFS_BASE],
  ['ENGINE_DATA_PIPELINE_RUNS_BASE', ENGINE_DATA_PIPELINE_RUNS_BASE],
]);

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(src: string): string[] {
  return src.replace(IMPORT_STMT_RE, '').split(/\r?\n/).filter((l) => !isComment(l));
}

/** 剥 import 的行集不能用来验导入腿（B6-4 规则 ⑦ 沿用）：导入面判据走本函数。 */
function declLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
}

/** `${…}` 逐层归一为 `{}`，与棘轮门族键口径一致（最多 5 层）。 */
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

/** 族成员判定：剥基址前缀后按归一化族键**判等**（① 级＝本批两枚族键本体，不含同根子路径）。 */
function inClosed(val: string): boolean {
  return CLOSED_FAMS.includes(normalize(val).replace(/^\/api/, ''));
}

/** 同根子路径判定（② 级＝含刻意留在行内的单属主腿）。 */
function inScope(val: string): boolean {
  return /\/v1\/(?:cognitive\/beliefs|engine\/data\/pipeline\/runs)\b/.test(val);
}

/**
 * 把派生点还原成运行期面值；绑定行不计，模板腿先从行内摘除后再数裸名引用。
 * 裸名引用必须同时避开前向属性位（`{` / `.`）与后向标识符字节。
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

const famFaces = (rel: string, src?: string) => faces(rel, inClosed, src);
const scopedFaces = (rel: string, src?: string) => faces(rel, inScope, src);

/** 族根声明构造点（属主唯一判据的正形）：只吃定界符收尾的整段族根。 */
function rootDecls(code: string): string[] {
  const hits: string[] = [];
  for (const line of codeLines(code)) for (const m of line.matchAll(ROOT_DECL_RE)) hits.push(m[0]);
  return hits;
}

/** 从基址直接拼出族根本体的书写点（第二属主的攻击面：裸形态与 `${API_BASE}` 派生态都计）。 */
function rootBodySites(code: string): string[] {
  const hits: string[] = [];
  for (const line of codeLines(code)) for (const m of line.matchAll(ROOT_BODY_RE)) hits.push(m[0]);
  return hits;
}

/** 绑定行上的被绑定标识符——「消费方不得自声明同名常量」必须按绑定名判（#145/B6-14 ② 教训）。 */
function boundName(line: string): string | undefined {
  return DERIVED_DECL_RE.exec(line)?.[1] ?? ALIAS_DECL_RE.exec(line)?.[1];
}

/**
 * 改前快照 ①（族键本体 4 条＝每族 2 枚页面写手各 1，双模实跑两侧一致）。
 * 属主两文件恒 0：改前它们连族根都不持（本批为净新增枚）。
 */
const HEAD_FAM_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [BELIEFS_OWNER]: [],
  [RUNS_OWNER]: [],
  'pages/scenario/OverrideTab.tsx': ['/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}'],
  'pages/scenario/ReplayTab.tsx': ['/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}'],
  'pages/data-workbench/api/pipeline.ts': ['/api/v1/engine/data/pipeline/runs/${encodeURIComponent(runId)}'],
  'pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx': [
    '/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}',
  ],
});
const HEAD_FAM_TOTAL = 4;

/**
 * 改前快照 ②（含同根子路径腿 6 条）：比 ① 多出的 2 条正是本批刻意留在行内的 override／replay 腿。
 * ①⊂② 的包含关系由末腿按文件逐条对账，两张表任何一张被清空都会在那里先红。
 */
const HEAD_SCOPED_FACES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  [BELIEFS_OWNER]: [],
  [RUNS_OWNER]: [],
  'pages/scenario/OverrideTab.tsx': [
    '/api/v1/cognitive/beliefs/${encodeURIComponent(variable.trim())}/override',
    '/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}',
  ],
  'pages/scenario/ReplayTab.tsx': [
    '/api/v1/cognitive/beliefs/${encodeURIComponent(variableValue)}/${encodeURIComponent(version.trim())}/replay?${params.toString()}',
    '/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}',
  ],
  'pages/data-workbench/api/pipeline.ts': ['/api/v1/engine/data/pipeline/runs/${encodeURIComponent(runId)}'],
  'pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx': [
    '/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}',
  ],
});
const HEAD_SCOPED_TOTAL = 6;

describe('重复族 /v1/cognitive/beliefs?domain={} + /v1/engine/data/pipeline/runs/{} 基址单源 — (#145/B6-15 关账批)', () => {
  it('前提：两枚族根常量面值单源，且「常量 + 行内尾巴」归一化后逐字等于棘轮族键', () => {
    expect(API_BASE).toBe('/api');
    expect(COGNITIVE_BELIEFS_BASE).toBe('/api/v1/cognitive/beliefs');
    expect(ENGINE_DATA_PIPELINE_RUNS_BASE).toBe('/api/v1/engine/data/pipeline/runs');
    // 族键形状前提：① 带查询串、② 带路径插值——常量只收族根，尾巴留行内 ⇒ 「常量 + 行内尾巴」
    // 归一化剥基址后必须逐字落回棘轮在册的那枚族键，否则本批收的不是同一族。
    const keyOf = (v: string) => normalize(v).replace(/^\/api/, '');
    expect(keyOf(`${COGNITIVE_BELIEFS_BASE}?domain=${'${encodeURIComponent(d)}'}`)).toBe(CLOSED_FAMS[0]);
    expect(keyOf(`${ENGINE_DATA_PIPELINE_RUNS_BASE}/${'${runId}'}`)).toBe(CLOSED_FAMS[1]);
    // 同文件的父族根 `COGNITIVE_BASE` 是**另一枚族**，本批不得把新常量并进它（并了＝族根构造点判据读空）
    expect(COGNITIVE_BASE).toBe('/api/v1/cognitive');
    expect(normalize(COGNITIVE_BASE)).not.toBe('/v1/cognitive/beliefs');
  });

  it('面值逐字不变 ①：族键本体多重集 = 改前快照（4 条 = 两族各 2 枚写手，属主两文件恒 0）', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = famFaces(rel);
      expect(vals, `${rel} 族键本体面值多重集应等于改前快照`).toEqual([...HEAD_FAM_FACES[rel]].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_FAM_TOTAL);
    // 属主侧不该出现「族键本体请求腿」：它只声明族根，列表腿面值全在消费方
    for (const { rel } of OWNERS) expect(famFaces(rel), `${rel} 出现族键本体腿 ⇒ 属主顺带把消费腿也搬进来了`).toEqual([]);
  });

  it('面值逐字不变 ②：含同根子路径腿的多重集 = 改前快照（6 条 = ① 的 4 条 + 留在行内的 override/replay 2 条）', () => {
    let total = 0;
    for (const rel of SCOPED) {
      const vals = scopedFaces(rel);
      expect(vals, `${rel} 同根面值多重集应等于改前快照（提声明不得动任何一条腿的面值）`).toEqual([
        ...HEAD_SCOPED_FACES[rel],
      ].sort());
      total += vals.length;
    }
    expect(total).toBe(HEAD_SCOPED_TOTAL);
    // ③ 裁定的正证：两枚单属主子路径腿仍在消费方行内以 `${API_BASE}` 形态可见（并进常量＝关掉可见性而不减债）
    const cog = scopedFaces('pages/scenario/OverrideTab.tsx');
    expect(cog.filter((v) => v.endsWith('/override')).length).toBe(1);
    const rep = scopedFaces('pages/scenario/ReplayTab.tsx');
    expect(rep.filter((v) => v.includes('/replay?')).length).toBe(1);
    expect(codeLines(read('pages/scenario/OverrideTab.tsx')).filter((l) => l.includes('`${API_BASE}/v1/cognitive/beliefs/')).length).toBe(1);
    expect(codeLines(read('pages/scenario/ReplayTab.tsx')).filter((l) => l.includes('`${API_BASE}/v1/cognitive/beliefs/')).length).toBe(1);
  });

  it('属主唯一：两族族根只允许各自属主一人书写（全仓生产文件扫，族键前缀的裸形态与 ${API_BASE} 派生态都计）', () => {
    const offenders: string[] = [];
    for (const rel of walk('.', [])) {
      if (rel === 'services/httpClient.ts') continue;
      const hits = rootBodySites(read(rel));
      if (hits.length === 0) continue;
      offenders.push(`${rel} -> ${JSON.stringify(hits)}`);
    }
    expect(offenders, '从基址直接拼出族键字面前缀即第二属主（含把 `?domain=` 尾巴退回 `${API_BASE}` 形态）').toEqual([]);
    for (const { rel } of OWNERS) {
      expect(rootDecls(read(rel)).length, `${rel} 应恰有一枚族根声明`).toBe(1);
    }
    // 双模读出：HEAD 族根声明 0 枚 ⇒ 本批两枚都是净新增；全仓不得出现第 3 枚
    expect(walk('.', []).reduce((n, rel) => n + rootDecls(read(rel)).length, 0)).toBe(2);
  });

  it('形状归因：两族同型（净新增族根声明 + 消费腿改裸名），且未顺手改写父族根或同段先例', () => {
    const cog = read(BELIEFS_OWNER);
    expect(cog).toContain('export const COGNITIVE_BELIEFS_BASE = `${API_BASE}/v1/cognitive/beliefs`;');
    expect(cog).toContain('export const COGNITIVE_BASE = `${API_BASE}/v1/cognitive`;');
    // 新常量不得写成 `${COGNITIVE_BASE}/beliefs`：面值等价但族根构造点读空 ⇒ C1 侧平白多降一枚
    expect(codeLines(cog).some((l) => /^export const COGNITIVE_BELIEFS_BASE = `\$\{COGNITIVE_BASE\}/.test(l))).toBe(false);

    const dc = read(RUNS_OWNER);
    expect(dc).toContain('export const ENGINE_DATA_PIPELINE_RUNS_BASE = `${API_BASE}/v1/engine/data/pipeline/runs`;');
    // 同段先例 `PIPELINE_DEFINITIONS_BASE` 必须原样在位（本批不动它，防"顺手合并两族"）
    expect(dc).toContain('export const PIPELINE_DEFINITIONS_BASE = `${API_BASE}/v1/pipeline/definitions`;');
    // 声明不得改成裸字面量：那会让两族从棘轮 DER_RE 面里凭空消失（关掉可见性＝假收口）
    for (const { name } of OWNERS) {
      expect(new RegExp(`^\\s*export const ${name} = ["']\\/api`).test(read(OWNERS.find((o) => o.name === name)!.rel)), `${name} 退化成裸字面量即族面被关掉`).toBe(false);
    }
  });

  it('消费方只有 import 腿：零族根体书写、零私设基址、不得自声明同名常量，且四条导入路径各自正确', () => {
    for (const { rel, importPath, name } of CONSUMERS) {
      const code = read(rel);
      const lines = codeLines(code);
      expect(rootBodySites(code), `${rel} 仍在书写族根本体`).toEqual([]);
      expect(lines.filter((l) => HARDCODED_RE.test(l)), `${rel} 仍有私设基址`).toEqual([]);
      expect(code).not.toMatch(/\/api\/api\b/);
      const imports = declLines(code).filter((l) => l.trimStart().startsWith('import ') && l.includes(name));
      expect(imports.length, `${rel} 应有且仅有 1 条 ${name} 导入`).toBe(1);
      expect(imports[0], `${rel} 导入路径不是 ${importPath}`).toMatch(
        new RegExp(`from '${importPath}'|from "${importPath}"`),
      );
      expect(lines.filter((l) => boundName(l) === name), `${rel} 不得自声明 ${name}`).toEqual([]);
    }
  });

  it('API_BASE 导入命运分态：三条消费腿保留（服务别的族），monitor 整条撤除（该文件除注释外零 token）', () => {
    for (const rel of ['pages/scenario/OverrideTab.tsx', 'pages/scenario/ReplayTab.tsx', 'pages/data-workbench/api/pipeline.ts']) {
      const imports = declLines(read(rel)).filter((l) => l.trimStart().startsWith('import ') && /\bAPI_BASE\b/.test(l));
      expect(imports.length, `${rel} 仍有同根子路径腿 ⇒ API_BASE 导入应恰 1 条，整删即把别的族一起关掉`).toBe(1);
    }
    const mon = read('pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx');
    expect(codeLines(mon).filter((l) => /\bAPI_BASE\b/.test(l)), 'monitor 的两轮询腿是本族唯一腿 ⇒ 代码行不得残留 API_BASE').toEqual([]);
    expect(declLines(mon).filter((l) => l.trimStart().startsWith('import ') && /\bAPI_BASE\b/.test(l)).length).toBe(0);
    expect(declLines(mon).some((l) => l.includes("from '../../../api'") && l.includes('ENGINE_DATA_PIPELINE_RUNS_BASE')), 'monitor 须经 src/api.ts 门面取族根').toBe(true);
  });

  it('判据能变红：第二属主裸拼、尾巴退回派生态、面值漂移、别名自声明、查询串并进基址各造违例且被对应腿吃出', () => {
    // a) 第二属主重新从基址拼族键前缀：beliefs 的 `?domain=` 形态与 runs 的子路径形态都要命中；
    //    而 ③ 裁定留在行内的 override 腿（同根不同族键）不得被误计成属主
    const REDECLARE = [
      'const a = `${API_BASE}/v1/cognitive/beliefs?domain=${d}`;',
      'const b = `${API_BASE}/v1/engine/data/pipeline/runs/${rid}`;',
      'const c = `${API_BASE}/v1/cognitive/beliefs/${v}/override`;',
    ].join('\n');
    expect(rootBodySites(REDECLARE).length, '两枚族键前缀的派生态书写必须都读成第二属主').toBe(2);
    expect(rootBodySites('const t = `${API_BASE}/v1/cognitive/beliefs/${v}/override`;')).toEqual([]);
    expect(rootBodySites('const t = "/api/v1/engine/data/pipeline/runs/" + rid;'), '裸形态同样要吃到').toHaveLength(1);
    // b) 族根声明判据只吃整段收尾的族根：父族根 `${API_BASE}/v1/cognitive` 不得被算成 beliefs 属主
    expect(rootDecls('export const COGNITIVE_BASE = `${API_BASE}/v1/cognitive`;')).toEqual([]);
    expect(rootDecls('export const X = `${API_BASE}/v1/cognitive/beliefs`;').length).toBe(1);
    // c) 面值漂移：常量值换了但快照照登 ⇒ 同一行源码必须读成另一枚面值
    const LEG = 'const r = await get(`${ENGINE_DATA_PIPELINE_RUNS_BASE}/${encodeURIComponent(runId)}`);';
    expect(famFaces('x.ts', LEG)).toEqual(['/api/v1/engine/data/pipeline/runs/${encodeURIComponent(runId)}']);
    const DRIFT = new Map([...CONST_VALUES].map(([k, v]) => [k, k === 'ENGINE_DATA_PIPELINE_RUNS_BASE' ? '/api/v1/engine/data/runs' : v]));
    const drifted = faces('x.ts', (v) => v.startsWith('/api/v1/engine/data'), LEG, DRIFT);
    expect(drifted).toEqual(['/api/v1/engine/data/runs/${encodeURIComponent(runId)}']);
    expect(HEAD_FAM_FACES['pages/data-workbench/api/pipeline.ts']).not.toContain(drifted[0]);
    // d) 别名自声明：消费方把属主名重新绑一遍 ⇒ boundName 抓得住
    expect(boundName('export const COGNITIVE_BELIEFS_BASE = `${API_BASE}/v1/cognitive/beliefs`;')).toBe('COGNITIVE_BELIEFS_BASE');
    expect(boundName('const x = COGNITIVE_BELIEFS_BASE;')).toBe('x');
    // e) 查询串并进常量（① 裁定禁止的形状）：面值归一化后仍落回同族键 ⇒ ① 级快照读不出发红，
    //    能读出的只有「这不再是一枚族根声明」（定界符收尾判据失灵）——所以本批必须两判据并跑，缺一即漏。
    const MERGED_DECL = 'export const COGNITIVE_BELIEFS_BASE = `${API_BASE}/v1/cognitive/beliefs?domain=`;';
    expect(rootDecls(MERGED_DECL), '带查询串尾巴的声明不该被认成族根声明').toEqual([]);
    expect(inClosed('/api/v1/cognitive/beliefs?domain=${encodeURIComponent(d)}')).toBe(true);
    // f) 规则 ⑦ 反证：单行 import 在剥 import 的行集里恒不可见 ⇒ 导入腿判据必须走 declLines
    const ONE_LINE_IMPORT = "import { ENGINE_DATA_PIPELINE_RUNS_BASE } from '../../../api/dataClient';";
    expect(codeLines(ONE_LINE_IMPORT), '剥 import 后只剩一个空行，导入腿在该行集恒不可见').toEqual(['']);
    expect(declLines(ONE_LINE_IMPORT).length).toBe(1);
  });

  it('棘轮在册性：两族已从 BASELINE 剪除，且关账后基线为空、判据活性由合成行承证', () => {
    const ratchet = read('__tests__/urlFamilyDualFormRatchet.test.ts');
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
    // 关账总账：B6 全 30 族逐族在册，本批两枚族键必须点名在列（清单被剪薄即红）
    const closed = Number((ratchet.match(/expect\(CLOSED_FAMILIES\.length[^)]*\)\.toBe\((\d+)\)/) ?? [])[1]);
    expect(closed).toBe(30);
    for (const fam of CLOSED_FAMS) {
      expect(ratchet.includes(`'${fam}',`), `CLOSED_FAMILIES 里找不到 ${fam} ⇒ 收口清单被剪薄`).toBe(true);
      expect(ratchet.includes(`'${fam}': [`), `${fam} 已收口，不得回到 BASELINE 豁免位`).toBe(false);
    }
  });

  it('快照自身可校验：4 = 两族各 2 枚写手、6 = 4 + 留在行内的 2 条子路径腿，且 ①⊂②', () => {
    const flatFam = SCOPED.flatMap((rel) => [...HEAD_FAM_FACES[rel]]);
    const flatScoped = SCOPED.flatMap((rel) => [...HEAD_SCOPED_FACES[rel]]);
    expect(flatFam.length).toBe(HEAD_FAM_TOTAL);
    expect(flatScoped.length).toBe(HEAD_SCOPED_TOTAL);
    expect(SCOPED.length).toBe(6);
    expect(HEAD_FAM_FACES[BELIEFS_OWNER].length).toBe(0);
    expect(HEAD_FAM_FACES[RUNS_OWNER].length).toBe(0);
    expect(HEAD_SCOPED_FACES[BELIEFS_OWNER].length).toBe(0);
    expect(HEAD_SCOPED_FACES[RUNS_OWNER].length).toBe(0);
    expect(HEAD_FAM_FACES['pages/scenario/OverrideTab.tsx'].length).toBe(1);
    expect(HEAD_FAM_FACES['pages/scenario/ReplayTab.tsx'].length).toBe(1);
    expect(HEAD_FAM_FACES['pages/data-workbench/api/pipeline.ts'].length).toBe(1);
    expect(HEAD_FAM_FACES['pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx'].length).toBe(1);
    for (const v of [...flatFam, ...flatScoped]) expect(v.startsWith('/api/v1/')).toBe(true);
    expect(CLOSED_FAMS.every((f) => f.startsWith('/v1/'))).toBe(true);
    for (const rel of SCOPED) {
      for (const v of HEAD_FAM_FACES[rel]) expect(HEAD_SCOPED_FACES[rel], `${rel} 的 ① 级面值不在 ② 级里`).toContain(v);
    }
    // 每枚族键在快照里恰有 2 个生产方＝改前的 dupBoth 属主数（快照被改成 1 或 3 都说明本批口径漂了）
    for (const fam of CLOSED_FAMS) {
      const producers = SCOPED.filter((rel) => HEAD_FAM_FACES[rel].some((v) => normalize(v).replace(/^\/api/, '') === fam));
      expect(producers.length, `${fam} 的改前属主数不是 2 ⇒ 与棘轮 dupBoth 4 族 / 8 属主对不上`).toBe(2);
    }
  });
});
