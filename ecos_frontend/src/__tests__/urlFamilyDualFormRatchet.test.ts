/**
 * urlFamilyDualFormRatchet.test.ts — 「同一 URL 族的第二生产方」双形态棘轮门 (#143/B5-19)
 *
 * 收口缺陷 **D-B5-40**（差距台账 §13.2.4 C1-C6 注 ㉘）：仓内「出现第二处硬编码字面量即违例」
 * 型单源判据普遍只认引号形态（`["'`]/api…`），对 C1 派生态 `` `${API_BASE}/…` `` 不可见。
 * C1 基址单源每扩一批域，这类腿就静默变薄：建门批（#143/B5-19）实跑读出 680 个生产文件、
 * 258 个 URL 族、30 个族存在 >1 生产方，而**只认裸字面量的口径当时只能看到 9 个** ⇒ 21 个重复族
 * 对旧判据完全隐形。#143/B5-20 复跑 = 30/6/24（该批把知识服务域 32 处裸面搬成派生态，
 * dupBoth 不减而裸形态口径又瞎三族）。#143/B5-21 复跑 = 680 文件 / 258 族 / 裸面 87 / 派生面 247
 * （两面之和 **334 恒定**，纯转换只挪点位）/ dupBoth 30 / dupRawOnly **5** / blind **25**：
 * 该批把 data-workbench/api 域 26 处裸面派生化，`/v1/engine/data/pipeline/runs/{}` 一族只剩
 * `pipeline-editor/PipelineExecutionMonitor.tsx` 一个裸面属主 ⇒ 又掉出旧判据视野。
 * 三个 pin 随批更新，读的是当批实测而非建门批快照。逐门去改 12 个遗留门的正则形状是开放式工作量且会再次漂移，
 * 本批改为在**树级**用一条双形态门覆盖这些腿共同保护的性质：
 * 「同一 URL 族在生产代码里只允许一个生产方」。
 *
 * 两种形态都计入（缺一即为本缺陷复发）：
 *   A 裸字面量：`"/api/v1/x"` / `'/api/v1/x'` / `` `/api/v1/x` ``（插值 `${…}` 归一为 `{}`）；
 *   B 派生态：  `` `${API_BASE}/v1/x` ``（API_BASE 单源 = services/httpClient.ts，值 "/api"）。
 * 单源正主 `services/httpClient.ts` 本身豁免（它必须持有 "/api"），测试面豁免。
 *
 * 棘轮口径 = **族 → 属主清单**（两侧都只减不增）：
 *   新族出现在重复面 → 红；基线族新增属主 → 红；基线族/属主已不再重复 → 陈旧豁免亦红
 *   （与 privateTransportRatchet「陈旧豁免即红」同口径：基线预算不留给已收口的项）。
 *   本门**不宣称**证明单源成立——30 个基线族是存量债清单（收敛属 B5 后续批与 B6 存量债），
 *   本门钉死的是「债不再增 + 派生态不再隐身」。
 *
 * 三防恒绿：
 *   ① 派生分支真在扫（site 下限，防正则形状漂移后该分支恒 0 命中）；
 *   ② 双形态计数严格大于裸形态计数（结构性证明本门比旧判据强，不是等价改写）；
 *   ③ DEFECT/CLEAN 双向 fixture，且 DEFECT 面专门放一条「两文件都用派生态造同一族」
 *      ——旧判据对它零命中，本门必须判红。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const CANONICAL = 'services/httpClient.ts';

type Forms = { raw: number; der: number };
type Index = Map<string, Map<string, Forms>>;

/** 族键 = 去掉基址前缀并以 `{}` 归一插值后的路径串（含 query 骨架）。 */
const normalize = (body: string): string => {
  let s = body;
  for (let i = 0; i < 5 && /\$\{[^{}]*\}/.test(s); i += 1) s = s.replace(/\$\{[^{}]*\}/g, '{}');
  return s.replace(/\$\{[^{}]*\}/g, '{}');
};

const RAW_RE = /["'`]\/api((?:\\.|(?!["'`])[^\\])*)["'`]/g;
const DER_RE = /`\$\{API_BASE\}((?:\\.|(?!`)[^\\])*)`/g;

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

/** 纯函数：从一段源码文本读出 {family → 形态计数}，供树级扫描与 fixture 双向复用。 */
function familiesOf(code: string): Map<string, Forms> {
  const out = new Map<string, Forms>();
  const bump = (f: string, kind: keyof Forms) => {
    const cur = out.get(f) ?? { raw: 0, der: 0 };
    cur[kind] += 1;
    out.set(f, cur);
  };
  const lines = code.split('\n').filter((l) => !isComment(l)).join('\n');
  for (const m of lines.matchAll(RAW_RE)) {
    const b = normalize(m[1]);
    if (b.startsWith('/v1/')) bump(b, 'raw');
  }
  for (const m of lines.matchAll(DER_RE)) {
    const b = normalize(m[1]);
    if (b.startsWith('/v1/')) bump(b, 'der');
  }
  return out;
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

function scan(srcRoot: string): { idx: Index; rawSites: number; derSites: number; prodFiles: number } {
  const files: string[] = [];
  walk(srcRoot, files);
  const idx: Index = new Map();
  let rawSites = 0;
  let derSites = 0;
  let prodFiles = 0;
  for (const f of files) {
    const rel = relative(srcRoot, f).split(sep).join('/');
    if (rel === CANONICAL || isTestFile(rel)) continue;
    prodFiles += 1;
    for (const [fam, forms] of familiesOf(readFileSync(f, 'utf8'))) {
      if (!idx.has(fam)) idx.set(fam, new Map());
      const owners = idx.get(fam)!;
      const cur = owners.get(rel) ?? { raw: 0, der: 0 };
      cur.raw += forms.raw;
      cur.der += forms.der;
      owners.set(rel, cur);
      rawSites += forms.raw;
      derSites += forms.der;
    }
  }
  return { idx, rawSites, derSites, prodFiles };
}

function dupByBothForms(idx: Index): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const [fam, owners] of idx) if (owners.size > 1) out.set(fam, [...owners.keys()].sort());
  return out;
}

/** 旧判据口径（只认裸字面量）在同一索引上能看到的重复族 —— 用于量化盲区。 */
function dupByRawOnly(idx: Index): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const [fam, owners] of idx) {
    const rawOwners = [...owners.entries()].filter(([, f]) => f.raw > 0).map(([o]) => o).sort();
    if (rawOwners.length > 1) out.set(fam, rawOwners);
  }
  return out;
}

/**
 * 基线 = 2026-10-19 #143/B5-19 本门实跑读出值（先跑门读实测、再登记，非推算）。
 * 族 → 属主清单；只减不增，收口一族/一属主必须与本批同款改动同批剪除。
 */
const BASELINE: Readonly<Record<string, readonly string[]>> = Object.freeze({
  '/v1/agent-mesh': ['api/agentClient.ts', 'services/agentMeshApi.ts'],
  '/v1/aip/agents': ['pages/aiworkbench/api.ts', 'services/aiworkbenchApi.ts'],
  '/v1/aip/agents/{}': ['pages/aiworkbench/api.ts', 'services/aiworkbenchApi.ts'],
  '/v1/aip/audit-logs': ['pages/aiworkbench/api.ts', 'services/aiworkbenchApi.ts'],
  '/v1/audit/stats': ['api/securityClient.ts', 'pages/SecurityAudit.tsx'],
  '/v1/cognitive/beliefs?domain={}': ['pages/scenario/OverrideTab.tsx', 'pages/scenario/ReplayTab.tsx'],
  '/v1/datanet/metadata': ['api/dataClient.ts', 'services/dataCatalogClient.ts'],
  '/v1/datanet/metadata/preview/{}?limit={}': ['pages/data-workbench/api/metadata.ts', 'services/datanetMetadataApi.ts'],
  '/v1/dq': ['api/dataClient.ts', 'pages/data-quality/api/_base.ts'],
  '/v1/ecos/ontologies': ['components/ontology-workbench/OntologyDomainPanel.tsx', 'pages/business-workbench/BusinessWorkbenchLayout.tsx'],
  '/v1/ecos/versions': ['pages/knowledge/tabs/DataWorkbenchImportTab.tsx', 'services/ontology/ontologyVersionApi.ts'],
  '/v1/ecos/world-model-graph': ['api/monitorClient.ts', 'services/worldModelGraphApi.ts'],
  '/v1/engine/cognitive': ['pages/MonitoringCenter.tsx', 'services/cognitiveClient.ts'],
  '/v1/engine/data/pipeline/runs/{}': ['pages/data-workbench/api/pipeline.ts', 'pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx'],
  '/v1/engine/data/settings': ['pages/EngineMonitor.tsx', 'pages/data-workbench/DataEngineConfigPanel.tsx'],
  '/v1/engine/ontology': ['pages/MonitoringCenter.tsx', 'services/ontology/ontologyEngineApi.ts'],
  '/v1/guardrails/policies': ['pages/aiworkbench/api.ts', 'services/cognitiveEngineApi.ts'],
  '/v1/knowledge': ['api/knowledgeClient.ts', 'pages/knowledge/services/assets.ts', 'pages/knowledge/services/extract.ts', 'pages/knowledge/services/govern.ts', 'pages/knowledge/services/graph.ts', 'pages/knowledge/services/overview.ts', 'pages/knowledge/services/wiki.ts'],
  '/v1/knowledge/compliance-rules': ['pages/knowledge/pages/components/govern/RuleRepoTable.tsx', 'pages/knowledge/services/wiki.ts'],
  '/v1/knowledge/query': ['pages/knowledge/services/wiki.ts', 'services/knowledgeQueryApi.ts'],
  '/v1/knowledge/rag': ['pages/knowledge/services/wiki.ts', 'services/knowledgeQueryApi.ts'],
  '/v1/ontology/data': ['services/ontology/ontologyDataApi.ts', 'services/ontologyWorkbenchApi.ts'],
  '/v1/ontology/export': ['pages/knowledge/services/overview.ts', 'services/ontology/ontologyExportApi.ts'],
  '/v1/ontology/glossary': ['pages/knowledge/services/wiki.ts', 'services/glossary.ts'],
  '/v1/ontology/mappings': ['pages/knowledge/pages/ExtractionPage.tsx', 'pages/knowledge/services/overview.ts', 'services/ontology/ontologyMappingApi.ts'],
  '/v1/ontology/proposals': ['pages/ScenarioManagementView.tsx', 'pages/aiworkbench/api.ts', 'services/ontology/ontologyProposalApi.ts'],
  '/v1/pipeline/definitions': ['api/dataClient.ts', 'pages/aiworkbench/api.ts', 'pages/data-workbench/api/_base.ts'],
  '/v1/system/organizations/tree': ['api/iamClient.ts', 'pages/WorldModelViewer.tsx'],
  '/v1/workspace/scenarios': ['pages/ScenarioManagementView.tsx', 'pages/workbench/NewScenarioModal.tsx', 'pages/workbench/index.tsx'],
  '/v1/workspace/scenarios/{}': ['pages/ScenarioManagementView.tsx', 'pages/workbench/[scenarioId]/index.tsx'],
});

describe('urlFamilyDualFormRatchet — URL 族第二生产方双形态棘轮门 (#143/B5-19 / D-B5-40)', () => {
  const snapshot = scan(SRC_ROOT);

  it('noNewDuplicateUrlOwner: 双形态扫描的重复族必须逐族逐属主等于基线（新增即红、陈旧亦红）', () => {
    const dup = dupByBothForms(snapshot.idx);
    const failures: string[] = [];

    for (const [fam, owners] of [...dup.entries()].sort()) {
      const allowed = BASELINE[fam];
      if (!allowed) {
        failures.push(`[基线外新重复族] ${fam} 由 ${owners.length} 个生产方各自构造：${owners.join(' + ')} — 单源判据对派生态不可见即为此族而设 (D-B5-40)`);
        continue;
      }
      const extra = owners.filter((o) => !allowed.includes(o));
      const gone = allowed.filter((o) => !owners.includes(o));
      if (extra.length > 0) failures.push(`[基线族新增属主] ${fam} 多出 ${extra.join(' + ')} — 只减不增`);
      if (gone.length > 0) failures.push(`[陈旧豁免] ${fam} 属主 ${gone.join(' + ')} 已不再构造该族，必须同批从 BASELINE 剪除`);
    }

    for (const fam of Object.keys(BASELINE)) if (!dup.has(fam)) failures.push(`[陈旧豁免] 基线族 ${fam} 现已无重复生产方，必须同批从 BASELINE 剪除`);

    expect(
      failures,
      failures.length ? `双形态重复族棘轮变红（实测 ${dup.size} 族重复 / 基线 ${Object.keys(BASELINE).length} 族）：\n${failures.join('\n')}` : undefined,
    ).toEqual([]);
  }, 30_000);

  it('派生分支真在扫 + 双形态口径严格强于裸字面量口径（本门不是旧判据的等价改写）', () => {
    expect(snapshot.derSites, '派生分支必须在扫，否则本门退化为裸形态旧判据').toBeGreaterThan(0);
    expect(snapshot.rawSites, '裸形态分支必须在扫').toBeGreaterThan(0);
    // #143/B5-21 起把两条 `>=100` 绝对地板换掉：C1 每收口一批就把点位从裸形态面搬到派生面，
    // 地板迟早被真实进度撞红（本批 rawSites 113→87 即撞）。换成**两面之和恒等**这条更强的
    // 判据——纯 raw→derived 转换只在两面之间移动点位，总数不变；扫描根一旦漂移（漏目录、
    // 判据改动）总数必变，比两条独立地板更能证「没在扫空」。334 = 本批实跑读出，与 dup* 同批重钉。
    expect(snapshot.rawSites + snapshot.derSites).toBe(334);
    const dupBoth = dupByBothForms(snapshot.idx);
    const dupRawOnly = dupByRawOnly(snapshot.idx);
    expect(dupBoth.size).toBeGreaterThan(dupRawOnly.size);
    // 三个数 = 本门实跑读出值，与 BASELINE 同批改动（重复面一变，这里必红，防「基线改了、盲区量化注记没跟上」）
    // #143/B5-20：知识服务域 32 处裸面派生化，dupBoth 不变（30），裸形态口径丢三族
    // （/v1/knowledge 五属主全裸面归零、/v1/ontology/mappings 与 /v1/knowledge/compliance-rules
    // 各降到单属主）⇒ 盲区 21 → 24。
    // #143/B5-21：data-workbench/api 域 26 处裸面派生化，dupBoth 仍 30，/v1/engine/data/pipeline/runs/{}
    // 只剩 1 个裸面属主 ⇒ dupRawOnly 6 → 5、盲区 24 → 25。这正是 D-B5-40 的预期行为：
    // C1 每收口一批，只认裸形态的旧判据就更瞎，不是本门放松。
    expect(dupBoth.size).toBe(30);
    expect(dupRawOnly.size).toBe(5);
    const blind = [...dupBoth.keys()].filter((f) => !dupRawOnly.has(f)).sort();
    expect(blind.length).toBe(25);
    expect(blind.length, '盲区族清单必须非空且全部在册（否则基线口径与本门不符）').toBeGreaterThan(0);
    for (const f of blind) expect(Object.keys(BASELINE)).toContain(f);
  }, 30_000);

  it('判据能变红：DEFECT fixture 里「两文件都用派生态造同一族」必须被本门判红、被裸形态口径放过', () => {
    const DERIVED_A = ['import { API_BASE } from "./httpClient";', 'const A = `${API_BASE}/v1/fixture/dup${id}`;', 'fetch(A);'].join('\n');
    const DERIVED_B = ['import { API_BASE } from "./httpClient";', 'const B = `${API_BASE}/v1/fixture/dup${id}`;', 'fetch(B);'].join('\n');
    const RAW_CLEAN = ['import { API_BASE } from "./httpClient";', 'const C = `${API_BASE}/v1/fixture/only-one/${id}`;', 'fetch(C);'].join('\n');

    expect([...familiesOf(DERIVED_A).keys()]).toEqual(['/v1/fixture/dup{}']);
    expect([...familiesOf(DERIVED_B).keys()]).toEqual(['/v1/fixture/dup{}']);
    expect([...familiesOf(RAW_CLEAN).keys()]).toEqual(['/v1/fixture/only-one/{}']);

    const idx: Index = new Map();
    for (const [rel, code] of [['fixture/A.ts', DERIVED_A], ['fixture/B.ts', DERIVED_B], ['fixture/C.ts', RAW_CLEAN]] as const) {
      for (const [fam, forms] of familiesOf(code)) {
        if (!idx.has(fam)) idx.set(fam, new Map());
        idx.get(fam)!.set(rel, forms);
      }
    }
    // 双形态口径：dup 族两属主 → 判红
    expect(dupByBothForms(idx).get('/v1/fixture/dup{}')).toEqual(['fixture/A.ts', 'fixture/B.ts']);
    // 裸字面量口径（旧判据形状）：该族零裸面 → 看不见（= D-B5-40 复现）
    expect(dupByRawOnly(idx).has('/v1/fixture/dup{}')).toBe(false);
    // 注释行不计（与 api-base / privateTransportRatchet 同口径）
    expect([...familiesOf("// const X = `${API_BASE}/v1/fixture/in-comment`;\n/* `${API_BASE}/v1/fixture/block` */")].length).toBe(0);
  }, 30_000);

  it('基线自身可校验：族面/属主面形态合规且与实跑读出同数', () => {
    const entries = Object.entries(BASELINE);
    expect(entries.length).toBe(30);
    expect(entries.reduce((n, [, o]) => n + o.length, 0)).toBe(69);
    for (const [fam, owners] of entries) {
      expect(fam.startsWith('/v1/')).toBe(true);
      expect(fam.includes('\\')).toBe(false);
      expect(owners.length).toBeGreaterThanOrEqual(2);
      expect([...owners].sort()).toEqual(owners);
      expect(new Set(owners).size).toBe(owners.length);
      for (const o of owners) {
        expect(o.startsWith('./')).toBe(false);
        expect(o.includes('\\')).toBe(false);
        expect(isTestFile(o)).toBe(false);
        expect(o).not.toBe(CANONICAL);
      }
    }
  }, 30_000);

  it('扫描面合规：生产文件数与豁免面与本门口径一致（防扫描根漂移成零命中）', () => {
    expect(snapshot.prodFiles).toBeGreaterThanOrEqual(600);
    expect(snapshot.idx.size).toBeGreaterThanOrEqual(200);
    for (const owners of snapshot.idx.values()) expect(owners.has(CANONICAL)).toBe(false);
    // 量级自播报：台账里的四个数（生产文件面/族面/裸形态命中行/派生态命中行）一律取自此行输出，
    // 防止「写进文档的数字其实是上一代判据的估算」——本台账反复要防的正是凭记忆断言。
    console.info(
      `[urlFamilyDualFormRatchet 实跑量级] prodFiles=${snapshot.prodFiles} families=${snapshot.idx.size} rawSites=${snapshot.rawSites} derSites=${snapshot.derSites}`,
    );
  }, 30_000);
});
