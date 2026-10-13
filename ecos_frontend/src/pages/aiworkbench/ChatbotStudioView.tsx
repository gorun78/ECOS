/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-19: ChatbotStudioView — 纯组合层
 *
 * §4.5（2026-10-13 全页检视报告 E 组）假真理止血：
 * - 回复：setTimeout 罐头关键词分支已删除 → 真实 POST /api/v1/agent-loop/chat；
 *   引擎未返回 content → 系统错误气泡，不合成回复/thinkingTrace/actionProposal。
 * - RAG 同步 / 发布：后端零通道（无对应端点）→ ConfigPanel 控件禁用 +
 *   「能力未上线」DemoBadge，不再 setInterval 播罐头日志后翻成功。
 * - 知识库：预置 3 条假文档（含 142 chunks）已删除；初始为空，上传仅入本地
 *   待同步暂存列表（不假装已入库）。
 * - 执行回执：假交易哈希 'TX_AOC_823901' fallback 已删除；只渲染真实 VO 字段，
 *   未上报字段显式标注「后端未上报」。
 * - CRUD：创建/编辑/删除接真实 /api/v1/agent-mesh/agents（ecos_agent_registry 持久化）。
 */
import React, { useState, useEffect } from 'react';
import { AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import ChatHeader from '../../components/aiworkbench/chatbot/ChatHeader';
import ConfigPanel from '../../components/aiworkbench/chatbot/ConfigPanel';
import ChatPanel from '../../components/aiworkbench/chatbot/ChatPanel';
import { agentLoopChat, createMeshAgent, updateMeshAgent, deleteMeshAgent, executeOntologyProposal } from './api';

interface ChatMessage {
  id: string; sender: 'user' | 'agent' | 'system'; content: string; timestamp: string;
  thinkingTrace?: string[];
  actionProposal?: { id?: string; actionId: string; actionName: string; payload: Record<string, string>; status: 'pending' | 'approved' | 'rejected' };
}
// W204/C186：chunksCount 可选——后端/本地无真实回传时保持 undefined，界面渲染"待同步/未知"语义，禁补零禁合成
interface RAGDocument { id: string; name: string; type: string; size: string; chunksCount?: number; status: 'synced' | 'pending'; lastModified: string; }

export default function ChatbotStudioView({ agents, models, guardrails, onUpdateAgents, onAddAuditLog, showToast }: {
  agents: AIPAgent[]; models: AIPModel[]; guardrails: AIPGuardrail[]; onUpdateAgents: (u: AIPAgent[]) => void; onAddAuditLog: (l: AIPAuditLog) => void; showToast?: (t: 'success'|'info'|'error', m: string) => void;
}) {
  const { styles } = useTheme(); const { t } = useLanguage();
  // §4.5 P1：agents 异步到达前不定型 —— 空串起步，列表到达后回填首个 id
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  useEffect(() => {
    if (!selectedAgentId && agents.length > 0) setSelectedAgentId(agents[0].id);
  }, [agents, selectedAgentId]);
  const activeChatbot = agents.find(a => a.id === selectedAgentId);
  const [activeWorkspaceTab, setActiveWorkspaceTab] = useState<'prompt'|'ontology'|'knowledge'|'guardrails'|'publish'>('prompt');
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState(''); const [isReplying, setIsReplying] = useState(false);
  const [activeUserRole, setActiveUserRole] = useState<'AOC_DIRECTOR'|'EXTERNAL_CONTRACTOR'>('AOC_DIRECTOR');
  const [activeContextDataset, setActiveContextDataset] = useState<string>('all_ontology');
  const [tempName, setTempName] = useState(''); const [tempRole, setTempRole] = useState('');
  const [tempDesc, setTempDesc] = useState(''); const [tempModel, setTempModel] = useState('');
  const [tempPrompt, setTempPrompt] = useState('');
  const [tempTemperature, setTempTemperature] = useState(0.4);
  const [tempTopP, setTempTopP] = useState(0.9);
  const [tempMaxTokens, setTempMaxTokens] = useState(2048);
  const [selectedObjects, setSelectedObjects] = useState<string[]>(['AviationFlight','AviationPilot']);
  const [selectedActions, setSelectedActions] = useState<string[]>(['act_reschedule_flight']);
  const [selectedFunctions, setSelectedFunctions] = useState<string[]>(['func_get_flight_weather']);
  const [selectedGuardrails, setSelectedGuardrails] = useState<string[]>([]);
  // §4.5 P0：预置假知识库文档已清除；仅承载用户本地上传的待同步暂存项（同步能力未上线）
  const [ragDocs, setRagDocs] = useState<RAGDocument[]>([]);
  const [dragOver, setDragOver] = useState(false);
  // §4.5 P0：发布无后端通道 → 版本恒为「未发布」，不再 v1.0.4→v1.0.5 硬跳
  const [chatbotVersion] = useState<string>(() => t('aiworkbench.chatbot.versionUnpublished'));
  const [embedTab, setEmbedTab] = useState<'iframe'|'web-component'|'widget-json'>('iframe');
  const [apiLang, setApiLang] = useState<'typescript'|'curl'>('curl');
  const [showMetadataModal, setShowMetadataModal] = useState(false);
  const [isCreatingNew, setIsCreatingNew] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => { if (activeChatbot) {
    setTempName(activeChatbot.name); setTempRole(activeChatbot.role); setTempDesc(activeChatbot.description);
    setTempModel(activeChatbot.modelId); setTempPrompt(activeChatbot.systemPrompt);
    setSelectedObjects(['AviationFlight','AviationPilot']);
    setSelectedActions([...activeChatbot.assignedTools.actionIds]);
    setSelectedFunctions([...activeChatbot.assignedTools.functionIds]);
    setSelectedGuardrails([...activeChatbot.guardrailIds]);
    setChatMessages([{ id:'welcome', sender:'agent',
      content: t('aiworkbench.chatbot.welcomeMessage').replace(/\{name\}/g, activeChatbot.name).replace('{guardrailCount}', String(activeChatbot.guardrailIds.length)),
      timestamp: new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' }) }]);
  }}, [selectedAgentId]);

  const hfo = (e:React.DragEvent) => { e.preventDefault(); setDragOver(true); };
  const hfl = () => setDragOver(false);
  const hfd = (e:React.DragEvent) => { e.preventDefault(); setDragOver(false); const fs = Array.from(e.dataTransfer.files); if(fs.length) hfa(fs); };
  const hfs = (e:React.ChangeEvent<HTMLInputElement>) => { const fs = Array.from(e.target.files||[]); if(fs.length) hfa(fs); };
  const hfa = (files: File[]) => {
    // W204/C186：新加文档不预设 chunksCount（无后端回传=未知，界面走"待同步/未知"渲染分支）
    const nd: RAGDocument[] = files.map((f,i) => ({ id:`doc-add-${Date.now()}-${i}`, name:f.name,
      type:f.name.split('.').pop()?.toUpperCase()||'DOCUMENT', size:`${(f.size/(1024*1024)).toFixed(2)} MB`,
      status:'pending', lastModified:new Date().toISOString().replace('T',' ').substring(0,16) }));
    setRagDocs(p=>[...p,...nd]); showToast?.('info', t('aiworkbench.chatbot.toastFilesAddedStaged').replace('{count}', String(files.length)));
  };

  // ── 真实推理通道：POST /api/v1/agent-loop/chat（§4.5 P0：罐头回复已删除）──
  const hSend = async (txt?: string) => {
    const text = txt || chatInput;
    if (!text.trim() || isReplying || !activeChatbot) return;
    const um: ChatMessage = { id:`user-${Date.now()}`, sender:'user', content:text, timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}) };
    setChatMessages(p=>[...p,um]); setChatInput(''); setIsReplying(true);
    const ts = new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' });
    try {
      const data = await agentLoopChat({ agentId: activeChatbot.id, message: text });
      if (!data || data.success === false || typeof data.content !== 'string' || !data.content) {
        // §2.3 诚实态：引擎未返回有效 content → 错误气泡，不合成回复/思考链/提案
        const reason = data?.errorMsg ? `${t('aiworkbench.chatbot.chat.engineFail')}: ${data.errorMsg}` : t('aiworkbench.chatbot.chat.engineFail');
        setChatMessages(p=>[...p,{ id:`sys-${Date.now()}`, sender:'system', content:reason, timestamp:ts }]);
        return;
      }
      // 思考链仅来自引擎真实 toolCalls 上报；无上报则不展示（不合成）
      const thinkingTrace = Array.isArray(data.toolCalls) && data.toolCalls.length > 0
        ? data.toolCalls.map(tc => `${String(tc.toolName ?? 'tool')}: ${tc.success ? 'OK' : 'FAIL'} (${tc.durationMs ?? '-'}ms)`)
        : undefined;
      setChatMessages(p=>[...p,{ id:`agent-${Date.now()}`, sender:'agent', content:data.content, timestamp:ts, thinkingTrace }]);
    } catch (e: unknown) {
      const msg = e instanceof Error && e.message ? e.message : String(e);
      setChatMessages(p=>[...p,{ id:`sys-${Date.now()}`, sender:'system', content:`${t('aiworkbench.chatbot.chat.engineFail')} (${msg})`, timestamp:ts }]);
    } finally {
      setIsReplying(false);
    }
  };

  // ── 提案确认：仅执行真实已注册提案；回执仅渲染后端实有字段（无假 TX 哈希）──
  const hConsent = async (mid:string, ok:boolean) => {
    const tm = chatMessages.find(m => m.id === mid);
    if (!tm?.actionProposal) return;
    if (!ok) {
      showToast?.('info', t('aiworkbench.chatbot.toastRejectAction'));
      setChatMessages(p => p.map(m => m.id===mid && m.actionProposal ? {...m, actionProposal:{...m.actionProposal, status:'rejected' as const}} : m));
      return;
    }
    const pid = tm.actionProposal.id;
    if (!pid) {
      // §4.5：'prop-1' 假 id fallback 已删除——未注册的提案不得演"执行成功"
      showToast?.('error', t('aiworkbench.chatbot.toastProposalUnregistered'));
      return;
    }
    try {
      const vo = await executeOntologyProposal(pid, {
        userRole: t('aiworkbench.chatbot.proposalUserRole'),
        userName: t('aiworkbench.chatbot.proposalUserName'),
      });
      setChatMessages(p => p.map(m => m.id===mid && m.actionProposal ? {...m, actionProposal:{...m.actionProposal, status:'approved' as const}} : m));
      const notReported = t('aiworkbench.chatbot.receiptNotReported');
      const ci = Array.isArray(vo.verificationMatrix) && vo.verificationMatrix.length > 0
        ? vo.verificationMatrix.map(m => t('aiworkbench.chatbot.verificationItem')
            .replace('{logicalField}', String(m.logicalField ?? '-'))
            .replace('{physicalCol}', String(m.physicalCol ?? '-'))
            .replace('{expectedValue}', String(m.expectedValue ?? '-'))
            .replace('{readbackValue}', String(m.readbackValue ?? '-'))).join('\n')
        : notReported;
      setChatMessages(p=>[...p,{ id:`sys-report-${Date.now()}`, sender:'system',
        content: t('aiworkbench.chatbot.sysReport')
          .replace('{executionDetail}', vo.executionDetail ?? notReported)
          .replace('{transactionHash}', vo.transactionHash ?? notReported)
          .replace('{checkItems}', ci),
        timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}) }]);
      onAddAuditLog({ id:`log-exec-${Date.now()}`, timestamp:new Date().toISOString().replace('T',' ').substring(0,19), source:'Ontology Engine',
        assetName:activeChatbot?.name||'Chatbot', user:t('aiworkbench.chatbot.auditUser'), inputTokens:0, outputTokens:0, status:'allowed',
        actionTaken:t('aiworkbench.chatbot.auditReconciliationSuccess'), details:t('aiworkbench.chatbot.auditExecDetails').replace('{proposalId}', pid),
        localOrigin:true });
      showToast?.('success', t('aiworkbench.chatbot.toastWritebackSuccess'));
    } catch (e: unknown) {
      const msg = e instanceof Error && e.message ? e.message : String(e);
      showToast?.('error', t('aiworkbench.chatbot.toastExecFailed').replace('{message}', msg));
    }
  };

  // ── CRUD 真实通道：/api/v1/agent-mesh/agents（gateway 重写 → AgentMeshController）──
  const hSaveMeta = async (e:React.FormEvent) => {
    e.preventDefault();
    if (!tempName.trim() || !tempRole.trim() || isSaving) return;
    const payload: Partial<AIPAgent> = {
      name:tempName.trim(), role:tempRole.trim(), description:tempDesc.trim(), modelId:tempModel,
      systemPrompt:tempPrompt.trim(), guardrailIds:selectedGuardrails,
      assignedTools:{ actionIds:selectedActions, functionIds:selectedFunctions },
    };
    setIsSaving(true);
    try {
      if (isCreatingNew) {
        const created = await createMeshAgent({ ...payload, status:'active' });
        onUpdateAgents([...agents, created]); setSelectedAgentId(created.id);
        showToast?.('success', t('aiworkbench.chatbot.toastCreateSuccess'));
      } else if (activeChatbot) {
        const updated = await updateMeshAgent(activeChatbot.id, { ...payload, status:activeChatbot.status });
        onUpdateAgents(agents.map(a => a.id===activeChatbot.id ? updated : a));
        showToast?.('success', t('aiworkbench.chatbot.toastUpdateSuccess'));
      }
      setShowMetadataModal(false);
    } catch (e2: unknown) {
      const msg = e2 instanceof Error && e2.message ? e2.message : String(e2);
      showToast?.('error', t('aiworkbench.chatbot.toastSaveFailed').replace('{message}', msg));
    } finally { setIsSaving(false); }
  };

  const hCreate = () => { setIsCreatingNew(true); setTempName(''); setTempRole(t('aiworkbench.chatbot.defaultRole')); setTempDesc(t('aiworkbench.chatbot.defaultDesc')); setTempModel('gemini-1.5-pro'); setTempPrompt(t('aiworkbench.chatbot.defaultPrompt')); setSelectedObjects(['AviationFlight','AviationPilot']); setSelectedActions(['act_reschedule_flight']); setSelectedFunctions(['func_get_flight_weather']); setSelectedGuardrails([]); setShowMetadataModal(true); };

  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);

  const hDelete = (id: string) => setPendingDeleteId(id);

  const runDelete = async () => {
    const id = pendingDeleteId;
    setPendingDeleteId(null);
    if (!id) return;
    try {
      await deleteMeshAgent(id);
      const u = agents.filter(a => a.id !== id);
      onUpdateAgents(u);
      if (selectedAgentId === id) setSelectedAgentId(u[0]?.id || '');
      showToast?.('success', t('aiworkbench.chatbot.toastDeleteSuccess'));
    } catch (e: unknown) {
      const msg = e instanceof Error && e.message ? e.message : String(e);
      showToast?.('error', t('aiworkbench.chatbot.toastDeleteFailed').replace('{message}', msg));
    }
  };

  const hReset = () => { if(activeChatbot){ setChatMessages([{id:'welcome-reset',sender:'agent',content:t('aiworkbench.chatbot.resetWelcome').replace('{name}',activeChatbot.name),timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}]); showToast?.('info',t('aiworkbench.chatbot.toastClearHistory')); }};

  return (
    <div className={`h-full w-full flex flex-col md:flex-row overflow-hidden ${styles.appBg} ${styles.appText} text-xs`}>
      <ChatHeader agents={agents} selectedAgentId={selectedAgentId} onSelectAgent={setSelectedAgentId} onStartCreate={hCreate}
        activeChatbot={activeChatbot} chatbotVersion={chatbotVersion} chatMessages={chatMessages}
        activeUserRole={activeUserRole} setActiveUserRole={setActiveUserRole} activeContextDataset={activeContextDataset} setActiveContextDataset={setActiveContextDataset}
        onResetChat={hReset} showToast={showToast} styles={styles} />
      {activeChatbot ? (
        <div className={`flex-1 flex flex-col md:flex-row overflow-hidden ${styles.inputBg}`}>
          <ConfigPanel activeChatbot={activeChatbot} chatbotVersion={chatbotVersion} activeWorkspaceTab={activeWorkspaceTab} setActiveWorkspaceTab={setActiveWorkspaceTab}
            tempPrompt={tempPrompt} setTempPrompt={setTempPrompt} tempTemperature={tempTemperature} setTempTemperature={setTempTemperature}
            tempTopP={tempTopP} setTempTopP={setTempTopP} tempMaxTokens={tempMaxTokens} setTempMaxTokens={setTempMaxTokens}
            selectedObjects={selectedObjects} setSelectedObjects={setSelectedObjects} selectedActions={selectedActions} setSelectedActions={setSelectedActions}
            selectedFunctions={selectedFunctions} setSelectedFunctions={setSelectedFunctions} selectedGuardrails={selectedGuardrails} setSelectedGuardrails={setSelectedGuardrails}
            guardrails={guardrails} handleDragOver={hfo} handleDragLeave={hfl} handleDrop={hfd} handleFileSelect={hfs}
            dragOver={dragOver} ragDocs={ragDocs} setRagDocs={setRagDocs}
            embedTab={embedTab} setEmbedTab={setEmbedTab}
            apiLang={apiLang} setApiLang={setApiLang} onEditConfig={()=>setShowMetadataModal(!showMetadataModal)} onDelete={()=>hDelete(activeChatbot.id)} styles={styles} />
          <ChatPanel activeChatbot={activeChatbot} chatMessages={chatMessages} chatInput={chatInput} setChatInput={setChatInput}
            isReplying={isReplying} onSend={hSend} onProposalConsent={hConsent} onResetChat={hReset}
            activeUserRole={activeUserRole} setActiveUserRole={setActiveUserRole} activeContextDataset={activeContextDataset} setActiveContextDataset={setActiveContextDataset}
            showToast={showToast} styles={styles} />
        </div>
      ) : (
        <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted} text-sm`}>
          {t('aiworkbench.chatbot.welcomeSelectAgent')}
        </div>
      )}
      <ConfirmDialog
        visible={!!pendingDeleteId}
        danger
        title={t('aiworkbench.chatbot.confirmDeleteTitle')}
        message={t('aiworkbench.chatbot.confirmDelete')}
        confirmText={t('common.confirm')}
        cancelText={t('common.cancel')}
        onConfirm={() => void runDelete()}
        onCancel={() => setPendingDeleteId(null)}
      />
    </div>
  );
}
