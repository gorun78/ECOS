import { StrictMode, lazy, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import './index.css';

// Keep core shell, auth + providers eager (first-paint critical path).
import App from './App.tsx';
import RequireAuth from './components/RequireAuth.tsx';
import Login from './pages/Login.tsx';
import { LanguageProvider } from './components/LanguageContext.tsx';
import { ThemeProvider } from './components/ThemeContext.tsx';

// Per-route lazy-loaded feature pages (50 routes → ~50 chunks)
const CognitiveOperatingSystem = lazy(() => import('./pages/CognitiveOperatingSystem.tsx'));
const OperationalApps = lazy(() => import('./pages/OperationalApps.tsx'));
const MonitoringCenter = lazy(() => import('./pages/MonitoringCenter.tsx'));
const OntologyWorkbenchLayoutStandalone = lazy(
  () => import('./pages/OntologyWorkbenchLayout.tsx').then(m => ({ default: m.OntologyWorkbenchLayoutStandalone }))
);
const DataWorkbenchLayoutStandalone = lazy(
  () => import('./pages/DataWorkbenchLayout.tsx').then(m => ({ default: m.DataWorkbenchLayoutStandalone }))
);
const BusinessWorkbenchLayoutStandalone = lazy(
  () => import('./pages/business-workbench/BusinessWorkbenchLayout.tsx').then(m => ({ default: m.BusinessWorkbenchLayoutStandalone }))
);
const AgentMesh = lazy(() => import('./pages/AgentMesh.tsx'));
const AgentBuilder = lazy(() => import('./pages/AgentBuilder.tsx'));
const AgentTestConsole = lazy(() => import('./pages/AgentTestConsole.tsx'));
const ObjectExplorer = lazy(() => import('./pages/ObjectExplorer.tsx'));
const WorldModelViewer = lazy(() => import('./pages/WorldModelViewer.tsx'));
const CaseLibrary = lazy(() => import('./components/CaseLibraryView.tsx'));
const AlertPanel = lazy(() => import('./components/AlertPanel.tsx'));
const GlossaryManager = lazy(() => import('./pages/GlossaryManager.tsx'));
const MarketplaceBrowser = lazy(() => import('./pages/MarketplaceBrowser.tsx'));
const UserManagement = lazy(() => import('./pages/UserManagement.tsx'));
const BizDashboard = lazy(() => import('./pages/BizDashboard.tsx'));
const ProjectTracker = lazy(() => import('./pages/ProjectTracker.tsx'));
const ContractManager = lazy(() => import('./pages/ContractManager.tsx'));
const OperationsDashboard = lazy(() => import('./pages/OperationsDashboard.tsx'));
const KanbanBoard = lazy(() => import('./pages/KanbanBoard.tsx'));
const DataLineage = lazy(() => import('./pages/DataLineage.tsx'));
const DictManager = lazy(() => import('./pages/DictManager.tsx'));
const SystemDictionary = lazy(() => import('./pages/SystemDictionary.tsx'));
const SystemConfigManager = lazy(() => import('./pages/SystemConfigManager.tsx'));
const SecurityCenter = lazy(() => import('./pages/security-center/SecurityCenterLayout'));
const TelemetryViewer = lazy(() => import('./pages/TelemetryViewer.tsx'));
const TokenDashboard = lazy(() => import('./pages/TokenDashboard.tsx'));
const TenantManager = lazy(() => import('./pages/TenantManager.tsx'));
const KnowledgeView = lazy(() => import('./pages/KnowledgeView.tsx'));
const GraphExplorerView = lazy(() => import('./pages/GraphExplorerView.tsx'));
const GuardrailsView = lazy(() => import('./pages/GuardrailsView.tsx'));
const AIWorkbench = lazy(() => import('./pages/aiworkbench/index.tsx'));
const WorkshopView = lazy(() => import('./pages/WorkshopView.tsx'));
const ScenarioManagementView = lazy(() => import('./pages/ScenarioManagementView.tsx'));
// EngineMonitor + CognitiveEngineView are imported once eagerly because
// lazy() can't pass static props at import site.
const EngineMonitor = lazy(() => import('./pages/EngineMonitor.tsx'));
const CognitiveEngineView = lazy(() => import('./pages/CognitiveEngineView.tsx'));
const AsyncTaskCenterView = lazy(() => import('./pages/AsyncTaskCenterView'));

// Suspense fallback — spinner shown while any lazy chunk is downloading
function RouteFallback() {
  return (
    <div className="h-full flex items-center justify-center">
      <div className="h-6 w-6 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
    </div>
  );
}

// Bridge for <Route element={...}>'s props — React.lazy can't be applied to
// a JSX element with static props (it doesn't accept props on import), so
// wrap each eager-props route in a thin component.
function EngineMonitorRoute({ engine }: { engine: string }) {
  return (
    <Suspense fallback={<RouteFallback />}>
      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
      <EngineMonitor engine={engine} {...({} as any)} />
    </Suspense>
  );
}

function TasksCenterRoute() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <AsyncTaskCenterView
        showToast={(type, msg) => console.log(`[TaskCenter] ${type}: ${msg}`)}
        onViewModeChange={() => {}}
      />
    </Suspense>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HashRouter>
      <LanguageProvider>
        <ThemeProvider>
          <Suspense fallback={<RouteFallback />}>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route
                element={
                  <RequireAuth>
                    <App />
                  </RequireAuth>
                }
              >
                {/* 总览 */}
                <Route index element={<CognitiveOperatingSystem />} />
                <Route path="mission_control" element={<CognitiveOperatingSystem />} />
                <Route path="monitor" element={<MonitoringCenter />} />
                {/* 业务应用 */}
                <Route path="biz_dashboard" element={<BizDashboard />} />
                <Route path="project_tracker" element={<ProjectTracker />} />
                <Route path="contract_manager" element={<ContractManager />} />
                <Route path="ops_dashboard" element={<OperationsDashboard />} />
                <Route path="ops_apps" element={<OperationalApps />} />
                <Route path="marketplace" element={<MarketplaceBrowser />} />
                {/* D · 数据层 */}
                <Route path="objects" element={<ObjectExplorer />} />
                {/* I · 信息层 — 本体工作台 (单页，内部分区切换) */}
                <Route path="ontology_workbench" element={<OntologyWorkbenchLayoutStandalone />} />
                <Route path="business-workbench" element={<BusinessWorkbenchLayoutStandalone />} />
                {/* 数据工作台 */}
                <Route path="data-workbench" element={<DataWorkbenchLayoutStandalone />} />
                {/* Legacy redirects — 旧路由统一重定向到本体工作台 */}
                <Route path="domains" element={<Navigate to="/ontology_workbench" replace />} />
                <Route path="domain_designer" element={<Navigate to="/ontology_workbench" replace />} />
                <Route path="ontology" element={<Navigate to="/ontology_workbench" replace />} />
                <Route path="ontology_designer" element={<Navigate to="/ontology_workbench" replace />} />
                <Route path="knowledge_graph" element={<GraphExplorerView />} />
                <Route path="glossary" element={<Navigate to="/ontology_workbench" replace />} />
                <Route path="dict" element={<DictManager />} />
                <Route path="system-config" element={<SystemConfigManager />} />
                {/* K · 知识层 */}
                <Route path="workflow_designer" element={<WorkshopView />} />
                <Route path="project_workbench" element={<ScenarioManagementView />} />
                <Route path="world_model" element={<WorldModelViewer />} />
                <Route path="knowledge_view" element={<KnowledgeView />} />
                {/* W · 智能层 */}
                <Route path="agent_studio" element={<Navigate to="/ai-workbench" replace />} />
                <Route path="ai-workbench" element={<AIWorkbench />} />
                <Route path="workshop" element={<WorkshopView />} />
                <Route path="agent_mesh" element={<AgentMesh />} />
                <Route path="agent-builder/:agentId?" element={<AgentBuilder />} />
                <Route path="agent-test/:agentId" element={<AgentTestConsole />} />
                <Route path="case_library" element={<CaseLibrary />} />
                <Route path="alerts" element={<AlertPanel />} />
                {/* 安全 */}
                <Route path="security-center" element={<SecurityCenter />} />
                <Route path="guardrails" element={<GuardrailsView />} />
                {/* 系统管理 */}
                <Route path="iam" element={<UserManagement />} />
                <Route path="kanban" element={<KanbanBoard />} />
                <Route path="telemetry" element={<TelemetryViewer />} />
                <Route path="tokens" element={<TokenDashboard />} />
                <Route path="tenants" element={<TenantManager />} />
                {/* 引擎监控 */}
                <Route path="engine-security" element={<EngineMonitorRoute engine="security" />} />
                <Route path="engine-data" element={<EngineMonitorRoute engine="data" />} />
                <Route path="engine-ontology" element={<EngineMonitorRoute engine="ontology" />} />
                <Route path="engine-cognitive" element={<CognitiveEngineView />} />
                <Route path="engine-knowledge" element={<EngineMonitorRoute engine="knowledge" />} />
                <Route path="engine-ai" element={<EngineMonitorRoute engine="ai" />} />
                <Route path="engine-tasks" element={<TasksCenterRoute />} />
                {/* 404 fallback */}
                <Route path="*" element={<WorldModelViewer />} />
              </Route>
            </Routes>
          </Suspense>
        </ThemeProvider>
      </LanguageProvider>
    </HashRouter>
  </StrictMode>
);
