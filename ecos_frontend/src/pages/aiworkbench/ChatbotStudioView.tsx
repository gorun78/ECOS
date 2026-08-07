/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-19: ChatbotStudioView — 纯组合层 <200行
 */
import React, { useState, useEffect } from 'react';
import { AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import ChatHeader from '../../components/aiworkbench/chatbot/ChatHeader';
import ConfigPanel from '../../components/aiworkbench/chatbot/ConfigPanel';
import ChatPanel from '../../components/aiworkbench/chatbot/ChatPanel';

interface ChatMessage {
  id: string; sender: 'user' | 'agent' | 'system'; content: string; timestamp: string;
  thinkingTrace?: string[];
  actionProposal?: { id?: string; actionId: string; actionName: string; payload: Record<string, string>; status: 'pending' | 'approved' | 'rejected' };
}
interface RAGDocument { id: string; name: string; type: string; size: string; chunksCount: number; status: 'synced' | 'pending'; lastModified: string; }

export default function ChatbotStudioView({ agents, models, guardrails, onUpdateAgents, onAddAuditLog, showToast }: {
  agents: AIPAgent[]; models: AIPModel[]; guardrails: AIPGuardrail[]; onUpdateAgents: (u: AIPAgent[]) => void; onAddAuditLog: (l: AIPAuditLog) => void; showToast?: (t: 'success'|'info'|'error', m: string) => void;
}) {
  const { styles } = useTheme(); const { t } = useLanguage();
  const [selectedAgentId, setSelectedAgentId] = useState<string>(agents[0]?.id || '');
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
  const [selectedGuardrails, setSelectedGuardrails] = useState<string[]>(['gr-pii','gr-approval','gr-hallucination']);
  const [ragDocs, setRagDocs] = useState<RAGDocument[]>([
    { id:'doc-1', name:'CAAC_AOC_Safety_Rules_v4.pdf', type:t('aiworkbench.chatbot.docTypePDF'), size:'2.4 MB', chunksCount:142, status:'synced', lastModified:'2026-07-02 11:20' },
    { id:'doc-2', name:'SOP_Emergency_Reschedule_Guide.md', type:'Markdown', size:'124 KB', chunksCount:45, status:'synced', lastModified:'2026-07-01 10:45' },
    { id:'doc-3', name:'Airline_Crew_FAQ_List.txt', type:t('aiworkbench.chatbot.docTypeText'), size:'48 KB', chunksCount:18, status:'synced', lastModified:'2026-06-28 14:15' },
  ]);
  const [dragOver, setDragOver] = useState(false);
  const [isSyncingRAG, setIsSyncingRAG] = useState(false);
  const [ragLogs, setRagLogs] = useState<string[]>([]);
  const [isPublishing, setIsPublishing] = useState(false);
  const [publishingLogs, setPublishingLogs] = useState<string[]>([]);
  const [chatbotVersion, setChatbotVersion] = useState<string>('v1.0.4');
  const [embedTab, setEmbedTab] = useState<'iframe'|'web-component'|'widget-json'>('iframe');
  const [apiLang, setApiLang] = useState<'typescript'|'curl'>('curl');
  const [showMetadataModal, setShowMetadataModal] = useState(false);
  const [isCreatingNew, setIsCreatingNew] = useState(false);

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
    const nd: RAGDocument[] = files.map((f,i) => ({ id:`doc-add-${Date.now()}-${i}`, name:f.name,
      type:f.name.split('.').pop()?.toUpperCase()||'DOCUMENT', size:`${(f.size/(1024*1024)).toFixed(2)} MB`,
      chunksCount:0, status:'pending', lastModified:new Date().toISOString().replace('T',' ').substring(0,16) }));
    setRagDocs(p=>[...p,...nd]); showToast?.('info', t('aiworkbench.chatbot.toastFilesAdded').replace('{count}', String(files.length)));
  };

  const hSync = () => { setIsSyncingRAG(true); setRagLogs([t('aiworkbench.chatbot.ragLogStart')]);
    const logs = [t('aiworkbench.chatbot.ragLogRead'),t('aiworkbench.chatbot.ragLogExtract'),t('aiworkbench.chatbot.ragLogChunk'),t('aiworkbench.chatbot.ragLogEmbed'),t('aiworkbench.chatbot.ragLogInject'),t('aiworkbench.chatbot.ragLogComplete')];
    let i=0; const iv=setInterval(()=>{if(i<logs.length){setRagLogs(p=>[...p,logs[i]]);i++;}else{clearInterval(iv);setRagDocs(p=>p.map(d=>({...d,status:'synced',chunksCount:d.chunksCount||Math.floor(Math.random()*80)+15})));setIsSyncingRAG(false);showToast?.('success',t('aiworkbench.chatbot.toastRagSyncSuccess'));}},700);
  };

  const hPub = () => { setIsPublishing(true); setPublishingLogs([t('aiworkbench.chatbot.pubLogStart')]);
    const steps=[t('aiworkbench.chatbot.pubLogVerify'),t('aiworkbench.chatbot.pubLogCompile'),t('aiworkbench.chatbot.pubLogTest'),t('aiworkbench.chatbot.pubLogPackage'),t('aiworkbench.chatbot.pubLogRegister'),t('aiworkbench.chatbot.pubLogDeploy')];
    let i=0; const iv=setInterval(()=>{if(i<steps.length){setPublishingLogs(p=>[...p,steps[i]]);i++;}else{clearInterval(iv);setIsPublishing(false);setChatbotVersion('v1.0.5');
      if(activeChatbot) onUpdateAgents(agents.map(a=>a.id===activeChatbot.id?{...a,name:tempName,role:tempRole,description:tempDesc,modelId:tempModel,systemPrompt:tempPrompt,guardrailIds:selectedGuardrails,assignedTools:{actionIds:selectedActions,functionIds:selectedFunctions}}:a));
      onAddAuditLog({ id:`log-pub-${Date.now()}`, timestamp:new Date().toISOString().replace('T',' ').substring(0,19), source:'Chatbot Studio', assetName:tempName, user:t('aiworkbench.chatbot.auditUser'), inputTokens:0,outputTokens:0,status:'allowed', actionTaken:t('aiworkbench.chatbot.auditPublishSuccess'), details:t('aiworkbench.chatbot.auditPublishDetails').replace('{objects}',selectedObjects.join(',')).replace('{actions}',selectedActions.join(',')) });
      showToast?.('success',t('aiworkbench.chatbot.toastPublishSuccess').replace('{name}',tempName));}},600);
  };

  const hSend = (txt?: string) => { const text = txt||chatInput; if(!text.trim()||isReplying||!activeChatbot) return;
    const um: ChatMessage = { id:`user-${Date.now()}`, sender:'user', content:text, timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}) };
    setChatMessages(p=>[...p,um]); setChatInput(''); setIsReplying(true);
    setTimeout(() => { let rc=''; let tt:string[]=[]; let pp:ChatMessage['actionProposal']; const ql=text.toLowerCase();
      if(activeUserRole==='EXTERNAL_CONTRACTOR'){ tt=[t('aiworkbench.chatbot.thinkingCheckRole'),t('aiworkbench.chatbot.thinkingContractorBlock'),t('aiworkbench.chatbot.thinkingDefenseRule')]; rc=t('aiworkbench.chatbot.simContractorBlock'); }
      else if(ql.includes(t('aiworkbench.chatbot.queryKeyword').toLowerCase())||ql.includes('ua102')){ tt=[t('aiworkbench.chatbot.thinkingExtractQuery'),t('aiworkbench.chatbot.thinkingRagSearch'),t('aiworkbench.chatbot.thinkingPhysicalQuery'),t('aiworkbench.chatbot.thinkingAssembleCard'),t('aiworkbench.chatbot.thinkingPiiGuard')]; const pm=selectedGuardrails.includes('gr-pii'); rc=t('aiworkbench.chatbot.simUa102Query').replace('{ssnValue}',pm?t('aiworkbench.chatbot.ssnRedacted'):t('aiworkbench.chatbot.ssnReal')).replace('{payrollValue}',pm?t('aiworkbench.chatbot.salaryRedacted'):t('aiworkbench.chatbot.salaryReal')); }
      else if(ql.includes(t('aiworkbench.chatbot.delayKeyword').toLowerCase())||ql.includes(t('aiworkbench.chatbot.rescheduleKeyword').toLowerCase())||ql.includes(t('aiworkbench.chatbot.hourKeyword').toLowerCase())||ql.includes('reschedule')){ tt=[t('aiworkbench.chatbot.thinkingRescheduleRequest'),t('aiworkbench.chatbot.thinkingIdentifyAction'),t('aiworkbench.chatbot.thinkingValidateParams'),t('aiworkbench.chatbot.thinkingApprovalGuard'),t('aiworkbench.chatbot.thinkingPendingProposal')]; rc=t('aiworkbench.chatbot.simRescheduleProposal'); pp={actionId:'act_reschedule_flight',actionName:t('aiworkbench.chatbot.actionReschedule'),payload:{flight_number:'UA102',new_status:'DELAYED',delay_minutes:'120',auth_required_by:'AOC_DIRECTOR'},status:'pending'}; }
      else if(ql.includes('ssn')||ql.includes(t('aiworkbench.chatbot.salaryKeyword').toLowerCase())||ql.includes(t('aiworkbench.chatbot.salaryKeyword2').toLowerCase())||ql.includes(t('aiworkbench.chatbot.idCardKeyword').toLowerCase())){ const pm=selectedGuardrails.includes('gr-pii'); tt=[t('aiworkbench.chatbot.thinkingSsnDetect'),t('aiworkbench.chatbot.thinkingPiiMatch').replace('{enabled}',String(pm))]; rc=pm?t('aiworkbench.chatbot.simPiiMasked'):t('aiworkbench.chatbot.simPiiUnmasked'); }
      else { tt=[t('aiworkbench.chatbot.thinkingGenericIntent'),t('aiworkbench.chatbot.thinkingParseRag')]; rc=t('aiworkbench.chatbot.simGenericReply').replace('{name}',activeChatbot.name); }
      if(pp) fetch('/api/v1/ontology/proposals',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({actionId:pp.actionId,actionName:pp.actionName,agentId:activeChatbot.id,agentName:activeChatbot.name,payload:pp.payload,proposedBy:t('aiworkbench.chatbot.proposedBy').replace('{name}',activeChatbot.name)})}).then(r=>r.json()).then(d=>{if(d.success&&d.proposal)pp!.id=d.proposal.id;}).catch(e=>console.error(e));
      setChatMessages(p=>[...p,{id:`agent-${Date.now()}`,sender:'agent',content:rc,timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}),thinkingTrace:tt,actionProposal:pp}]); setIsReplying(false); },1500);
  };

  const hConsent = (mid:string,ok:boolean) => { const tm=chatMessages.find(m=>m.id===mid); if(!tm?.actionProposal)return; const pid=tm.actionProposal.id||'prop-1';
    if(ok) fetch(`/api/v1/ontology/proposals/${pid}/execute`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({userRole:t('aiworkbench.chatbot.proposalUserRole'),userName:t('aiworkbench.chatbot.proposalUserName')})}).then(r=>r.json()).then(d=>{if(d.success){showToast?.('success',t('aiworkbench.chatbot.toastWritebackSuccess'));setChatMessages(p=>p.map(m=>m.id===mid&&m.actionProposal?{...m,actionProposal:{...m.actionProposal,status:'approved' as const}}:m));onAddAuditLog({id:`log-exec-${Date.now()}`,timestamp:new Date().toISOString().replace('T',' ').substring(0,19),source:'Ontology Engine',assetName:activeChatbot?.name||'AIP Chatbot',user:t('aiworkbench.chatbot.auditUser'),inputTokens:0,outputTokens:250,status:'allowed',actionTaken:t('aiworkbench.chatbot.auditReconciliationSuccess'),details:t('aiworkbench.chatbot.auditExecDetails')});const ci=d.verificationMatrix?.map((m:any)=>t('aiworkbench.chatbot.verificationItem').replace('{logicalField}',m.logicalField).replace('{physicalCol}',m.physicalCol).replace('{expectedValue}',m.expectedValue).replace('{readbackValue}',m.readbackValue)).join('\n')||'';setChatMessages(p=>[...p,{id:`sys-report-${Date.now()}`,sender:'system',content:t('aiworkbench.chatbot.sysReport').replace('{executionDetail}',d.executionDetail).replace('{transactionHash}',d.transactionHash||'TX_AOC_823901').replace('{checkItems}',ci),timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}]);}else showToast?.('error',t('aiworkbench.chatbot.toastExecFailed').replace('{message}',d.message||d.error));}).catch(e=>{console.error(e);showToast?.('error',t('aiworkbench.chatbot.toastExecError'));});
    else { showToast?.('info',t('aiworkbench.chatbot.toastRejectAction')); setChatMessages(p=>p.map(m=>m.id===mid&&m.actionProposal?{...m,actionProposal:{...m.actionProposal,status:'rejected' as const}}:m)); }
  };

  const hSaveMeta = (e:React.FormEvent) => { e.preventDefault(); if(!tempName.trim()||!tempRole.trim())return;
    if(isCreatingNew){ const na:AIPAgent={id:`agent-${Date.now().toString().slice(-4)}`,name:tempName.trim(),role:tempRole.trim(),description:tempDesc.trim(),avatar:'Bot',modelId:tempModel,systemPrompt:tempPrompt.trim(),assignedTools:{actionIds:selectedActions,functionIds:selectedFunctions},guardrailIds:selectedGuardrails,status:'active',lastModified:new Date().toISOString().replace('T',' ').substring(0,16)}; onUpdateAgents([...agents,na]); setSelectedAgentId(na.id); showToast?.('success',t('aiworkbench.chatbot.toastCreateSuccess')); }
    else if(activeChatbot){ onUpdateAgents(agents.map(a=>a.id===activeChatbot.id?{...a,name:tempName.trim(),role:tempRole.trim(),description:tempDesc.trim(),modelId:tempModel,systemPrompt:tempPrompt.trim(),guardrailIds:selectedGuardrails,assignedTools:{actionIds:selectedActions,functionIds:selectedFunctions},lastModified:new Date().toISOString().replace('T',' ').substring(0,16)}:a)); showToast?.('success',t('aiworkbench.chatbot.toastUpdateSuccess')); }
    setShowMetadataModal(false);
  };

  const hCreate = () => { setIsCreatingNew(true); setTempName(''); setTempRole(t('aiworkbench.chatbot.defaultRole')); setTempDesc(t('aiworkbench.chatbot.defaultDesc')); setTempModel('gemini-1.5-pro'); setTempPrompt(t('aiworkbench.chatbot.defaultPrompt')); setSelectedObjects(['AviationFlight','AviationPilot']); setSelectedActions(['act_reschedule_flight']); setSelectedFunctions(['func_get_flight_weather']); setSelectedGuardrails(['gr-pii','gr-approval']); setShowMetadataModal(true); };

  const hDelete = (id:string) => { if(!window.confirm(t('aiworkbench.chatbot.confirmDelete')))return; const u=agents.filter(a=>a.id!==id); onUpdateAgents(u); if(selectedAgentId===id&&u.length>0)setSelectedAgentId(u[0].id); showToast?.('success',t('aiworkbench.chatbot.toastDeleteSuccess')); };

  const hReset = () => { if(activeChatbot){ setChatMessages([{id:'welcome-reset',sender:'agent',content:t('aiworkbench.chatbot.resetWelcome').replace('{name}',activeChatbot.name),timestamp:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})}]); showToast?.('info',t('aiworkbench.chatbot.toastClearHistory')); }};

  return (
    <div className={`h-full w-full flex flex-col md:flex-row overflow-hidden ${styles.appBg} ${styles.appText} text-xs select-none`}>
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
            dragOver={dragOver} ragDocs={ragDocs} setRagDocs={setRagDocs} isSyncingRAG={isSyncingRAG} handleSyncRAG={hSync} ragLogs={ragLogs}
            isPublishing={isPublishing} handlePublishChatbot={hPub} publishingLogs={publishingLogs} embedTab={embedTab} setEmbedTab={setEmbedTab}
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
    </div>
  );
}
