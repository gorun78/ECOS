/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { authHeaders, convertMeshAgentToAIP } from '../../services/aiworkbenchApi';
import type { AgentMeshAgentRaw } from '../../services/aiworkbenchApi';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface AgentStudioViewProps {
  agents: AIPAgent[];
  models: AIPModel[];
  guardrails: AIPGuardrail[];
  onUpdateAgents: (updated: AIPAgent[]) => void;
  onAddAuditLog: (log: AIPAuditLog) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

interface ChatMessage {
  id: string;
  sender: 'user' | 'agent' | 'system';
  content: string;
  timestamp: string;
  thinkingTrace?: string[];
  actionProposal?: {
    id?: string;
    actionId: string;
    actionName: string;
    payload: Record<string, string>;
    status: 'pending' | 'approved' | 'rejected';
  };
}

/**
 * Build a local mock simulation result when the backend sandbox endpoint
 * (/api/v1/guardrails/policies/preview) is unavailable. Produces a realistic
 * verdict trace so the Simulation Sandbox stays interactive (graceful
 * degradation) instead of showing a blank state.
 */
function buildMockSimulationResult(userId: string, datasetId: string, query: string): any {
  const isSensitive = /ssn|社保|身份证|薪资|salary|电话|phone|住址|薪酬/i.test(query);
  const verdict = isSensitive ? 'BLOCKED' : 'GRANTED';
  return {
    success: true,
    overallVerdict: verdict,
    nodes: [
      {
        id: 'node_security_filter',
        name: '零信任身份准入网关 (Identity Gateway)',
        verdict: 'GRANTED',
        traces: [
          `▶ 身份主体 "${userId}" 通过 Org-IP 白名单校验`,
          `▶ 项目级 DAC 检查: 数据集 "${datasetId}" 访问权限已授权`,
          isSensitive
            ? '⚠️ 标记级 MAC 策略: 检测到高敏字段访问请求，已升级审计级别'
            : '✅ 标记级 MAC 策略: 常规数据访问放行'
        ]
      },
      {
        id: 'node_rag_retrieval',
        name: 'RAG 知识检索与上下文重排 (RAG Retrieval)',
        verdict: 'GRANTED',
        isMaskedEnforced: isSensitive,
        groundedContext: isSensitive
          ? `[已脱敏] 检索到飞行员档案记录 3 条，其中 SSN 及 base_salary 字段已被正则屏蔽引擎强制掩码。原始值不会进入 LLM 上下文。`
          : `检索到数据集 "${datasetId}" 的常规运营数据 12 条，包含航班号、航线、状态等字段，无需脱敏处理。`,
        traces: [
          '▶ 向量检索 top-k=5 文档片段',
          isSensitive
            ? '🔒 PII 正则屏蔽引擎已激活: SSN/薪资字段已替换为 [REDACTED]'
            : '✅ 无敏感字段命中，上下文原样传递'
        ]
      },
      {
        id: 'node_llm_inference',
        name: 'LLM 主权推理引擎 (Sovereign LLM Inference)',
        verdict: verdict,
        answer: isSensitive
          ? '⚠️ 抱歉，您请求的字段（SSN/薪资）属于高敏感个人信息，已触发数据防火墙拦截策略。根据 GDPR 及民航数据安全规程，该信息不对当前安全密级开放。如需审计级访问，请联系 CSO 申请特许授权。'
          : `根据检索到的数据，当前数据集 "${datasetId}" 的常规运营指标正常。所有航班状态均在可控范围内，未检测到异常。`,
        traces: [
          '▶ 调用 Sovereign LLM 进行推理生成',
          verdict === 'BLOCKED'
            ? '🚫 安全策略编译器判定: 输出包含受限字段，已拦截'
            : '✅ 推理完成，输出通过安全审查'
        ]
      },
      {
        id: 'node_data_masking',
        name: '行列防火墙隔离输出 (Data Firewall Masking)',
        verdict: isSensitive ? 'BLOCKED' : 'GRANTED',
        dataRows: isSensitive ? [] : [
          { id: 'row_001', field: 'flight_num', value: 'UA102', masked: false },
          { id: 'row_002', field: 'status', value: 'ON_TIME', masked: false }
        ],
        traces: [
          isSensitive
            ? '🚫 行级过滤条件生效: 当前用户安全密级不足，物理输出截断为 0 条记录'
            : '✅ 列级脱敏规则应用完毕，输出 2 条合规记录',
          isSensitive
            ? '🔒 敏感列 (ssn_number, base_salary) 已被强制抹除'
            : '✅ 无敏感列需要脱敏'
        ]
      }
    ]
  };
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
  const [selectedAgentId, setSelectedAgentId] = useState<string>(agents[0]?.id || '');
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [isReplying, setIsReplying] = useState(false);

  // Modal states
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AIPAgent | null>(null);
  const [formName, setFormName] = useState('');
  const [formRole, setFormRole] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formModel, setFormModel] = useState('');
  const [formPrompt, setFormPrompt] = useState('');
  const [formTools, setFormTools] = useState<string[]>([]);
  const [formGuardrails, setFormGuardrails] = useState<string[]>([]);

  // Simulation Sandbox states
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
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (data.success) {
        setSimResult(data);
        showToast?.('success', '推理干涉仿真模拟完成！决策追踪链路已刷新。');
      } else {
        throw new Error(data.error || '仿真执行失败');
      }
    } catch (err) {
      // Backend simulation-sandbox endpoint is not deployed yet — degrade
      // gracefully by rendering a locally-computed mock verdict so the
      // sandbox stays interactive instead of showing a blank state.
      console.warn('[AgentStudio] simulation-sandbox unavailable, using local fallback', err);
      setSimResult(buildMockSimulationResult(simUserId, simDatasetId, simQuery));
      showToast?.('info', '仿真引擎离线，已切换至本地沙箱推演模式');
    } finally {
      setIsSimulating(false);
    }
  };

  const selectedAgent = agents.find(a => a.id === selectedAgentId);

  // Connect to the real AgentMesh API (GET /api/agent-mesh/agents) and replace
  // the mock agent list with live data. Uses authHeaders() for the Bearer token.
  // Falls back to the existing (mock) list on error so the UI degrades gracefully.
  useEffect(() => {
    let cancelled = false;
    fetch('/api/agent-mesh/agents', { headers: authHeaders() })
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

  // Load welcome chat messages when selected agent changes
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

  // Chat Submission & Simulated Dynamic Multi-Step AI Reasoning
  const handleSendChat = (textToSend?: string) => {
    const text = textToSend || chatInput;
    if (!text.trim() || isReplying || !selectedAgent) return;

    // Add user message
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

    // Audit Log writing on request
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

    // Simulating thinking process and reply
    setTimeout(() => {
      const replyMsgId = `agent-${Date.now()}`;
      
      let replyContent = '';
      let thinkingTrace: string[] = [];
      let proposal: ChatMessage['actionProposal'] = undefined;

      const lowerText = text.toLowerCase();
      if (lowerText.includes('ua102') || lowerText.includes('查询')) {
        thinkingTrace = [
          '⚡ 正在解析用户请求，提取 Ontology 目标：航班 "UA102"',
          '🔍 触发系统集成查询：检索 ObjectType: Flight (ID: UA102)',
          '🔗 级联读取关联属性：执飞飞机 N101UA, 指派飞行员 P01 (张建国)',
          '📊 融合数据安全审计：PII 脱敏机制启动，正常运行。'
        ];
        replyContent = `已为您成功从航空本体库拉取 **UA102** 航班的实时多维详情：\n\n*   **航班号**: UA102 (芝加哥 ORD → 旧金山 SFO)\n*   **计划起飞**: 今日 08:00 (ON_TIME 准点)\n*   **执飞机型**: Boeing 737-800 (尾号: **N101UA**)\n*   **责任机长**: **张建国** (Captain, 累积飞行 8200 小时)\n\n**AI 安全评估建议**：\n执飞飞机 N101UA 的最后维保时间为 2026-05-12，气象检测显示 ORD 机场阵风 12 节，适航评级为【极佳(Excellent)】。无需调配改签。`;
      } else if (lowerText.includes('延误') || lowerText.includes('小时') || lowerText.includes('改') || lowerText.includes('reschedule')) {
        thinkingTrace = [
          '⚡ 用户请求对本体数据发起修改指令。操作意图: 重新调度/航班重新指派',
          '🛡️ 安全审查：触发 Guardrail: Ontology Action 强制人工确认 (gr-approval)',
          '⚠️ 检测到操作对象：Flight: UA102, 修改延误参数：120 分钟',
          '💾 构造 Ontology Action Payload, 暂停事务，发送授权请求卡片...'
        ];
        replyContent = `我已理解您的调配指令：因突发设备检测，需将 **UA102** 航班延误状态更新。由于该操作涉及本体状态修改，受 **AIP Guardrails 安全护栏约束**，必须由您点击下方卡片人工确认授权，方可写入企业主本体数据库。`;
        proposal = {
          actionId: 'act_reschedule_flight',
          actionName: '重新指派航班与状态修改 (act_reschedule_flight)',
          payload: {
            flight_number: 'UA102',
            new_status: 'DELAYED',
            delay_minutes: '120',
            auth_required_by: 'AOC_DIRECTOR'
          },
          status: 'pending'
        };
      } else {
        thinkingTrace = [
          '⚡ 解析通用会话指令...',
          '🧠 调用大语言模型大局观评估...'
        ];
        replyContent = `我是一个工作在航空运行控制大厅的智能助手。我可以协助您高效检索以下本体信息：\n\n1.  **航班与气象级联查询** (如："帮我查询 UA102 航班状态及风险")\n2.  **机组与CAAC合规审查** (如："评估飞行员 P02 的疲劳与资质风险")\n3.  **拟定 Ontology 修改意图** (如："帮我把 UA102 航班延误改派为2小时")`;
      }

      if (proposal) {
        // Send proposal to backend
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

  // Handle Action Approval Card Interactions
  const handleActionConsent = (msgId: string, approved: boolean) => {
    const targetMsg = chatMessages.find(m => m.id === msgId);
    const propId = targetMsg?.actionProposal?.id || 'prop-1';

    if (approved) {
      // Execute via backend with role Check (defaulting to AOC Director / 签派总监)
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

          // Add success log
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

          // Insert system confirmation bubble with verification details
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
    <div className={`flex h-full overflow-hidden select-none ${styles.appBg} text-xs`}>
      
      {/* 1. Left Agents List */}
      <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
        <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
          <span className={`font-bold ${styles.cardText}`}>智能助手工坊 ({agents.length})</span>
          <button
            onClick={handleStartCreate}
            className="p-1 bg-blue-50 hover:bg-blue-100 text-blue-600 border border-blue-200 rounded-md transition-colors cursor-pointer"
            title="新增智能体"
          >
            <Icon name="Plus" size={12} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
          {agents.map(a => {
            const isSelected = selectedAgentId === a.id;
            return (
              <div
                key={a.id}
                onClick={() => setSelectedAgentId(a.id)}
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1 ${
                  isSelected
                    ? `${styles.accentBg} text-white shadow-xs`
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-center gap-1.5 font-bold">
                  <span className={`p-1 rounded ${isSelected ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'}`}>
                    <Icon name={a.avatar} size={11} />
                  </span>
                  <span className="truncate">{a.name}</span>
                </div>
                <p className={`text-[10px] line-clamp-2 leading-relaxed ${styles.cardTextMuted}`}>
                  {a.role}
                </p>
                <div className={`flex items-center justify-between text-[9px] pt-1 mt-0.5 border-t ${styles.inputBorder}/10`}>
                  <span className={`font-mono ${styles.cardTextMuted}`}>{a.modelId.replace('-1.5-pro', '')}</span>
                  <span className="px-1 bg-blue-500/10 text-blue-500 rounded text-[8px] font-bold">ACTIVE</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 2. Central Agent Settings Config Editor */}
      {selectedAgent ? (
        <div className="flex-1 flex overflow-hidden">
          <div className={`flex-1 flex flex-col h-full ${styles.inputBg} overflow-y-auto p-5 space-y-4`}>
            
            {/* Agent Header */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs flex items-start justify-between`}>
              <div className="flex gap-3">
                <span className="p-3 rounded-xl bg-blue-100 text-blue-600 shrink-0">
                  <Icon name={selectedAgent.avatar} size={20} />
                </span>
                <div className="space-y-1">
                  <h2 className={`text-sm font-black ${styles.cardText}`}>{selectedAgent.name}</h2>
                  <p className="text-xs font-bold text-blue-600">{selectedAgent.role}</p>
                  <p className={`text-[11px] ${styles.cardTextMuted} max-w-lg leading-relaxed`}>{selectedAgent.description}</p>
                </div>
              </div>

              <div className="flex gap-2">
                <button
                  onClick={() => handleStartEdit(selectedAgent)}
                  className={`px-2.5 py-1.5 ${styles.appBg} hover:bg-slate-200 ${styles.cardTextMuted} border ${styles.cardBorder} rounded-lg transition-all cursor-pointer flex items-center gap-1`}
                >
                  <Icon name="Settings2" size={11} />
                  <span>管理智能体</span>
                </button>
                <button
                  onClick={() => handleDelete(selectedAgent.id)}
                  className="px-2.5 py-1.5 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-lg transition-all cursor-pointer flex items-center gap-1"
                >
                  <Icon name="XCircle" size={11} />
                  <span>注销</span>
                </button>
              </div>
            </div>

            {/* Config Panels */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              
              {/* Box 1: Prompt Guidelines */}
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
                <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
                  <Icon name="Sliders" size={12} className="text-blue-500" />
                  <span>系统角色指令 (System Persona)</span>
                </h3>
                <div className={`h-56 overflow-y-auto ${styles.inputBg} p-3 border ${styles.cardBorder} rounded-lg text-[11px] ${styles.cardTextMuted} font-sans leading-relaxed whitespace-pre-line`}>
                  {selectedAgent.systemPrompt}
                </div>
              </div>

              {/* Box 2: Tool Actions & Guardrails */}
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs flex flex-col justify-between space-y-4`}>
                
                <div className="space-y-3">
                  <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
                    <Icon name="Boxes" size={12} className="text-indigo-500" />
                    <span>挂载本体动作与函数能力 (Tools Plugin)</span>
                  </h3>
                  
                  <div className="space-y-2 max-h-32 overflow-y-auto">
                    {selectedAgent.assignedTools.actionIds.map(act => (
                      <div key={act} className="flex items-center gap-2 p-1.5 bg-amber-500/5 border border-amber-200/50 rounded-lg">
                        <span className="p-0.5 rounded bg-amber-100 text-amber-600">
                          <Icon name="Zap" size={10} />
                        </span>
                        <div className="flex-1">
                          <p className={`font-bold text-[10px] ${styles.cardText}`}>{act}</p>
                          <p className={`text-[9px] ${styles.cardTextMuted} font-mono`}>Ontology Action Write-Back</p>
                        </div>
                        <span className="px-1.5 bg-amber-500/10 text-amber-600 text-[8px] font-bold rounded">已提权</span>
                      </div>
                    ))}
                    {selectedAgent.assignedTools.functionIds.map(fn => (
                      <div key={fn} className="flex items-center gap-2 p-1.5 bg-blue-500/5 border border-blue-200/50 rounded-lg">
                        <span className="p-0.5 rounded bg-blue-100 text-blue-600">
                          <Icon name="Code" size={10} />
                        </span>
                        <div className="flex-1">
                          <p className={`font-bold text-[10px] ${styles.cardText}`}>{fn}</p>
                          <p className={`text-[9px] ${styles.cardTextMuted} font-mono`}>Ontology Function Query</p>
                        </div>
                        <span className="px-1.5 bg-blue-500/10 text-blue-600 text-[8px] font-bold rounded">只读</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className={`space-y-3 pt-3 border-t ${styles.cardBorder}`}>
                  <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
                    <Icon name="ShieldAlert" size={12} className="text-rose-500" />
                    <span>激活关联安全护栏 (Active Guardrails)</span>
                  </h3>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedAgent.guardrailIds.map(grid => {
                      const g = guardrails.find(x => x.id === grid);
                      return (
                        <span key={grid} className="px-2 py-1 bg-rose-50 border border-rose-200 text-rose-600 rounded-full font-bold text-[9px] flex items-center gap-1">
                          <span className="w-1 h-1 rounded-full bg-rose-600" />
                          <span>{g?.name || grid}</span>
                        </span>
                      );
                    })}
                  </div>
                </div>

              </div>

            </div>

          </div>

          {/* 3. Right: Live Sandbox Playground (Chat Box or Simulation Sandbox) */}
          <div className={`w-[450px] ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0`}>
            
            {/* Header with Tab Selectors */}
            <div className={`p-2 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between shrink-0`}>
              <div className="flex bg-slate-200/60 p-1 rounded-lg">
                <button
                  onClick={() => setSandboxMode('chat')}
                  className={`px-3 py-1.5 rounded-md font-bold text-[10px] transition-all cursor-pointer flex items-center gap-1 ${
                    sandboxMode === 'chat'
                      ? 'bg-white text-slate-800 shadow-xs'
                      : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  <Icon name="MessageSquare" size={10} />
                  <span>交互对话 (Chat)</span>
                </button>
                <button
                  onClick={() => setSandboxMode('simulation')}
                  className={`px-3 py-1.5 rounded-md font-bold text-[10px] transition-all cursor-pointer flex items-center gap-1 ${
                    sandboxMode === 'simulation'
                      ? `${styles.accentBg} text-white shadow-xs`
                      : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  <Icon name="ShieldAlert" size={10} className="text-amber-400" />
                  <span>推理干涉沙箱 (Sandbox)</span>
                </button>
              </div>

              {sandboxMode === 'chat' ? (
                <button
                  onClick={() => {
                    setChatMessages([
                      {
                        id: 'welcome',
                        sender: 'agent',
                        content: `对话控制台已重启。有什么我可以帮您的？`,
                        timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
                      }
                    ]);
                  }}
                  className={`p-1 ${styles.cardTextMuted} hover:${styles.cardTextMuted} cursor-pointer`}
                  title="清除聊天历史"
                >
                  <Icon name="RefreshCcw" size={11} />
                </button>
              ) : (
                <span className="px-2 py-0.5 bg-amber-500/10 text-amber-600 rounded text-[9px] font-black">
                  SIMULATOR v1.2
                </span>
              )}
            </div>

            {/* TAB 1: Chat Mode */}
            {sandboxMode === 'chat' && (
              <div className="flex-1 flex flex-col overflow-hidden">
                {/* Message Area */}
                <div className="flex-1 overflow-y-auto p-4 space-y-4">
                  {chatMessages.map(msg => {
                    const isUser = msg.sender === 'user';
                    const isSys = msg.sender === 'system';

                    if (isSys) {
                      return (
                        <div key={msg.id} className={`p-2.5 ${styles.appBg} rounded-lg text-[11px] ${styles.cardTextMuted} border ${styles.cardBorder}/50 leading-relaxed font-sans`}>
                          {msg.content}
                        </div>
                      );
                    }

                    return (
                      <div key={msg.id} className={`flex flex-col gap-1.5 ${isUser ? 'items-end' : 'items-start'}`}>
                        
                        {/* Message Head */}
                        <div className={`flex items-center gap-1.5 text-[9px] ${styles.cardTextMuted} font-mono`}>
                          {!isUser && <span className={`font-bold ${styles.cardTextMuted}`}>{selectedAgent.name}</span>}
                          <span>{msg.timestamp}</span>
                          {isUser && <span className="font-bold text-blue-600">You (签派总监)</span>}
                        </div>

                        {/* Chat Bubble */}
                        <div className={`p-3 rounded-2xl max-w-[85%] leading-relaxed whitespace-pre-wrap text-[11px] ${
                          isUser
                            ? 'bg-blue-600 text-white rounded-tr-none font-medium'
                            : 'bg-slate-100 text-slate-800 rounded-tl-none border border-slate-200/40'
                        }`}>
                          {msg.content}
                        </div>

                        {/* Embedded Reasoning Trace (Thinking Process) */}
                        {msg.thinkingTrace && msg.thinkingTrace.length > 0 && (
                          <div className={`w-[85%] ${styles.appBg} ${styles.cardTextMuted} rounded-lg p-2.5 font-mono text-[9px] space-y-1`}>
                            <span className="text-[8px] text-blue-400 uppercase font-extrabold block mb-1">AIP 逻辑链追踪 (AIP Trace):</span>
                            {msg.thinkingTrace.map((log, idx) => (
                              <div key={idx} className="flex items-start gap-1">
                                <span className={`${styles.cardTextMuted}`}>▶</span>
                                <span>{log}</span>
                              </div>
                            ))}
                          </div>
                        )}

                        {/* Embedded Action Consent Approval Card */}
                        {msg.actionProposal && msg.actionProposal.status === 'pending' && (
                          <div className="w-[85%] border-2 border-amber-400 bg-amber-50/50 rounded-xl p-3 space-y-2.5 shadow-sm">
                            <div className="flex items-center gap-2 font-bold text-amber-800 text-[10px] border-b border-amber-200 pb-1.5">
                              <span className="p-1 rounded bg-amber-100 text-amber-600">
                                <Icon name="ShieldAlert" size={11} className="animate-pulse" />
                              </span>
                              <span>{msg.actionProposal.actionName}</span>
                            </div>
                            
                            <div className={`space-y-1 font-mono text-[9px] ${styles.cardTextMuted}`}>
                              <div><span className={`font-bold ${styles.cardText}`}>目标航班 (flight_number):</span> {msg.actionProposal.payload.flight_number}</div>
                              <div><span className={`font-bold ${styles.cardText}`}>延误时长 (delay_minutes):</span> {msg.actionProposal.payload.delay_minutes} 分钟</div>
                              <div><span className={`font-bold ${styles.cardText}`}>执行指令 (new_status):</span> {msg.actionProposal.payload.new_status}</div>
                              <div className="text-[8px] text-rose-500 font-bold bg-rose-50 p-1 rounded mt-1">⚠️ 警告: 该操作将覆盖全局航空本体运行图，需签派总监密钥授权。</div>
                            </div>

                            <div className="flex gap-1.5 pt-1 border-t border-amber-200/50">
                              <button
                                onClick={() => handleActionConsent(msg.id, true)}
                                className="flex-1 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg text-[10px] transition-colors cursor-pointer flex items-center justify-center gap-1"
                              >
                                <Icon name="Check" size={10} />
                                <span>确认授权并写入</span>
                              </button>
                              <button
                                onClick={() => handleActionConsent(msg.id, false)}
                                className={`px-2.5 py-1.5 border ${styles.cardBorder} hover:${styles.inputBg} rounded-lg text-[10px] font-semibold ${styles.cardTextMuted} transition-colors cursor-pointer`}
                              >
                                <span>拒绝</span>
                              </button>
                            </div>
                          </div>
                        )}

                        {msg.actionProposal && msg.actionProposal.status === 'approved' && (
                          <div className={`w-[85%] ${styles.appBg} border border-emerald-300 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-emerald-700 font-semibold`}>
                            <span className="p-1 rounded bg-emerald-100 text-emerald-600">
                              <Icon name="CheckCircle2" size={12} />
                            </span>
                            <span>Ontology Action 已通过授权，写入完毕。</span>
                          </div>
                        )}

                        {msg.actionProposal && msg.actionProposal.status === 'rejected' && (
                          <div className={`w-[85%] ${styles.appBg} border border-red-200 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-red-600 font-semibold`}>
                            <span className="p-1 rounded bg-red-100 text-red-600">
                              <Icon name="XCircle" size={12} />
                            </span>
                            <span>操作已被安全护栏拦截丢弃。</span>
                          </div>
                        )}

                      </div>
                    );
                  })}

                  {isReplying && (
                    <div className="flex flex-col gap-1.5 items-start">
                      <div className={`flex items-center gap-1.5 text-[9px] ${styles.cardTextMuted} font-mono`}>
                        <span className={`font-bold ${styles.cardTextMuted}`}>{selectedAgent.name}</span>
                        <span>正在思考...</span>
                      </div>
                      <div className={`p-3 ${styles.appBg} rounded-2xl rounded-tl-none border ${styles.cardBorder}/40 flex items-center gap-1.5`}>
                        <span className="w-1.5 h-1.5 bg-slate-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                        <span className="w-1.5 h-1.5 bg-slate-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                        <span className="w-1.5 h-1.5 bg-slate-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                      </div>
                    </div>
                  )}
                </div>

                {/* Quick Prompts list */}
                <div className={`px-3 py-1.5 border-t ${styles.cardBorder} flex items-center gap-1.5 overflow-x-auto shrink-0 ${styles.appBg}`}>
                  {[
                    '查询 UA102 航班状态',
                    'UA102 出现异常怎么调配'
                  ].map(p => (
                    <button
                      key={p}
                      onClick={() => handleSendChat(p)}
                      className={`px-2.5 py-1 ${styles.cardBg} hover:bg-blue-50 hover:border-blue-200 border ${styles.cardBorder} rounded-full text-[10px] ${styles.cardTextMuted} font-medium whitespace-nowrap cursor-pointer transition-colors`}
                    >
                      {p}
                    </button>
                  ))}
                </div>

                {/* Input Bar */}
                <div className={`p-3 border-t ${styles.cardBorder} ${styles.cardBg} flex items-center gap-2 shrink-0`}>
                  <input
                    type="text"
                    placeholder="发送指令（可尝试询问：查询UA102航班）..."
                    value={chatInput}
                    onChange={e => setChatInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSendChat()}
                    className={`flex-1 h-8 px-3 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500`}
                  />
                  <button
                    onClick={() => handleSendChat()}
                    disabled={isReplying || !chatInput.trim()}
                    className="h-8 w-8 bg-blue-600 hover:bg-blue-700 text-white rounded-lg flex items-center justify-center cursor-pointer transition-colors disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
                  >
                    <Icon name="Send" size={13} />
                  </button>
                </div>
              </div>
            )}

            {/* TAB 2: Simulation Sandbox Mode */}
            {sandboxMode === 'simulation' && (
              <div className={`flex-1 flex flex-col overflow-hidden ${styles.inputBg}`}>
                
                {/* Form Inputs Panel */}
                <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} space-y-3 shrink-0`}>
                  <h3 className={`text-[11px] font-black ${styles.cardText} flex items-center gap-1`}>
                    <Icon name="SlidersHorizontal" size={12} className="text-blue-600" />
                    <span>仿真推理上下文设置 (Context Inputs)</span>
                  </h3>

                  <div className="grid grid-cols-2 gap-2 text-[10px]">
                    <div className="space-y-1">
                      <label className={`block ${styles.cardTextMuted} font-bold`}>模拟访问主体 (User)</label>
                      <select
                        value={simUserId}
                        onChange={e => setSimUserId(e.target.value)}
                        className={`w-full px-2 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-md font-medium ${styles.cardTextMuted}`}
                      >
                        <option value="analyst_li">李博士 (高级航空分析师)</option>
                        <option value="hr_manager">张主管 (航空人事主管)</option>
                        <option value="external_auditor">王凯 (外部独立审计员)</option>
                        <option value="EU_DPO">莫尼卡 (欧盟数据保护合规官)</option>
                        <option value="admin_guorong">郭荣 (首席安全官 CSO)</option>
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`block ${styles.cardTextMuted} font-bold`}>目标受控资产 (Dataset)</label>
                      <select
                        value={simDatasetId}
                        onChange={e => setSimDatasetId(e.target.value)}
                        className={`w-full px-2 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-md font-medium ${styles.cardTextMuted}`}
                      >
                        <option value="ds_pilots_biography">飞行员保密档案表 (Pilots)</option>
                        <option value="ds_flights_clean">航班实时编排表 (Flights)</option>
                        <option value="ds_ticket_sales">票务资金收益结算表 (Sales)</option>
                      </select>
                    </div>
                  </div>

                  {/* Natural Language Query Input */}
                  <div className="space-y-1">
                    <label className={`block text-[10px] ${styles.cardTextMuted} font-bold`}>提问词 / 推理目标 (Natural Language Query)</label>
                    <textarea
                      value={simQuery}
                      onChange={e => setSimQuery(e.target.value)}
                      placeholder="例如: 帮我列出飞行员档案里面的 SSN 号码和保底薪资..."
                      rows={2}
                      className={`w-full px-2.5 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg text-xs font-medium resize-none focus:outline-hidden focus:${styles.cardBorder}`}
                    />
                  </div>

                  {/* Suggestion tags */}
                  <div className="flex flex-wrap gap-1">
                    <span className={`text-[9px] ${styles.cardTextMuted} self-center font-bold mr-1`}>预设高危场景:</span>
                    <button
                      onClick={() => {
                        setSimUserId('analyst_li');
                        setSimDatasetId('ds_pilots_biography');
                        setSimQuery('查询李维民机长的身份证 SSN 社保号码');
                      }}
                      className="px-2 py-0.5 bg-red-50 hover:bg-red-100 border border-red-100 rounded text-[9px] text-red-700 font-bold"
                    >
                      SSN泄露拦截
                    </button>
                    <button
                      onClick={() => {
                        setSimUserId('EU_DPO');
                        setSimDatasetId('ds_pilots_biography');
                        setSimQuery('欧盟区域合规审计：检索飞行员李维民的授信与资质状况');
                      }}
                      className="px-2 py-0.5 bg-emerald-50 hover:bg-emerald-100 border border-emerald-100 rounded text-[9px] text-emerald-700 font-bold"
                    >
                      DPO合规特许
                    </button>
                    <button
                      onClick={() => {
                        setSimUserId('hr_manager');
                        setSimDatasetId('ds_flights_clean');
                        setSimQuery('展示目前所有的航班延误与疲劳时长数据');
                      }}
                      className="px-2 py-0.5 bg-blue-50 hover:bg-blue-100 border border-blue-100 rounded text-[9px] text-blue-700 font-bold"
                    >
                      常态数据检索
                    </button>
                  </div>

                  {/* Simulation Trigger Button */}
                  <button
                    onClick={handleRunSimulation}
                    disabled={isSimulating}
                    className={`w-full py-2 ${styles.appBg} hover:bg-slate-850 text-white rounded-lg font-black text-xs transition-all cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50`}
                  >
                    {isSimulating ? (
                      <>
                        <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                        <span>正在全链路对账仿真计算...</span>
                      </>
                    ) : (
                      <>
                        <Icon name="Play" size={11} className="fill-current text-emerald-400" />
                        <span>运行干涉仿真模拟 (Run Simulator)</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Simulation Output Area */}
                <div className="flex-1 overflow-y-auto p-4 space-y-4">
                  
                  {isSimulating && (
                    <div className={`flex flex-col items-center justify-center py-20 ${styles.cardTextMuted} space-y-3`}>
                      <Icon name="ShieldAlert" size={32} className="text-blue-500 animate-pulse" />
                      <div className="text-center space-y-1">
                        <p className={`font-extrabold ${styles.cardTextMuted}`}>正在评估零信任级联门禁...</p>
                        <p className="text-[10px]">Org IP Whitelist ➔ Project DAC ➔ Marking MAC ➔ Purpose PBAC</p>
                      </div>
                    </div>
                  )}

                  {!isSimulating && !simResult && (
                    <div className={`flex flex-col items-center justify-center py-24 ${styles.cardTextMuted} text-center space-y-2`}>
                      <Icon name="Tv" size={28} className={`${styles.cardTextMuted}`} />
                      <span className={`font-bold ${styles.cardTextMuted}`}>待执行仿真模拟</span>
                      <p className={`text-[10px] ${styles.cardTextMuted} max-w-xs`}>设置好上方的模拟角色和提问，点击运行，系统将追踪每一个决策节点的放行/阻断判定日志。</p>
                    </div>
                  )}

                  {!isSimulating && simResult && (
                    <div className="space-y-4">
                      
                      {/* Overall Status Banner */}
                      <div className={`p-3.5 rounded-xl border flex items-center justify-between ${
                        simResult.overallVerdict === 'GRANTED'
                          ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                          : 'bg-rose-50 border-rose-200 text-rose-800'
                      }`}>
                        <div className="flex items-center gap-2.5">
                          <span className={`p-1.5 rounded-lg shrink-0 ${
                            simResult.overallVerdict === 'GRANTED' ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'
                          }`}>
                            <Icon name={simResult.overallVerdict === 'GRANTED' ? 'CheckCircle2' : 'ShieldAlert'} size={18} />
                          </span>
                          <div>
                            <p className="font-black text-xs">决策大盘最终判定: {simResult.overallVerdict}</p>
                            <p className="text-[10px] opacity-80">全链路安全编译器已成功执行策略溯源与物理隔离</p>
                          </div>
                        </div>
                        <span className={`px-2.5 py-1 text-[10px] font-black rounded-md uppercase tracking-wider ${
                          simResult.overallVerdict === 'GRANTED'
                            ? 'bg-emerald-600 text-white shadow-xs'
                            : 'bg-rose-600 text-white shadow-xs'
                        }`}>
                          {simResult.overallVerdict === 'GRANTED' ? 'Passed' : 'Intercepted'}
                        </span>
                      </div>

                      {/* Nodes Section */}
                      <div className="space-y-3">
                        <h4 className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1`}>
                          <Icon name="Route" size={11} />
                          <span>全链路节点决策追踪 (Decision Traces Log)</span>
                        </h4>

                        {simResult.nodes.map((node: any) => {
                          const isExpanded = expandedNodes[node.id];
                          const nodePassed = node.verdict === 'GRANTED';
                          
                          return (
                            <div key={node.id} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
                              
                              {/* Node Title Header */}
                              <div
                                onClick={() => toggleNodeExpanded(node.id)}
                                className={`p-3 ${styles.inputBg} flex items-center justify-between cursor-pointer select-none border-b ${styles.cardBorder}`}
                              >
                                <div className="flex items-center gap-2">
                                  <span className={`p-1 rounded-md text-[9px] font-bold ${
                                    nodePassed ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                                  }`}>
                                    <Icon name={nodePassed ? 'Check' : 'X'} size={10} />
                                  </span>
                                  <span className={`font-extrabold ${styles.cardText} text-[11px]`}>{node.name}</span>
                                </div>

                                <div className="flex items-center gap-2">
                                  <span className={`px-1.5 py-0.5 text-[8px] font-bold rounded uppercase ${
                                    nodePassed ? 'bg-emerald-500/10 text-emerald-600' : 'bg-rose-500/10 text-rose-600'
                                  }`}>
                                    {node.verdict}
                                  </span>
                                  <Icon
                                    name={isExpanded ? 'ChevronDown' : 'ChevronRight'}
                                    size={12}
                                    className={`${styles.cardTextMuted}`}
                                  />
                                </div>
                              </div>

                              {/* Expanded Node Content */}
                              {isExpanded && (
                                <div className={`p-3 space-y-3 ${styles.cardBg} text-[10px]`}>
                                  
                                  {/* Node Trace logs */}
                                  <div className={`${styles.appBg} ${styles.cardTextMuted} p-2.5 rounded-lg font-mono text-[9px] leading-relaxed space-y-1`}>
                                    {node.traces.map((trace: string, tIdx: number) => {
                                      const isErr = trace.includes('❌') || trace.includes('FAIL') || trace.includes('被拒');
                                      const isAlert = trace.includes('⚠️');
                                      return (
                                        <div key={tIdx} className={`flex items-start gap-1 ${isErr ? 'text-rose-400' : isAlert ? 'text-amber-400' : ''}`}>
                                          <span className={`${styles.cardTextMuted} shrink-0`}>▶</span>
                                          <span>{trace}</span>
                                        </div>
                                      );
                                    })}
                                  </div>

                                  {/* Special Node 2 Visualization: RAG Documents and PII Redaction */}
                                  {node.id === 'node_rag_retrieval' && (
                                    <div className={`space-y-2 border-t ${styles.cardBorder} pt-2.5`}>
                                      <div className="flex items-center justify-between">
                                        <span className={`font-bold ${styles.cardTextMuted}`}>RAG Rerank 元数据检索载荷</span>
                                        {node.isMaskedEnforced && (
                                          <span className="px-2 py-0.5 bg-red-500 text-white rounded text-[8px] font-black animate-pulse flex items-center gap-0.5">
                                            <Icon name="Shield" size={8} />
                                            <span>正则屏蔽生效 (REGEX MASKED)</span>
                                          </span>
                                        )}
                                      </div>

                                      <div className={`p-2 ${styles.inputBg} border ${styles.cardBorder} rounded-lg text-[10px] ${styles.cardTextMuted} max-h-32 overflow-y-auto whitespace-pre-wrap font-mono`}>
                                        {node.groundedContext}
                                      </div>
                                    </div>
                                  )}

                                  {/* Special Node 3 Visualization: LLM Response */}
                                  {node.id === 'node_llm_inference' && (
                                    <div className={`space-y-2 border-t ${styles.cardBorder} pt-2.5`}>
                                      <span className={`font-bold ${styles.cardTextMuted} block`}>LLM 主权审计闭环答复 (Final Answer)</span>
                                      <div className={`p-3 ${styles.appBg} text-emerald-400 rounded-lg text-[10px] leading-relaxed whitespace-pre-wrap font-sans border ${styles.cardBorder}`}>
                                        {node.answer}
                                      </div>
                                    </div>
                                  )}

                                  {/* Special Node 4 Visualization: Data Firewall Table Rows */}
                                  {node.id === 'node_data_masking' && (
                                    <div className={`space-y-2 border-t ${styles.cardBorder} pt-2.5`}>
                                      <span className={`font-bold ${styles.cardTextMuted} block`}>行/列防火墙隔离后物理数据输出 (Masked Records)</span>
                                      {node.dataRows && node.dataRows.length > 0 ? (
                                        <div className={`p-2 ${styles.cardBg} ${styles.cardTextMuted} rounded-lg text-[9px] font-mono overflow-x-auto max-h-36`}>
                                          <pre className="leading-tight">{JSON.stringify(node.dataRows, null, 2)}</pre>
                                        </div>
                                      ) : (
                                        <p className="text-[10px] text-rose-500 font-bold bg-rose-50 p-1.5 rounded">
                                          ⚠️ 拦截警告：当前用户的安全密级不足，或者已被行级条件完全过滤。物理内存直接截断，输出 0 条记录。
                                        </p>
                                      )}
                                    </div>
                                  )}

                                </div>
                              )}

                            </div>
                          );
                        })}
                      </div>

                    </div>
                  )}

                </div>

              </div>
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
        <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden flex flex-col max-h-[90vh]`}>
            
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>
                {editingAgent ? '配置智能体核心参数' : '部署全新智能体'}
              </h3>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} cursor-pointer`}
              >
                <Icon name="X" size={15} />
              </button>
            </div>

            <form onSubmit={handleSave} className="flex-1 overflow-y-auto p-4 space-y-4">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>智能体名称 (Name) <span className="text-red-500">*</span></label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder="例如: 机场地面调度专家"
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  required
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>业务职责角色 (Role) <span className="text-red-500">*</span></label>
                <input
                  type="text"
                  value={formRole}
                  onChange={e => setFormRole(e.target.value)}
                  placeholder="例如: 机场廊桥与行李分发智能化调度管家"
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  required
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>简介描述 (Description)</label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder="说明该智能体的定位及服务群体"
                  rows={2}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none`}
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>挂载大语言模型 (Model) <span className="text-red-500">*</span></label>
                <select
                  value={formModel}
                  onChange={e => setFormModel(e.target.value)}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                >
                  {models.map(m => (
                    <option key={m.id} value={m.id}>{m.displayName}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>设定系统提示词 (System Instructions) <span className="text-red-500">*</span></label>
                <textarea
                  value={formPrompt}
                  onChange={e => setFormPrompt(e.target.value)}
                  placeholder="在此写入详细的 Persona、操作规范、CAAC 执照评定约束和工具调用流程..."
                  rows={4}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none font-sans leading-relaxed`}
                  required
                />
              </div>

              {/* Tools assignment */}
              <div className="space-y-1.5">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>提权挂载 Ontology 动作工具</label>
                <div className={`space-y-1 border ${styles.cardBorder} p-2 rounded-lg ${styles.appBg} max-h-24 overflow-y-auto`}>
                  {['act_reschedule_flight', 'act_assign_pilot'].map(tool => {
                    const isChecked = formTools.includes(tool);
                    return (
                      <label key={tool} className="flex items-center gap-2 cursor-pointer py-0.5">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => {
                            if (isChecked) {
                              setFormTools(formTools.filter(t => t !== tool));
                            } else {
                              setFormTools([...formTools, tool]);
                            }
                          }}
                          className="rounded text-blue-600 border-slate-300 h-3 w-3"
                        />
                        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{tool}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              {/* Safety Guardrails */}
              <div className="space-y-1.5">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>关联平台安全审计护栏</label>
                <div className={`space-y-1 border ${styles.cardBorder} p-2 rounded-lg ${styles.appBg} max-h-24 overflow-y-auto`}>
                  {guardrails.map(g => {
                    const isChecked = formGuardrails.includes(g.id);
                    return (
                      <label key={g.id} className="flex items-center gap-2 cursor-pointer py-0.5">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => {
                            if (isChecked) {
                              setFormGuardrails(formGuardrails.filter(gid => gid !== g.id));
                            } else {
                              setFormGuardrails([...formGuardrails, g.id]);
                            }
                          }}
                          className="rounded text-blue-600 border-slate-300 h-3 w-3"
                        />
                        <span className={`text-[10px] ${styles.cardTextMuted} font-bold`}>{g.name}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className={`pt-3 border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-[11px]"
                >
                  确认部署
                </button>
              </div>
            </form>

          </div>
        </div>
      )}

    </div>
  );
}
