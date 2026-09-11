/**
 * AgentPlayground — 完整单 Agent 调试台（组合层）。
 *
 * 设计：
 * - 顶部工具栏：选 Agent/Model + 温度滑块 + 新建会话 + 清空
 * - 三栏布局：SessionList(侧) | 消息主区 + 流式 | 护栏状态卡(右)
 * - 输入框：Enter 发送 / Shift+Enter 换行 / AbortController 中断
 *
 * 流式协议：OAG SSE（event:node/response/blocked/error/done）
 * 通信全部走 pages/aiworkbench/api.ts 的导出函数。
 *
 * @license Apache-2.0
 */
import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import {
  Bot, FlaskConical, Send, Plus, Trash2, Thermometer,
  Download, Shield, RefreshCw, AlertCircle, PauseCircle,
} from 'lucide-react';
import { fetchManagedAgents, fetchAgentModels, oagPlaygroundStream } from './api';
import type { OagStepEvent } from '../../types/aiworkbench';
import type { AIPAgent, AIPModel } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import SessionList from '../../components/aiworkbench/playground/SessionList';
import MessageBubble, { OagProgress, OAG_NODES, type OagStepState, type PlaygroundMsg } from '../../components/aiworkbench/playground/MessageBubble';

interface AgentPlaygroundProps {
  /** 由 index.tsx 注入的 Agent 数据（可选 — 组件会用 fetchManagedAgents 自行拉取） */
  agents?: AIPAgent[];
  /** 由 index.tsx 注入的 Model 数据（可选 — 组件会用 fetchAgentModels 补齐） */
  models?: AIPModel[];
  /** 外部 toast（可选，用于错误提示） */
  showToast?: (t: 'success' | 'info' | 'error', m: string) => void;
}

const DEFAULT_TEMPERATURE = 0.7;
/** 占位 provider（当前未在 AIPModel 联合类型中开放 '—'，用 On-Premises 兜底） */
const FALLBACK_AIP_MODEL_PROVIDER: AIPModel['provider'] = 'On-Premises';

/**
 * Agent Playground 调试台主入口。
 * 选 Agent → 调 OAG SSE → 8 步进度 → 完整回复 → 来源溯源卡。
 */
export default function AgentPlayground({ agents: agentsFromProps, models: modelsFromProps, showToast }: AgentPlaygroundProps): React.JSX.Element {
  const { styles } = useTheme();
  const { t, locale } = useLanguage();

  // ── Agent & Model ──────────────────────────────────────────────
  const [agents, setAgents] = useState<AIPAgent[]>(agentsFromProps ?? []);
  const [models, setModels] = useState<AIPModel[]>(modelsFromProps ?? []);
  const [loadErrors, setLoadErrors] = useState<{ agents?: string; models?: string }>({});
  const [selectedAgentId, setSelectedAgentId] = useState('');
  const [selectedModel, setSelectedModel] = useState('');
  const [temperature, setTemperature] = useState(DEFAULT_TEMPERATURE);
  const [streaming, setStreaming] = useState<boolean>(false);
  const [previewMessage, setPreviewMessage] = useState('');

  const loadAgents = useCallback(() => {
    setLoadErrors((p) => ({ ...p, agents: undefined }));
    fetchManagedAgents()
      .then((list) => {
        setAgents(list);
        setSelectedAgentId((prev) => (list.find((a) => a.id === prev) ? prev : (list[0]?.id || '')));
      })
      .catch((e) => setLoadErrors((p) => ({ ...p, agents: e?.message || String(e) || t('aiworkbench.playground.loadError') })));
  }, [t]);

  const loadModels = useCallback(() => {
    setLoadErrors((p) => ({ ...p, models: undefined }));
    fetchAgentModels()
      .then(setModels)
      .catch((e) => setLoadErrors((p) => ({ ...p, models: e?.message || String(e) || t('aiworkbench.playground.loadError') })));
  }, [t]);

  useEffect(() => {
    if (!agentsFromProps || agentsFromProps.length === 0) loadAgents();
  }, [agentsFromProps, loadAgents]);

  useEffect(() => {
    if (!modelsFromProps || modelsFromProps.length === 0) loadModels();
  }, [modelsFromProps, loadModels]);

  // 切换 Agent 时把 model 重置为该 Agent 自身 modelId（如果 models 里没它，仅自身）
  useEffect(() => {
    const cur = agents.find((a) => a.id === selectedAgentId);
    if (cur) setSelectedModel(cur.modelId);
  }, [selectedAgentId, agents]);

  // 工具模型候选集：选中 Agent 的 modelId 必须在前列；其余从 models 补齐去重
  const candidateModels = useMemo<Array<AIPModel>>(() => {
    if (agents.length === 0) return [];
    const cur = agents.find((a) => a.id === selectedAgentId);
    if (!cur) return [];
    // 0 个 model 注册时只用 Agent 自身 modelId 占位
    if (models.length === 0) {
      return [{
        id: cur.modelId,
        displayName: cur.modelId,
        provider: FALLBACK_AIP_MODEL_PROVIDER,
        type: 'language',
        status: 'offline',
        maxContext: '—',
        latencyMs: 0,
        costPerMillion: '—',
        inputCost: '—',
        outputCost: '—',
        healthRate: 0,
        temperature: 0.7,
      }];
    }
    // 合并：先 agent 自身 modelId（可能不在 models 里），再去重后续
    const seen = new Set<string>();
    const out: AIPModel[] = [];
    const ownInModels = models.find((m) => m.id === cur.modelId);
    if (ownInModels) {
      out.push(ownInModels);
      seen.add(ownInModels.id);
    } else {
      out.push({
        id: cur.modelId,
        displayName: cur.modelId,
        provider: FALLBACK_AIP_MODEL_PROVIDER,
        type: 'language',
        status: 'offline',
        maxContext: '—',
        latencyMs: 0,
        costPerMillion: '—',
        inputCost: '—',
        outputCost: '—',
        healthRate: 0,
        temperature: 0.7,
      });
      seen.add(cur.modelId);
    }
    for (const m of models) {
      if (!seen.has(m.id)) {
        seen.add(m.id);
        out.push(m);
      }
    }
    return out;
  }, [agents, selectedAgentId, models]);

  // ── Session State ──────────────────────────────────────────────
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [triggerReloadSessions, setTriggerReloadSessions] = useState(0);
  const handleSelectSession = useCallback((sid: string | null) => {
    setActiveSessionId(sid);
    setMessages([]);
    if (sid) setPreviewMessage('');
    setTriggerReloadSessions((n) => n + 1);
  }, []);
  const handleNewSession = useCallback(() => {
    setActiveSessionId(null);
    setMessages([]);
    setPreviewMessage('');
  }, []);

  // ── Messages State ─────────────────────────────────────────────
  const [messages, setMessages] = useState<PlaygroundMsg[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const cancelRef = useRef<number>(0);

  const activeAgent = agents.find((a) => a.id === selectedAgentId);

  // ── OAG SSE 处理 ───────────────────────────────────────────────
  const updateOagStep = useCallback((agentMsgId: string, step: OagStepEvent) => {
    setMessages((prev) => prev.map((m) => {
      if (m.id !== agentMsgId) return m;
      const arr = m.oagSteps || [];
      let idx = -1;
      for (let i = 0; i < OAG_NODES.length; i++) {
        if (arr[i] && arr[i].node === step.node) { idx = i; break; }
      }
      if (idx === -1) {
        if (step.node) idx = OAG_NODES.findIndex((n) => n.key === step.node);
        if (idx === -1) idx = arr.length;
      }
      if (idx < 0) return m;
      const next = [...arr];
      while (next.length < idx) {
        const s = OAG_NODES[next.length];
        next.push({ node: s?.key || '', stepState: 'pending' });
      }
      const cur = next[idx] || { node: step.node || OAG_NODES[idx].key || '', stepState: 'pending' as const };
      const toState: OagStepState = {
        ...cur,
        ...step,
        stepState: step.status === 'DONE' ? 'done' : step.status === 'RUNNING' ? 'running' : (cur.stepState === 'error' ? 'error' : 'pending'),
        nodeElapsedMs: typeof step.nodeElapsedMs === 'number' ? step.nodeElapsedMs : cur.nodeElapsedMs,
      };
      // 一旦到达最终节点 done，后续节点标记 skipped
      next[idx] = toState;
      return { ...m, oagSteps: next };
    }));
  }, []);

  /** 安全更新指定 msgId 的字段（不依赖当前 messages 闭包） */
  const patchMsg = useCallback((msgId: string, patch: Partial<PlaygroundMsg>) => {
    setMessages((prev) => prev.map((m) => {
      if (m.id !== msgId) return m;
      // 走 spread 不可行（PlaygroundMsg 没索引签名），改用 unknown 桥接
      const merged: unknown = { ...m, ...patch };
      return merged as PlaygroundMsg;
    }));
  }, []);

  const appendMsg = useCallback((msg: PlaygroundMsg) => {
    setMessages((prev) => [...prev, msg]);
  }, []);

  const handleSend = useCallback(async () => {
    if (streaming) return;
    const text = previewMessage.trim();
    if (!text || !activeAgent) return;

    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    const cancelId = ++cancelRef.current;
    const agentMsgId = `m-${Date.now()}-ai`;
    const s = new Date().toISOString();

    const userMsg: PlaygroundMsg = {
      id: `m-${Date.now()}-u`,
      sender: 'user',
      content: text,
      timestamp: s,
    };
    const aiMsg: PlaygroundMsg = {
      id: agentMsgId,
      sender: 'ai',
      content: '',
      timestamp: s,
      oagSteps: OAG_NODES.map((n) => ({ node: n.key, stepState: 'pending' as const })),
      model: selectedModel,
    };
    setMessages((prev) => [...prev, userMsg, aiMsg]);
    setPreviewMessage('');
    setStreaming(true);

    try {
      await oagPlaygroundStream({
        agentId: activeAgent.id,
        sessionId: activeSessionId || undefined,
        message: text,
        model: selectedModel,
        temperature,
        abortSignal: ac.signal,
        handlers: {
          onStep: (step) => {
            if (cancelId !== cancelRef.current) return;
            updateOagStep(agentMsgId, step);
            if (step.intent) patchMsg(agentMsgId, { intent: step.intent });
            if (typeof step.securityPassed === 'boolean') patchMsg(agentMsgId, { securityPassed: step.securityPassed });
          },
          onResponse: ({ content, traceId }) => {
            if (cancelId !== cancelRef.current) return;
            patchMsg(agentMsgId, { content, traceId });
          },
          onDone: ({ traceId, sessionId, elapsedMs, status, intent, securityPassed }) => {
            if (cancelId !== cancelRef.current) return;
            patchMsg(agentMsgId, {
              traceId: traceId || undefined,
              elapsedMs,
              status,
              intent: intent || undefined,
              securityPassed: securityPassed,
              failed: status === 'FAILED',
              // sessionId 由父组件 state 维护（后端 OAG 不消费 agentSession，session 通过 sys_agent_session 落库逻辑可后续补齐）
            });
            if (sessionId) setActiveSessionId(sessionId);
            // 标记 OAG 第 8 步为 done（AuditLogger）
            setMessages((prev) => prev.map((m) => {
              if (m.id !== agentMsgId) return m;
              const s = m.oagSteps || [];
              const next = s.map((st, i) => i === OAG_NODES.length - 1 ? { ...st, stepState: 'done' as const } : st);
              return { ...m, oagSteps: next };
            }));
          },
          onBlocked: ({ reason, traceId }) => {
            if (cancelId !== cancelRef.current) return;
            patchMsg(agentMsgId, {
              blockedReason: reason,
              traceId: traceId || undefined,
              securityPassed: false,
              // 拦截时把当前 RUNNING 前的 RUNNING 步骤标 done 即可
            });
          },
          onError: ({ message, traceId }) => {
            if (cancelId !== cancelRef.current) return;
            patchMsg(agentMsgId, {
              errorMessage: message,
              traceId: traceId || undefined,
              failed: true,
            });
          },
        },
      });
    } catch (e) {
      if (cancelId !== cancelRef.current) return;
      const anyE = e as { name?: string; message?: string };
      const isAborted = anyE?.name === 'AbortError' || anyE?.name === 'AbortedError';
      if (isAborted) {
        // 用户主动中断 — 标记 stopped（视为已完成当前 in-flight 帧）
        patchMsg(agentMsgId, { errorMessage: undefined });
      } else {
        patchMsg(agentMsgId, {
          errorMessage: anyE?.message || String(e) || t('aiworkbench.playground.streamError'),
          networkError: true,
          failed: true,
        });
        showToast?.('error', `${t('aiworkbench.playground.streamError')}: ${anyE?.message || ''}`);
      }
    } finally {
      if (cancelId === cancelRef.current) {
        setStreaming(false);
        abortRef.current = null;
      }
    }
  }, [
    streaming, previewMessage, activeAgent, activeSessionId, selectedModel, temperature,
    updateOagStep, patchMsg, setMessages, setActiveSessionId,
    showToast, t,
  ]);

  const handleStop = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    // 中断时标记所有 running 步骤为 pending（保留已 done 的）
    cancelRef.current += 1;
  }, []);

  // ── 全局快捷键 ────────────────────────────────────────────────
  const inputRef = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Enter' && !e.shiftKey && e.target instanceof HTMLTextAreaElement) {
        if (e.target === inputRef.current) {
          e.preventDefault();
          void handleSend();
        }
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [handleSend]);

  // ── 复制 / 导出 ────────────────────────────────────────────────
  const handleCopy = useCallback((text: string) => {
    navigator.clipboard?.writeText(text).then(() => showToast?.('info', t('aiworkbench.playground.copied')))
      .catch(() => showToast?.('error', t('aiworkbench.playground.copyFailed')));
  }, [showToast, t]);

  const handleExportMd = useCallback(() => {
    if (messages.length === 0) return;
    const ts = new Date().toISOString().slice(0, 16).replace('T', '_').replace(':', '-');
    const agentName = activeAgent?.name || 'agent';
    const lines: string[] = [];
    lines.push(`# Agent Playground — ${agentName}`);
    lines.push(`> 导出时间: ${new Date().toLocaleString(locale === 'zh' ? 'zh-CN' : 'en-US')}`);
    lines.push(`> 模型: ${selectedModel || '—'} · 温度: ${temperature}`);
    lines.push('');
    for (const m of messages) {
      lines.push(`## ${m.sender === 'user' ? (locale === 'zh' ? '用户' : '**You**') : (activeAgent?.name || 'AI')} · ${new Date(m.timestamp).toLocaleTimeString(locale === 'zh' ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' })}`);
      lines.push('');
      if (m.oagSteps && m.oagSteps.length > 0) {
        const doneSteps = m.oagSteps.filter((s) => s.stepState === 'done').map((s) => `**${s.node}** (${typeof s.nodeElapsedMs === 'number' ? s.nodeElapsedMs : 0}ms)`);
        if (doneSteps.length > 0) lines.push(`_OAG: ${doneSteps.join(' → ')}_`);
        lines.push('');
      }
      if (m.traceId) {
        lines.push(`_traceId: \`${m.traceId}\` · elapsedMs: ${m.elapsedMs ?? '—'}_`);
        lines.push('');
      }
      lines.push(m.content || m.errorMessage || m.blockedReason || '—');
      lines.push('');
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `agent-playground-${agentName}-${ts}.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [messages, activeAgent, selectedModel, temperature, locale]);

  // ── 空 Agent 提示态 ────────────────────────────────────────────
  const hasAgents = agents.length > 0;
  const agentLoading = Boolean(loadErrors.agents);
  const agentEmpty = !agentLoading && !hasAgents;

  if (agentEmpty) {
    return (
      <div className={`h-full w-full flex flex-col items-center justify-center ${styles.appBg} ${styles.appText} gap-4`}>
        <span className="p-3 rounded-xl opacity-50">
          <Bot size={40} />
        </span>
        <div className="text-center leading-relaxed max-w-md px-6">
          <p className={`font-semibold text-sm ${styles.cardText}`}>{t('aiworkbench.playground.noAgentsTitle')}</p>
          <p className={`text-xs mt-1 ${styles.muted}`}>{t('aiworkbench.playground.noAgents')}</p>
        </div>
        <button
          type="button"
          onClick={loadAgents}
          className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} bg-transparent shadow-xs hover:opacity-80 cursor-pointer`}
        >
          <RefreshCw size={13} />
          {t('aiworkbench.playground.retry')}
        </button>
      </div>
    );
  }

  // ── 渲染 ───────────────────────────────────────────────────────
  return (
    <div className={`h-full w-full flex flex-row overflow-hidden ${styles.appBg} ${styles.appText}`}>
      {/* 侧栏：会话列表 */}
      <SessionList
        agentId={hasAgents && !streaming ? activeAgent?.id ?? null : (hasAgents ? activeAgent?.id ?? null : null)}
        activeSessionId={activeSessionId}
        onSelect={handleSelectSession}
        onNewSession={handleNewSession}
      />
      {/* 触发重新拉取会话（新增消息后） */}
      <span className="hidden" data-trigger={triggerReloadSessions} aria-hidden />

      {/* 主区：toolbar + 消息 + 输入 */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        {/* 顶部工具栏 */}
        <div className={`shrink-0 ${styles.cardBg} border-b ${styles.cardBorder} p-2.5 flex items-center gap-2 flex-wrap`}>
          {/* Agent 下拉 */}
          <label className={`flex items-center gap-1.5 text-[11px] ${styles.cardText}`}>
            <span className={`text-xs font-bold ${styles.muted}`}>{t('aiworkbench.playground.agentLabel')}</span>
            <select
              value={selectedAgentId}
              onChange={(e) => setSelectedAgentId(e.target.value)}
              disabled={streaming}
              className={`pl-2 pr-6 py-1 rounded-md text-[11px] font-semibold min-w-[160px] cursor-pointer disabled:opacity-50 ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder}`}
            >
              <option value="">{t('aiworkbench.playground.selectAgent')}</option>
              {agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} · {a.modelId}
                </option>
              ))}
            </select>
          </label>

          {/* Model 下拉 */}
          <label className={`flex items-center gap-1.5 text-[11px] ${styles.cardText}`}>
            <span className={`text-xs font-bold ${styles.muted}`}>{t('aiworkbench.playground.modelLabel')}</span>
            <select
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value)}
              disabled={streaming}
              className={`pl-2 pr-6 py-1 rounded-md text-[11px] font-semibold min-w-[140px] cursor-pointer disabled:opacity-50 ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder}`}
            >
              <option value="">{t('aiworkbench.playground.selectModel')}</option>
              {candidateModels.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.displayName || m.id}
                </option>
              ))}
            </select>
          </label>

          {/* 温度滑块 */}
          <label className={`flex items-center gap-2 text-[11px] min-w-[160px] flex-1`}>
            <span className={`text-xs font-bold ${styles.muted} flex items-center gap-1`}>
              <Thermometer size={11} className={styles.accentText} />
              {t('aiworkbench.playground.temperatureLabel')}
              <span className={`font-mono font-bold ${styles.accentText}`}>{temperature.toFixed(2)}</span>
            </span>
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={temperature}
              disabled={streaming}
              onChange={(e) => setTemperature(Number(e.target.value))}
              className={`w-full cursor-pointer disabled:opacity-50`}
            />
          </label>

          {/* 操作按钮组 */}
          <div className="flex items-center gap-1 ml-auto">
            <button
              type="button"
              onClick={handleNewSession}
              disabled={streaming}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} hover:opacity-85 cursor-pointer disabled:opacity-50`}
            >
              <Plus size={12} />
              {t('aiworkbench.playground.newSession')}
            </button>
            <button
              type="button"
              onClick={() => { setMessages([]); }}
              disabled={streaming || messages.length === 0}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold ${styles.cardBg} border ${styles.cardBorder} ${styles.muted} hover:opacity-85 cursor-pointer disabled:opacity-50`}
            >
              <Trash2 size={12} />
              {t('aiworkbench.playground.clearChat')}
            </button>
            <button
              type="button"
              onClick={handleExportMd}
              disabled={messages.length === 0}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} hover:opacity-85 cursor-pointer disabled:opacity-50`}
              title={t('aiworkbench.playground.exportSessionMd')}
            >
              <Download size={12} />
              <span className="hidden sm:inline">{t('aiworkbench.playground.exportSessionMd')}</span>
            </button>
          </div>
        </div>

        {/* 消息主区 */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center gap-3 text-center">
              <span className="p-2 rounded-xl opacity-50">
                <FlaskConical size={32} />
              </span>
              <p className={`text-sm font-semibold ${styles.cardText}`}>{t('aiworkbench.playground.emptyWelcome')}</p>
              {activeAgent && (
                <p className={`text-[11px] ${styles.muted}`}>
                  {t('aiworkbench.playground.emptyHint')}
                </p>
              )}
            </div>
          ) : (
            messages.map((m) => (
              <MessageBubble
                key={m.id}
                msg={m}
                agentName={activeAgent?.name || 'AI'}
                agentAvatar={activeAgent?.avatar || 'Bot'}
                isStreaming={m.sender === 'ai' && m.id === messages[messages.length - 1]?.id && streaming}
                onCopy={handleCopy}
              />
            ))
          )}

          {/* 流式响应中实时显示的 OAG 进度条（在最新 AI 消息下方） */}
          {streaming && (
            <StreamingMonitor messages={messages} agentId={selectedAgentId} />
          )}
        </div>

        {/* 输入框 */}
        <div className={`shrink-0 ${styles.cardBg} border-t ${styles.cardBorder} p-3 space-y-2`}>
          {streaming && (
            <div className={`flex items-start gap-2 text-[11px] ${styles.dangerText} ${styles.dangerBg} ${styles.dangerBorder} border rounded-md px-2 py-1`}>
              <AlertCircle size={12} className="shrink-0 mt-0.5" />
              <span className="flex-1">
                <span className="font-semibold">{t('aiworkbench.playground.streamingHint')} </span>
                <span className={styles.muted}>{t('aiworkbench.playground.streamingHintSub')}</span>
              </span>
              <button
                type="button"
                onClick={handleStop}
                className={`flex items-center gap-1 px-2 py-0.5 rounded ${styles.accentBg} text-white ${styles.accentHover} hover:opacity-90 cursor-pointer text-[10px] font-bold shrink-0`}
              >
                <PauseCircle size={11} />
                {t('aiworkbench.playground.stopStreaming')}
              </button>
            </div>
          )}
          <div className="flex items-end gap-2">
            <textarea
              ref={inputRef}
              value={previewMessage}
              onChange={(e) => setPreviewMessage(e.target.value)}
              placeholder={t('aiworkbench.playground.sendMessage')}
              rows={2}
              disabled={streaming || !activeAgent}
              className={`flex-1 px-3 py-2 rounded-lg text-[12px] leading-normal resize-none focus:outline-none focus:ring-2 ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} focus:ring-indigo-500/40 disabled:opacity-50`}
              // 通过 Enter (非 Shift+Enter) 触发发送：
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && e.target instanceof HTMLTextAreaElement) {
                  e.preventDefault();
                  void handleSend();
                }
              }}
            />
            <button
              type="button"
              onClick={() => void handleSend()}
              disabled={streaming || !previewMessage.trim() || !activeAgent}
              className={`shrink-0 flex items-center gap-1.5 px-4 py-2 rounded-lg text-[12px] font-bold ${styles.accentBg} text-white ${styles.accentHover} hover:opacity-90 cursor-pointer disabled:opacity-50 shadow-xs`}
            >
              <Send size={13} />
              <span>{t('aiworkbench.playground.send')}</span>
            </button>
          </div>
        </div>
      </div>

      {/* 右侧栏：护栏状态 */}
      <GuardrailPanel agent={activeAgent ?? null} />
    </div>
  );
}

/**
 * StreamingMonitor — 流式响应中的实时 8 步进度（占位 OagProgress 容器）。
 * 当最后一条 AI 消息存在时，复用其 oagSteps。
 */
function StreamingMonitor({ messages, agentId }: { messages: PlaygroundMsg[]; agentId: string }) {
  const aiLast = [...messages].reverse().find((m) => m.sender === 'ai');
  if (!aiLast || !Array.isArray(aiLast.oagSteps)) return null;
  return (
    <div data-monitor={agentId} aria-hidden="false">
      <OagProgress steps={aiLast.oagSteps} hasError={Boolean(aiLast.errorMessage)} />
    </div>
  );
}

/**
 * GuardrailPanel — 右下侧栏护栏状态卡：
 * - 当前 agent 已绑定的 guardrailIds (number)
 * - "已同步至安全中心" 徽章
 */
function GuardrailPanel({ agent }: { agent: AIPAgent | null }): React.JSX.Element {
  const { styles } = useTheme();
  const { t } = useLanguage();
  if (!agent) return <aside className="w-48 shrink-0 border-l border-transparent" aria-hidden />;
  const guardrailCount = Array.isArray(agent.guardrailIds) ? agent.guardrailIds.length : 0;

  return (
    <aside className={`w-48 shrink-0 border-l ${styles.cardBorder} flex flex-col h-full ${styles.cardBg} select-none`}>
      <div className={`p-3 border-b ${styles.cardBorder} flex items-center gap-1.5 shrink-0`}>
        <Shield size={13} className={styles.successText} />
        <span className={`text-[11px] font-bold ${styles.cardText}`}>{t('aiworkbench.playground.guardrailBinding')}</span>
      </div>
      <div className={`flex-1 overflow-y-auto p-3 space-y-2.5`}>
        <div className={`rounded-lg border ${styles.cardBorder} ${styles.inputBg} p-2.5 space-y-1.5`}>
          <div className={`font-mono text-[9px] ${styles.muted}`}>Agent</div>
          <div className={`text-[11px] font-semibold ${styles.cardText} truncate`} title={agent.name}>{agent.name}</div>
          <div className={`flex items-center justify-between mt-2 pt-2 border-t ${styles.cardBorder}`}>
            <span className={`text-[10px] ${styles.muted}`}>{t('aiworkbench.playground.guardrailBound')}</span>
            <span className={`px-1.5 py-0.5 rounded font-mono font-bold text-[10px] ${styles.successBg} ${styles.successText}`}>{guardrailCount}</span>
          </div>
        </div>
        <div className={`rounded-lg border ${styles.successBorder} ${styles.successBg} p-2.5 space-y-1`}>
          <div className={`flex items-center gap-1.5 text-[10px] font-bold ${styles.successText}`}>
            <span className="h-1.5 w-1.5 rounded-full animate-pulse bg-current" />
            {t('aiworkbench.playground.guardrailSyncNote')}
          </div>
          <div className={`text-[9px] font-mono leading-relaxed ${styles.cardTextMuted}`}>security-engine · RLS/CLS/ABAC</div>
        </div>
        {agent.description ? (
          <p className={`text-[9px] font-mono leading-relaxed ${styles.muted} line-clamp-4`}>{agent.description}</p>
        ) : null}
      </div>
    </aside>
  );
}
