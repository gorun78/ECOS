/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  mockAIPAuditLogs,
} from './mockData';
import { AIPLogicPipeline, AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import {
  fetchAIPAgentsFromMesh,
  fetchGuardrailPolicies,
  fetchPipelineDefinitions,
  fetchAgentModels,
} from './api';
import DashboardView from './DashboardView';
import LogicView from './LogicView';
import AgentStudioView from './AgentStudioView';
import ModelCatalogView from './ModelCatalogView';
import AgentPlayground from './AgentPlayground';
import EvalsView from './EvalsView';
import CognitionView from './CognitionView';
import {
  LayoutDashboard, Network, FlaskConical, Cpu, BarChart3, Layers,
  CloudOff, RefreshCw, BrainCircuit,
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

type TabId = 'overview' | 'agent' | 'playground' | 'orchestration' | 'evals' | 'models' | 'cognition';

const NAV_TABS: Array<{
  id: TabId;
  icon: React.ComponentType<{ size?: number | string; className?: string }>;
  labelKey: string;
  descKey: string;
}> = [
  { id: 'overview', icon: LayoutDashboard, labelKey: 'aiworkbench.tab.overview', descKey: 'aiworkbench.tabdesc.overview' },
  { id: 'agent', icon: Network, labelKey: 'aiworkbench.tab.agent', descKey: 'aiworkbench.tabdesc.agent' },
  { id: 'playground', icon: FlaskConical, labelKey: 'aiworkbench.tab.playground', descKey: 'aiworkbench.tabdesc.playground' },
  { id: 'orchestration', icon: Cpu, labelKey: 'aiworkbench.tab.orchestration', descKey: 'aiworkbench.tabdesc.orchestration' },
  { id: 'evals', icon: BarChart3, labelKey: 'aiworkbench.tab.evals', descKey: 'aiworkbench.tabdesc.evals' },
  { id: 'models', icon: Layers, labelKey: 'aiworkbench.tab.models', descKey: 'aiworkbench.tabdesc.models' },
  { id: 'cognition', icon: BrainCircuit, labelKey: 'aiworkbench.tab.cognition', descKey: 'aiworkbench.tabdesc.cognition' },
];

const VIEW_TITLES: Record<TabId, string> = {
  overview: 'aiworkbench.viewTitle.overview',
  agent: 'aiworkbench.viewTitle.agent',
  playground: 'aiworkbench.viewTitle.playground',
  orchestration: 'aiworkbench.viewTitle.orchestration',
  evals: 'aiworkbench.viewTitle.evals',
  models: 'aiworkbench.viewTitle.models',
  cognition: 'aiworkbench.viewTitle.cognition',
};

interface AIPWorkbenchProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AIPWorkbench({ showToast }: AIPWorkbenchProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [currentTab, setCurrentTab] = useState<TabId>('overview');

  // Master AIP States (loaded from backend API, PMO-43 T3: errors are
  // re-thrown rather than swallowed — the affected tab shows an inline
  // error + retry state instead of silently rendering empty data).
  const [pipelines, setPipelines] = useState<AIPLogicPipeline[]>([]);
  const [agents, setAgents] = useState<AIPAgent[]>([]);
  const [models, setModels] = useState<AIPModel[]>([]);
  const [guardrails, setGuardrails] = useState<AIPGuardrail[]>([]);
  const [auditLogs, setAuditLogs] = useState<AIPAuditLog[]>(mockAIPAuditLogs);
  type LoadKey = 'pipelines' | 'agents' | 'models' | 'guardrails';
  const [loadErrors, setLoadErrors] = useState<Partial<Record<LoadKey, string>>>({});

  const loadPipelines = useCallback(() => {
    setLoadErrors((prev) => ({ ...prev, pipelines: undefined }));
    fetchPipelineDefinitions()
      .then(setPipelines)
      .catch((e) => setLoadErrors((prev) => ({ ...prev, pipelines: e?.message || String(e) })));
  }, []);
  const loadAgents = useCallback(() => {
    setLoadErrors((prev) => ({ ...prev, agents: undefined }));
    fetchAIPAgentsFromMesh()
      .then(setAgents)
      .catch((e) => setLoadErrors((prev) => ({ ...prev, agents: e?.message || String(e) })));
  }, []);
  const loadModels = useCallback(() => {
    setLoadErrors((prev) => ({ ...prev, models: undefined }));
    fetchAgentModels()
      .then(setModels)
      .catch((e) => setLoadErrors((prev) => ({ ...prev, models: e?.message || String(e) })));
  }, []);
  const loadGuardrails = useCallback(() => {
    setLoadErrors((prev) => ({ ...prev, guardrails: undefined }));
    fetchGuardrailPolicies()
      .then(setGuardrails)
      .catch((e) => setLoadErrors((prev) => ({ ...prev, guardrails: e?.message || String(e) })));
  }, []);

  // Load data from backend APIs on mount
  useEffect(() => {
    loadAgents();
    loadGuardrails();
    loadPipelines();
    loadModels();
  }, [loadAgents, loadGuardrails, loadPipelines, loadModels]);

  const handleAddAuditLog = (newLog: AIPAuditLog) => {
    setAuditLogs((prev) => [newLog, ...prev]);
  };

  return (
    <div className={`h-full w-full flex flex-row overflow-hidden ${styles.appBg} ${styles.appText} font-sans`}>
      {/* 1. AIP Left Sidebar — theme tokens only, no hardcoded colors */}
      <aside className={`w-64 border-r ${styles.sidebarBorder} flex flex-col h-full select-none shrink-0 text-xs ${styles.sidebarBg} ${styles.sidebarText}`}>
        {/* Title / Branding */}
        <div className={`p-3 border-b ${styles.sidebarBorder}`}>
          <div className={`py-2 px-3 ${styles.cardBg} ${styles.sidebarText} rounded-lg flex items-center justify-between shadow-xs`}>
            <div className="flex items-center gap-2 truncate">
              <span className={`p-1 rounded ${styles.accentBg} ${styles.accentText} flex items-center justify-center`}>
                <LayoutDashboard size={13} className="animate-pulse" />
              </span>
              <div className="flex flex-col min-w-0">
                <span className={`font-extrabold text-[11px] leading-tight tracking-wide ${styles.sidebarText}`}>
                  {t('aiworkbench.brand')}
                </span>
                <span className={`text-[9px] font-medium leading-none mt-0.5 ${styles.muted}`}>
                  {t('aiworkbench.brandTagline')}
                </span>
              </div>
            </div>
            <span className={`text-[9px] px-1 py-0.5 rounded font-mono select-none shrink-0 ${styles.badgeBg} ${styles.muted}`}>
              v2.4
            </span>
          </div>
        </div>

        {/* Sidebar Nav Items */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1">
          {NAV_TABS.map((tab) => {
            const isActive = currentTab === tab.id;
            const IconComp = tab.icon;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setCurrentTab(tab.id)}
                className={`w-full p-2.5 rounded-lg text-left transition-all cursor-pointer flex items-start gap-3 border ${
                  isActive
                    ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
                    : `${styles.sidebarText} ${styles.sidebarHoverBg} border-transparent`
                }`}
              >
                <span className={`p-1.5 rounded-md flex items-center justify-center shrink-0 transition-all ${
                  isActive ? `${styles.accentBg} ${styles.accentText}` : `${styles.badgeBg} ${styles.muted}`
                }`}>
                  <IconComp size={13} />
                </span>
                <div className="flex flex-col min-w-0">
                  <span className={`text-[11px] font-bold leading-normal ${isActive ? styles.sidebarActiveText : styles.sidebarText}`}>
                    {t(tab.labelKey)}
                  </span>
                  <span className={`text-[9px] truncate mt-0.5 font-medium leading-none ${styles.muted}`}>
                    {t(tab.descKey)}
                  </span>
                </div>
              </button>
            );
          })}
        </div>

        {/* Sidebar Footer with Running Badge — theme-aware semantic colors */}
        <div className={`p-3 border-t ${styles.sidebarBorder} shrink-0`}>
          <div className={`p-2.5 ${styles.cardBg} border ${styles.sidebarBorder} rounded-lg space-y-1.5 shadow-2xs`}>
            <div className={`flex items-center justify-between text-[10px] font-bold ${styles.muted}`}>
              <span className="flex items-center gap-1.5">
                <span className={`h-1.5 w-1.5 rounded-full ${styles.successText} animate-ping`} />
                <span>{t('aiworkbench.footer.onlineTitle')}</span>
              </span>
              <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono ${styles.successBg} ${styles.successText}`}>
                {t('aiworkbench.footer.onlineBadge')}
              </span>
            </div>
            <div className={`w-full ${styles.cardBorder} rounded-full h-1`}>
              <div className={`h-1 rounded-full w-4/5 animate-pulse ${styles.successBg}`} />
            </div>
            <p className={`text-[9px] leading-tight ${styles.muted}`}>
              {t('aiworkbench.footer.shieldText')}
            </p>
          </div>
        </div>
      </aside>

      {/* 2. Active View Panel Render */}
      <div className={`flex-1 overflow-hidden relative flex flex-col ${styles.appBg}`}>
        {/* Right Stage Sub-Header for View Title */}
        <div className={`h-10 ${styles.cardBg} border-b ${styles.cardBorder} px-6 flex items-center justify-between shrink-0 select-none`}>
          <div className="flex items-center gap-2">
            <span className={`font-extrabold text-xs ${styles.cardText}`}>
              {t(VIEW_TITLES[currentTab])}
            </span>
            <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono font-medium ${styles.badgeBg} ${styles.muted}`}>
              {t('aiworkbench.footer.moduleCode')}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <span className={`h-2 w-2 rounded-full ${styles.infoText} animate-pulse`} />
            <span className={`text-[10px] font-medium ${styles.muted}`}>
              {t('aiworkbench.footer.boundary')}
            </span>
          </div>
        </div>

        <div className="flex-1 overflow-hidden relative">
          {/* overview: DashboardView (全量仪表盘，无 ErrorState — dashboard 自带子 Tab) */}
          {currentTab === 'overview' && (
            <DashboardView
              pipelines={pipelines}
              agents={agents}
              models={models}
              auditLogs={auditLogs}
              onNavigateToView={() => {}}
            />
          )}

          {/* agent: AgentStudioView */}
          {currentTab === 'agent' && (loadErrors.agents
            ? <ErrorState message={loadErrors.agents} onRetry={loadAgents} />
            : <AgentStudioView
                agents={agents}
                models={models}
                guardrails={guardrails}
                onUpdateAgents={setAgents}
                onAddAuditLog={handleAddAuditLog}
                showToast={showToast}
              />
          )}

          {/* playground: Agent 多轮调试台（消费 index 已加载的 agents/models，失败时独立重试） */}
          {currentTab === 'playground' && (
            <AgentPlayground agents={agents} models={models} showToast={showToast} />
          )}

          {/* orchestration: Logic 工作流画布 */}
          {currentTab === 'orchestration' && (loadErrors.pipelines
            ? <ErrorState message={loadErrors.pipelines} onRetry={loadPipelines} />
            : <LogicView
                pipelines={pipelines}
                models={models}
                onUpdatePipelines={setPipelines}
                showToast={showToast}
              />
          )}

          {/* evals: 独立渲染 radar + 趋势 */}
          {currentTab === 'evals' && (
            <EvalsView
              agents={agents}
              models={models}
            />
          )}

          {/* models: ModelCatalogView (内含 sub-tab: catalog / compare / guardrails) */}
                    {currentTab === 'models' && (loadErrors.models
                      ? <ErrorState message={loadErrors.models} onRetry={loadModels} />
                      : <ModelCatalogView
                          models={models}
                          agents={agents}
                          guardrails={guardrails}
                          onUpdateModels={setModels}
                          showToast={showToast}
                        />
                    )}

          {/* cognition: 场景认知四件套执行入口（PMO-60 v2.0 P2b 搭壳；P3b 落地真实端点） */}
          {currentTab === 'cognition' && <CognitionView />}
        </div>
      </div>
    </div>
  );
}

/**
 * PMO-43 T3: inline error/retry state for the AIP data-fetch
 * anti-patterns ("terminal coding style: empty catch → real error state,
 * error/retry to the UI"). Pure presentation — no global state store.
 * Kept verbatim from the original spec (already theme-token aware).
 */
const ErrorState: React.FC<{ message?: string; onRetry: () => void }> = ({ message, onRetry }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div
      className={`h-full w-full flex flex-col items-center justify-center gap-3 text-sm ${styles.appText} px-6`}
      role="alert"
    >
      <CloudOff size={36} className="opacity-60 shrink-0" />
      <div className="text-center leading-relaxed">
        <p className="font-semibold">{t('network.error.title')}</p>
        <p className="opacity-70 text-xs mt-1 max-w-md">{message}</p>
      </div>
      <button
        type="button"
        onClick={onRetry}
        className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} bg-transparent shadow-xs hover:opacity-80 cursor-pointer`}
      >
        <RefreshCw size={13} />
        {t('network.retry')}
      </button>
    </div>
  );
};
