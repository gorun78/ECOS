// @vitest-environment node
/**
 * b6AgentMeshGuardrailsSingleSource.spec.ts — `/v1/agent-mesh` + `/v1/guardrails/policies` 两族单源化取证 (#145/B6-11)
 *
 * <p>差距台账 §13.2.4 C1 腿的 B6 存量债第十一批。改前 `urlFamilyDualFormRatchet` 的 BASELINE 在册两族：
 * `/v1/agent-mesh`（`api/agentClient.ts` 的私有 `AGENT_MESH_BASE` ＋ `services/agentMeshApi.ts` 的私有 `MESH`）、
 * `/v1/guardrails/policies`（`services/cognitiveEngineApi.ts` 的两枚行内腿 ＋ `pages/aiworkbench/api.ts`
 * 的一枚行内腿 ＋ `pages/Guardrails/constants.ts` 的一枚**影子名**声明）。现每族恰 1 枚导出常量持有族根体
 * ⇒ dupBoth 9 族 → 7 族，属主 18 → 14。</p>
 *
 * <p>**本批的真正的债不在"两属主"这一维**。改前 `pages/Guardrails/constants.ts` 写的是
 * `import { API_BASE as API_ROOT } from '../../services/httpClient'` ＋
 * `export const API_BASE = \`${API_ROOT}/v1/guardrails/policies\`;`——**本文件的 `API_BASE` 不叫单源那个值**，
 * 而 `pages/GuardrailsView.tsx` 恰恰 `import { API_BASE } from './Guardrails/constants'` 并把它用作 7 条腿的基址。
 * 于是两族判据同时瞎：① 棘轮的 `DER_RE` 把 token 名钉死成 `API_BASE`，`${API_ROOT}/v1/…` 这类**别名派生态**
 * 不入族面（D-B5-40 的名字变体腿，名字轴）；② 本仓任何"按名取值"的解析器只要把 `import { API_BASE }`
 * 一律当成 httpClient 的 `/api`，`GuardrailsView` 的 7 条腿就整体隐形——**这条我在写本门时实踩过**
 * （第一版解析器给出的 HEAD 面数 22，比正确值少 7，恰好等于那 7 条腿）。修法与棘轮同批：
 * 派生态 token 名改成**按文件取令牌集**（`API_BASE` ∪ 该文件的 `API_BASE as X` 别名），
 * 面值解析改成**按模块说明符跟值**（`import { N as L } from 'spec'` ⇒ 读 spec 文件的导出表取 N 的值，
 * `export { N } from 'spec2'` 转发链跟到底）。生产面另加两条禁令钉死不复发：
 * 全 src 零 `API_BASE as X` 导入别名、零非 httpClient 的 `const API_BASE =` 影子声明。</p>
 *
 * <p>**两族属主落位不同**（按实测形状逐条钉，防后继批把某一族当通则）：
 * ① agent-mesh 两属主分属 api/services，按 #145/B6-8 ② 判据落 **api 层** `agentClient.ts`
 * （services→api 不成环），另一侧私有 `MESH` **整枚删除** ⇒ 该箱族根 −1；
 * ② guardrails/policies 的属主落**声明位不动**的服务侧 `cognitiveEngineApi.ts`（该文件本就是这端点的正典实现，
 * 且 `pages/Guardrails/*` 与 `pages/aiworkbench/api.ts` 都已在服务面取数，页面→服务取常量不倒置分层），
 * 页面侧 `constants.ts` 的影子声明改为**别名重导出行**（`export { NAME } from` 不含 URL 体 ⇒
 * 不构成第二个族根构造方，#145/B6-5 ② / #145/B6-9 ② / #145/B6-10 ③ 判例），该箱 −1、属主箱 +1。</p>
 *
 * <p>**面值快照 29 条**系 2026-10-20 把 `git archive HEAD ./src` 解出的整棵树与本批工作树用**同一份解析器**
 * 逐行读出（非手抄），总数 29 → 28，差额恰由三处**有名字归属**的调整互相抵出：
 * `services/agentMeshApi.ts` −1（私有 `MESH` 族根声明删除）、`pages/Guardrails/constants.ts` −1
 * （影子 `API_BASE` 声明上收）、`services/cognitiveEngineApi.ts` +1（净新增属主声明）。
 * 8 箱逐箱等式 + 两个总数等式同时成立 ⇒ 本批没有任何一条腿凭空消失，也没有多出一条改前不存在的请求面；
 * 特别是 `pages/GuardrailsView.tsx` 那 7 条腿在两棵树上**逐字同面**（改前改后都是那 7 条，只是终于可被看见）。
 * 口径纪律与 #145/B6-10 同：别名链迭代求解到不动点、行尾 `\r` 先剥（本仓 CRLF，`$` 无 `m` 标志只认输入末尾）、
 * 同值别名声明行不计为一条请求、裸名引用要过两侧标识符边界（`NAME` 撞长名 `NAMEX` 不算腿——
 * 这条判据缺陷是 #145/B6-11 的防变红 fixture 自己踩出来的，同批在 services/aiworkbench 两枚姊妹门一并修掉）。</p>
 *
 * <p>**族作用域口径与棘轮一致**：`inFamily` 只吃"族根本身"或"族根后紧跟斜杠"，故族根后**直接**跟 `?`
 * 的腿不计（本批无此形状；`agentClient` 的 `missions?limit=` 是斜杠后的更细腿，两棵树里都照常计入）。
 * `pages/aiworkbench/api.ts` 的 4 条 `/agent-mesh/agents` 腿与 `cognitiveEngineApi` 的
 * `policies/${id}` DELETE 腿刻意保持 `${API_BASE}` 行内形态——并进去只是对棘轮隐形而不减债
 * （#145/B6-1 ② / #145/B6-9 ④ / #145/B6-10 ④ 判例）。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative, sep, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { AGENT_MESH_BASE } from '../api/agentClient';
import { GUARDRAILS_POLICIES_BASE } from '../services/cognitiveEngineApi';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const CANONICAL = 'services/httpClient.ts';

const esc = (s: string): string => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** 两枚属主常量：族根面值 + 属主文件 + 声明位命运。 */
const OWNERS = [
  {
    body: '/api/v1/agent-mesh',
    name: 'AGENT_MESH_BASE',
    owner: 'api/agentClient.ts',
    value: AGENT_MESH_BASE,
    netNewDecl: false,
  },
  {
    body: '/api/v1/guardrails/policies',
    name: 'GUARDRAILS_POLICIES_BASE',
    owner: 'services/cognitiveEngineApi.ts',
    value: GUARDRAILS_POLICIES_BASE,
    netNewDecl: true,
  },
] as const;

const BODIES: readonly string[] = OWNERS.map((o) => o.body);

/**
 * 改前逐箱运行期面值（29 条，同一解析器在 `git archive HEAD` 解出的树上实跑读出）。
 * 箱键＝`族根|文件`；只收录该文件在该族作用域内的面值，插值段按字面保留。
 */
const HEAD_FACES: Record<string, readonly string[]> = {
  '/api/v1/agent-mesh|api/agentClient.ts': [
    '/api/v1/agent-mesh',
    '/api/v1/agent-mesh/agents',
    '/api/v1/agent-mesh/missions',
    '/api/v1/agent-mesh/missions/${id}',
    '/api/v1/agent-mesh/missions/${id}/execute',
    '/api/v1/agent-mesh/missions/${id}/tasks',
    '/api/v1/agent-mesh/missions?limit=${limit}',
  ],
  '/api/v1/agent-mesh|services/agentMeshApi.ts': [
    '/api/v1/agent-mesh',
    '/api/v1/agent-mesh/agents',
    '/api/v1/agent-mesh/missions',
    '/api/v1/agent-mesh/missions',
    '/api/v1/agent-mesh/missions/${missionId}/execute',
  ],
  '/api/v1/agent-mesh|pages/aiworkbench/api.ts': [
    '/api/v1/agent-mesh/agents',
    '/api/v1/agent-mesh/agents',
    '/api/v1/agent-mesh/agents/${encodeURIComponent(id)}',
    '/api/v1/agent-mesh/agents/${encodeURIComponent(id)}',
  ],
  '/api/v1/guardrails/policies|services/cognitiveEngineApi.ts': [
    '/api/v1/guardrails/policies',
    '/api/v1/guardrails/policies',
    '/api/v1/guardrails/policies/${id}',
  ],
  '/api/v1/guardrails/policies|pages/aiworkbench/api.ts': [
    '/api/v1/guardrails/policies',
  ],
  '/api/v1/guardrails/policies|pages/Guardrails/constants.ts': [
    '/api/v1/guardrails/policies',
  ],
  '/api/v1/guardrails/policies|pages/GuardrailsView.tsx': [
    '/api/v1/guardrails/policies',
    '/api/v1/guardrails/policies',
    '/api/v1/guardrails/policies/${encodeURIComponent(deleteTarget.id)}',
    '/api/v1/guardrails/policies/${encodeURIComponent(editing.id)}',
    '/api/v1/guardrails/policies/${encodeURIComponent(policy.id)}',
    '/api/v1/guardrails/policies/${encodeURIComponent(policy.id)}/compile',
    '/api/v1/guardrails/policies/${encodeURIComponent(policy.id)}/preview',
  ],
  '/api/v1/guardrails/policies|pages/aiworkbench/AgentStudioView.tsx': [
    '/api/v1/guardrails/policies/preview',
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
    kind: 'merged-root-decl',
    body: '/api/v1/agent-mesh',
    file: 'services/agentMeshApi.ts',
    name: 'MESH',
    why: '改前该文件自持一枚 `${API_BASE}/v1/agent-mesh` 私有声明，本批整枚删除改引属主 ⇒ 该箱 −1，属主 2→1',
  },
  {
    kind: 'merged-root-decl',
    body: '/api/v1/guardrails/policies',
    file: 'pages/Guardrails/constants.ts',
    name: 'API_BASE',
    why: '改前该文件用导入别名 `API_BASE as API_ROOT` 造出一枚**同名影子** `API_BASE` 持有族根，本批上收属主、本行改为别名重导出（不含 URL 体）⇒ 该箱 −1',
  },
  {
    kind: 'net-new-decl',
    body: '/api/v1/guardrails/policies',
    file: 'services/cognitiveEngineApi.ts',
    name: 'GUARDRAILS_POLICIES_BASE',
    why: '属主声明位净新增 ⇒ 该箱 +1（族根面从页面侧搬进服务侧，不是多出一条请求）',
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
  // "整行形状"类判据（同值别名排除、影子声明）会因一行有没有注释而随机失效。
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

const OWNER_BY_NAME = new Map(OWNERS.map((o) => [o.name, o.value] as const));

const TMPL_DECL = /(?:export\s+)?(?:const|let|var)\s+([A-Za-z0-9_]+)\s*(?::[^=]*)?=\s*`([^`]*)`/;
const LIT_DECL = /(?:export\s+)?(?:const|let|var)\s+([A-Za-z0-9_]+)\s*(?::[^=]*)?=\s*['"]([^'"]*)['"]/;
const ALIAS_DECL = /(?:export\s+)?(?:const|let|var)\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+)\s*;?\s*(?:\/\/.*)?$/;
const IMPORT_RE = /^import\s+(?:type\s+)?\{([^}]*)\}\s*from\s*['"]([^'"]+)['"]/;
const REEXPORT_RE = /^export\s+\{([^}]*)\}\s*(?:type\s+)?from\s*['"]([^'"]+)['"]/;

/** `${X}` 插值求值：已解析常量优先（含本文件对 `API_BASE` 的影子声明），运行期变量段按字面保留。 */
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
      if (simple && Object.prototype.hasOwnProperty.call(consts, expr)) out += consts[expr];
      else if (expr === 'API_BASE') out += API_BASE;
      else out += '${' + expr + '}';
      i = end + 1;
      continue;
    }
    out += body[i];
    i += 1;
  }
  return out;
}

function intraFile(src: string): { consts: Record<string, string>; lines: string[] } {
  const consts: Record<string, string> = {};
  const lines = codeLines(src);
  for (const l of lines) {
    const m = l.match(IMPORT_RE);
    if (!m) continue;
    for (const raw of m[1].split(',').map((x) => x.trim()).filter(Boolean)) {
      const parts = raw.split(/\s+as\s+/);
      if ((parts[0] || '').trim() === 'API_BASE') consts[(parts[1] || parts[0]).trim()] = API_BASE;
    }
  }
  // 文件内 const 链迭代求解到不动点（私有名 → 私有名、影子名覆盖单源名都靠这条）
  for (let pass = 0; pass < 8; pass++) {
    let changed = false;
    for (const l of lines) {
      const d = l.match(TMPL_DECL);
      if (d) {
        const deps = [...d[2].matchAll(/\$\{([A-Za-z0-9_]+)(?:\.[^}]*)?\}/g)].map((x) => x[1]);
        if (deps.every((x) => x === 'API_BASE' || x in consts)) { consts[d[1]] = evalTemplate(d[2], consts); changed = true; }
      }
      const t = l.match(LIT_DECL);
      if (t) { consts[t[1]] = t[2]; changed = true; }
      const a = l.match(ALIAS_DECL);
      if (a) {
        if (a[2] === 'API_BASE') { consts[a[1]] = API_BASE; changed = true; }
        else if (a[2] in consts) { consts[a[1]] = consts[a[2]]; changed = true; }
      }
    }
    if (!changed) break;
  }
  return { consts, lines };
}

/** 模块说明符 → src 内相对路径（`./`、`../`、`@/` 三种写法，补扩展名与 index）。 */
function moduleOf(fromRel: string, spec: string): string | null {
  if (!spec.startsWith('.') && !spec.startsWith('@/')) return null;
  const base = spec.startsWith('@/')
    ? join(SRC_ROOT, '..', spec.slice(2))
    : join(dirname(join(SRC_ROOT, fromRel)), spec);
  for (const cand of [`${base}.ts`, `${base}.tsx`, join(base, 'index.ts'), join(base, 'index.tsx')]) {
    const rel = relative(SRC_ROOT, cand).split(sep).join('/');
    if (existsSync(cand)) return rel;
  }
  return null;
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

/** 族作用域内的导出值表：`文件 → 名 → 面值`，并顺着 `export { N } from` 转发链求解（三遍到不动）。 */
let exportCache: Map<string, Map<string, string>> | null = null;
function exportsInScope(): Map<string, Map<string, string>> {
  if (exportCache !== null) return exportCache;
  const prod = prodSources();
  const cache = new Map<string, Map<string, string>>();
  for (let pass = 0; pass < 3; pass++) {
    for (const [rel, src] of prod) {
      const own = cache.get(rel) ?? new Map<string, string>();
      const { consts } = intraFile(src);
      for (const l of codeLines(src)) {
        const d = l.match(TMPL_DECL) ?? l.match(LIT_DECL) ?? l.match(ALIAS_DECL);
        if (d && l.trimStart().startsWith('export') && BODIES.includes(consts[d[1]])) own.set(d[1], consts[d[1]]);
        const r = l.match(REEXPORT_RE);
        if (!r) continue;
        const target = moduleOf(rel, r[2]);
        if (!target) continue;
        for (const raw of r[1].split(',').map((x) => x.trim()).filter(Boolean)) {
          const parts = raw.split(/\s+as\s+/);
          const exported = (parts[0] || '').trim();
          const v = cache.get(target)?.get(exported);
          if (v) own.set((parts[1] || parts[0]).trim(), v);
        }
      }
      cache.set(rel, own);
    }
  }
  exportCache = cache;
  return cache;
}

/**
 * 该文件的常量表 = 文件内链不动点 ＋ 跨模块 import **按说明符跟值**（只认导出表，不认名字）。
 * 后者是本批改前踩过的坑：`pages/GuardrailsView.tsx` 的 `API_BASE` 来自
 * `./Guardrails/constants`（改前是影子声明、改后是重导出行），只按名字取 `/api` 的话
 * 它那 7 条腿在两棵树上一起隐形，"总数守恒"会变成解析器形状造出来的假象。
 * 刻意不给属主名开后门：值只能从真实导出表跟出来，名字接不上就退回隐形（防变红腿依赖这一点）。
 */
function constsFor(rel: string, src: string): Record<string, string> {
  const { consts, lines } = intraFile(src);
  const cache = exportsInScope();
  for (const l of lines) {
    const m = l.match(IMPORT_RE);
    if (!m) continue;
    const target = moduleOf(rel, m[2]);
    const own = target ? cache.get(target) : undefined;
    if (!own) continue;
    for (const raw of m[1].split(',').map((x) => x.trim()).filter(Boolean)) {
      const parts = raw.split(/\s+as\s+/);
      const imported = (parts[0] || '').trim();
      const local = (parts[1] || parts[0]).trim();
      if (own.has(imported)) consts[local] = own.get(imported)!;
    }
  }
  for (let pass = 0; pass < 8; pass++) {
    let changed = false;
    for (const l of lines) {
      const d = l.match(TMPL_DECL);
      if (d) {
        const deps = [...d[2].matchAll(/\$\{([A-Za-z0-9_]+)(?:\.[^}]*)?\}/g)].map((x) => x[1]);
        if (deps.every((x) => x === 'API_BASE' || x in consts)) { consts[d[1]] = evalTemplate(d[2], consts); changed = true; }
      }
      const a = l.match(ALIAS_DECL);
      if (a && a[2] in consts) { consts[a[1]] = consts[a[2]]; changed = true; }
    }
    if (!changed) break;
  }
  return consts;
}

/**
 * 该文件在本族作用域内的全部运行期面值 = 模板腿 + 裸字符串腿 + 裸名引用腿。
 * 裸名引用剔除四类：import 行、`export { } from` 转发行、该名的声明行、已含 `` `${NAME}` `` 的行、
 * 同值别名声明行（只是改名，不是一条请求）。出现次数按标识符边界数：
 * `NAME` 撞进 `NAMEX`（右侧）或 `cfg.NAME`（左侧）都不算腿——右侧边界含 `$`，
 * 因为 `${NAME}` 已在上一道排除里跳过，这里的 `$` 只防 `$$NAME` 这类畸形面。
 */
function facesOf(rel: string, srcOverride?: string): string[] {
  const src = srcOverride ?? read(rel);
  const consts = constsFor(rel, src);
  const out: string[] = [];
  const tracked = Object.entries(consts).filter(([, v]) => BODIES.includes(v)).map(([n]) => n);
  for (const line of codeLines(src)) {
    const trimmed = line.trimStart();
    for (const m of line.matchAll(/`((?:\\.|(?!`)[^\\])*)`/g)) out.push(evalTemplate(m[1], consts));
    for (const m of line.matchAll(/['"](\/api[^'"]*)['"]/g)) out.push(m[1]);
    if (trimmed.startsWith('import ') || trimmed.startsWith('export {')) continue;
    for (const name of tracked) {
      if (!new RegExp('(^|[^A-Za-z0-9_.$])' + name + '\\b').test(line)) continue;
      if (new RegExp('(?:const|let|var)\\s+' + name + '\\s*=').test(line)) continue;
      if (new RegExp('\\$\\{' + name + '\\}').test(line)) continue;
      const noTrailingComment = trimmed.replace(/\/\/[^'"]*$/, '').trim();
      if (new RegExp('^(?:export\\s+)?const\\s+[A-Za-z0-9_]+\\s*=\\s*' + name + '\\s*;?$').test(noTrailingComment)) continue;
      const parts = line.split(name);
      for (let i = 0; i < parts.length - 1; i++) {
        if (/[A-Za-z0-9_.$]/.test(parts[i].slice(-1))) continue;
        if (/[A-Za-z0-9_$]/.test(parts[i + 1].charAt(0))) continue;
        out.push(consts[name]);
      }
    }
  }
  return out.filter(inFamily);
}

/** 该文件的 API 令牌集：单源名 `API_BASE` ∪ 本文件的 `API_BASE as X` 导入别名（D-B5-40 名字轴补宽）。 */
function apiTokens(src: string): string[] {
  const t = new Set<string>(['API_BASE']);
  for (const l of codeLines(src)) for (const m of l.matchAll(/API_BASE\s+as\s+([A-Za-z0-9_]+)/g)) t.add(m[1]);
  return [...t];
}

/** `${任一令牌}/<族根后缀>` 且紧跟族边界（族内更细的腿与前缀撞名都不是族根构造点）。 */
function familyRootBuilders(src: string, body: string): number {
  const toks = apiTokens(src).map(esc).join('|');
  const re = new RegExp('`\\$\\{(?:' + toks + ')\\}' + esc(body.slice('/api'.length)) + '(?![A-Za-z0-9_/-])', 'g');
  let n = 0;
  for (const line of codeLines(src)) n += (line.match(re) ?? []).length;
  return n;
}

function privBaseDecls(src: string, suffix: string): string[] {
  const toks = apiTokens(src).map(esc).join('|');
  const re = new RegExp('(?:export\\s+)?(?:const|let|var)\\s+([A-Z0-9_]+)\\s*=\\s*`?\\$\\{(?:' + toks + ')\\}' + esc(suffix) + '(?![A-Za-z0-9_/-])');
  return codeLines(src).filter((l) => re.test(l)).map((l) => l.match(re)![1]);
}

/** 影子声明：非 httpClient 文件里 `const API_BASE = …`（改前的形状，本批起全 src 禁）。 */
function apiBaseShadows(src: string): string[] {
  return codeLines(src).filter((l) => /(?:const|let|var)\s+API_BASE\s*=/.test(l) && !l.trimStart().startsWith('import')).map((l) => l.trim());
}
function aliasImports(src: string): string[] {
  return codeLines(src).filter((l) => /API_BASE\s+as\s+[A-Za-z0-9_]+/.test(l)).map((l) => l.trim());
}

const BIN_KEYS = Object.keys(HEAD_FACES);
const FILES_IN_BINS = [...new Set(BIN_KEYS.map((k) => k.slice(k.indexOf('|') + 1)))].sort();

describe('b6AgentMeshGuardrailsSingleSource — 两族单源化取证 (#145/B6-11)', () => {
  it('前提：两枚属主常量运行期面值 = 改前各属主私设基址的族根面值（逐字）', () => {
    expect(API_BASE).toBe('/api');
    expect(AGENT_MESH_BASE).toBe('/api/v1/agent-mesh');
    expect(GUARDRAILS_POLICIES_BASE).toBe('/api/v1/guardrails/policies');
    // 解析器静态读出的属主值必须与真实模块的运行期值逐字相同，否则快照与快照外的等式都无意义
    for (const fam of OWNERS) {
      expect(constsFor(fam.owner, read(fam.owner))[fam.name], `属主 ${fam.name} 静态跟值与运行期脱钩`)
        .toBe(OWNER_BY_NAME.get(fam.name));
    }
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

  it('名字轴两条禁令：全 src 零 `API_BASE as X` 导入别名、零非单源文件的 `const API_BASE =` 影子声明', () => {
    const shadows: string[] = [];
    const aliases: string[] = [];
    for (const [rel, src] of prodSources()) {
      for (const l of apiBaseShadows(src)) shadows.push(`${rel}: ${l}`);
      for (const l of aliasImports(src)) aliases.push(`${rel}: ${l}`);
    }
    // 改前 `pages/Guardrails/constants.ts` 正是"别名导入 + 影子声明"成套出现的那一枚
    expect(aliases, `仍有 ${aliases.length} 处 API_BASE 导入别名 ⇒ 派生态判据重新变瞎`).toEqual([]);
    expect(shadows, `仍有 ${shadows.length} 处影子 API_BASE 声明`).toEqual([]);
    expect(read(CANONICAL), '单源自身的 API_BASE 声明必须在（本腿只禁别处影子，不禁单源）')
      .toContain('export const API_BASE');
  });

  it('转发行不是第二属主：constants.ts 零面零构造，但属主值确实经它喂给页面 7 条腿', () => {
    const constants = read('pages/Guardrails/constants.ts');
    const body = '/api/v1/guardrails/policies';
    expect(familyRootBuilders(constants, body)).toBe(0);
    expect(privBaseDecls(constants, body.slice('/api'.length))).toEqual([]);
    expect(constants).toMatch(/^export \{ GUARDRAILS_POLICIES_BASE \} from '\.\.\/\.\.\/services\/cognitiveEngineApi';$/m);
    expect(facesOf('pages/Guardrails/constants.ts'), '重导出行不含 URL 体 ⇒ 该箱须为空面（−1 的那条就在这里）').toEqual([]);
    // 反向证据：页面那 7 条腿的基址值只能经"按说明符跟值"取到 ⇒ 跟值判据不是摆设
    const view = facesOf('pages/GuardrailsView.tsx');
    expect(view.filter((v) => v === body).length, 'GuardrailsView 两条裸名引用腿应各计一枚族根面').toBe(2);
    expect(read('pages/GuardrailsView.tsx')).toMatch(/from '\.\/Guardrails\/constants'/);
  });

  it('面值逐字不变：八箱逐箱 = 改前 29 条 ± 三处有名字归属的差额，总数 29→28', () => {
    let headTotal = 0;
    let gotTotal = 0;
    let delta = 0;
    for (const adj of FACE_ADJUSTMENTS) delta += adj.kind === 'net-new-decl' ? 1 : -1;
    for (const key of BIN_KEYS) {
      const body = key.slice(0, key.indexOf('|'));
      const rel = key.slice(key.indexOf('|') + 1);
      const want = [...HEAD_FACES[key]];
      for (const adj of FACE_ADJUSTMENTS) {
        if (adj.file !== rel || adj.body !== body) continue;
        if (adj.kind === 'net-new-decl') want.push(adj.body);
        else want.splice(want.indexOf(adj.body), 1);
      }
      // 一箱只吃本族作用域内的腿（`pages/aiworkbench/api.ts` 同文件跨两族，8 箱 7 文件靠这条分开）
      const got = facesOf(rel).filter((v) => v === body || v.startsWith(`${body}/`)).sort();
      expect(got, `${rel} 在族 ${body} 的面值分箱漂移`).toEqual(want.sort());
      headTotal += HEAD_FACES[key].length;
      gotTotal += got.length;
    }
    expect(headTotal, '改前快照总面位不是实跑的 29 条 ⇒ 快照被剪过').toBe(29);
    expect(delta, '三处差额的净效应必须是 −1（两处族根声明上收、一处属主声明净新增）').toBe(-1);
    expect(gotTotal, '本批后面值总面位须等于 29 加净差额 −1').toBe(28);
    expect(FACE_ADJUSTMENTS.length).toBe(3);
    expect(BIN_KEYS.length).toBe(8);
    expect(FILES_IN_BINS.length).toBe(7);
  });

  it('可见性：单属主更细的腿刻意保持 `${API_BASE}` 行内形态（族索引面不因本批关掉）', () => {
    // 4 条 `/agent-mesh/agents` 腿属另一枚（更细的）族根，本批不动它们 ⇒ 两棵树同面
    expect(facesOf('pages/aiworkbench/api.ts')).toEqual([
      '/api/v1/agent-mesh/agents',
      '/api/v1/agent-mesh/agents',
      '/api/v1/agent-mesh/agents/${encodeURIComponent(id)}',
      '/api/v1/agent-mesh/agents/${encodeURIComponent(id)}',
      '/api/v1/guardrails/policies',
    ]);
    const client = read('api/agentClient.ts');
    expect(familyRootBuilders(client, '/api/v1/agent-mesh')).toBe(1);
    expect(codeLines(client).filter((l) => l.includes('${AGENT_MESH_BASE}')).length).toBe(6);
    const mesh = read('services/agentMeshApi.ts');
    expect(codeLines(mesh).filter((l) => l.includes('${AGENT_MESH_BASE}')).length).toBe(4);
    expect(privBaseDecls(mesh, '/v1/agent-mesh'), '消费侧又私设族根 ⇒ 属主 2 枚').toEqual([]);
    const cog = read('services/cognitiveEngineApi.ts');
    // DELETE 腿写成 `${API_BASE}/v1/guardrails/policies/${id}`：并进属主只是对棘轮隐形而不减债
    expect(codeLines(cog).filter((l) => l.includes('${API_BASE}/v1/guardrails/policies/${id}')).length).toBe(1);
    const cogInline = apiBaseFaces(cog, '/api/v1/guardrails/policies');
    expect(cogInline.filter((v) => v === '/api/v1/guardrails/policies').length, '行内族根构造必须只剩属主声明那一枚').toBe(1);
    expect(cogInline.filter((v) => v.startsWith('/api/v1/guardrails/policies/')).length, 'DELETE 腿须留在行内').toBe(1);
    const clientInline = apiBaseFaces(client, '/api/v1/agent-mesh');
    expect(clientInline.filter((v) => v === '/api/v1/agent-mesh').length).toBe(1);
    expect(clientInline.filter((v) => v.startsWith('/api/v1/agent-mesh/'))).toEqual([]);
    expect(constsFor('services/cognitiveEngineApi.ts', cog)['GUARDRAILS_POLICIES_BASE'])
      .toBe('/api/v1/guardrails/policies');
  });

  it('棘轮在册性：两族已从 urlFamilyDualFormRatchet 的 BASELINE 剪除，且两处定点与解析行集互相对得上', () => {
    const ratchet = read('__tests__/urlFamilyDualFormRatchet.test.ts');
    const rows = [...ratchet.matchAll(/^ {2}'([^']+)':\s*\[/gm)].map((m) => m[1]);
    expect(rows.length, 'BASELINE 行集解析为空 ⇒ contains 类探针在此恒真，本腿失去意义').toBeGreaterThan(0);
    const probe = (name: string) => ratchet.includes(`'${name}': [`);
    expect(probe(rows[0]), '同形探针连在册行都判不出 ⇒ 探针形状与 BASELINE 书写形状已脱钩').toBe(true);
    const dupPin = Number((ratchet.match(/expect\(dupBoth\.size\)\.toBe\((\d+)\)/) ?? [])[1]);
    const entriesPin = Number((ratchet.match(/expect\(entries\.length\)\.toBe\((\d+)\)/) ?? [])[1]);
    expect(Number.isInteger(dupPin) && Number.isInteger(entriesPin), '棘轮源码里两处规模定点形状变了').toBe(true);
    expect(rows.length).toBe(dupPin);
    expect(entriesPin).toBe(dupPin);
    for (const fam of OWNERS) expect(probe(fam.body.slice('/api'.length))).toBe(false);
    // 反向探针：本批刻意留在册的下一族必须仍看得见（防"探针把整段 BASELINE 读空"这种假绿）。
    // #145/B6-13 起改用 `/v1/dq`：原探针 `/v1/audit/stats` 已在本批从 BASELINE 剪除，留在原地会让本腿恒红。
    expect(probe('/v1/dq')).toBe(true);
  });

  it('判据能变红：别名族根、影子声明、跟值失灵、名字边界、面值漂移各造违例且被对应腿吃出（防恒绿）', () => {
    const mesh = '/api/v1/agent-mesh';
    const ALIAS = [
      "import { API_BASE as API_ROOT } from './services/httpClient';",
      'const A = `${API_ROOT}/v1/agent-mesh`;',
      'fetch(`${A}/missions`);',
    ].join('\n');
    expect(apiTokens(ALIAS)).toEqual(['API_BASE', 'API_ROOT']);
    expect(familyRootBuilders(ALIAS, mesh), '别名造出的族根构造点吃不出 ⇒ 名字轴补宽是空转').toBe(1);
    expect(privBaseDecls(ALIAS, mesh.slice('/api'.length)), '私设判据吃不出别名形状').toEqual(['A']);
    expect(facesOf('fixture.ts', ALIAS)).toEqual([mesh, `${mesh}/missions`]);
    // 影子声明：名字对不上单源，判据须按形状吃出（改前 constants.ts 的形状）
    const SHADOW = [
      "import { API_BASE as API_ROOT } from '../../services/httpClient';",
      'export const API_BASE = `${API_ROOT}/v1/guardrails/policies`;',
    ].join('\n');
    expect(apiBaseShadows(SHADOW)).toEqual([SHADOW.split('\n')[1]]);
    expect(apiBaseShadows("import { API_BASE } from './services/httpClient';")).toEqual([]);
    // 只有 `${VAR}/v1/…` 而无 VAR≡API_BASE 别名证据时不算族根（防把任意变量误吃）
    expect(familyRootBuilders('const D = `${OTHER}/v1/agent-mesh`;', mesh)).toBe(0);
    // 跟值失灵即整体隐形：把页面基址改指回单源，7 条腿一条不剩 ⇒ 本门的"跨模块跟值"确实在承重
    const rewired = read('pages/GuardrailsView.tsx').replace("from './Guardrails/constants'", "from '../services/httpClient'");
    expect(facesOf('pages/GuardrailsView.tsx', rewired)).toEqual([]);
    // 名字边界：长名撞前缀不是腿（#145/B6-11 姊妹门同批修的判据缺陷），真用法行仍是腿
    expect(facesOf('fixture.ts', [
      'const U = GUARDRAILS_POLICIES_BASEX;',
      'const V = cfg.GUARDRAILS_POLICIES_BASE;',
      "import { GUARDRAILS_POLICIES_BASE } from './services/cognitiveEngineApi';",
      'const W = apiCall(GUARDRAILS_POLICIES_BASE);',
    ].join('\n'))).toEqual(['/api/v1/guardrails/policies']);
    // 面值漂移：改一条腿必须被逐箱等式吃出
    expect(facesOf('pages/aiworkbench/AgentStudioView.tsx')).toEqual(['/api/v1/guardrails/policies/preview']);
    expect(facesOf('pages/aiworkbench/AgentStudioView.tsx', read('pages/aiworkbench/AgentStudioView.tsx')
      .replace('/v1/guardrails/policies/preview', '/v1/guardrails/policie/preview'))).toEqual([]);
    // 两级别名链 + CRLF 必须同时被解析器吃掉，否则 monitor 式隐形在两棵树上口径不一
    const CRLF_SRC = [
      'const WM_BASE = `${API_BASE}/v1/agent-mesh`;',
      'const CAUSAL_BASE = WM_BASE; // 单值收敛',
      'const u = `${CAUSAL_BASE}/missions`;',
    ].join('\r\n');
    expect(constsFor('fixture.ts', CRLF_SRC)['CAUSAL_BASE']).toBe(mesh);
    // 声明行的模板腿本身算一条族根面（与属主箱 +1 同一口径），第二级链喂出的腿是 `/missions`
    expect(facesOf('fixture.ts', CRLF_SRC)).toEqual([mesh, `${mesh}/missions`]);
    // 同值别名声明不是一条请求 ⇒ 裸名判据不许把它计成族根面
    expect(facesOf('fixture.ts', [
      'const AGENT_MESH_BASE = `${API_BASE}/v1/agent-mesh`;',
      'const MESH2 = AGENT_MESH_BASE; // 单值收敛',
    ].join('\n'))).toEqual([mesh]);
  });
});

/** 该文件里以 `${API_BASE}` 行内形态落在该族作用域内的面值清单（族根构造点之外的可见性口径）。 */
function apiBaseFaces(src: string, body: string): string[] {
  return codeLines(src)
    .map((l) => [...l.matchAll(/`((?:\\.|(?!`)[^\\])*)`/g)].map((m) => evalTemplate(m[1], { API_BASE })))
    .flat()
    .filter((v) => v === body || v.startsWith(`${body}/`));
}
