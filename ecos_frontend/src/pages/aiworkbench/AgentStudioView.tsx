/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { authHeaders, convertMeshAgentToAIP } from '../../services/aiworkbenchApi';
import type { AgentMeshAgentRaw } from '../../services/aiworkbenchApi';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import SimulationModal from '../../components/aiworkbench/agent-studio/SimulationModal';
import { Icon } from './agent-studio/Icon';
import AgentListSidebar from './agent-studio/AgentListSidebar';
import AgentConfigEditor from './agent-studio/AgentConfigEditor';
import ChatPlayground from './agent-studio/ChatPlayground';
import AgentFormModal from './agent-studio/AgentFormModal';
import { buildMockAgentReply } from './agent-studio/agentStudioHelpers';
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
  const [selectedAgentId, setSelectedAgentId] = useState<string>(agents[0]?.id || '');
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
  const [simQuery, setSimQuery] = useState<string>('查询责任机长李维民的社保SSN和保底工资薪酬');
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
      const response = await fetch('/api/v1/guardrails/policies/preview', {
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
            verdict: n.verdict || (n.messages?.length ? 'GRANTED' : 'UNKNOWN'),
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

  useEffect(() => {
    let cancelled = false;
    fetch('/api/v1/agent-mesh/agents', { headers: authHeaders() })
      .then(r => r.json())
      .then(d => {
        if (cancelled) return;
        const raw = Array.isArray(d?.data) ? d.data : (Array.isArray(d) ? d : []);
        const mapped: AIPAgent[] = raw.map((x: AgentMeshAgentRaw) => convertMeshAgentToAIP(x));
        if (mapped.length > 0) onUpdateAgents(mapped);
      })
      .catch(e => console.error('[AgentStudioView] Failed to load agents:', e));
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (selectedAgent) {
      setChatMessages([
        {
          id: 'welcome',
          sender: 'agent',
          content: `您好！我是 **${selectedAgent.name}**。\n${selectedAgent.role}。\n我被授权调用多路航空本体资源（包含执行相关的 Ontology Actions）。请问现在有什么我能帮您调配、查询或审计的吗？`,
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

  const handleDelete = (id: string) => {
    if (!window.confirm('确定要注销这个 AIP 智能体吗？')) return;
    const updated = agents.filter(a => a.id !== id);
    onUpdateAgents(updated);
    if (selectedAgentId === id && updated.length > 0) {
      setSelectedAgentId(updated[0].id);
    }
    showToast?.('success', '已注销智能体服务');
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim() || !formRole.trim()) return;

    if (editingAgent) {
      const updated = agents.map(a => {
        if (a.id === editingAgent.id) {
          return {
            ...a,
            name: formName.trim(),
            role: formRole.trim(),
            description: formDesc.trim(),
            modelId: formModel,
            systemPrompt: formPrompt.trim(),
            assignedTools: {
              actionIds: formTools,
              functionIds: ['func_get_flight_weather']
            },
            guardrailIds: formGuardrails,
            lastModified: '2026-07-03 12:00'
          };
        }
        return a;
      });
      onUpdateAgents(updated);
      showToast?.('success', '智能体配置修改已应用');
    } else {
      const newId = `agent-${Date.now().toString().slice(-4)}`;
      const newAgent: AIPAgent = {
        id: newId,
        name: formName.trim(),
        role: formRole.trim(),
        description: formDesc.trim(),
        avatar: 'Bot',
        modelId: formModel,
        systemPrompt: formPrompt.trim(),
        assignedTools: {
          actionIds: formTools,
          functionIds: ['func_get_flight_weather']
        },
        guardrailIds: formGuardrails,
        status: 'active',
        lastModified: '2026-07-03 12:00'
      };
      onUpdateAgents([...agents, newAgent]);
      setSelectedAgentId(newId);
      showToast?.('success', '成功部署全新 AIP 智能体');
    }
    setShowCreateModal(false);
  };

  const handleSendChat = (textToSend?: string) => {
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
      user: '王凯 (AOC签派总监)',
      inputTokens: Math.floor(Math.random() * 500) + 400,
      outputTokens: 0,
      status: 'allowed',
      details: `通过交互沙箱向智能体发送问题: "${text.substring(0, 40)}..."`
    });

    setTimeout(() => {
      const replyMsgId = `agent-${Date.now()}`;

      // mock 回复决策为纯函数，已抽取至 agent-studio/agentStudioHelpers.ts（逐行一致）
      const { replyContent, thinkingTrace, proposal } = buildMockAgentReply(text);

      if (proposal) {
        fetch('/api/v1/ontology/proposals', {
          method: 'POST',
          headers: authHeaders(),
          body: JSON.stringify({
            actionId: proposal.actionId,
            actionName: proposal.actionName,
            agentId: selectedAgent.id,
            agentName: selectedAgent.name,
            payload: proposal.payload,
            proposedBy: `智能助手交互沙箱 (${selectedAgent.name})`
          })
        })
        .then(res => res.json())
        .then(data => {
          if (data.success && data.proposal) {
            proposal.id = data.proposal.id;
          }
        })
        .catch(err => console.error('Failed to register proposal:', err));
      }

      setChatMessages(prev => [...prev, {
        id: replyMsgId,
        sender: 'agent',
        content: replyContent,
        timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        thinkingTrace,
        actionProposal: proposal
      }]);
      setIsReplying(false);
    }, 1800);
  };

  const handleActionConsent = (msgId: string, approved: boolean) => {
    const targetMsg = chatMessages.find(m => m.id === msgId);
    const propId = targetMsg?.actionProposal?.id || 'prop-1';

    if (approved) {
      fetch(`/api/v1/ontology/proposals/${propId}/execute`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          userRole: '签派总监',
          userName: '王凯'
        })
      })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          showToast?.('success', 'Ontology Action 物理写回成功并通过双向对账校验！');
          
          setChatMessages(prev => prev.map(msg => {
            if (msg.id === msgId && msg.actionProposal) {
              return {
                ...msg,
                actionProposal: {
                  ...msg.actionProposal,
                  status: 'approved' as const
                }
              };
            }
            return msg;
          }));

          onAddAuditLog({
            id: `log-${Date.now()}`,
            timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19),
            source: 'Ontology Engine',
            assetName: selectedAgent?.name || 'AIP',
            user: '王凯 (AOC签派总监)',
            inputTokens: 0,
            outputTokens: 120,
            status: 'allowed',
            actionTaken: '双向核对成功',
            details: `人工授权动作执行成功: ${data.executionDetail}`
          });

          const matrixStr = data.verificationMatrix?.map((m: any) => 
            `• \`${m.logicalField}\` 映射到 \`${m.physicalCol}\`: 预估 [${m.expectedValue}] ↔ 物理读回 [${m.readbackValue}] ✅ 强对齐`
          ).join('\n') || '';

          setChatMessages(prev => [...prev, {
            id: `sys-${Date.now()}`,
            sender: 'system',
            content: `✅ **双向核对对账执行报告 (Bi-directional Validation Report)**：\n\n${data.executionDetail}\n\n**物理-逻辑字段值强一致性读回核对 (Read-back Consistency Verification)**:\n${matrixStr}\n\n🎉 写入成功，底层物理表数据行已成功同步刷新。`,
            timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
          }]);
        } else {
          showToast?.('error', `执行失败: ${data.message || data.error}`);
        }
      })
      .catch(err => {
        console.error(err);
        showToast?.('error', '与执行引擎建立连接失败，请重试');
      });
    } else {
      showToast?.('info', '已拒绝该操作申请，指令已被安全拦截。');
      setChatMessages(prev => prev.map(msg => {
        if (msg.id === msgId && msg.actionProposal) {
          return {
            ...msg,
            actionProposal: {
              ...msg.actionProposal,
              status: 'rejected' as const
            }
          };
        }
        return msg;
      }));
    }
  };

  return (
    <div className={`flex h-full overflow-hidden select-none ${styles.appBg} ${styles.appText} text-xs`}>

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
          <span>请在左侧选择或注册智能体进行控制</span>
        </div>
      )}

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

    </div>
  );
}
