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
  'components/ontology-workbench/OntologyDomainPanel.tsx',
  'hooks/useBindingCatalog.ts',
  'pages/ContractManager.tsx',
  'pages/DataAssetsDashboard.tsx',
  'pages/DataLineage.tsx',
  'pages/ProjectTracker.tsx',
  'pages/ScenarioManagementView.tsx',
  'pages/TelemetryViewer.tsx',
  'pages/TenantManager.tsx',
  'pages/TokenDashboard.tsx',
  'pages/aiworkbench/api.ts',
  'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts',
  'pages/business-workbench/BusinessWorkbenchLayout.tsx',
  'pages/data-quality/api/_base.ts',
  'pages/data-workbench/DataEngineConfigPanel.tsx',
  'pages/data-workbench/api/datasource.ts',
  'pages/data-workbench/api/lineage.ts',
  'pages/data-workbench/api/metadata.ts',
  'pages/data-workbench/api/pipeline.ts',
  'pages/data-workbench/api/unstructured.ts',
  'pages/data-workbench/pipeline-editor/CopilotPanel.tsx',
  'pages/data-workbench/pipeline-editor/GitCommitDialog.tsx',
  'pages/data-workbench/pipeline-editor/GitVersionPanel.tsx',
  'pages/data-workbench/pipeline-editor/NodePalette.tsx',
  'pages/data-workbench/pipeline-editor/PipelineExecutionMonitor.tsx',
  'pages/data-workbench/pipeline-editor/PropertyPanel.tsx',
  'pages/data-workbench/pipeline-editor/UdfBuilderPanel.tsx',
  'pages/data-workbench/pipelineDebugApi.ts',
  'pages/knowledge/pages/ExtractionPage.tsx',
  'pages/knowledge/pages/components/govern/RuleRepoTable.tsx',
  'pages/knowledge/services/assets.ts',
  'pages/knowledge/services/extract.ts',
  'pages/knowledge/services/graph.ts',
  'pages/knowledge/services/overview.ts',
  'pages/knowledge/services/wiki.ts',
  'pages/knowledge/tabs/DataWorkbenchImportTab.tsx',
  'pages/knowledge/tabs/DocumentUploadTab.tsx',
  'pages/knowledge/tabs/GraphBuilderTab.tsx',
  'pages/knowledge/tabs/KnowledgeRuleRepositoryTab.tsx',
  'pages/scenario/CognitionPanel.tsx',
  'pages/scenario/CounterfactualTab.tsx',
  'pages/scenario/MentalReviewTab.tsx',
  'pages/scenario/OverrideTab.tsx',
  'pages/scenario/ReplayTab.tsx',
  'pages/workbench/[scenarioId]/index.tsx',
  'pages/workbench/index.tsx',
  'providers/graphqlClient.ts',
  'services/aiworkbenchApi.ts',
  'services/cognitiveClient.ts',
  'services/cognitiveEngineApi.ts',
  'services/dataCatalogClient.ts',
  'services/gitService.ts',
  'services/knowledgeNavApi.ts',
  'services/ontology/ontologyDataApi.ts',
  'services/ontology/ontologyDomainApi.ts',
  'services/ontology/ontologyEngineApi.ts',
  'services/ontology/ontologyLineageApi.ts',
  'services/ontology/ontologyMappingApi.ts',
  'services/ontology/ontologyModelApi.ts',
  'services/ontology/ontologyProposalApi.ts',
  'services/ontology/ontologyVersionApi.ts',
  'services/ontologyClient.ts',
  'services/ontologyWorkbenchApi.ts',
  'services/platformApi.ts',
  'services/taskCenter.ts',
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
