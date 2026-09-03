/**
 * AIP Copilot — Intelligent Co-Processor Drawer (主壳)
 *
 * Wave-2A T1: 拆分为 4 子组件 + 1 自动化 hook
 *   - useAipAutomation:    业务逻辑 (12 场景执行流)
 *   - CopilotMessageList:  消息列表 + typing 指示器
 *   - AgentQuickActions:   一键智能代理按钮网格
 *   - CopilotInputBar:     自然语言输入栏
 *
 * 中文显示全部经 `copilot.*` i18n namespace (~156 keys)。
 * 备注：handleSendMessage 的 query.includes(...) 关键字检测是**逻辑**
 * (用户预设 prompt 匹配)，保留中文原文以便做语义路由，不算显示字符串。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback } from 'react';
import LucideIcon from '../LucideIcon';
import { useLanguage } from '../LanguageContext';
import AgentQuickActions from './AgentQuickActions';
import CopilotMessageList from './CopilotMessageList';
import CopilotInputBar from './CopilotInputBar';
import { useAipAutomation } from './useAipAutomation';
import { AgentScenarioType, scenarioPrompt } from './AgentScenarioData';

interface AIPCopilotDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  viewMode?: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop';
  onViewModeChange?: (mode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop') => void;
  selectedCategory?: 'overview' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';
  onSelectCategory?: (category: 'overview' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function', id: string | null) => void;
  integrationTab?: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide';
  onIntegrationTabChange?: (tab: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide') => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
  objectTypes?: any[];
  setObjectTypes?: React.Dispatch<React.SetStateAction<any[]>>;
  linkTypes?: any[];
  setLinkTypes?: React.Dispatch<React.SetStateAction<any[]>>;
  datasets?: any[];
  setDatasets?: React.Dispatch<React.SetStateAction<any[]>>;
  securityTab?: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit';
  onSecurityTabChange?: (tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit') => void;
  securityOrgs?: any[];
  setSecurityOrgs?: React.Dispatch<React.SetStateAction<any[]>> | ((val: any[]) => void);
  securityProjects?: any[];
  setSecurityProjects?: React.Dispatch<React.SetStateAction<any[]>> | ((val: any[]) => void);
  securityMarkings?: any[];
  setSecurityMarkings?: React.Dispatch<React.SetStateAction<any[]>> | ((val: any[]) => void);
  securityPurposes?: any[];
  setSecurityPurposes?: React.Dispatch<React.SetStateAction<any[]>> | ((val: any[]) => void);
  securityRowColPolicies?: any[];
  setSecurityRowColPolicies?: React.Dispatch<React.SetStateAction<any[]>> | ((val: any[]) => void);
  securityAuditLogs?: any[];
  setSecurityAuditLogs?: React.Dispatch<React.SetStateAction<any[]>> | ((val: any[]) => void);
  securitySimUser?: string;
  setSecuritySimUser?: (val: string) => void;
  securitySimDataset?: string;
  setSecuritySimDataset?: (val: string) => void;
  securitySimPurpose?: string;
  setSecuritySimPurpose?: (val: string) => void;
  securitySimResult?: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null;
  setSecuritySimResult?: (val: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null) => void;
  securitySelectedRowColDs?: string;
  setSecuritySelectedRowColDs?: (val: string) => void;
}

/**
 * Detect scenario type from a free-form user query,
 * by matching key Chinese/English keywords (logic keywords only, not display text).
 * Kept inside the drawer since it bridges viewMode and semantically
 * routes to the right AgentScenarioType.
 */
function detectScenarioType(
  viewMode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop',
  query: string,
): AgentScenarioType {
  if (viewMode === 'workshop' || query.includes('无代码') || query.includes('大盘') || query.includes('画布') || query.includes('变量') || query.includes('绑定') || query.includes('看板') || query.includes('低代码') || query.includes('组件') || query.includes('挂载')) {
    if (query.includes('大盘') || query.includes('一键组装') || query.includes('监控') || query.includes('生成')) return 'ws_generate_dashboard';
    if (query.includes('变量') || query.includes('绑定') || query.includes('联动')) return 'ws_auto_bind';
    if (query.includes('Copilot') || query.includes('辅助面板') || query.includes('协处理器') || query.includes('操作面板')) return 'ws_inject_copilot';
    return 'ws_transform_theme';
  }
  if (viewMode === 'security' || query.includes('安全') || query.includes('隔离') || query.includes('信任') || query.includes('审计') || query.includes('GDPR') || query.includes('脱敏') || query.includes('掩膜') || query.includes('行列') || query.includes('财务') || query.includes('越权') || query.includes('日志')) {
    if (query.includes('GDPR') || query.includes('零信任') || query.includes('欧盟')) return 'sec_gdpr';
    if (query.includes('脱敏') || query.includes('行列') || query.includes('ssn') || query.includes('过滤') || query.includes('pilots')) return 'sec_row_col';
    if (query.includes('审计') || query.includes('日志') || query.includes('拦截') || query.includes('扫描') || query.includes('越权')) return 'sec_audit';
    return 'sec_finance';
  }
  if (query.includes('本体') || query.includes('实体') || query.includes('映射') || query.includes('AviationFlight') || query.includes('Object')) return 'ontology';
  if (query.includes('健康') || query.includes('监控') || query.includes('质量') || query.includes('规则') || query.includes('check') || query.includes('SLA')) return 'health';
  if (query.includes('血缘') || query.includes('链路') || query.includes('上下游') || query.includes('依赖') || query.includes('lineage')) return 'lineage';
  return 'pipeline';
}

/**
 * Match a free-form query against the 12 preset prompts from the
 * scenario catalog (logic — string compare, not display).
 */
function isScenarioQuery(t: (k: string) => string, query: string): boolean {
  if (query.startsWith('/')) return true;
  const typeList: AgentScenarioType[] = [
    'pipeline', 'ontology', 'health', 'lineage',
    'sec_gdpr', 'sec_row_col', 'sec_finance', 'sec_audit',
    'ws_generate_dashboard', 'ws_auto_bind', 'ws_inject_copilot', 'ws_transform_theme',
  ];
  return typeList.some((type) => query.includes(scenarioPrompt(t, type).slice(0, 15)));
}

export default function AIPCopilotDrawer({
  isOpen,
  onClose,
  viewMode = 'aip',
  onViewModeChange = () => {},
  selectedCategory = 'overview',
  onSelectCategory = () => {},
  integrationTab = 'connections',
  onIntegrationTabChange = () => {},
  showToast = () => {},
  objectTypes = [],
  setObjectTypes = (() => {}) as any,
  linkTypes = [],
  setLinkTypes = (() => {}) as any,
  datasets = [],
  setDatasets = (() => {}) as any,
  securityTab = 'overview',
  onSecurityTabChange = () => {},
  securityOrgs = [],
  setSecurityOrgs = (() => {}) as any,
  securityProjects = [],
  setSecurityProjects = (() => {}) as any,
  securityMarkings = [],
  setSecurityMarkings = (() => {}) as any,
  securityPurposes = [],
  setSecurityPurposes = (() => {}) as any,
  securityRowColPolicies = [],
  setSecurityRowColPolicies = (() => {}) as any,
  securityAuditLogs = [],
  setSecurityAuditLogs = (() => {}) as any,
  securitySimUser = '',
  setSecuritySimUser = () => {},
  securitySimDataset = '',
  setSecuritySimDataset = () => {},
  securitySimPurpose = '',
  setSecuritySimPurpose = () => {},
  securitySimResult = null,
  setSecuritySimResult = () => {},
  securitySelectedRowColDs = '',
  setSecuritySelectedRowColDs = () => {},
}: AIPCopilotDrawerProps) {
  const { t } = useLanguage();
  const [inputText, setInputText] = useState('');

  const {
    messages,
    isTyping,
    currentStep,
    setMessages,
    runAgentAutomation,
  } = useAipAutomation({
    viewMode,
    onViewModeChange,
    selectedCategory,
    onSelectCategory,
    integrationTab,
    onIntegrationTabChange,
    securityTab,
    onSecurityTabChange,
    showToast,
    objectTypes,
    setObjectTypes: (v) => { (setObjectTypes as any)(v); },
    linkTypes,
    setLinkTypes: (v) => { (setLinkTypes as any)(v); },
    datasets,
    setDatasets: (v) => { (setDatasets as any)(v); },
    securityOrgs: securityOrgs as any,
    setSecurityOrgs: (v: any) => { (setSecurityOrgs as any)(v); },
    securityProjects: securityProjects as any,
    setSecurityProjects: (v: any) => { (setSecurityProjects as any)(v); },
    securityMarkings: securityMarkings as any,
    setSecurityMarkings: (v: any) => { (setSecurityMarkings as any)(v); },
    securityPurposes: securityPurposes as any,
    setSecurityPurposes: (v: any) => { (setSecurityPurposes as any)(v); },
    securityRowColPolicies: securityRowColPolicies as any,
    setSecurityRowColPolicies: (v: any) => { (setSecurityRowColPolicies as any)(v); },
    securityAuditLogs: securityAuditLogs as any,
    setSecurityAuditLogs: (v: any) => { (setSecurityAuditLogs as any)(v); },
    securitySimUser,
    setSecuritySimUser,
    securitySimDataset,
    setSecuritySimDataset,
    securitySimPurpose,
    setSecuritySimPurpose,
    securitySimResult: securitySimResult as any,
    setSecuritySimResult: (v) => { (setSecuritySimResult as any)(v); },
    securitySelectedRowColDs,
    setSecuritySelectedRowColDs,
  } as any);

  /**
   * Free-text send: detect scenario, run automation; else call Q&A endpoint.
   */
  const handleSendMessage = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    const query = inputText.trim();
    if (!query) return;
    setInputText('');

    // Re-use the runAgentAutomation entrypoint to push the user message —
    // If a preset prompt matched, route to the matching scenario; else,
    // fire the backend Q&A.
    if (isScenarioQuery(t, query)) {
      const detectedType = detectScenarioType(viewMode, query);
      await runAgentAutomation(detectedType, query);
    } else {
      try {
        // Org / project / IP defaults mapped from current simulator context
        let orgId = 'org_aviation_hq';
        if (securitySimUser === 'operator_zhang') orgId = 'org_logistics_p';
        else if (securitySimUser === 'contractor_eng' || securitySimUser === 'external_auditor') orgId = 'org_contractor';
        else if (securitySimUser === 'EU_DPO') orgId = 'Org_EU_Ops';
        else if (securitySimUser === 'auditor_wang') orgId = 'Org_Finance_Dept';

        let projectId = 'proj_aviation_core';
        if (securitySimDataset === 'ds_ticket_sales') projectId = 'proj_flight_analytics';
        else if (securitySimDataset === 'ds_pilots_biography') projectId = 'proj_pilot_credentials';
        else if (securitySimDataset === 'ds_passenger_manifest') projectId = 'proj_passenger_eu';

        let clientIp = '10.120.5.23';
        if (securitySimUser === 'operator_zhang') clientIp = '172.16.45.12';
        else if (securitySimUser === 'contractor_eng' || securitySimUser === 'external_auditor') clientIp = '202.96.128.44';
        else if (securitySimUser === 'EU_DPO') clientIp = '10.120.9.15';
        else if (securitySimUser === 'unauthorized_ip_user') clientIp = '198.51.100.45';

        const res = await fetch('/api/v1/knowledge/query', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            query,
            userId: securitySimUser,
            orgId,
            projectId,
            datasetId: securitySimDataset,
            purposeId: securitySimPurpose,
            clientIp,
          }),
        });
        const data = await res.json();
        const answerMsgId = `agent-qa-${Date.now()}`;
        // Push message through the hook's setMessages
        setMessages((prev: any) => [...(prev || []), {
          id: answerMsgId,
          sender: 'agent',
          text: data.answer || t('copilot.chat.noAnswer'),
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        }]);

        if (data.verdict === 'DENIED') {
          showToast('error', t('copilot.qa.denied'));
          const logsRes = await fetch('/api/v1/security/audit-logs');
          if (logsRes.ok) {
            const logs = await logsRes.json();
            (setSecurityAuditLogs as any)(logs);
          }
        }
      } catch (err) {
        console.error('Copilot Q&A backend query failed:', err);
        const errMsgId = `agent-err-${Date.now()}`;
        setMessages((prev: any) => [...(prev || []), {
          id: errMsgId,
          sender: 'agent',
          text: t('copilot.chat.gatewayOffline'),
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        }]);
      }
    }
  }, [inputText, t, viewMode, securitySimUser, securitySimDataset, securitySimPurpose, runAgentAutomation, showToast, setSecurityAuditLogs, messages]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-2xl border-l border-slate-200 flex flex-col z-50 animate-slide-in select-none">
      {/* 1. Drawer Header */}
      <div className="h-14 bg-slate-900 text-white flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-2">
          <span className="p-1.5 rounded-lg bg-indigo-600 text-white flex items-center justify-center animate-pulse">
            <LucideIcon name="Bot" size={15} />
          </span>
          <div>
            <h3 className="text-xs font-black tracking-wide font-sans text-white">ECOS AIP Copilot</h3>
            <span className="text-[9px] text-indigo-400 font-mono">Agent-driven Co-Processor Active</span>
          </div>
        </div>
        <button
          onClick={onClose}
          aria-label={t('common.close')}
          className="p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
        >
          <LucideIcon name="X" size={16} />
        </button>
      </div>

      {/* 2. Live Agent Steps Indicator Banner */}
      {currentStep && (
        <div className="bg-indigo-900/90 text-indigo-100 px-3 py-2 flex items-center gap-2 text-[10px] font-mono shrink-0 border-b border-indigo-950">
          <LucideIcon name="Loader2" size={12} className="animate-spin text-indigo-400" />
          <span className="flex-1 font-bold">{currentStep}</span>
          <span className="bg-indigo-950 px-1.5 py-0.5 rounded text-[8px] text-indigo-400">Agent Action</span>
        </div>
      )}

      {/* 3. Messages Chat History */}
      <CopilotMessageList
        messages={messages as any}
        isTyping={isTyping}
      />

      {/* 4. Quick Automation Agents */}
      <AgentQuickActions
        viewMode={viewMode}
        isExecuting={isTyping || !!currentStep}
        onRun={(type) => { void runAgentAutomation(type); }}
      />

      {/* 5. Natural Language Input Bar */}
      <CopilotInputBar
        value={inputText}
        onChange={setInputText}
        onSubmit={handleSendMessage}
        disabled={isTyping || !!currentStep}
      />
    </div>
  );
}
