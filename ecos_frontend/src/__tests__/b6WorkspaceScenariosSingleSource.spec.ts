// @vitest-environment node
/**
 * b6WorkspaceScenariosSingleSource — #145/B6-2 存量债收口的守恒 + 单源双门
 *
 * 本批改的是**生产方个数**（`/v1/workspace/scenarios` 三属主 + `/v1/workspace/scenarios/{}`
 * 两属主 → 由 `services/scenarioActionClient.ts` 导出的 `WORKSPACE_SCENARIOS_BASE` 单源持有），
 * 不是 URL 形态。而单源化有一个必须显式对冲的盲区：页面里 `` `${WORKSPACE_SCENARIOS_BASE}/x` ``
 * 不含 `/v1/` 族体，`urlFamilyDualFormRatchet` 的 DER_RE/RAW_RE **吃不到** ⇒ 族面变薄与判据失灵
 * 同形（同 B6-1 文件头 ②）。故本门用**常量解析器**把导入态/别名态还原成面值，逐文件对 HEAD 快照
 * 做多重集守恒：9 条腿一条不多、一条不少、面值一字不改，才是「只挪属主、没改语义」。
 *
 * 判据三条形状约定（临时脚本 `b62cmp.cjs` 双模式实跑对读后即原样收录，HEAD/工作树各 9/9 全等）：
 *   ① **绑定行只入表、不算腿**：``const X = `${API_BASE}…`;`` 与 `const X = Y;` 是常量的定义，
 *      不是一次 URL 使用。漏了这条，属主自己的声明会被记成第 4 条根腿 ⇒ 守恒虚增。
 *   ② **裸引用算腿**：`apiFetchData(WORKSPACE_SCENARIOS_BASE)` 是该族根面值的一次真实使用。
 *      漏了这条，四个页面的 6 条根/详情腿会静默消失 ⇒ 守恒给出假 −N，把「收口成功」读成「删了请求」。
 *   ③ 族成员用**边界判定**（`v === FAM || v.startsWith(FAM + '/')`），不用裸前缀——同根异族
 *      （`/api/v1/workspace/scenario-sandbox`）必须排除在外。
 *
 * 口径注：`services/cognitive/outcomesApi.ts` 与 `pages/scenario/CognitionPanel.tsx` 里的
 * `` `${API_BASE}/v1/workspace/scenarios/{}/runs…` `` 属**另外的族**（单属主、不在 BASELINE 欠账面内），
 * 本批不收口；故构造方判定按「归一化族体恰为这两个收口族」取值，而不是按前缀。
 *
 * @license Apache-2.0
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import {
  WORKSPACE_SCENARIOS_BASE,
  scenarioActionsUrl,
  scenarioDecisionsUrl,
  workspaceScenariosUrl,
} from '../services/scenarioActionClient';

const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));
/** 两族收口后的唯一属主。 */
const OWNER = 'services/scenarioActionClient.ts';
/** 本批从属主 import 常量的四个消费方（rel → 导入腿全文）。 */
const CONSUMERS: ReadonlyArray<{ rel: string; importLine: string }> = [
  { rel: 'pages/ScenarioManagementView.tsx', importLine: "import { WORKSPACE_SCENARIOS_BASE } from '../services/scenarioActionClient';" },
  { rel: 'pages/workbench/NewScenarioModal.tsx', importLine: "import { WORKSPACE_SCENARIOS_BASE } from '../../services/scenarioActionClient';" },
  { rel: 'pages/workbench/index.tsx', importLine: "import { WORKSPACE_SCENARIOS_BASE } from '../../services/scenarioActionClient';" },
  { rel: 'pages/workbench/[scenarioId]/index.tsx', importLine: "import { WORKSPACE_SCENARIOS_BASE } from '../../../services/scenarioActionClient';" },
];
/** 本批收口的两个族（族体归一化后的键，与棘轮门同口径）。 */
const CLOSED_FAMS = ['/v1/workspace/scenarios', '/v1/workspace/scenarios/{}'];

/** 族面值 = 单源常量的运行期取值；HEAD 快照 9 条腿全部以此为前缀。 */
const FAM = WORKSPACE_SCENARIOS_BASE;

const RAW_CONSTRUCT_RE = /["'`]\/api\/v1\/workspace\/scenarios["'`]/;
/** 派生腿：`` `${任意常量名}尾串` ``（尾串允许转义字符，不允许裸反引号）。 */
const DER_SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
/** ① 绑定行：派生基址声明 / 名别名（二者只入表，不构成腿）。 */
const DERIVED_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`\$\{API_BASE\}([^`]*)`;?\s*$/;
const ALIAS_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+);?\s*$/;
/** 消费方导入单源常量的那一条（同时是解析器的种子）。 */
const IMPORT_BASE_RE = /import\s*\{[^}]*\bWORKSPACE_SCENARIOS_BASE\b[^}]*\}\s*from\s*'[^']*services\/scenarioActionClient'/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}
function codeLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l) && !/^\s*import\s/.test(l));
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

/** `${...}` 归一为 `{}`（与棘轮门 normalize 同款迭代展开，最多 5 轮）。 */
function normalize(body: string): string {
  let s = body;
  for (let i = 0; i < 5 && /\$\{[^{}]*\}/.test(s); i += 1) s = s.replace(/\$\{[^{}]*\}/g, '{}');
  return s.replace(/\$\{[^{}]*\}/g, '{}');
}

/**
 * 一段源码文本里「常量名 → 面值」表：导入的单源常量做种子，本地 `${API_BASE}…` 声明入表，
 * `const A = B;` 名别名迭代展开。刻意不展开二级模板派生（只解 ① 的两类绑定行）。
 */
function baseMap(code: string): Map<string, string> {
  const map = new Map<string, string>([['API_BASE', API_BASE]]);
  if (IMPORT_BASE_RE.test(code)) map.set('WORKSPACE_SCENARIOS_BASE', FAM);
  const lines = codeLines(code);
  for (const line of lines) {
    const m = line.match(DERIVED_DECL_RE);
    if (m) map.set(m[1], `${API_BASE}${m[2]}`);
  }
  for (let i = 0; i < 3; i += 1) {
    for (const line of lines) {
      const m = line.match(ALIAS_DECL_RE);
      if (m && map.has(m[2]) && !map.has(m[1])) map.set(m[1], map.get(m[2])!);
    }
  }
  return map;
}

/** 面值是否落在族内（**边界判定**，不是前缀判定）。 */
function inFamily(val: string): boolean {
  return val === FAM || val.startsWith(`${FAM}/`);
}

/**
 * 一个文件里所有还原后落在族内的 URL 面值（排序后即该文件的面值多重集）。
 * 模板位点先从该行文本中摘除，再对表内常量做**裸引用**计数——`${` 与 `.` 前缀不计，防重复计腿。
 */
function famFaces(code: string): string[] {
  const bm = baseMap(code);
  const out: string[] = [];
  for (const line of codeLines(code)) {
    if (DERIVED_DECL_RE.test(line) || ALIAS_DECL_RE.test(line)) continue;
    let rest = line;
    for (const m of line.matchAll(DER_SITE_RE)) {
      rest = rest.split(m[0]).join('');
      const val = bm.has(m[1]) ? bm.get(m[1])! + m[2] : `\${${m[1]}}${m[2]}`;
      if (inFamily(val)) out.push(val);
    }
    for (const [name, val] of bm) {
      if (name === 'API_BASE' || !inFamily(val)) continue;
      const parts = rest.split(name);
      for (let i = 0; i < parts.length - 1; i += 1) {
        const before = parts[i].slice(-1);
        if (before === '{' || before === '.') continue;
        out.push(val);
      }
    }
  }
  return out.sort();
}

/** 一段源码文本里两个收口族的构造点条数（按**归一化族体**取值，runs 等别族不计）。 */
function constructCount(code: string): number {
  let n = 0;
  for (const line of codeLines(code)) {
    if (RAW_CONSTRUCT_RE.test(line)) n += 1;
    const m = line.match(/`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/);
    if (m && CLOSED_FAMS.includes(normalize(m[1]))) n += 1;
  }
  return n;
}

/** 全 src 生产面：两个收口族的构造方（rel → 构造点数）。 */
function constructors(): Map<string, number> {
  const out = new Map<string, number>();
  for (const rel of walk('.', [])) {
    if (rel === 'services/httpClient.ts') continue;
    const n = constructCount(read(rel));
    if (n > 0) out.set(rel, n);
  }
  return out;
}

/**
 * 改前（HEAD 62bf1c12）五文件族腿面值快照，临时脚本 `b62cmp.cjs` 按 `git show HEAD:` 在本判据下
 * 实跑读出后原样收录。条数 3+1+1+1+3 = **9**。
 */
const HEAD_FACES: Readonly<Record<string, readonly string[]>> = {
  'pages/ScenarioManagementView.tsx': [
    '/api/v1/workspace/scenarios',
    '/api/v1/workspace/scenarios',
    '/api/v1/workspace/scenarios/${wizardScenarioId}',
  ],
  'pages/workbench/NewScenarioModal.tsx': ['/api/v1/workspace/scenarios'],
  'pages/workbench/index.tsx': ['/api/v1/workspace/scenarios'],
  'pages/workbench/[scenarioId]/index.tsx': ['/api/v1/workspace/scenarios/${scenarioId}'],
  [OWNER]: [
    '/api/v1/workspace/scenarios',
    '/api/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/actions',
    '/api/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/decisions',
  ],
};
const HEAD_TOTAL = Object.values(HEAD_FACES).reduce((n, a) => n + a.length, 0);

describe('b6WorkspaceScenariosSingleSource — /v1/workspace/scenarios 两族单源守恒 (#145/B6-2)', () => {
  it('单源常量取值 = HEAD 族根面值（属主改值即红，页面腿一律随之）', () => {
    expect(API_BASE).toBe('/api');
    expect(FAM).toBe('/api/v1/workspace/scenarios');
    expect(workspaceScenariosUrl()).toBe('/api/v1/workspace/scenarios');
  });

  it('URL 工厂输出与 HEAD 逐字一致（真实函数调用，非文本比对）', () => {
    expect(scenarioActionsUrl('s-1')).toBe('/api/v1/workspace/scenarios/s-1/actions');
    expect(scenarioDecisionsUrl('s-1')).toBe('/api/v1/workspace/scenarios/s-1/decisions');
    // 保留字/斜杠必须转义，否则路径段会被注入（收口前后同一语义）
    expect(scenarioActionsUrl('a/b')).toBe('/api/v1/workspace/scenarios/a%2Fb/actions');
  });

  it('守恒：五文件族腿面值多重集与 HEAD 快照逐文件全等，总数 9 不变', () => {
    for (const [rel, headFaces] of Object.entries(HEAD_FACES)) {
      expect(famFaces(read(rel)), `${rel} 族腿漂移`).toEqual([...headFaces].sort());
    }
    const total = Object.keys(HEAD_FACES).reduce((n, rel) => n + famFaces(read(rel)).length, 0);
    expect(total).toBe(HEAD_TOTAL);
    expect(HEAD_TOTAL).toBe(9);
  });

  it('属主唯一：两个收口族在全 src 生产面只有属主一个构造方', () => {
    const cs = constructors();
    expect([...cs.keys()].sort()).toEqual([OWNER]);
    expect(cs.get(OWNER)).toBe(1);
  });

  it('消费方四文件：导入单源常量，且不再各自声明族基址', () => {
    for (const c of CONSUMERS) {
      const code = read(c.rel);
      expect(code, `${c.rel} 缺单源导入腿`).toContain(c.importLine);
      expect(codeLines(code).some((l) => DERIVED_DECL_RE.test(l) && l.includes('/v1/workspace/scenarios')),
        `${c.rel} 不得再自建族基址`).toBe(false);
    }
    // NewScenarioModal 保留 `const SCENARIOS_URL = WORKSPACE_SCENARIOS_BASE;` 别名腿：
    // 它是①的活样本（绑定行不算腿、别名面值仍进②的裸引用计数）
    expect(read('pages/workbench/NewScenarioModal.tsx')).toContain('const SCENARIOS_URL = WORKSPACE_SCENARIOS_BASE;');
  });

  it('判据能变红 ①：消费方再自建一份族基址 ⇒ 属主唯一腿立即变多属主', () => {
    const DEFECT = [
      "import { API_BASE } from '../../services/httpClient';",
      'const LOCAL = `${API_BASE}/v1/workspace/scenarios`;',
      'fetch(LOCAL);',
    ].join('\n');
    expect(constructCount(DEFECT)).toBe(1);
    expect(famFaces(DEFECT)).toEqual(['/api/v1/workspace/scenarios']);
  });

  it('判据能变红 ②：绑定行不算腿、裸引用必须算腿（漏任一条即守恒虚报）', () => {
    const ONLY_BIND = 'const B = `${API_BASE}/v1/workspace/scenarios`;';
    expect(famFaces(ONLY_BIND), '声明行本身不是请求腿').toEqual([]);
    const ALIAS_USED = [ONLY_BIND, 'const U = B;', 'apiFetchData(U);'].join('\n');
    expect(famFaces(ALIAS_USED)).toEqual(['/api/v1/workspace/scenarios']);
    const TEMPLATE_USED = ['const B = `${API_BASE}/v1/workspace/scenarios`;', 'apiFetchData(`${B}/x`);'].join('\n');
    expect(famFaces(TEMPLATE_USED)).toEqual(['/api/v1/workspace/scenarios/x']);
    // 裸引用去重依赖「模板位点先摘除」：同一条腿不得既按模板又按裸名计两次
    expect(famFaces(TEMPLATE_USED).length).toBe(1);
  });

  it('判据能变红 ③：面值漂移与同根异族都不被放过', () => {
    const DRIFT = ['const B = `${API_BASE}/v1/workspace/scene`;', 'apiFetchData(B);'].join('\n');
    expect(famFaces(DRIFT), '漂移后的面值不该仍算族内腿').toEqual([]);
    expect(famFaces(read('pages/workbench/NewScenarioModal.tsx')).length).toBe(1);
    // 同根异族（`/api/v1/workspace/scenario-sandbox`）按边界判定排除
    const SAME_ROOT = 'const X = `${API_BASE}/v1/workspace/scenario-sandbox`;\nfetch(X);';
    expect(famFaces(SAME_ROOT)).toEqual([]);
    expect(constructCount(SAME_ROOT)).toBe(0);
  });
});
