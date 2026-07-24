import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {HashRouter, Routes, Route, Navigate} from 'react-router-dom';
import App from './App.tsx';
import RequireAuth from './components/RequireAuth.tsx';
import Login from './pages/Login.tsx';
import { LanguageProvider } from './components/LanguageContext.tsx';
import { ThemeProvider } from './components/ThemeContext.tsx';
import './index.css';

// Pages
import CognitiveOperatingSystem from './pages/CognitiveOperatingSystem.tsx';
import DataCatalog from './pages/DataCatalog.tsx';
import DatasetExplorer from './pages/DatasetExplorer.tsx';
import PipelineBuilder from './pages/PipelineBuilder.tsx';
import CodeWorkbook from './pages/CodeWorkbook.tsx';
import OperationalApps from './pages/OperationalApps.tsx';
import MonitoringCenter from './pages/MonitoringCenter.tsx';
import OntologyWorkbenchLayout, { OntologyWorkbenchLayoutStandalone } from './pages/OntologyWorkbenchLayout.tsx';
import DataWorkbenchLayout, { DataWorkbenchLayoutStandalone } from './pages/DataWorkbenchLayout.tsx';
import BusinessWorkbenchLayout, { BusinessWorkbenchLayoutStandalone } from './pages/business-workbench/BusinessWorkbenchLayout.tsx';
import AgentMesh from './pages/AgentMesh.tsx';
import AgentBuilder from './pages/AgentBuilder.tsx';
import AgentTestConsole from './pages/AgentTestConsole.tsx';
import ObjectExplorer from './pages/ObjectExplorer.tsx';
import WorldModelViewer from './pages/WorldModelViewer.tsx';
import CaseLibrary from './components/CaseLibraryView.tsx';
import AlertPanel from './components/AlertPanel.tsx';
import DataQualityDashboard from './pages/DataQualityDashboard.tsx';
import GlossaryManager from './pages/GlossaryManager.tsx';
import MarketplaceBrowser from './pages/MarketplaceBrowser.tsx';
import UserManagement from './pages/UserManagement.tsx';
import BizDashboard from './pages/BizDashboard.tsx';
import ProjectTracker from './pages/ProjectTracker.tsx';
import ContractManager from './pages/ContractManager.tsx';
import OperationsDashboard from './pages/OperationsDashboard.tsx';
import KanbanBoard from './pages/KanbanBoard.tsx';
import DataLineage from './pages/DataLineage.tsx';
import DictManager from './pages/DictManager.tsx';
import SystemDictionary from './pages/SystemDictionary.tsx';
import SystemConfigManager from './pages/SystemConfigManager.tsx';
import DataSourceManager from './pages/DataSourceManager.tsx';
import SecurityCenter from './pages/security-center/SecurityCenter.tsx';
import DataLake from './pages/DataLake.tsx';
import TelemetryViewer from './pages/TelemetryViewer.tsx';
import TokenDashboard from './pages/TokenDashboard.tsx';
import TenantManager from './pages/TenantManager.tsx';
import AsyncTaskCenterView from './pages/AsyncTaskCenterView';
import EngineMonitor from './pages/EngineMonitor';
import CognitiveEngineView from './pages/CognitiveEngineView';
import KnowledgeView from './pages/KnowledgeView.tsx';
import GraphExplorerView from './pages/GraphExplorerView.tsx';
import GuardrailsView from './pages/GuardrailsView.tsx';
import AIWorkbench from './pages/aiworkbench/index.tsx';
import WorkshopView from './pages/WorkshopView.tsx';
import ScenarioManagementView from './pages/ScenarioManagementView.tsx';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HashRouter>
      <LanguageProvider>
        <ThemeProvider>
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
              <Route path="catalog" element={<Navigate to="/data-workbench" replace />} />
              <Route path="dataset_explorer/:assetId?" element={<Navigate to="/data-workbench" replace />} />
              <Route path="lineage" element={<Navigate to="/data-workbench" replace />} />
              <Route path="objects" element={<ObjectExplorer />} />
              <Route path="dq_dashboard" element={<Navigate to="/data-workbench" replace />} />
              <Route path="datalake" element={<Navigate to="/data-workbench" replace />} />
              {/* I · 信息层 — 本体工作台（单页，内部分区切换） */}
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
              <Route path="datasources" element={<Navigate to="/data-workbench" replace />} />
              {/* K · 知识层 */}
              <Route path="workflow_designer" element={<WorkshopView />} />
              <Route path="project_workbench" element={<ScenarioManagementView />} />
              <Route path="world_model" element={<WorldModelViewer />} />
              <Route path="knowledge_view" element={<KnowledgeView />} />
              <Route path="pipeline" element={<Navigate to="/data-workbench" replace />} />
              <Route path="workbook" element={<Navigate to="/data-workbench" replace />} />
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
              <Route path="security" element={<SecurityCenter />} />
              <Route path="policy-engine" element={<SecurityCenter />} />
              <Route path="guardrails" element={<GuardrailsView />} />
              {/* 系统管理 */}
              <Route path="iam" element={<UserManagement />} />
              <Route path="kanban" element={<KanbanBoard />} />
              <Route path="telemetry" element={<TelemetryViewer />} />
              <Route path="tokens" element={<TokenDashboard />} />
              <Route path="tenants" element={<TenantManager />} />
              <Route path="task_center" element={<Navigate to="/data-workbench" replace />} />
              {/* 引擎监控 */}
              <Route path="engine-security" element={<EngineMonitor engine="security" />} />
              <Route path="engine-data" element={<EngineMonitor engine="data" />} />
              <Route path="engine-ontology" element={<EngineMonitor engine="ontology" />} />
              <Route path="engine-cognitive" element={<CognitiveEngineView />} />
              <Route path="engine-knowledge" element={<EngineMonitor engine="knowledge" />} />
              <Route path="engine-ai" element={<EngineMonitor engine="ai" />} />
              <Route path="engine-tasks" element={<AsyncTaskCenterView showToast={(type, msg) => console.log(`[TaskCenter] ${type}: ${msg}`)} onViewModeChange={(mode) => {}} />} />
              {/* 404 fallback */}
              <Route path="*" element={<WorldModelViewer />} />
            </Route>
          </Routes>
        </ThemeProvider>
      </LanguageProvider>
    </HashRouter>
  </StrictMode>,
);
