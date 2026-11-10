/**
 * ECOS Copilot — Intelligent Co-Processor Drawer (主壳)
 *
 * 组成（Wave-2A 拆分 + 2026-11-09 Copilot 批）：
 *   - useAipAutomation:      既有 12 场景自动化执行流（保留，关键字/预设命中时使用）
 *   - CopilotAgentSwitcher:  6 个内置智能体切换（默认按当前页面路由自动选择）
 *   - CopilotMessageList:    消息列表 + typing 指示器
 *   - CopilotSuggestions:    底部「一键智能代理协同」动态建议区（默认收起）
 *   - CopilotInputBar:       自然语言输入栏
 *
 * 对话路由：命中预设场景 → 既有自动化流；否则 → 当前智能体经
 * `/api/v1/agent/copilot/chat`（agent-loop + 引擎工具白名单）真实执行。
 *
 * 中文显示全部经 `copilot.*` i18n namespace。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Bot,
  Loader2,
  X
} from 'lucide-react';
import { useLanguage } from '../LanguageContext';
import { useTheme } from '../ThemeContext';
import CopilotAgentSwitcher from './CopilotAgentSwitcher';
import CopilotSuggestions from './CopilotSuggestions';
import CopilotMessageList from './CopilotMessageList';
import CopilotInputBar from './CopilotInputBar';
import { useAipAutomation } from './useAipAutomation';
import { AgentScenarioType, scenarioPrompt } from './AgentScenarioData';
import { fetchCopilotAgents, copilotAgentChat, type CopilotAgentInfo } from '../../services/copilotApi';

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

/** 当前路由 → 默认智能体（前缀最长匹配；无命中回 AI 智能体）。 */
function resolveAgentForRoute(agents: CopilotAgentInfo[], pathname: string): CopilotAgentInfo | undefined {
  let best: CopilotAgentInfo | undefined;
  let bestLen = -1;
  for (const agent of agents) {
    for (const route of agent.routes ?? []) {
      if (pathname === route || pathname.startsWith(route + '/')) {
        if (route.length > bestLen) {
          best = agent;
          bestLen = route.length;
        }
      }
    }
  }
  return best ?? agents.find((a) => a.id === 'agent-ai') ?? agents[0];
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
  const { styles } = useTheme();
  const location = useLocation();
  const [inputText, setInputText] = useState('');
  const [agents, setAgents] = useState<CopilotAgentInfo[]>([]);
  const [manualAgentId, setManualAgentId] = useState<string | null>(null);
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [chatSending, setChatSending] = useState(false);
  /** Copilot 会话 id（同一次应用中保持上下文） */
  const sessionIdRef = useRef(`copilot-${Date.now()}`);

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
    setObjectTypes: (v: any) => { (setObjectTypes as any)(v); },
    linkTypes,
    setLinkTypes: (v: any) => { (setLinkTypes as any)(v); },
    datasets,
    setDatasets: (v: any) => { (setDatasets as any)(v); },
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
    setSecuritySimResult: (v: any) => { (setSecuritySimResult as any)(v); },
    securitySelectedRowColDs,
    setSecuritySelectedRowColDs,
  } as any);

  // 6 内置智能体目录（服务端单源）；不可用时保持空列表（不伪造智能体）
  useEffect(() => {
    let cancelled = false;
    fetchCopilotAgents()
      .then((list) => { if (!cancelled) setAgents(list); })
      .catch(() => { if (!cancelled) setAgents([]); });
    return () => { cancelled = true; };
  }, []);

  // 路由变化 → 回到该页面的默认智能体（清掉手动覆盖）
  useEffect(() => {
    setManualAgentId(null);
  }, [location.pathname]);

  const routeAgent = useMemo(
    () => resolveAgentForRoute(agents, location.pathname),
    [agents, location.pathname],
  );
  const selectedAgentId = manualAgentId && agents.some((a) => a.id === manualAgentId)
    ? manualAgentId
    : (routeAgent?.id ?? 'agent-ai');
  const selectedAgent = agents.find((a) => a.id === selectedAgentId);

  // 动态建议：当前智能体能力建议 + 本会话最近用户提问（用户数据驱动）
  const suggestions = useMemo(() => {
    const defaults = selectedAgent?.quickQuestions ?? [];
    const recentTopics = messages
      .filter((m) => m.sender === 'user')
      .slice(-3)
      .map((m) => m.text.replace(/\s+/g, ' ').trim())
      .filter(Boolean)
      .map((text) => (text.length > 28 ? `${text.slice(0, 28)}…` : text))
      .map((topic) => t('copilot.suggestions.followUp', { topic }));
    return Array.from(new Set([...recentTopics, ...defaults])).slice(0, 4);
  }, [selectedAgent, messages, t]);

  const suggestionHint = useMemo(() => {
    const userCount = messages.filter((m) => m.sender === 'user').length;
    if (userCount > 0) {
      return t('copilot.suggestions.hintUser', { count: userCount });
    }
    return t('copilot.suggestions.hintAgent', { name: selectedAgent?.name ?? 'ECOS Copilot' });
  }, [messages, selectedAgent, t]);

  const busy = isTyping || !!currentStep || chatSending;

  /**
   * 统一发送：命中预设场景 → 既有自动化流；否则 → 当前智能体真实对话（agent-loop + 工具）。
   */
  const sendQuery = useCallback(async (raw: string) => {
    const query = raw.trim();
    if (!query || busy) return;

    if (isScenarioQuery(t, query)) {
      await runAgentAutomation(detectScenarioType(viewMode, query), query);
      return;
    }

    const now = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    setMessages((prev) => [...prev, {
      id: `user-${Date.now()}`,
      sender: 'user',
      text: query,
      timestamp: now(),
    }]);
    setChatSending(true);
    try {
      const res = await copilotAgentChat({
        agentId: selectedAgentId,
        message: query,
        sessionId: sessionIdRef.current,
      });
      const tools = Array.from(new Set(
        (res.toolCalls ?? [])
          .map((tc) => tc.toolName)
          .filter((name): name is string => !!name),
      ));
      let text = res.answer && res.answer.trim() ? res.answer : t('copilot.chat.noAnswer');
      if (tools.length > 0) {
        text += `\n\n> ${t('copilot.chat.toolCalls')}: ${tools.join(', ')}`;
      }
      if (res.success === false) {
        text = `${t('copilot.chat.agentFail')}${res.errorCode ?? ''}\n\n${text}`.trim();
      }
      setMessages((prev) => [...prev, {
        id: `agent-qa-${Date.now()}`,
        sender: 'agent',
        text,
        timestamp: now(),
      }]);
    } catch {
      setMessages((prev) => [...prev, {
        id: `agent-err-${Date.now()}`,
        sender: 'agent',
        text: t('copilot.chat.gatewayOffline'),
        timestamp: now(),
      }]);
    } finally {
      setChatSending(false);
    }
  }, [busy, t, viewMode, selectedAgentId, runAgentAutomation, setMessages]);

  const handleSendMessage = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    const query = inputText.trim();
    if (!query) return;
    setInputText('');
    await sendQuery(query);
  }, [inputText, sendQuery]);

  if (!isOpen) return null;

  return (
    <div className={`fixed inset-y-0 right-0 w-96 ${styles.cardBg} shadow-2xl border-l ${styles.cardBorder} flex flex-col z-50 animate-slide-in select-none`}>
      {/* 1. Drawer Header */}
      <div className={`h-14 ${styles.sidebarBg} text-white flex items-center justify-between px-4 shrink-0`}>
        <div className="flex items-center gap-2">
          <span className="p-1.5 rounded-lg bg-indigo-600 text-white flex items-center justify-center animate-pulse">
            <Bot size={15} />
          </span>
          <div>
            <h3 className="text-xs font-black tracking-wide font-sans text-white">{t('copilot.header.title')}</h3>
            <span className="text-[9px] text-indigo-400 font-mono">{t('copilot.header.subtitle')}</span>
          </div>
        </div>
        <button type="button"
          onClick={onClose}
          aria-label={t('common.close')}
          className={`p-1.5 text-[var(--text-muted)] hover:text-white hover:bg-black/20 rounded-lg transition-colors cursor-pointer`}
        >
          <X size={16} />
        </button>
      </div>

      {/* 2. Built-in agent switcher (route-driven default) */}
      <CopilotAgentSwitcher
        agents={agents}
        selectedId={selectedAgentId}
        onSelect={setManualAgentId}
        disabled={busy}
      />

      {/* 3. Live Agent Steps Indicator Banner */}
      {currentStep && (
        <div className="bg-indigo-900/90 text-indigo-100 px-3 py-2 flex items-center gap-2 text-[10px] font-mono shrink-0 border-b border-indigo-950">
          <Loader2 size={12} className="animate-spin text-indigo-400" />
          <span className="flex-1 font-bold">{currentStep}</span>
          <span className="bg-indigo-950 px-1.5 py-0.5 rounded text-[8px] text-indigo-400">Agent Action</span>
        </div>
      )}

      {/* 4. Messages Chat History */}
      <CopilotMessageList
        messages={messages as any}
        isTyping={isTyping || chatSending}
      />

      {/* 5. Dynamic suggestions — collapsed by default */}
      <CopilotSuggestions
        open={suggestOpen}
        title={t('copilot.chat.quickHeader')}
        hint={suggestionHint}
        items={suggestions}
        emptyText={t('copilot.suggestions.empty')}
        disabled={busy}
        onToggle={() => setSuggestOpen((v) => !v)}
        onAsk={(text) => { void sendQuery(text); }}
      />

      {/* 6. Natural Language Input Bar */}
      <CopilotInputBar
        value={inputText}
        onChange={setInputText}
        onSubmit={handleSendMessage}
        disabled={busy}
      />
    </div>
  );
}
