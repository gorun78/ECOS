/**
 * api-base-single-source.test.ts — 冻结门禁名 `noHardcodedApiPrefixOutsideHttpClient`
 * （详设-08 §九 W206/C188，前端开发规范 §十：API 收口，基址单源）
 *
 * 口径：`services/httpClient.ts` 是唯一有权定义/硬编码 "/api" 前缀的模块
 * （`API_BASE = "/api"`，W206 已实证单源正确）。其余任何 .ts/.tsx 代码行
 * （排除注释/测试/.d.ts）不得再硬编码 "/api..." 前缀字符串——包括：
 *   a) 自建 `API_BASE = '/api/...'` 常量；b) 裸 `fetch('/api/...')` 绕过 httpClient；
 *   c) apiFetchData / apiFetch 等封装传字面 "/api/..." 绝对路径；
 *   d) SSE/流式各自拼 URL（§十）。
 *
 * 基线白名单（文件级登记）：清单内容为准，本注释不写死计数（改前写「实测 95 个」而
 * 实数组 101 枚，注释与代码漂移本身就是一种假绿——以 `new Set([...]).size` 实跑为准）。
 * 属主 W206/B 批（任务 #119）按域渐进收敛为经 httpClient 的相对路径或
 * `import { API_BASE } from ".../httpClient"`；参照 R-55②/H6-T5 口径
 * **清单只减不增**——新增文件即红，禁止扩充本清单来豁免新违规。
 * 2026-10-10 收口减 3：ChatPanel.tsx（SSE sseUrl）、knowledge/services/govern.ts（KB_V1）
 * 已改由 API_BASE 派生，WorldModelViewer.tsx 两处裸 fetch + `j.data.data` 手拆信封
 * 改走 apiFetchData（§5.3 唯一解包点）。aiworkbench/api.ts 流式点同批改用
 * API_BASE，但因存量 24 处其他字面暂留基线。
 * 2026-10-18 #143/B5-6 收口减 4：services/dict.ts（私设 API_BASE + 裸 fetch + 不带
 * Authorization）、services/glossary.ts（私设基址 + 自留 pickTraceId/拆信封）、
 * api/dictConfigClient.ts、pages/Guardrails/constants.ts 四处基址改由 API_BASE 派生；
 * 同批消 pages/workbench/NewScenarioModal.tsx 一处 HEAD 既有红（该文件从未入基线）。
 * 2026-10-18 #143/B5-8 收口减 1：pages/data-workbench/api/_base.ts 三枚 /api 常量改由
 * API_BASE 派生（URL 逐字不变），同批该文件的 DW 共享 HTTP 四动词合流 apiFetchData 单源。
 * 2026-10-19 #143/B5-11b 收口减 1：services/worldModelGraphApi.ts 私设 fetch 两通道腿
 * 合流 apiFetchData，基址由字面量改 API_BASE 派生（五腿 URL 逐字不变）。
 * 2026-10-19 #143/B5-11a 收口减 1：services/alertsApi.ts 六枚私设 fetch 腿整体重写为
 * apiFetchEnvelope 单源通道，基址改由 API_BASE 派生（URL 逐字不变）。
 * 2026-10-19 #143/B5-11c 收口减 1：services/agentMeshApi.ts 两通道腿合流 apiFetchData，
 * 基址由字面量改 API_BASE 派生（六腿 URL 逐字不变），并补上改前缺失的 Bearer（D-B5-35）。
 * 2026-10-19 #143/B5-11e 收口减 3：services/copilotApi.ts 两腿、services/datanetMetadataApi.ts
 * 两腿改走匿名具名通道 rawAnonUrlFetch 且基址由 API_BASE 派生；services/knowledgeQueryApi.ts
 * 五腿（#143/B5-11d 已合流传输，本批补基址）的 "/api/…" 字面量全部改 API_BASE 派生。
 * 三文件 URL 逐字不变；`pages/knowledge/services/extract.ts` 经实测仍有 8 行 `/api` 字面量
 * （`KB_V1`、lineage、integration 三族）未派生 ⇒ **保留在基线内**（不冒充已收口）。
 * 2026-10-19 #143/B5-11f 收口减 2：`services/systemDictApi.ts` 一腿与
 * `services/ontology/ontologyExportApi.ts` 的 `EXPORT_BASE` 改由 API_BASE 派生
 * （取值逐字不变），两文件同批从 privateTransportRatchet 基线一并剪除。
 * 2026-10-19 #143/B5-11g 收口减 2：`pages/EngineMonitor.tsx`（health/config/status 三枚
 * 调用点 + `ENGINE_META.data.configPath` 共 4 处）与 `pages/MonitoringCenter.tsx`
 * （`ENGINES` 六枚 `apiBase`）的 `"/api/v1/engine/…"` 字面量全改 API_BASE 派生，
 * 取值逐字不变；两文件同批从 privateTransportRatchet 基线一并剪除
 * （`pages/Guardrails/helpers.ts` 只收传输，本就无基址字面量，不在本清单）。
 * 2026-10-19 #143/B5-11h 收口减 3：`pages/knowledge/tabs/SyncTab.tsx`
 * （`'/api/integration/metadata'` 一枚改 `${API_BASE}/integration/metadata`，取值逐字不变）
 * 与 `pages/data-workbench/tabs/connections/InlineSqlConsole.tsx`
 * （`'/api/v1/engine/data/query/execute'` 一枚同改）随传输合流一并派生；
 * `pages/aiworkbench/AgentStudioView.tsx` 实测改前即 0 命中（早批已用 API_BASE），
 * 属本门检不出的**陈旧豁免**（本门只扫基线外文件，不像棘轮门会把 0 命中项判红）⇒ 同批剪除。
 * 基线内此类陈旧项另有 11 枚（含 8 枚指向已不存在的路径），已单独立项 #143/B5-12 处置，
 * 不在本批夹带。
 * 2026-10-19 #143/B5-12 本门补「陈旧即红」腿：此前只判「基线外新增」不判「基线内陈旧」，
 * 空占的豁免位永远检不出（与 privateTransportRatchet 的 `[陈旧豁免]` 判据口径不对称）。
 * 补腿后同批实跑读出 **11 枚陈旧** ⇒ 全部剪除（8 枚路径已不存在：
 * `components/ontology-workbench/OntologyWorkbenchSidebar.tsx`、`pages/OntologyObjectBrowser.tsx`、
 * `pages/SqlQueryConsole.tsx`、`pages/SystemDictionary.tsx`、`pages/sql-query-console/{HistoryPanel,
 * SchemaTree,TemplatePanel,api}`；3 枚在位但代码行 0 命中 = 早批已收口：
 * `components/data-workbench/GitPanel.tsx`、`pages/aiworkbench/ChatbotStudioView.tsx`、`services/auth.ts`）。
 * 另配一条合成数据反向自检（缺失/零命中/有命中三例），证明该判据本身能变红。
 * 2026-10-19 #143/B5-16 收口减 7：`src/api/` 域七文件（agentClient / dataClient / iamClient /
 * knowledgeClient / monitorClient / ontologyClient / securityClient）代码行 `"/api…"` 字面量
 * **83 处**全部改由 `API_BASE` 派生（取值逐字不变：`API_BASE === "/api"`，拼接结果与改前同串），
 * 只动代码行、注释里的端点说明不动（判据本就不计注释行，改注释属无谓抖动）。
 * 72 → 65 枚系本批实跑读出后剪除，非推算。
 * 2026-10-19 #143/B5-17 收口减 8：`src/services/ontology/` 域基线在册八文件（data/domain/engine/
 * lineage/mapping/model/proposal/version 各 `*Api.ts`）代码行私设基址 **17 处**改由 `API_BASE`
 * 派生（13 枚双引号常量 + 4 枚反引号内联，判据与 `HARDCODED_RE` 同口径含反引号——只用
 * `["']` 去扫会把反引号那 4 处漏成"已收口"，本批实测抓出）。`API_BASE === "/api"` ⇒ 拼接面值
 * 逐字不变；同批该目录（含 #143/B5-11f 已派生的 `ontologyExportApi.ts`）整域残留 0 处。
 * 65 → 57 枚同样系实跑读出后剪除。
 * 2026-10-19 #143/B5-18 收口减 10：`src/services/` 根十个基线在册文件（aiworkbenchApi /
 * cognitiveClient / cognitiveEngineApi / dataCatalogClient / gitService / knowledgeNavApi /
 * ontologyClient / ontologyWorkbenchApi / platformApi / taskCenter）代码行私设基址 **48 处**
 * 改由 `API_BASE` 派生，形态拆分为双引号 16 + 单引号 7 + **反引号 25**——本批反引号占比过半，
 * 窄口径 `["']` 只能看到 23 处，是 #143/B5-17 已照登漏扫形状的放大版，故域级取证门
 * `services/__tests__/servicesRootBaseSingleSource.spec.ts` 把三形态拆分写成快照自校验断言。
 * `API_BASE === "/api"` ⇒ 48 条面值逐字不变（多重集比对，非眼看）；57 → 47 枚系实跑读出后剪除。
 * 2026-10-19 #143/B5-20 收口减 5：`src/pages/knowledge/services/` 五个基线在册文件
 * （assets / extract / graph / overview / wiki）代码行私设基址 **32 处**改由 `API_BASE` 派生，
 * 形态拆分 = 单引号 25 + **反引号 7** + 双引号 0（该域惯用单引号，与 B5-18 根域的反引号过半
 * 相反 ⇒ 每批必须各自数形态，不可沿用上一批的形状结论）。域级取证门
 * `pages/knowledge/services/__tests__/knowledgeServicesBaseSingleSource.spec.ts` 按
 * `API_BASE` 实际值还原后与 HEAD 快照做多重集比对（32 条逐字不变）；整域九个 `.ts`
 * 代码行零私设基址（`govern.ts` 系 2026-10-10 W206/C188 已派生，本批只复核不动）。
 * 47 → 42 枚系实跑读出后剪除。
 * 2026-10-19 #143/B5-21 收口减 5：`src/pages/data-workbench/api/` 五个基线在册文件
 * （datasource / lineage / metadata / pipeline / unstructured）代码行私设基址 **26 处**
 * （逐文件 1 + 1 + 11 + 4 + 9）改由 `API_BASE` 派生，形态拆分 = 单引号 11 + **反引号 15**
 * + 双引号 0。域级取证门 `pages/data-workbench/api/__tests__/dataWorkbenchApiBaseSingleSource.spec.ts`
 * 的比对面为 **27** 条＝26 条裸形态 + 1 条改前既有派生态（`unstructured.ts` 上传腿，#143/B5-14），
 * 漏计既有派生态会让「多重集相等」假绿；`_base.ts` 三枚常量系 #143/B5-8 即已派生，只由
 * 「整域八个 `.ts` 零私设基址」腿覆盖、不计入本批派生点数。42 → 37 枚系实跑读出后剪除。
 * 2026-10-19 #143/B5-22 收口减 9：`src/pages/data-workbench/` **页面侧**九个基线在册文件
 * （pipeline-editor 七个 + pipelineDebugApi + DataEngineConfigPanel）代码行私设基址 **16 处**
 * （逐文件 1 + 3 + 1 + 1 + 1 + 1 + 2 + 2 + 4）改由 `API_BASE` 派生，形态拆分 = 单引号 10 +
 * **反引号 6** + 双引号 0。域级取证门 `pages/data-workbench/__tests__/dataWorkbenchPageBaseSingleSource.spec.ts`
 * 递归扫整域 78 个生产文件（排除 `__tests__` 与 `*.test.*`/`*.spec.*`）判零私设基址——至此
 * 数据工作台域（api/ 子域系 #143/B5-21）整体收口；该门比对面 16 条＝全部裸形态，
 * 因这九文件改前零派生态（与 B5-21 有一条既有上传腿须计入不同）。37 → 28 枚系实跑读出后剪除。
 * 2026-10-19 #143/B5-23 收口减 8：**场景域页面**八个基线在册文件代码行私设基址 **17 处**
 * （`pages/scenario/` 五个：CognitionPanel 3 + OverrideTab 2 + ReplayTab 2 + CounterfactualTab 1 +
 * MentalReviewTab 1；`pages/workbench/` 两个：index 1 + [scenarioId]/index 1；根侧
 * ScenarioManagementView 6）改由 `API_BASE` 派生，形态拆分 = 单引号 6 + **反引号 11** + 双引号 0。
 * 域级取证门 `pages/__tests__/scenarioWorkbenchBaseSingleSource.spec.ts` 递归扫
 * `pages/scenario` 15 个与 `pages/workbench` 20 个生产文件判零私设基址（后者 NewScenarioModal
 * 一枚派生态系 #143/B5-6 既有（`git log -S API_BASE` 实跑读出），非本批）。28 → 20 枚系实跑读出后剪除。
 * 2026-10-19 #143/B5-24 收口减 4：**工作台散点**四个基线在册文件代码行私设基址 **11 处**
 * （`pages/business-workbench/BusinessWorkbenchLayout.tsx` 4 + `components/ontology-workbench/
 * OntologyDomainPanel.tsx` 2 + `pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts` 2 +
 * `pages/TelemetryViewer.tsx` 3）改由 `API_BASE` 派生，形态拆分 = 单引号 6 + 反引号 3 + **双引号 2**
 * （本波首次出现双引号腿：`apiFetchData<HealthData>("/api/telemetry/health")` 两条，只吃单引号的
 * 判据会放过）。另注：`TelemetryViewer` 三条腿是**无版本段** `/api/telemetry/…`（不带 `v1/`），
 * 双形态棘轮门按 `startsWith('/v1/')` 收族故不在族面内，但 C1 判据对它们可见⇒仍须收口。
 * 批级门 `__tests__/workbenchScatterBaseSingleSource.spec.ts` 另判 `pages/business-workbench` 38 个与
 * `components/ontology-workbench` 14 个生产文件整域零私设基址；`pages/aiworkbench` 尚有 `api.ts`
 * 27 处⇒该域本批不宣称闭合。20 → 16 枚系实跑读出后剪除。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 单源正主：允许硬编码前缀。 */
const CANONICAL = 'services/httpClient.ts';

/** 基线（存量登记，2026-10 拆分 ontologyApi 后全量实测；只减不增）。 */
const BASELINE = new Set([
  'hooks/useBindingCatalog.ts',
  'pages/ContractManager.tsx',
  'pages/DataAssetsDashboard.tsx',
  'pages/DataLineage.tsx',
  'pages/ProjectTracker.tsx',
  'pages/TenantManager.tsx',
  'pages/TokenDashboard.tsx',
  'pages/aiworkbench/api.ts',
  'pages/data-quality/api/_base.ts',
  'pages/knowledge/pages/ExtractionPage.tsx',
  'pages/knowledge/pages/components/govern/RuleRepoTable.tsx',
  'pages/knowledge/tabs/DataWorkbenchImportTab.tsx',
  'pages/knowledge/tabs/DocumentUploadTab.tsx',
  'pages/knowledge/tabs/GraphBuilderTab.tsx',
  'pages/knowledge/tabs/KnowledgeRuleRepositoryTab.tsx',
  'providers/graphqlClient.ts',
]);

/** 代码行硬编码 "/api 前缀（引号或反引号后紧跟 /api）。 */
const HARDCODED_RE = /["'`]\/api\b/;

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

/**
 * 陈旧豁免判据（纯函数，供本门与「能变红」自检共用）：
 * 基线只登记「真有 `/api` 字面量待收口」的文件。路径不存在、或代码行零命中，
 * 都说明豁免位空占——本门若只判「基线外新增」，这类空位永远检不出（与 C2′ 棘轮门口径不对称）。
 */
function staleEntries(entries: readonly string[], read: (rel: string) => string | null): string[] {
  const stale: string[] = [];
  for (const rel of [...entries].sort()) {
    const body = read(rel);
    if (body === null) {
      stale.push(`${rel} — 文件已不存在`);
      continue;
    }
    const hits = body.split('\n').filter((l) => !isComment(l) && HARDCODED_RE.test(l)).length;
    if (hits === 0) stale.push(`${rel} — 代码行 0 命中（已收口，豁免位应剪除）`);
  }
  return stale;
}

const readSrc = (rel: string): string | null => {
  try {
    return readFileSync(join(SRC_ROOT, ...rel.split('/')), 'utf8');
  } catch {
    return null;
  }
};

describe('api-base-single-source — API 前缀基址单源 (W206/C188, §十)', () => {
  it('noHardcodedApiPrefixOutsideHttpClient', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const newViolations: string[] = [];

    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (rel === CANONICAL || isTestFile(rel) || BASELINE.has(rel)) continue;
      const lines = readFileSync(f, 'utf8').split('\n');
      const idx = lines.findIndex((l, i) => !isComment(l) && HARDCODED_RE.test(l));
      if (idx !== -1) {
        newViolations.push(`${rel}:${idx + 1} — ${lines[idx].trim().slice(0, 120)}`);
      }
    }

    expect(
      newViolations,
      newViolations.length
        ? `httpClient 之外新硬编码 "/api" 前缀（基线只减不增，新增即红；应收口到 httpClient/API_BASE 单源）：\n${newViolations.join('\n')}`
        : undefined,
    ).toEqual([]);
  }, 30_000);

  it('noStaleExemptionInsideBaseline — 基线内陈旧豁免同批判红（#143/B5-12）', () => {
    const stale = staleEntries([...BASELINE], readSrc);
    expect(
      stale,
      stale.length
        ? `陈旧豁免 ${stale.length} 枚（文件不存在或代码行 0 命中），必须同批从 BASELINE 剪除：\n${stale.join('\n')}`
        : undefined,
    ).toEqual([]);
  }, 30_000);

  it('判据反向自检：合成数据必须各判出该判的红，否则本门是自证清白', () => {
    const fake = (rel: string): string | null => {
      if (rel === 'gone/missing.ts') return null;
      if (rel === 'clean/already.ts') return "import { API_BASE } from '../services/httpClient';\nconst u = `${API_BASE}/v1/x`;\n// '/api/v1/legacy' 注释行不计\n";
      if (rel === 'dirty/still.ts') return "const u = '/api/v1/still-hardcoded';\n";
      throw new Error(`合成用例外的路径：${rel}`);
    };
    // 返回按条目名排序 ⇒ 断言顺序 = clean < gone；dirty 有命中不该判红。
    expect(
      staleEntries(['gone/missing.ts', 'clean/already.ts', 'dirty/still.ts'], fake),
    ).toEqual([
      'clean/already.ts — 代码行 0 命中（已收口，豁免位应剪除）',
      'gone/missing.ts — 文件已不存在',
    ]);
  });
});
