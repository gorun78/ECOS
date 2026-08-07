/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { AIPAgent, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { authHeaders, convertMeshAgentToAIP } from '../../services/aiworkbenchApi';
import type { AgentMeshAgentRaw } from '../../services/aiworkbenchApi';
import type { ChatMessage } from '../../components/aiworkbench/agent-studio/AgentToolPanel';

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

export function useAgentStudio(
  agents: AIPAgent[],
  guardrails: AIPGuardrail[],
  onUpdateAgents: (updated: AIPAgent[]) => void,
  onAddAuditLog: (log: AIPAuditLog) => void,
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void,
) {
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
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (data.success) {
        setSimResult(data);
        showToast?.('success', '推理干涉仿真模拟完成！决策追踪链路已刷新。');
      } else {
        throw new Error(data.error || '仿真执行失败');
      }
    } catch (err) {
      console.warn('[AgentStudio] simulation-sandbox unavailable, using local fallback', err);
      setSimResult(buildMockSimulationResult(simUserId, simDatasetId, simQuery));
      showToast?.('info', '仿真引擎离线，已切换至本地沙箱推演模式');
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

  return {
    selectedAgentId,
    setSelectedAgentId,
    chatMessages,
    setChatMessages,
    chatInput,
    setChatInput,
    isReplying,
    showCreateModal,
    setShowCreateModal,
    editingAgent,
    formName,
    setFormName,
    formRole,
    setFormRole,
    formDesc,
    setFormDesc,
    formModel,
    setFormModel,
    formPrompt,
    setFormPrompt,
    formTools,
    setFormTools,
    formGuardrails,
    setFormGuardrails,
    sandboxMode,
    setSandboxMode,
    simUserId,
    setSimUserId,
    simDatasetId,
    setSimDatasetId,
    simQuery,
    setSimQuery,
    isSimulating,
    simResult,
    expandedNodes,
    toggleNodeExpanded,
    handleRunSimulation,
    selectedAgent,
    handleStartCreate,
    handleStartEdit,
    handleDelete,
    handleSave,
    handleSendChat,
    handleActionConsent,
  };
}
