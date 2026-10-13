/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { AIPLogicPipeline, AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import {
  fetchAIPAgentsFromMesh,
  fetchGuardrailPolicies,
  fetchPipelineDefinitions,
  fetchAgentModels,
  fetchAIPAuditLogs,
} from './api';
import DashboardView from './DashboardView';
import LogicView from './LogicView';
import AgentStudioView from './AgentStudioView';
import ModelCatalogView from './ModelCatalogView';
import AgentPlayground from './AgentPlayground';
import EvalsView from './EvalsView';
import {
  LayoutDashboard, Network, FlaskConical, Cpu, BarChart3, Layers,
  CloudOff, RefreshCw, MessageSquare,
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

type TabId = 'overview' | 'agent' | 'playground' | 'orchestration' | 'evals' | 'models';

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
];

const VIEW_TITLES: Record<TabId, string> = {
  overview: 'aiworkbench.viewTitle.overview',
  agent: 'aiworkbench.viewTitle.agent',
  playground: 'aiworkbench.viewTitle.playground',
  orchestration: 'aiworkbench.viewTitle.orchestration',
  evals: 'aiworkbench.viewTitle.evals',
  models: 'aiworkbench.viewTitle.models',
};

interface AIPWorkbenchProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AIPWorkbench({ showToast }: AIPWorkbenchProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [currentTab, setCurrentTab] = useState<TabId>('overview');

  // Master AIP States (loaded from backend API, PMO-43 T3: errors are
  // re-thrown rather than swallowed — the affected tab shows an inline
  // error + retry state instead of silently rendering empty data).
  const [pipelines, setPipelines] = useState<AIPLogicPipeline[]>([]);
  const [agents, setAgents] = useState<AIPAgent[]>([]);
  const [models, setModels] = useState<AIPModel[]>([]);
  const [guardrails, setGuardrails] = useState<AIPGuardrail[]>([]);
  // §4.5：审计流禁 mock 播种 —— 空态起步，GET /api/v1/aip/audit-logs 真实加载
  const [auditLogs, setAuditLogs] = useState<AIPAuditLog[]>([]);
  const [auditError, setAuditError] = useState<string | undefined>(undefined);
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
  const loadAuditLogs = useCallback(() => {
    setAuditError(undefined);
    fetchAIPAuditLogs()
      .then(setAuditLogs)
      .catch((e) => setAuditError(e?.message || String(e)));
  }, []);

  // Load data from backend APIs on mount
  useEffect(() => {
    loadAgents();
    loadGuardrails();
    loadPipelines();
    loadModels();
    loadAuditLogs();
  }, [loadAgents, loadGuardrails, loadPipelines, loadModels, loadAuditLogs]);

  const handleAddAuditLog = (newLog: AIPAuditLog) => {
    // 本地会话追加必须标记 localOrigin，UI 明示「本地」，与引擎审计区分
    setAuditLogs((prev) => [{ ...newLog, localOrigin: true }, ...prev]);
  };

  // DashboardView 的导航按钮落地到真实 Tab（护栏绑定矩阵在 Models 子 Tab）
  const handleNavigateToView = useCallback((view: 'logic' | 'agent' | 'model' | 'guardrails') => {
    if (view === 'logic') setCurrentTab('orchestration');
    else if (view === 'model' || view === 'guardrails') setCurrentTab('models');
    else setCurrentTab('agent');
  }, []);

  const hasLoadError = Boolean(loadErrors.pipelines || loadErrors.agents || loadErrors.models || loadErrors.guardrails || auditError);

  // 单一内容树：桌面/移动共用一次挂载（原双分支各挂一份会双实例化视图）
  const content = (
    <>
      {currentTab === 'overview' && (
        <DashboardView
          pipelines={pipelines}
          agents={agents}
          models={models}
          auditLogs={auditLogs}
          auditError={auditError}
          onRetryAudit={loadAuditLogs}
          onNavigateToView={handleNavigateToView}
        />
      )}
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
      {currentTab === 'playground' && (
        <AgentPlayground agents={agents} models={models} showToast={showToast} />
      )}
      {currentTab === 'orchestration' && (loadErrors.pipelines
        ? <ErrorState message={loadErrors.pipelines} onRetry={loadPipelines} />
        : <LogicView
            pipelines={pipelines}
            models={models}
            onUpdatePipelines={setPipelines}
            showToast={showToast}
          />
      )}
      {currentTab === 'evals' && (
        <EvalsView agents={agents} models={models} />
      )}
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
    </>
  );

  return (
    <div className={`h-full w-full flex flex-col md:flex-row overflow-hidden ${styles.appBg} ${styles.appText} font-sans`}>
      {/* Mobile: brand header + horizontal scroll tab bar (below md) */}
      <div className="md:hidden w-full flex flex-col shrink-0">
        <div className={`flex items-center gap-2 px-3 pt-2 pb-1 shrink-0 ${styles.cardBg}`}>
          <span className={`p-1 rounded ${styles.accentBg} ${styles.accentText} flex items-center justify-center`}>
            <LayoutDashboard size={13} className="animate-pulse" />
          </span>
          <span className={`font-extrabold text-[11px] leading-tight tracking-wide ${styles.cardText}`}>
            {t('aiworkbench.brand')}
          </span>
          <span className={`text-[9px] px-1 py-0.5 rounded font-mono shrink-0 ${styles.badgeBg} ${styles.muted}`}>
            {t('aiworkbench.footer.envBadge')}
          </span>
        </div>

        {/* Horizontal scroll tab bar — flat, all 6 tabs in a single row */}
        <div className={`flex-shrink-0 border-b overflow-x-auto whitespace-nowrap -mx-1 px-1 ${styles.cardBg}`} style={{ borderColor: styles.cardBorder }}>
          <div className="flex flex-nowrap gap-0.5 items-end pt-2">
            {NAV_TABS.map((tab) => {
              const isActive = currentTab === tab.id;
              const IconComp = tab.icon;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setCurrentTab(tab.id)}
                  className={`h-9 px-3 rounded-t-md border-t-2 flex items-center gap-2 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition ${
                    isActive
                      ? `bg-transparent ${styles.accentText} font-bold ${styles.accentBorder}`
                      : `border-transparent opacity-70 hover:opacity-100 ${styles.cardTextMuted}`
                  }`}
                >
                  <IconComp size={13} className="shrink-0" />
                  {t(tab.labelKey)}
                </button>
              );
            })}
            {/* §4.5 P1（行232）：navConfig 注释所称「ai-workbench 页内跳转」入口实装 —
                chatbot_studio 独立路由由此进入（此前全 src 零链接，仅手输 URL 可达） */}
            <button
              type="button"
              onClick={() => navigate('/chatbot_studio')}
              className={`h-9 px-3 rounded-t-md border-t-2 flex items-center gap-2 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition border-transparent opacity-70 hover:opacity-100 ${styles.cardTextMuted}`}
            >
              <MessageSquare size={13} className="shrink-0" />
              {t('aiworkbench.tab.chatbotStudio')}
            </button>
          </div>
        </div>
      </div>

      {/* Desktop: left sidebar (md+) */}
      <div className="hidden md:flex w-64 flex-shrink-0 border-r flex-col h-full text-xs" style={{ borderColor: styles.sidebarBorder, background: styles.sidebarBg, color: styles.sidebarText }}>
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
            <span className={`text-[9px] px-1 py-0.5 rounded font-mono shrink-0 ${styles.badgeBg} ${styles.muted}`}>
              {t('aiworkbench.footer.envBadge')}
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

          {/* §4.5 P1（行232）：Chatbot Studio 独立路由入口（桌面侧栏） */}
          <button
            type="button"
            onClick={() => navigate('/chatbot_studio')}
            className={`w-full p-2.5 rounded-lg text-left transition-all cursor-pointer flex items-start gap-3 border ${styles.sidebarBorder} ${styles.sidebarText} ${styles.sidebarHoverBg}`}
          >
            <span className={`p-1.5 rounded-md flex items-center justify-center shrink-0 ${styles.badgeBg} ${styles.muted}`}>
              <MessageSquare size={13} />
            </span>
            <div className="flex flex-col min-w-0">
              <span className={`text-[11px] font-bold leading-normal ${styles.sidebarText}`}>
                {t('aiworkbench.tab.chatbotStudio')}
              </span>
              <span className={`text-[9px] truncate mt-0.5 font-medium leading-none ${styles.muted}`}>
                {t('aiworkbench.tabdesc.chatbotStudio')}
              </span>
            </div>
          </button>
        </div>

        {/* Sidebar Footer — 在线/降级徽章按真实加载结果判定，禁恒亮 */}
        <div className={`p-3 border-t ${styles.sidebarBorder} shrink-0`}>
          <div className={`p-2.5 ${styles.cardBg} border ${styles.sidebarBorder} rounded-lg space-y-1.5 shadow-2xs`}>
            <div className={`flex items-center justify-between text-[10px] font-bold ${styles.muted}`}>
              <span className="flex items-center gap-1.5">
                <span className={`h-1.5 w-1.5 rounded-full animate-ping ${hasLoadError ? styles.dangerText : styles.successText} bg-current`} />
                <span>{hasLoadError ? t('aiworkbench.footer.degradedTitle') : t('aiworkbench.footer.onlineTitle')}</span>
              </span>
              <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono ${hasLoadError ? `${styles.dangerBg} ${styles.dangerText}` : `${styles.successBg} ${styles.successText}`}`}>
                {hasLoadError ? t('aiworkbench.footer.degradedBadge') : t('aiworkbench.footer.onlineBadge')}
              </span>
            </div>
            <div className={`w-full ${styles.cardBorder} rounded-full h-1`}>
              <div className={`h-1 rounded-full w-4/5 animate-pulse ${hasLoadError ? styles.dangerBg : styles.successBg}`} />
            </div>
            <p className={`text-[9px] leading-tight ${styles.muted}`}>
              {t('aiworkbench.footer.shieldText')}
            </p>
          </div>
        </div>
      </div>

      {/* Content area — mounted exactly once */}
      <div className={`flex-1 overflow-hidden relative flex flex-col w-full min-w-0 ${styles.appBg}`}>
        {/* Right Stage Sub-Header for View Title (desktop only) */}
        <div className={`h-10 ${styles.cardBg} border-b ${styles.cardBorder} px-6 hidden md:items-center md:justify-between shrink-0`}>
          <div className="flex items-center gap-2">
            <span className={`font-extrabold text-xs ${styles.cardText}`}>
              {t(VIEW_TITLES[currentTab])}
            </span>
            <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono font-medium ${styles.badgeBg} ${styles.muted}`}>
              {t('aiworkbench.footer.moduleCode')}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <span className={`h-2 w-2 rounded-full animate-pulse bg-current ${hasLoadError ? styles.dangerText : styles.infoText}`} />
            <span className={`text-[10px] font-medium ${styles.muted}`}>
              {t('aiworkbench.footer.boundary')}
            </span>
          </div>
        </div>

        <div className="flex-1 overflow-hidden relative">
          {content}
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
