/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { API_BASE } from '../../services/httpClient';
import { authHeaders } from '../../services/auth';
import {
  fetchAIPAgentsFromMesh, createMeshAgent, updateMeshAgent, deleteMeshAgent,
  agentLoopChat, executeOntologyProposal,
} from './api';
import { logger } from '../../services/logger';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import SimulationModal from '../../components/aiworkbench/agent-studio/SimulationModal';
import { Icon } from './agent-studio/Icon';
import AgentListSidebar from './agent-studio/AgentListSidebar';
import AgentConfigEditor from './agent-studio/AgentConfigEditor';
import ChatPlayground from './agent-studio/ChatPlayground';
import AgentFormModal from './agent-studio/AgentFormModal';
import type { ChatMessage } from './agent-studio/agentStudioHelpers';

interface AgentStudioViewProps {
  agents: AIPAgent[];
  models: AIPModel[];
  guardrails: AIPGuardrail[];
  onUpdateAgents: (updated: AIPAgent[]) => void;
  onAddAuditLog: (log: AIPAuditLog) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AgentStudioView({
  agents,
  models,
  guardrails,
  onUpdateAgents,
  onAddAuditLog,
  showToast,
}: AgentStudioViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  // §4.5 P1（行235）：agents 异步到达前不定型 —— 空串起步，列表到达后回填首个 id
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  useEffect(() => {
    if (!selectedAgentId && agents.length > 0) setSelectedAgentId(agents[0].id);
  }, [agents, selectedAgentId]);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [isReplying, setIsReplying] = useState(false);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AIPAgent | null>(null);
  const [formName, setFormName] = useState('');
  const [formRole, setFormRole] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formModel, setFormModel] = useState('');
  const [formPrompt, setFormPrompt] = useState('');
  const [formTools, setFormTools] = useState<string[]>([]);
  const [formGuardrails, setFormGuardrails] = useState<string[]>([]);

  const [sandboxMode, setSandboxMode] = useState<'chat' | 'simulation'>('chat');
  const [simUserId, setSimUserId] = useState<string>('analyst_li');
  const [simDatasetId, setSimDatasetId] = useState<string>('ds_pilots_biography');
  // §4.5 P1（行241）：默认提问词入 i18n（原硬编码中文）
  const [simQuery, setSimQuery] = useState<string>(() => t('aiworkbench.agentStudio.sim.defaultQuery'));
  const [isSimulating, setIsSimulating] = useState<boolean>(false);
  const [simResult, setSimResult] = useState<any | null>(null);
  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({
    node_security_filter: true,
    node_rag_retrieval: true,
    node_llm_inference: true,
    node_data_masking: true
  });

  const toggleNodeExpanded = (nodeId: string) => {
    setExpandedNodes(prev => ({
      ...prev,
      [nodeId]: !prev[nodeId]
    }));
  };

  const handleRunSimulation = async () => {
    if (!simQuery.trim()) return;
    setIsSimulating(true);
    setSimResult(null);

    try {
      // §4.5 P1（行243）：URL 走 API_BASE 单源（原裸 '/api/v1/...' 前缀硬编码）
      const response = await fetch(`${API_BASE}/v1/guardrails/policies/preview`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          userId: simUserId,
          datasetId: simDatasetId,
          query: simQuery
        })
      });
      const d = await response.json().catch((): null => null);
      if (!response.ok || !d) {
        // 网络/HTTP 异常：纯真实响应，不 fallback 到 mock
        setSimResult({ success: false, overallVerdict: 'ERROR', error: `HTTP ${response.status}` });
        showToast?.('error', t('aiworkbench.agent.sim.runFail'));
        return;
      }
      if (d.success) {
        const data = d.data || {};
        // 直接渲染真实响应（fallback 已移除）
        setSimResult({
          success: true,
          overallVerdict: data.overallVerdict || 'COMPLETED',
          latency: d.elapsedMs ? `${d.elapsedMs}ms` : undefined,
          nodes: (Array.isArray(data.nodes) ? data.nodes : []).map((n: any, idx: number) => ({
            id: n.id || n.name || `node-${idx}`,
            name: n.name || n.id || `node-${idx}`,
            // W204/C186：引擎未给出节点 verdict 时不得默认 GRANTED（缺省=未知，非放行）
            verdict: n.verdict || 'UNKNOWN',
            traces: n.traces || n.messages || []
          })),
          summary: data.summary
        });
        showToast?.('success', t('aiworkbench.agent.sim.runSuccess'));
      } else {
        // 业务错误：真实错误信息上抛
        setSimResult({
          success: false,
          overallVerdict: 'ERROR',
          error: d.message || d.error || 'unknown error'
        });
        showToast?.('error', d.message || t('aiworkbench.agent.sim.runFail'));
      }
    } catch (err: any) {
      // 网络错误：显示空状态卡
      setSimResult({ success: false, overallVerdict: 'NETWORK', error: err?.message || 'network error' });
      showToast?.('error', t('aiworkbench.agent.sim.runFail'));
    } finally {
      setIsSimulating(false);
    }
  };

  const selectedAgent = agents.find(a => a.id === selectedAgentId);

  const [agentsLoadError, setAgentsLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    // §4.5 P1（行243）：原裸 fetch('/api/v1/agent-mesh/agents') → api.ts 单源通道
    fetchAIPAgentsFromMesh()
      .then((mapped) => {
        if (cancelled) return;
        if (mapped.length > 0) onUpdateAgents(mapped);
        setAgentsLoadError(null);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const msg = e instanceof Error && e.message ? e.message : String(e);
        logger.debug('[AgentStudioView] agents reload failed:', msg);
        setAgentsLoadError(msg);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedAgent) {
      setChatMessages([
        {
          id: 'welcome',
          sender: 'agent',
          content: t('aiworkbench.agent.chatWelcome', { name: selectedAgent.name, role: selectedAgent.role }),
          timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        }
      ]);
    }
  }, [selectedAgentId]);

  const handleStartCreate = () => {
    setEditingAgent(null);
    setFormName('');
    setFormRole('');
    setFormDesc('');
    setFormModel('gemini-1.5-pro');
    setFormPrompt('');
    setFormTools(['act_reschedule_flight']);
    setFormGuardrails(['gr-pii', 'gr-approval']);
    setShowCreateModal(true);
  };

  const handleStartEdit = (a: AIPAgent) => {
    setEditingAgent(a);
    setFormName(a.name);
    setFormRole(a.role);
    setFormDesc(a.description);
    setFormModel(a.modelId);
    setFormPrompt(a.systemPrompt);
    setFormTools([...a.assignedTools.actionIds]);
    setFormGuardrails([...a.guardrailIds]);
    setShowCreateModal(true);
  };

  // §4.5 P0（行225）：原「内存 CRUD + 成功 toast」假回执已删除。
  // 创建/编辑/删除一律走真实 /api/v1/agent-mesh/agents（ecos_agent_registry 持久化），
  // 成功文案仅在 API 返回后弹出；失败 → error toast + 保留原列表，不静默。
  const [savingAgent, setSavingAgent] = useState(false);

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);

  const handleDelete = (id: string) => setPendingDeleteId(id);

  const runDelete = async () => {
    const id = pendingDeleteId;
    setPendingDeleteId(null);
    if (!id) return;
    try {
      await deleteMeshAgent(id);
      const updated = agents.filter(a => a.id !== id);
      onUpdateAgents(updated);
      if (selectedAgentId === id) {
        setSelectedAgentId(updated[0]?.id || '');
      }
      showToast?.('success', t('aiworkbench.agent.deleted'));
    } catch (e: unknown) {
      const msg = e instanceof Error && e.message ? e.message : String(e);
      showToast?.('error', t('aiworkbench.agent.deleteFailed', { msg }));
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim() || !formRole.trim() || savingAgent) return;
    const payload: Partial<AIPAgent> = {
      name: formName.trim(),
      role: formRole.trim(),
      description: formDesc.trim(),
      modelId: formModel,
      systemPrompt: formPrompt.trim(),
      // W204/C186：functionIds 不再前端硬塞单值 —— 保留既有绑定，未设置则为空（不合成）
      assignedTools: {
        actionIds: formTools,
        functionIds: editingAgent ? [...editingAgent.assignedTools.functionIds] : [],
      },
      guardrailIds: formGuardrails,
    };
    setSavingAgent(true);
    try {
      if (editingAgent) {
        const updatedAgent = await updateMeshAgent(editingAgent.id, { ...payload, status: editingAgent.status });
        onUpdateAgents(agents.map(a => (a.id === editingAgent.id ? updatedAgent : a)));
        showToast?.('success', t('aiworkbench.agent.updated'));
      } else {
        const created = await createMeshAgent({ ...payload, status: 'active' });
        onUpdateAgents([...agents, created]);
        setSelectedAgentId(created.id);
        showToast?.('success', t('aiworkbench.agent.created'));
      }
      setShowCreateModal(false);
    } catch (err: unknown) {
      const msg = err instanceof Error && err.message ? err.message : String(err);
      showToast?.('error', t('aiworkbench.agent.saveFailed', { msg }));
    } finally {
      setSavingAgent(false);
    }
  };

  // §4.5 P1（行241/243）：硬编码中文身份入 i18n；裸 fetch → agentLoopChat 单源通道；
  // 罐头回复（buildMockAgentReply + setTimeout）保持删除状态 —— 引擎无有效 content 即错误态。
  const handleSendChat = async (textToSend?: string) => {
    const text = textToSend || chatInput;
    if (!text.trim() || isReplying || !selectedAgent) return;

    const userMsgId = `user-${Date.now()}`;
    const timestampStr = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    const userMsg: ChatMessage = {
      id: userMsgId,
      sender: 'user',
      content: text,
      timestamp: timestampStr
    };

    setChatMessages(prev => [...prev, userMsg]);
    setChatInput('');
    setIsReplying(true);

    onAddAuditLog({
      id: `log-${Date.now()}`,
      timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19),
      source: 'Agent Studio',
      assetName: selectedAgent.name,
      user: t('aiworkbench.agentStudio.audit.operator'),
      // W204/C186：发送时刻引擎尚未上报 token → 记 0（不合成随机用量）
      inputTokens: 0,
      outputTokens: 0,
      status: 'allowed',
      details: t('aiworkbench.agentStudio.audit.chatSent', { text: text.substring(0, 40) }),
      localOrigin: true
    });

    const replyId = `agent-${Date.now()}`;
    const ts = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    try {
      const data = await agentLoopChat({ agentId: selectedAgent.id, message: text });
      if (!data || data.success === false || typeof data.content !== 'string' || !data.content) {
        // §2.3 诚实态：引擎未返回有效 content → 系统错误气泡，不合成回复/思考链
        const reason = data?.errorMsg
          ? `${t('aiworkbench.agentStudio.chat.engineFail')}: ${data.errorMsg}`
          : t('aiworkbench.agentStudio.chat.engineFail');
        setChatMessages(prev => [...prev, { id: replyId, sender: 'system', content: reason, timestamp: ts }]);
        return;
      }
      // 思考链仅来自引擎真实 toolCalls 上报；无上报则不展示（不合成）
      const thinkingTrace = Array.isArray(data.toolCalls) && data.toolCalls.length > 0
        ? data.toolCalls.map(tc =>
            `${String(tc.toolName ?? 'tool')}: ${tc.success ? 'OK' : 'FAIL'} (${tc.durationMs ?? '-'}ms)`)
        : undefined;
      setChatMessages(prev => [...prev, {
        id: replyId,
        sender: 'agent',
        content: data.content,
        timestamp: ts,
        thinkingTrace
      }]);
    } catch (err: unknown) {
      const msg = err instanceof Error && err.message ? err.message : String(err);
      setChatMessages(prev => [...prev, {
        id: replyId,
        sender: 'system',
        content: `${t('aiworkbench.agentStudio.chat.engineFail')} (${msg})`,
        timestamp: ts
      }]);
    } finally {
      setIsReplying(false);
    }
  };

  const handleActionConsent = async (msgId: string, approved: boolean) => {
    const targetMsg = chatMessages.find(m => m.id === msgId);

    if (approved) {
      if (!targetMsg?.actionProposal) return;
      const pid = targetMsg.actionProposal.id;
      if (!pid) {
        // §4.5（行226）：'prop-1' 假 id fallback 已删除 —— 未注册提案不得演"执行成功"
        showToast?.('error', t('aiworkbench.agentStudio.toastProposalUnregistered'));
        return;
      }
      try {
        const vo = await executeOntologyProposal(pid, {
          userRole: t('aiworkbench.agentStudio.execUserRole'),
          userName: t('aiworkbench.agentStudio.execUserName'),
        });
        showToast?.('success', t('aiworkbench.agent.execSuccess'));

        setChatMessages(prev => prev.map(msg => {
          if (msg.id === msgId && msg.actionProposal) {
            return { ...msg, actionProposal: { ...msg.actionProposal, status: 'approved' as const } };
          }
          return msg;
        }));

        // §4.5 P0（行226）：双向核对改为真实比较 —— 逐行对比期望值/物理读回值，
        // 相等=一致 / 不等=不一致 / 缺任一值=未知；绝不无条件输出"✅ 强对齐"。
        const matrix = Array.isArray(vo.verificationMatrix) ? vo.verificationMatrix : [];
        let mismatchCount = 0;
        let unknownCount = 0;
        const matrixLines = matrix.map((m) => {
          const expected = m.expectedValue;
          const readback = m.readbackValue;
          let verdictKey: string;
          if (expected === undefined || expected === null || readback === undefined || readback === null) {
            verdictKey = 'aiworkbench.agentStudio.verify.unknown';
            unknownCount += 1;
          } else if (String(expected) === String(readback)) {
            verdictKey = 'aiworkbench.agentStudio.verify.matched';
          } else {
            verdictKey = 'aiworkbench.agentStudio.verify.mismatched';
            mismatchCount += 1;
          }
          return `• \`${m.logicalField ?? '-'}\` → \`${m.physicalCol ?? '-'}\`: [${expected ?? '-'}] ↔ [${readback ?? '-'}] ${t(verdictKey)}`;
        });
        const notReported = t('aiworkbench.agentStudio.verify.notReported');
        const overall = matrix.length === 0
          ? t('aiworkbench.agentStudio.verify.noneReported')
          : mismatchCount > 0
            ? t('aiworkbench.agentStudio.verify.summaryMismatch', { count: mismatchCount })
            : unknownCount > 0
              ? t('aiworkbench.agentStudio.verify.summaryUnknown', { count: unknownCount })
              : t('aiworkbench.agentStudio.verify.summaryMatched');

        setChatMessages(prev => [...prev, {
          id: `sys-${Date.now()}`,
          sender: 'system',
          content:
            `**${t('aiworkbench.agentStudio.verify.reportTitle')}**\n\n` +
            `${vo.executionDetail ?? notReported}\n\n` +
            `**${t('aiworkbench.agentStudio.verify.matrixTitle')}**\n` +
            (matrixLines.length > 0 ? matrixLines.join('\n') : notReported) +
            `\n\n${overall}`,
          timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        }]);

        onAddAuditLog({
          id: `log-${Date.now()}`,
          timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19),
          source: 'Ontology Engine',
          assetName: selectedAgent?.name || 'AIP',
          user: t('aiworkbench.agentStudio.audit.operator'),
          // 提案执行回执无 token 上报字段 → 记 0（不合成 120）
          inputTokens: 0,
          outputTokens: 0,
          status: 'allowed',
          actionTaken: matrix.length === 0
            ? t('aiworkbench.agentStudio.verify.noneReported')
            : mismatchCount > 0
              ? t('aiworkbench.agentStudio.verify.summaryMismatch', { count: mismatchCount })
              : t('aiworkbench.agentStudio.verify.summaryMatched'),
          details: t('aiworkbench.agentStudio.audit.execDetails', {
            detail: vo.executionDetail ?? notReported,
            tx: vo.transactionHash ?? notReported,
          }),
          localOrigin: true
        });
      } catch (err: unknown) {
        const msg = err instanceof Error && err.message ? err.message : String(err);
        // 网关 ApiResponse 原文（含 traceId）已在 message 中透传，错误态展示之
        showToast?.('error', `${t('aiworkbench.agent.execFail')}: ${msg}`);
      }
    } else {
      showToast?.('info', t('aiworkbench.agent.rejected'));
      setChatMessages(prev => prev.map(msg => {
        if (msg.id === msgId && msg.actionProposal) {
          return { ...msg, actionProposal: { ...msg.actionProposal, status: 'rejected' as const } };
        }
        return msg;
      }));
    }
  };

  // §4.5 P2（行249）：根容器 select-none 已去除 —— 聊天/审计文本必须可选中复制
  return (
    <div className={`flex h-full flex-col overflow-hidden ${styles.appBg} ${styles.appText} text-xs`}>
      {agentsLoadError && (
        <div className={`shrink-0 px-3 py-1.5 text-[11px] font-semibold ${styles.dangerBg} ${styles.dangerText}`}>
          {t('aiworkbench.agent.loadFailed').replace('{msg}', agentsLoadError)}
        </div>
      )}
      <div className="flex-1 flex overflow-hidden min-h-0">
      {/* 1. Left Agents List */}
      <AgentListSidebar
        agents={agents}
        selectedAgentId={selectedAgentId}
        onSelect={setSelectedAgentId}
        onCreate={handleStartCreate}
      />

      {/* 2. Central Agent Settings Config Editor */}
      {selectedAgent ? (
        <div className="flex-1 flex overflow-hidden">
          <AgentConfigEditor
            agent={selectedAgent}
            guardrails={guardrails}
            onEdit={() => handleStartEdit(selectedAgent)}
            onDelete={() => handleDelete(selectedAgent.id)}
          />

          {/* 3. Right: Live Sandbox Playground */}
          <div className={`w-full max-w-[450px] ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0`}>

            {/* 右侧沙箱 header + simulation 子组件 (chat mode 由主组件渲染) */}
            <SimulationModal
              sandboxMode={sandboxMode}
              onModeChange={setSandboxMode}
              simUserId={simUserId}
              simDatasetId={simDatasetId}
              simQuery={simQuery}
              onSimUserIdChange={setSimUserId}
              onSimDatasetIdChange={setSimDatasetId}
              onSimQueryChange={setSimQuery}
              isSimulating={isSimulating}
              simResult={simResult}
              expandedNodes={expandedNodes}
              onToggleNode={toggleNodeExpanded}
              onRunSimulation={handleRunSimulation}
              showToast={showToast}
            />

            {/* TAB 1: Chat Mode */}
            {sandboxMode === 'chat' && (
              <ChatPlayground
                chatMessages={chatMessages}
                agentName={selectedAgent.name}
                isReplying={isReplying}
                chatInput={chatInput}
                onChatInputChange={setChatInput}
                onSend={handleSendChat}
                onConsent={handleActionConsent}
              />
            )}

          </div>
        </div>
      ) : (
        <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted}`}>
          <Icon name="Bot" size={32} className={`${styles.cardTextMuted} animate-bounce mb-2`} />
          <span>{t('aiworkbench.agent.selectHint')}</span>
        </div>
      )}
      </div>

      {/* Create / Edit Agent Modal */}
      {showCreateModal && (
        <AgentFormModal
          editingAgent={editingAgent}
          models={models}
          guardrails={guardrails}
          formName={formName}
          setFormName={setFormName}
          formRole={formRole}
          setFormRole={setFormRole}
          formDesc={formDesc}
          setFormDesc={setFormDesc}
          formModel={formModel}
          setFormModel={setFormModel}
          formPrompt={formPrompt}
          setFormPrompt={setFormPrompt}
          formTools={formTools}
          setFormTools={setFormTools}
          formGuardrails={formGuardrails}
          setFormGuardrails={setFormGuardrails}
          onClose={() => setShowCreateModal(false)}
          onSubmit={handleSave}
        />
      )}
      <ConfirmDialog
        visible={!!pendingDeleteId}
        danger
        title={t('aiworkbench.agent.deleteTitle')}
        message={t('aiworkbench.agent.deleteConfirm')}
        confirmText={t('common.confirm')}
        cancelText={t('common.cancel')}
        onConfirm={() => void runDelete()}
        onCancel={() => setPendingDeleteId(null)}
      />

    </div>
  );
}
