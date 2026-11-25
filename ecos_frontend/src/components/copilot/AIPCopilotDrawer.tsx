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
import { readApiError } from '../../services/httpClient';

interface AIPCopilotDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  viewMode?: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop';
  onViewModeChange?: (mode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop') => void;
  securityTab?: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit';
  onSecurityTabChange?: (tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit') => void;
}

/**
 * Detect scenario type from a free-form user query,
 * by matching key Chinese/English keywords (logic keywords only, not display text).
 *
 * 2026-11-14：只剩 4 条真实安全场景。原先的 pipeline/ontology/health/lineage 与 4 条 ws_* 
 * 分支已删除（纯 sleep 演示流，无后端调用）；未命中安全关键字一律**不走自动化**，
 * 落到真实 chat（agent-loop）。
 */
function detectScenarioType(
  viewMode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop',
  query: string,
): AgentScenarioType | null {
  if (viewMode === 'security' || query.includes('安全') || query.includes('隔离') || query.includes('信任') || query.includes('审计') || query.includes('GDPR') || query.includes('脱敏') || query.includes('掩膜') || query.includes('行列') || query.includes('财务') || query.includes('越权') || query.includes('日志')) {
    if (query.includes('GDPR') || query.includes('零信任') || query.includes('欧盟')) return 'sec_gdpr';
    if (query.includes('脱敏') || query.includes('行列') || query.includes('ssn') || query.includes('过滤') || query.includes('pilots')) return 'sec_row_col';
    if (query.includes('审计') || query.includes('日志') || query.includes('拦截') || query.includes('扫描') || query.includes('越权')) return 'sec_audit';
    return 'sec_finance';
  }
  return null;
}

/**
 * Match a free-form query against the 4 real security scenario prompts
 * (logic — string compare, not display).
 */
function isScenarioQuery(t: (k: string) => string, query: string): boolean {
  if (query.startsWith('/')) return true;
  const typeList: AgentScenarioType[] = ['sec_gdpr', 'sec_row_col', 'sec_finance', 'sec_audit'];
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
  securityTab = 'overview',
  onSecurityTabChange = () => {},
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
    onViewModeChange,
    onSecurityTabChange,
    showToast: () => {},
  });
  // 6 内置智能体目录（服务端单源）；不可用时保持空列表（不伪造智能体）
  useEffect(() => {
    let cancelled = false;
    fetchCopilotAgents()
      .then((list) => { if (!cancelled) setAgents(list); })
      .catch(() => { if (!cancelled) setAgents([]); });
    return () => { cancelled = true; };
  }, []);

  /**
   * 清空对话（2026-11-25 用户要求：切换智能体时清空对话框内容）。
   *
   * <p>对话上下文属于「一个智能体的一次会话」，跨智能体留痕会造成两种失真：
   * ① 上一个智能体的工具调用过程/结论被误读成本智能体说的；
   * ② 后端 sessionId 仍复用 ⇒ 新智能体的 agent-loop 会带着旧会话历史继续跑。</p>
   *
   * <p>三件事同批做：清掉除首条欢迎语外的全部消息（欢迎语是本组件固有开场，不是对话内容）、
   * 换新 sessionId（新智能体开新会话，绝不复用旧上下文）、清空输入框与建议面板残态。</p>
   */
  const resetConversation = useCallback(() => {
    setMessages((prev) => prev.filter((m) => m.id === 'welcome'));
    sessionIdRef.current = `copilot-${Date.now()}`;
    setInputText('');
    setSuggestOpen(false);
  }, [setMessages]);

  /** 手动切换智能体：只记手动覆盖，实际清空由下方「生效智能体变化」单点触发（点同一个不白清）。 */
  const handleAgentSelect = useCallback((agentId: string) => {
    setManualAgentId(agentId);
  }, []);

  // 路由变化 → 回到该页面的默认智能体（清掉手动覆盖）。
  // 这里只负责「解除手动覆盖」，不直接清对话：是否算切换由下方生效值判定。
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

  /**
   * 生效智能体变化 → 清空对话（2026-11-25 用户要求：「切换智能体时清空对话框内容」）。
   *
   * <p>挂在**生效值**而非点击事件上，一次覆盖两条切换路径：手动点切换器、路由变化导致的
   * 默认智能体回落（例如从数据工作台切到本体工作台）。点当前已在用的智能体时值不变，
   * 不会白清一次。挂载首帧同样跳过 —— 此时本就是初始欢迎语，重置只会白扔一次 sessionId。</p>
   */
  const prevAgentIdRef = useRef(selectedAgentId);
  useEffect(() => {
    if (prevAgentIdRef.current === selectedAgentId) return;
    prevAgentIdRef.current = selectedAgentId;
    resetConversation();
  }, [selectedAgentId, resetConversation]);

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

    const scenario = isScenarioQuery(t, query) ? detectScenarioType(viewMode, query) : null;
    if (scenario) {
      await runAgentAutomation(scenario, query);
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
    } catch (e) {
      // 2026-11-24 修复「AIP 网关离线」误报：原实现把**任何**异常都渲染成「网关离线」，
      // 而实际上后端返回的是 503 GUARDRAIL_FAIL_CLOSED / 500 PARSE_FAILURE 等有明确分类的
      // 业务错误（真因被掩盖，用户按「网关离线」去查网络方向必然查不到）。
      // 现按 readApiError 单源口径分态：① 无 status ⇒ 真·传输失败（网络/网关不可达）；
      // ② 有 status ⇒ 后端已应答，如实报 HTTP 状态 + 后端 message，并带 traceId 供追溯。
      const err = readApiError(e);
      const offline = err.status === undefined;
      const detail = offline
        ? `${t('copilot.chat.gatewayOffline')}\n\n> ${err.message}`
        : `${t('copilot.chat.backendRejected')} (HTTP ${err.status})\n\n> ${err.message}${
            err.traceId ? `\n\n\`traceId: ${err.traceId}\`` : ''
          }`;
      setMessages((prev) => [...prev, {
        id: `agent-err-${Date.now()}`,
        sender: 'agent',
        text: detail,
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
        onSelect={handleAgentSelect}
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
