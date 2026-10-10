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
 * 基线白名单（文件级登记）：当前实测 95 个既有违规文件，全部登记如下。
 * 属主 W206/B 批（任务 #119）按域渐进收敛为经 httpClient 的相对路径或
 * `import { API_BASE } from ".../httpClient"`；参照 R-55②/H6-T5 口径
 * **清单只减不增**——新增文件即红，禁止扩充本清单来豁免新违规。
 * 2026-10-10 收口减 3：ChatPanel.tsx（SSE sseUrl）、knowledge/services/govern.ts（KB_V1）
 * 已改由 API_BASE 派生，WorldModelViewer.tsx 两处裸 fetch + `j.data.data` 手拆信封
 * 改走 apiFetchData（§5.3 唯一解包点）。aiworkbench/api.ts 流式点同批改用
 * API_BASE，但因存量 24 处其他字面暂留基线。
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 单源正主：允许硬编码前缀。 */
const CANONICAL = 'services/httpClient.ts';

/** 基线（存量登记，2026-10 拆分 ontologyApi 后全量实测；只减不增）。 */
const BASELINE = new Set([
  'api/agentClient.ts',
  'api/dataClient.ts',
  'api/dictConfigClient.ts',
  'api/iamClient.ts',
  'api/knowledgeClient.ts',
  'api/monitorClient.ts',
  'api/ontologyClient.ts',
  'api/securityClient.ts',
  'components/data-workbench/GitPanel.tsx',
  'components/ontology-workbench/OntologyDomainPanel.tsx',
  'components/ontology-workbench/OntologyWorkbenchSidebar.tsx',
  'hooks/useBindingCatalog.ts',
  'pages/ContractManager.tsx',
  'pages/DataAssetsDashboard.tsx',
  'pages/DataLineage.tsx',
  'pages/EngineMonitor.tsx',
  'pages/Guardrails/constants.ts',
  'pages/MonitoringCenter.tsx',
  'pages/OntologyObjectBrowser.tsx',
  'pages/ProjectTracker.tsx',
  'pages/ScenarioManagementView.tsx',
  'pages/SqlQueryConsole.tsx',
  'pages/SystemDictionary.tsx',
  'pages/TelemetryViewer.tsx',
  'pages/TenantManager.tsx',
  'pages/TokenDashboard.tsx',
  'pages/aiworkbench/AgentStudioView.tsx',
  'pages/aiworkbench/ChatbotStudioView.tsx',
  'pages/aiworkbench/api.ts',
  'pages/aiworkbench/cognition/overview.tsx',
  'pages/aiworkbench/logic-canvas/useLogicPipelineRun.ts',
  'pages/business-workbench/BusinessWorkbenchLayout.tsx',
  'pages/data-quality/api/_base.ts',
  'pages/data-workbench/DataEngineConfigPanel.tsx',
  'pages/data-workbench/api/_base.ts',
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
  'pages/data-workbench/tabs/connections/InlineSqlConsole.tsx',
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
  'pages/knowledge/tabs/SyncTab.tsx',
  'pages/scenario/CognitionPanel.tsx',
  'pages/scenario/CounterfactualTab.tsx',
  'pages/scenario/MentalReviewTab.tsx',
  'pages/scenario/OverrideTab.tsx',
  'pages/scenario/ReplayTab.tsx',
  'pages/sql-query-console/HistoryPanel.tsx',
  'pages/sql-query-console/SchemaTree.tsx',
  'pages/sql-query-console/TemplatePanel.tsx',
  'pages/sql-query-console/api.ts',
  'pages/workbench/[scenarioId]/index.tsx',
  'pages/workbench/index.tsx',
  'providers/graphqlClient.ts',
  'services/agentMeshApi.ts',
  'services/aiworkbenchApi.ts',
  'services/alertsApi.ts',
  'services/auth.ts',
  'services/cognitiveClient.ts',
  'services/cognitiveEngineApi.ts',
  'services/copilotApi.ts',
  'services/dataCatalogClient.ts',
  'services/datanetMetadataApi.ts',
  'services/dict.ts',
  'services/gitService.ts',
  'services/glossary.ts',
  'services/knowledgeNavApi.ts',
  'services/knowledgeQueryApi.ts',
  'services/ontology/ontologyDataApi.ts',
  'services/ontology/ontologyDomainApi.ts',
  'services/ontology/ontologyEngineApi.ts',
  'services/ontology/ontologyExportApi.ts',
  'services/ontology/ontologyLineageApi.ts',
  'services/ontology/ontologyMappingApi.ts',
  'services/ontology/ontologyModelApi.ts',
  'services/ontology/ontologyProposalApi.ts',
  'services/ontology/ontologyVersionApi.ts',
  'services/ontologyClient.ts',
  'services/ontologyWorkbenchApi.ts',
  'services/platformApi.ts',
  'services/systemDictApi.ts',
  'services/taskCenter.ts',
  'services/worldModelGraphApi.ts',
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
  });
});
