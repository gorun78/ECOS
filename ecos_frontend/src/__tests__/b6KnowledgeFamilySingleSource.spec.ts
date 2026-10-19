// @vitest-environment node
/**
 * B6 存量债首批（#145/B6-1）取证门 —— 重复 URL 族 `/v1/knowledge` 单源化。
 *
 * <p>差距台账 §13.2.4 的 C1 腿已于 #143/B5-26 归零，余项是**同一 URL 族的第二生产方**面：
 * `urlFamilyDualFormRatchet` 改前在册 30 族 / 69 属主（B6 存量债清单）。本批收口其中属主最多的一族
 * `/v1/knowledge`：改前 7 个生产文件各自写一份面值同为 `/api/v1/knowledge` 的基址常量
 * （`api/knowledgeClient.ts` 的 `KNOWLEDGE_SEARCH_BASE`、`pages/knowledge/services/` 六文件的
 * `KB_V1`／`GRAPH_BASE`／`KNOWLEDGE_BASE`，常量名三种、面值一份），现由 `api/knowledgeClient.ts`
 * **导出** `KNOWLEDGE_BASE` 单源持有，其余六文件改成 `import`。</p>
 *
 * <p>**本门存在的理由 = 棘轮门的补集**。单源化之后 `` `${KNOWLEDGE_BASE}/assets` `` 不含 `/v1/` 体，
 * `familiesOf` 的两种形态都吃不到 ⇒ 该族之下的子路径腿对族面口径**隐形**（族面 258 本批未动，
 * 因为刻意只换基址声明、保留各文件 `${API_BASE}/v1/knowledge/…` 直写子路径腿原样；若连子路径一起
 * 合流，族面会降，而那降是可见性变化、不是新增重复）。所以「72 条 KB 族腿的 URL 面值逐字不变」
 * 这件事棘轮门看不见，必须由本门的**常量解析器**（导入单源 + 本地基址常量 + 名别名 → 面值）守着：
 * 快照 72 条取自改前（HEAD 34d03f61，临时脚本 `git show HEAD:` 实跑读出、非手抄），
 * 断言的是「改后按当前源码解析出的多重集 = 改前」，故后续谁改了常量面值或漏改一条腿即红。</p>
 *
 * <p>三处形状特例须显式处置，通用「行内派生态还原」在这里吃不到：
 * ① `wiki.ts`/`overview.ts` 的 `const KB_V1 = KNOWLEDGE_BASE;` 是**名别名行**（不含 URL 体），
 *    解析器必须展开它，否则这两文件的 8/15 条腿读成未解析占位 ⇒ 假红；
 * ② `govern.ts` 的 `PROFILES_BASE`/`ASSUMPTIONS_BASE` 是**二级派生**（`` `${KNOWLEDGE_BASE}/profiles` ``
 *    赋给常量后另有引用），本门**刻意不做二级展开**——二级常量自己的声明行已经是一条族腿，
 *    再做展开会让改后比改前多算腿（改前那条腿的写法是 `${KB_V1}/profiles`，同一位置同一面值），
 *    守恒判据会假红；故此形状记为「快照口径 = 一层解析」，二级常量的下游引用不在本门面内；
 * ③ 改后 `govern.ts` 不再需要 `API_BASE`（该文件唯一用途就是被收口那枚声明），
 *    导入残留即未用绑定 ⇒ 本门第 3 条腿把「消费方不得残留私设基址」与「导入腿唯一」一起判。</p>
 *
 * <p>属主文件自己也有一条腿：`knowledgeClient.ts` 里的 `searchKnowledge` 是 `KNOWLEDGE_BASE` 在定义处的
 * 唯一自用点，改名时只换声明行不换引用行会让单源常量成**死导出**（tsc 会报未用名，但门禁面必须自己守住），
 * 故守恒腿把属主那条 `search?q=…` 也计入 72 条一起比。</p>
 *
 * <p>判据自身的形状缺陷由**反向腿**抓出（不是产品代码，记此防复发）：族内判定起初写成
 * `startsWith('/api/v1/knowledge')`，对 `` `${API_BASE}/v1/knowledge-base` `` 这类**同根异族**面值恒真
 * ⇒ 面值漂移腿会被误计入族内、DRIFT fixture 反而不红。改为边界判定
 * （`val === FAM || val.startsWith(FAM + '/')`）后该 fixture 才给出预期形状。这与 D-B5-40 同形：
 * 单源判据「看着在判、其实在放过」多半出在**匹配边界**而不是匹配内容。</p>
 */
import { describe, expect, it, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_BASE } from '../services/httpClient';
import { KNOWLEDGE_BASE } from '../api/knowledgeClient';

const SRC_ROOT = fileURLToPath(new URL('../', import.meta.url));
/** 族 `/v1/knowledge` 收口后的唯一属主。 */
const OWNER = 'api/knowledgeClient.ts';
/** 本批从属主 import 常量的六个消费方（rel → 导入腿全文、该文件 KB 族腿条数，均实跑读出）。 */
const CONSUMERS: ReadonlyArray<{ rel: string; importLine: string; sites: number }> = [
  { rel: 'pages/knowledge/services/assets.ts', importLine: "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';", sites: 7 },
  { rel: 'pages/knowledge/services/extract.ts', importLine: "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';", sites: 25 },
  { rel: 'pages/knowledge/services/govern.ts', importLine: "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';", sites: 8 },
  { rel: 'pages/knowledge/services/graph.ts', importLine: "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';", sites: 8 },
  { rel: 'pages/knowledge/services/overview.ts', importLine: "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';", sites: 15 },
  { rel: 'pages/knowledge/services/wiki.ts', importLine: "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';", sites: 8 },
];

/** 族面值 = 单源常量的运行期取值，快照里 72 条腿全部以此为前缀。 */
const FAM = '/api/v1/knowledge';
/** 族体恰为 `/v1/knowledge`（无尾串）的两种构造形态，与棘轮门 RAW_RE/DER_RE 同族口径。 */
const RAW_CONSTRUCT_RE = /["'`]\/api\/v1\/knowledge["'`]/;
const DER_CONSTRUCT_RE = /`\$\{API_BASE\}\/v1\/knowledge`/;
/** 派生腿：`` `${任意常量名}尾串` ``（尾串允许转义字符，不允许裸反引号）。 */
const DER_SITE_RE = /`\$\{([A-Za-z0-9_]+)\}((?:\\.|(?!`)[^\\])*)`/g;
const BASE_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*`\$\{API_BASE\}([^`]*)`;/;
const ALIAS_DECL_RE = /^\s*(?:export\s+)?const\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+);/;
/** 消费方导入单源常量的那一条（同时是解析器的种子）。 */
const IMPORT_BASE_RE = /import \{[^}]*\bKNOWLEDGE_BASE\b[^}]*\} from '[^']*api\/knowledgeClient'/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}
function codeLines(src: string): string[] {
  return src.split(/\r?\n/).filter((l) => !isComment(l));
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

/**
 * 一段源码文本里「常量名 → 面值」表：导入的单源常量做种子，本地 `${API_BASE}…` 声明入表，
 * `const A = B;` 名别名迭代展开。刻意不展开二级派生常量（见文件头特例 ②）。
 */
function baseMap(code: string): Map<string, string> {
  const map = new Map<string, string>();
  if (IMPORT_BASE_RE.test(code)) map.set('KNOWLEDGE_BASE', KNOWLEDGE_BASE);
  const lines = codeLines(code);
  for (const line of lines) {
    const m = line.match(BASE_DECL_RE);
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

/** 面值是否落在族内（**边界判定**，不是前缀判定：`/api/v1/knowledge-base/assets` 属另一族，不得混计）。 */
function inFamily(val: string): boolean {
  return val === FAM || val.startsWith(`${FAM}/`);
}

/** 一个文件里所有还原后落在 KB 族内的 URL 面值（排序后即该文件的面值多重集）。 */
function kbFaces(code: string): string[] {
  const bm = baseMap(code);
  const out: string[] = [];
  for (const line of codeLines(code)) {
    for (const m of line.matchAll(DER_SITE_RE)) {
      const val = bm.has(m[1]) ? bm.get(m[1])! + m[2] : `\${${m[1]}}${m[2]}`;
      if (inFamily(val)) out.push(val);
    }
  }
  return out.sort();
}

/** 一段源码文本里的族构造点条数（树级与 fixture 共用同一判据）。 */
function constructCount(code: string): number {
  let n = 0;
  for (const line of codeLines(code)) if (RAW_CONSTRUCT_RE.test(line) || DER_CONSTRUCT_RE.test(line)) n += 1;
  return n;
}

/** 全 src 生产面：族 `/v1/knowledge` 的构造方（rel → 构造点数），httpClient 本体不在族面口径内。 */
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
 * 改前（HEAD 34d03f61）七文件 KB 族腿面值快照，临时脚本按 `git show HEAD:` 实跑读出后原样收录。
 * 条数 1+7+25+8+8+15+8 = **72**。
 */
const HEAD_FACES: Readonly<Record<string, readonly string[]>> = {
  [OWNER]: ['/api/v1/knowledge/search?q=${encodeURIComponent(q)}'],
  'pages/knowledge/services/assets.ts': [
    '/api/v1/knowledge/assets',
    '/api/v1/knowledge/assets/${encodeURIComponent(assetId)}/transition',
    '/api/v1/knowledge/assets/${encodeURIComponent(assetId)}/transition',
    '/api/v1/knowledge/assets?${qs.toString()}',
    '/api/v1/knowledge/assets?${qs.toString()}',
    '/api/v1/knowledge/lifecycle/audit',
    '/api/v1/knowledge/lifecycle/audit',
  ],
  'pages/knowledge/services/extract.ts': [
    '/api/v1/knowledge/extract/candidates/${encodeURIComponent(fileId)}',
    '/api/v1/knowledge/extract/files',
    '/api/v1/knowledge/extract/ontology-tree',
    '/api/v1/knowledge/extract/ontology-tree',
    '/api/v1/knowledge/extract/scheduled',
    '/api/v1/knowledge/extract/scheduled',
    '/api/v1/knowledge/extract/scheduled',
    '/api/v1/knowledge/extract/scheduled',
    '/api/v1/knowledge/extract/scheduled/${id}',
    '/api/v1/knowledge/extract/scheduled/${id}',
    '/api/v1/knowledge/extract/scheduled/${id}',
    '/api/v1/knowledge/extract/status/${encodeURIComponent(taskId)}',
    '/api/v1/knowledge/extract/structured',
    '/api/v1/knowledge/extract/structured',
    '/api/v1/knowledge/extract/structured/export-log',
    '/api/v1/knowledge/extract/structured/jobs/${encodeURIComponent(jobId)}',
    '/api/v1/knowledge/extract/structured/jobs?limit=${clamped}',
    '/api/v1/knowledge/extract/structured/jobs?pageNum=${pageNum}&pageSize=${clampedSize}',
    '/api/v1/knowledge/extract/structured/jobs?pageNum=${pageNum}&pageSize=${clampedSize}',
    '/api/v1/knowledge/extract/structured/logs/${encodeURIComponent(jobId)}',
    '/api/v1/knowledge/extract/structured/logs/${encodeURIComponent(jobId)}',
    '/api/v1/knowledge/extract/structured/status/${encodeURIComponent(taskId)}',
    '/api/v1/knowledge/extract/tasks/${taskId}',
    '/api/v1/knowledge/extract/upload',
    '/api/v1/knowledge/extract/upload-enabled',
  ],
  'pages/knowledge/services/govern.ts': [
    '/api/v1/knowledge/assumptions',
    '/api/v1/knowledge/eval/run',
    '/api/v1/knowledge/eval/run',
    '/api/v1/knowledge/eval/seeds',
    '/api/v1/knowledge/eval/seeds',
    '/api/v1/knowledge/eval/seeds/upload',
    '/api/v1/knowledge/profiles',
    '/api/v1/knowledge/rag?query=${encodeURIComponent(query)}&stream=true',
  ],
  'pages/knowledge/services/graph.ts': [
    '/api/v1/knowledge/edges',
    '/api/v1/knowledge/graph${q}',
    '/api/v1/knowledge/neighbors/${encodeURIComponent(id)}?d=${depth}',
    '/api/v1/knowledge/nodes',
    '/api/v1/knowledge/nodes/${encodeURIComponent(id)}',
    '/api/v1/knowledge/path?s=${encodeURIComponent(source)}&t=${encodeURIComponent(target)}',
    '/api/v1/knowledge/search?q=${encodeURIComponent(query)}',
    '/api/v1/knowledge/source',
  ],
  'pages/knowledge/services/overview.ts': [
    '/api/v1/knowledge/engine-config',
    '/api/v1/knowledge/engine-config?scope=${encodeURIComponent(scope)}',
    '/api/v1/knowledge/graph/build',
    "/api/v1/knowledge/graph/build/preview?${params?.dryRun ? 'dryRun=true' : ''}",
    '/api/v1/knowledge/health',
    '/api/v1/knowledge/index-status',
    '/api/v1/knowledge/sync/jobs',
    '/api/v1/knowledge/sync/jobs',
    '/api/v1/knowledge/sync/jobs/${encodeURIComponent(jobId)}/logs',
    '/api/v1/knowledge/sync/jobs/${encodeURIComponent(jobId)}/preview',
    '/api/v1/knowledge/sync/jobs/${encodeURIComponent(jobId)}/rollback',
    '/api/v1/knowledge/sync/logs',
    '/api/v1/knowledge/sync/object/${encodeURIComponent(objectType)}',
    '/api/v1/knowledge/sync/status',
    '/api/v1/knowledge/sync/trigger',
  ],
  'pages/knowledge/services/wiki.ts': [
    '/api/v1/knowledge/compliance-rules/${id}',
    '/api/v1/knowledge/compliance-rules/${id}',
    '/api/v1/knowledge/compliance-rules/${ruleId}/versions',
    '/api/v1/knowledge/compliance-rules/${ruleId}/versions',
    '/api/v1/knowledge/compliance-rules?${query}',
    '/api/v1/knowledge/compliance-rules?${query}',
    '/api/v1/knowledge/settings',
    '/api/v1/knowledge/settings',
  ],
};

describe('b6KnowledgeFamilySingleSource — 重复族 /v1/knowledge 单源化取证 (#145/B6-1)', () => {
  it('前提：单源常量的运行期面值 = 改前 7 枚私设基址的面值（派生化即取值不变的根据）', () => {
    expect(API_BASE).toBe('/api');
    expect(KNOWLEDGE_BASE).toBe(FAM);
  });

  it('属主唯一：全 src 生产面构造族 /v1/knowledge 的文件只有 api/knowledgeClient.ts 一个、1 条，且是导出常量', () => {
    const c = constructors();
    expect([...c.keys()].sort(), `族 /v1/knowledge 仍有 ${c.size} 个构造方`).toEqual([OWNER]);
    expect(c.get(OWNER)).toBe(1);
    const decl = codeLines(read(OWNER)).filter((l) => l.trim() === 'export const KNOWLEDGE_BASE = `${API_BASE}/v1/knowledge`;');
    expect(decl.length).toBe(1);
    // 属主文件自己也是消费方：原语内那条自用引用必须指单源常量（漏改即死导出，见文件头「属主自己也有一条腿」注）
    expect(kbFaces(read(OWNER))).toEqual([...HEAD_FACES[OWNER]].sort());
  });

  it('消费方六文件：各恰 1 条 KNOWLEDGE_BASE 导入（深度 ../../../api/knowledgeClient）且零私设 KB 基址声明、零未用 API_BASE', () => {
    for (const { rel, importLine } of CONSUMERS) {
      const lines = codeLines(read(rel));
      const imports = lines.filter((l) => /^\s*import\s.*\bKNOWLEDGE_BASE\b.*$/.test(l)).map((l) => l.trim());
      expect(imports.length, `${rel} 应有且仅有 1 条 KNOWLEDGE_BASE 导入`).toBe(1);
      expect(imports[0], `${rel} 导入腿不是单源那一跳`).toBe(importLine);
      const privateBase = lines.filter((l) => {
        const m = l.match(BASE_DECL_RE);
        return m !== null && m[2] === '/v1/knowledge';
      });
      expect(privateBase, `${rel} 不得再私设 KB 族基址`).toEqual([]);
      // 判据只吃族根：更细的族（compliance-rules 等）本批不动，仍按在册基线保持私设形态
      const subFamilyBase = lines.filter((l) => {
        const m = l.match(BASE_DECL_RE);
        return m !== null && m[2].startsWith('/v1/knowledge/');
      });
      if (rel === 'pages/knowledge/services/wiki.ts') expect(subFamilyBase.length, 'wiki.ts 的子族基址 RULES_BASE 属另一族，本批不动').toBe(1);
      else expect(subFamilyBase, `${rel} 本批不涉子族基址`).toEqual([]);
      const usesApiBase = lines.filter((l) => /\bAPI_BASE\b/.test(l) && !l.trim().startsWith('import '));
      // govern.ts 的 API_BASE 唯一用途就是被收口那枚声明 ⇒ 收口后导入必须一起清
      if (rel === 'pages/knowledge/services/govern.ts') expect(usesApiBase, 'govern.ts 收口后不应残留 API_BASE 用途').toEqual([]);
      else expect(usesApiBase.length).toBeGreaterThan(0);
    }
  });

  it('面值守恒（棘轮门看不见的部分）：七文件解析出的 KB 族腿面值多重集逐字等于改前 72 条快照', () => {
    let total = 0;
    for (const rel of [OWNER, ...CONSUMERS.map((c) => c.rel)]) {
      const faces = kbFaces(read(rel));
      expect(faces, `${rel} KB 族腿面值与改前不一致`).toEqual([...HEAD_FACES[rel]].sort());
      total += faces.length;
    }
    expect(total).toBe(72);
    expect(CONSUMERS.reduce((n, c) => n + c.sites, 0) + HEAD_FACES[OWNER].length).toBe(72);
  });

  it('解析器形状：名别名展开到位、二级派生常量刻意不展开（否则改后比改前多算腿 ⇒ 守恒假红）', () => {
    const wiki = baseMap(read('pages/knowledge/services/wiki.ts'));
    expect(wiki.get('KB_V1')).toBe(FAM);
    const gov = baseMap(read('pages/knowledge/services/govern.ts'));
    expect(gov.get('KNOWLEDGE_BASE')).toBe(FAM);
    expect(gov.get('PROFILES_BASE')).toBeUndefined();
    expect(kbFaces(read('pages/knowledge/services/govern.ts')).filter((v) => v === '/api/v1/knowledge/profiles').length).toBe(1);
    expect(constructors().size).toBe(1);
  });

  it('棘轮在册性：/v1/knowledge 已随本批从 urlFamilyDualFormRatchet 的 BASELINE 剪除（不留陈旧豁免）', () => {
    const ratchet = read('__tests__/urlFamilyDualFormRatchet.test.ts');
    expect(ratchet).not.toContain("'/v1/knowledge': [");
    // 反向腿：判据不是在空串上跑——同文件里在册族必须还能被这条 contains 判出
    expect(ratchet).toContain("'/v1/knowledge/rag': [");
  });

  it('快照自检：72 条全部落在族内、有重复腿（非去重集合）且逐文件条数与本批口径相符（防快照清空成恒真）', () => {
    const all = Object.values(HEAD_FACES).flat();
    expect(all.length).toBe(72);
    for (const v of all) expect(v.startsWith(FAM)).toBe(true);
    expect(new Set(all).size).toBeLessThan(all.length);
    for (const { rel, sites } of CONSUMERS) expect(HEAD_FACES[rel].length).toBe(sites);
  });

  it('判据能变红：第二构造方/面值漂移/别名断链三类违例各被判出，合规源不命中（防恒绿）', () => {
    const DEFECT = [
      "import { API_BASE } from './httpClient';",
      'const OTHER_BASE = `${API_BASE}/v1/knowledge`;',
      'const A = `${OTHER_BASE}/assets`;',
      'const B = `${API_BASE}/v1/knowledge/x`;',
    ].join('\n');
    expect(constructCount(DEFECT)).toBe(1);
    // 一层解析口径：`${API_BASE}/v1/knowledge/x` 里 API_BASE 不是该文件内的常量 ⇒ 这条直写腿
    // 既不记为族构造点（constructCount 已证），也不进面值面（本门只管常量链）
    expect(kbFaces(DEFECT)).toEqual(['/api/v1/knowledge/assets']);
    const CLEAN = [
      "import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';",
      'const KB_V1 = KNOWLEDGE_BASE;',
      'const P = `${KB_V1}/profiles`;',
      'const R = `${KNOWLEDGE_BASE}/rag?query=${encodeURIComponent(query)}`;',
    ].join('\n');
    expect(constructCount(CLEAN)).toBe(0);
    expect(kbFaces(CLEAN)).toEqual(['/api/v1/knowledge/profiles', '/api/v1/knowledge/rag?query=${encodeURIComponent(query)}']);
    // 断链：别名右端指向不存在的常量 ⇒ 该腿解析失败、不计入面（守恒腿必红）
    const BROKEN = ['const KB_V1 = NOT_IMPORTED_BASE;', 'const P = `${KB_V1}/profiles`;'].join('\n');
    expect(kbFaces(BROKEN)).toEqual([]);
    // 面值漂移：单源常量被改成别的族 ⇒ 原面值腿不再落族内
    const DRIFT = "import { API_BASE } from './httpClient';\nexport const KNOWLEDGE_BASE = `${API_BASE}/v1/knowledge-base`;\nconst A = `${KNOWLEDGE_BASE}/assets`;";
    expect(kbFaces(DRIFT)).toEqual([]);
    expect(constructCount(DRIFT)).toBe(0);
  });
});
