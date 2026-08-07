/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { AIPAgent, AIPGuardrail } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface RAGDocument {
  id: string;
  name: string;
  type: string;
  size: string;
  chunksCount: number;
  status: 'synced' | 'pending';
  lastModified: string;
}

interface ConfigPanelProps {
  activeChatbot: AIPAgent;
  chatbotVersion: string;
  activeWorkspaceTab: string;
  setActiveWorkspaceTab: (tab: any) => void;
  // Prompt tab
  tempPrompt: string;
  setTempPrompt: (v: string) => void;
  tempTemperature: number;
  setTempTemperature: (v: number) => void;
  tempTopP: number;
  setTempTopP: (v: number) => void;
  tempMaxTokens: number;
  setTempMaxTokens: (v: number) => void;
  // Ontology tab
  selectedObjects: string[];
  setSelectedObjects: React.Dispatch<React.SetStateAction<string[]>>;
  selectedActions: string[];
  setSelectedActions: React.Dispatch<React.SetStateAction<string[]>>;
  selectedFunctions: string[];
  setSelectedFunctions: React.Dispatch<React.SetStateAction<string[]>>;
  // Guardrails tab
  selectedGuardrails: string[];
  setSelectedGuardrails: React.Dispatch<React.SetStateAction<string[]>>;
  guardrails: AIPGuardrail[];
  // RAG tab
  handleDragOver: (e: React.DragEvent) => void;
  handleDragLeave: () => void;
  handleDrop: (e: React.DragEvent) => void;
  handleFileSelect: (e: React.ChangeEvent<HTMLInputElement>) => void;
  dragOver: boolean;
  ragDocs: RAGDocument[];
  setRagDocs: React.Dispatch<React.SetStateAction<RAGDocument[]>>;
  isSyncingRAG: boolean;
  handleSyncRAG: () => void;
  ragLogs: string[];
  // Publish tab
  isPublishing: boolean;
  handlePublishChatbot: () => void;
  publishingLogs: string[];
  embedTab: string;
  setEmbedTab: React.Dispatch<React.SetStateAction<string>>;
  apiLang: string;
  setApiLang: React.Dispatch<React.SetStateAction<string>>;
  // Actions
  onEditConfig: () => void;
  onDelete: () => void;
  styles: Record<string, string>;
}

export default function ConfigPanel({
  activeChatbot,
  chatbotVersion,
  activeWorkspaceTab,
  setActiveWorkspaceTab,
  tempPrompt,
  setTempPrompt,
  tempTemperature,
  setTempTemperature,
  tempTopP,
  setTempTopP,
  tempMaxTokens,
  setTempMaxTokens,
  selectedObjects,
  setSelectedObjects,
  selectedActions,
  setSelectedActions,
  selectedFunctions,
  setSelectedFunctions,
  selectedGuardrails,
  setSelectedGuardrails,
  guardrails,
  handleDragOver,
  handleDragLeave,
  handleDrop,
  handleFileSelect,
  dragOver,
  ragDocs,
  setRagDocs,
  isSyncingRAG,
  handleSyncRAG,
  ragLogs,
  isPublishing,
  handlePublishChatbot,
  publishingLogs,
  embedTab,
  setEmbedTab,
  apiLang,
  setApiLang,
  onEditConfig,
  onDelete,
  styles,
}: ConfigPanelProps) {
  const { t } = useLanguage();

  return (
    <div className={`flex-1 flex flex-col border-r ${styles.cardBorder} h-full overflow-hidden ${styles.cardBg}`}>
      {/* Header of Active Workspace */}
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.appBg} flex flex-col gap-2 shrink-0`}>
        <div className="flex items-start justify-between gap-4">
          <div className="flex gap-2.5 items-center min-w-0">
            <span className={`p-2 rounded-lg ${styles.badgeBg} ${styles.accentText} shrink-0`}>
              <Icon name={activeChatbot.avatar} size={16} />
            </span>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className={`text-sm font-black ${styles.cardText} truncate`}>{activeChatbot.name}</h1>
                <span className={`px-1.5 py-0.5 ${styles.appBg} ${styles.cardTextMuted} text-[9px] font-mono font-bold rounded`}>
                  {chatbotVersion}
                </span>
              </div>
              <p className={`text-[10px] ${styles.cardTextMuted} font-medium truncate mt-0.5`}>{activeChatbot.role}</p>
            </div>
          </div>

          <div className="flex gap-1.5 shrink-0">
            <button
              onClick={onEditConfig}
              className={`p-1.5 ${styles.appBg} ${styles.accentHover} ${styles.cardTextMuted} border ${styles.cardBorder} rounded-lg font-bold transition-all cursor-pointer flex items-center justify-center`}
              title={t('aiworkbench.chatbot.configMetadata')}
            >
              <Icon name="Settings2" size={12} />
            </button>
            <button
              onClick={onDelete}
              className="p-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 rounded-lg font-bold transition-all cursor-pointer flex items-center justify-center"
              title={t('aiworkbench.chatbot.deleteAgent')}
            >
              <Icon name="Trash2" size={12} />
            </button>
          </div>
        </div>

        {/* Tabs list inside active workspace configuration */}
        <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} text-[10px] font-bold mt-2`}>
          {[
            { id: 'prompt', label: t('aiworkbench.chatbot.tabPrompt'), icon: 'Sliders' },
            { id: 'ontology', label: t('aiworkbench.chatbot.tabOntology'), icon: 'Workflow' },
            { id: 'knowledge', label: t('aiworkbench.chatbot.tabKnowledge'), icon: 'FileText' },
            { id: 'guardrails', label: t('aiworkbench.chatbot.tabGuardrails'), icon: 'ShieldAlert' },
            { id: 'publish', label: t('aiworkbench.chatbot.tabPublish'), icon: 'Send' }
          ].map(tab => {
            const isActive = activeWorkspaceTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveWorkspaceTab(tab.id as any)}
                className={`flex-1 py-1 px-2 rounded-md font-bold transition-all cursor-pointer flex items-center justify-center gap-1 ${
                  isActive ? `${styles.cardBg} ${styles.cardText} shadow-2xs` : `${styles.cardTextMuted} ${styles.accentHover}`
                }`}
              >
                <Icon name={tab.icon} size={11} />
                <span className="hidden lg:inline">{tab.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Workspace Column Dynamic Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* TAB 1: System prompt config */}
        {activeWorkspaceTab === 'prompt' && (
          <div className="space-y-4 font-sans">
            <div className="space-y-1.5">
              <label className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono`}>
                {t('aiworkbench.chatbot.systemInstructions')}
              </label>
              <textarea
                value={tempPrompt}
                onChange={(e) => setTempPrompt(e.target.value)}
                className={`w-full h-64 p-3 border ${styles.cardBorder} rounded-xl text-[11px] font-mono leading-relaxed ${styles.inputBg} ${styles.cardText} focus:${styles.cardBg} focus:ring-1 focus:ring-blue-500 focus:border-blue-500 outline-none`}
                placeholder={t('aiworkbench.chatbot.promptPlaceholder')}
              />
              <p className={`text-[9px] ${styles.cardTextMuted} leading-normal`}>
                {t('aiworkbench.chatbot.promptHint')}
              </p>
            </div>

            <div className={`${styles.inputBg} border ${styles.cardBorder} rounded-xl p-3.5 space-y-3`}>
              <h3 className={`font-bold ${styles.cardTextMuted} text-[11px] flex items-center gap-1`}>
                <Icon name="Settings" size={12} className={styles.accentText} />
                <span>{t('aiworkbench.chatbot.hyperparamsTitle')}</span>
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Temperature Slider */}
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center text-[10px]">
                    <span className={`${styles.cardTextMuted} font-bold`}>{t('aiworkbench.chatbot.temperature')}</span>
                    <span className={`font-mono ${styles.accentText} font-extrabold`}>{tempTemperature}</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={tempTemperature}
                    onChange={(e) => setTempTemperature(parseFloat(e.target.value))}
                    className={`w-full accent-blue-600 h-1 ${styles.inputBg} rounded-lg cursor-pointer`}
                  />
                  <span className={`text-[8px] ${styles.cardTextMuted} block`}>{t('aiworkbench.chatbot.tempHint')}</span>
                </div>

                {/* Top P Slider */}
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center text-[10px]">
                    <span className={`${styles.cardTextMuted} font-bold`}>{t('aiworkbench.chatbot.topP')}</span>
                    <span className={`font-mono ${styles.accentText} font-extrabold`}>{tempTopP}</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={tempTopP}
                    onChange={(e) => setTempTopP(parseFloat(e.target.value))}
                    className={`w-full accent-blue-600 h-1 ${styles.inputBg} rounded-lg cursor-pointer`}
                  />
                  <span className={`text-[8px] ${styles.cardTextMuted} block`}>{t('aiworkbench.chatbot.topPHint')}</span>
                </div>

                {/* Max Tokens */}
                <div className="space-y-1.5 md:col-span-2">
                  <div className="flex justify-between items-center text-[10px]">
                    <span className={`${styles.cardTextMuted} font-bold`}>{t('aiworkbench.chatbot.maxTokens')}</span>
                    <span className={`font-mono ${styles.accentText} font-extrabold`}>{tempMaxTokens}</span>
                  </div>
                  <input
                    type="range"
                    min="256"
                    max="8192"
                    step="256"
                    value={tempMaxTokens}
                    onChange={(e) => setTempMaxTokens(parseInt(e.target.value))}
                    className={`w-full accent-blue-600 h-1 ${styles.inputBg} rounded-lg cursor-pointer`}
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: Ontology bindings */}
        {activeWorkspaceTab === 'ontology' && (
          <div className="space-y-4">
            <div className={`p-3 ${styles.badgeBg} ${styles.accentText} rounded-xl border ${styles.accentBorder} flex items-start gap-2`}>
              <Icon name="Info" size={14} className="shrink-0 mt-0.5" />
              <p className="leading-relaxed">
                <strong>{t('aiworkbench.chatbot.ontologyProtocol')}</strong>{t('aiworkbench.chatbot.ontologyProtocolDesc')}
              </p>
            </div>

            {/* 1. Object Types */}
            <div className="space-y-2">
              <h3 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono flex items-center gap-1`}>
                <Icon name="Layers" size={11} className={styles.cardTextMuted} />
                <span>{t('aiworkbench.chatbot.objectTypeBindings')}</span>
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                {[
                  { id: 'AviationFlight', label: t('aiworkbench.chatbot.objFlight'), desc: t('aiworkbench.chatbot.objFlightDesc') },
                  { id: 'AviationPilot', label: t('aiworkbench.chatbot.objPilot'), desc: t('aiworkbench.chatbot.objPilotDesc') },
                  { id: 'AviationAirport', label: t('aiworkbench.chatbot.objAirport'), desc: t('aiworkbench.chatbot.objAirportDesc') },
                  { id: 'AviationEquipment', label: t('aiworkbench.chatbot.objEquipment'), desc: t('aiworkbench.chatbot.objEquipmentDesc') }
                ].map(obj => {
                  const isBound = selectedObjects.includes(obj.id);
                  return (
                    <div
                      key={obj.id}
                      onClick={() => {
                        setSelectedObjects(prev =>
                          prev.includes(obj.id) ? prev.filter(x => x !== obj.id) : [...prev, obj.id]
                        );
                      }}
                      className={`p-2.5 rounded-lg border cursor-pointer transition-all flex items-start gap-2.5 ${
                        isBound ? `${styles.badgeBg} ${styles.accentBorder}` : `${styles.cardBg} ${styles.cardBorder} hover:${styles.inputBg}`
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isBound}
                        onChange={() => {}}
                        className="mt-0.5 accent-blue-600 rounded"
                      />
                      <div className="flex flex-col">
                        <span className={`font-bold ${styles.cardText} text-[11px]`}>{obj.label}</span>
                        <span className={`text-[9px] ${styles.cardTextMuted} leading-tight mt-0.5`}>{obj.desc}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* 2. Action Types */}
            <div className="space-y-2">
              <h3 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono flex items-center gap-1`}>
                <Icon name="Settings" size={11} className={styles.cardTextMuted} />
                <span>{t('aiworkbench.chatbot.actionTypeBindings')}</span>
              </h3>
              <div className="space-y-2">
                {[
                  { id: 'act_reschedule_flight', name: t('aiworkbench.chatbot.actionReschedule'), desc: t('aiworkbench.chatbot.actionRescheduleDesc'), target: ' DDL: ds_flights_clean.status' },
                  { id: 'act_assign_pilot', name: t('aiworkbench.chatbot.actionAssignPilot'), desc: t('aiworkbench.chatbot.actionAssignPilotDesc'), target: ' DDL: ds_flights_clean.captain_id' }
                ].map(act => {
                  const isBound = selectedActions.includes(act.id);
                  return (
                    <div
                      key={act.id}
                      onClick={() => {
                        setSelectedActions(prev =>
                          prev.includes(act.id) ? prev.filter(x => x !== act.id) : [...prev, act.id]
                        );
                      }}
                      className={`p-3 rounded-lg border cursor-pointer transition-all flex items-start gap-3 ${
                        isBound ? 'bg-indigo-50/50 border-indigo-200' : `${styles.cardBg} ${styles.cardBorder} hover:${styles.inputBg}`
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isBound}
                        onChange={() => {}}
                        className="mt-0.5 accent-indigo-600 rounded"
                      />
                      <div className="flex-1 flex flex-col">
                        <div className="flex items-center justify-between">
                          <span className={`font-bold ${styles.cardText} text-[11px]`}>{act.name}</span>
                          <span className={`text-[9px] ${styles.appBg} ${styles.cardTextMuted} font-mono px-1 rounded`}>{act.target}</span>
                        </div>
                        <p className={`text-[9px] ${styles.cardTextMuted} mt-1 leading-normal`}>{act.desc}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: RAG / Document Source upload */}
        {activeWorkspaceTab === 'knowledge' && (
          <div className="space-y-4 font-sans">
            {/* Upload Drop Zone */}
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              className={`border-2 border-dashed rounded-2xl p-6 flex flex-col items-center justify-center transition-all cursor-pointer ${
                dragOver
                  ? `border-blue-500 ${styles.badgeBg}/40`
                  : `${styles.inputBorder} ${styles.appBg}/40 hover:${styles.inputBg}`
              }`}
            >
              <input
                type="file"
                multiple
                id="file-upload"
                className="hidden"
                onChange={handleFileSelect}
              />
              <label htmlFor="file-upload" className="flex flex-col items-center cursor-pointer space-y-2">
                <Icon name="UploadCloud" size={32} className={styles.cardTextMuted} />
                <span className={`font-bold ${styles.cardTextMuted} text-xs text-center`}>{t('aiworkbench.chatbot.dragUpload')}</span>
                <span className={`text-[10px] ${styles.cardTextMuted} text-center`}>{t('aiworkbench.chatbot.uploadHint')}</span>
              </label>
            </div>

            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <h4 className={`font-extrabold ${styles.cardTextMuted} text-[11px] uppercase tracking-wider font-mono`}>
                {t('aiworkbench.chatbot.mountedDocs')} ({ragDocs.length})
              </h4>
              <button
                onClick={handleSyncRAG}
                disabled={isSyncingRAG || !ragDocs.some(d => d.status === 'pending')}
                className={`px-3 py-1 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg transition-all text-[10px] flex items-center gap-1 cursor-pointer ${
                  isSyncingRAG || !ragDocs.some(d => d.status === 'pending') ? 'opacity-40 cursor-not-allowed' : ''
                }`}
              >
                {isSyncingRAG ? (
                  <>
                    <span className={`w-3 h-3 border ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                    <span>{t('aiworkbench.chatbot.vectorAligning')}</span>
                  </>
                ) : (
                  <>
                    <Icon name="RefreshCcw" size={10} />
                    <span>{t('aiworkbench.chatbot.startChunkAlign')}</span>
                  </>
                )}
              </button>
            </div>

            {/* Document List */}
            <div className="space-y-1.5 max-h-56 overflow-y-auto">
              {ragDocs.map(doc => (
                <div key={doc.id} className={`p-2 ${styles.cardBg} border ${styles.cardBorder}/80 hover:${styles.inputBg} rounded-lg flex items-center justify-between gap-4 transition-colors`}>
                  <div className="flex items-center gap-2.5 min-w-0">
                    <span className={`p-1.5 rounded ${doc.type === 'PDF' ? 'bg-red-50 text-red-500' : `${styles.inputBg} ${styles.cardTextMuted}`}`}>
                      <Icon name="FileText" size={12} />
                    </span>
                    <div className="min-w-0">
                      <p className={`font-bold ${styles.cardText} truncate text-[11px]`}>{doc.name}</p>
                      <p className={`text-[9px] ${styles.cardTextMuted} flex items-center gap-2 mt-0.5`}>
                        <span>{t('aiworkbench.chatbot.docSize')} {doc.size}</span>
                        <span>•</span>
                        <span>{t('aiworkbench.chatbot.docChunks')} {doc.chunksCount > 0 ? `${doc.chunksCount} chunks` : t('aiworkbench.chatbot.pendingExtract')}</span>
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {doc.status === 'synced' ? (
                      <span className="text-emerald-600 font-extrabold text-[9px] flex items-center gap-1.5 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-100">
                        <span className="w-1 h-1 rounded-full bg-emerald-500" />
                        <span>{t('aiworkbench.chatbot.alignSuccess')}</span>
                      </span>
                    ) : (
                      <span className="text-amber-600 font-extrabold text-[9px] flex items-center gap-1.5 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-100 animate-pulse">
                        <span className="w-1 h-1 rounded-full bg-amber-500" />
                        <span>{t('aiworkbench.chatbot.waitVectorize')}</span>
                      </span>
                    )}
                    <button
                      onClick={() => setRagDocs(prev => prev.filter(x => x.id !== doc.id))}
                      className={`p-1 ${styles.cardTextMuted} hover:text-red-500 rounded-md transition-colors cursor-pointer`}
                      title={t('aiworkbench.chatbot.unmountFile')}
                    >
                      <Icon name="X" size={11} />
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Log Console */}
            {ragLogs.length > 0 && (
              <div className={`${styles.cardBg} p-3 rounded-xl shadow-xs border ${styles.cardBorder} flex flex-col space-y-2`}>
                <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-1.5`}>
                  <span className={`font-mono ${styles.cardText} text-[9px] font-bold`}>RAG Pipeline Vectorization Stream</span>
                  <span className={`text-[8px] ${styles.cardTextMuted} font-mono`}>text-embedding-004</span>
                </div>
                <div className={`space-y-1 font-mono text-[8.5px] ${styles.cardTextMuted} leading-normal max-h-24 overflow-y-auto`}>
                  {ragLogs.map((log, idx) => (
                    <p key={idx} className={log.includes('✅') ? 'text-emerald-400 font-bold' : styles.cardTextMuted}>
                      {log}
                    </p>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 4: Safety guardrails */}
        {activeWorkspaceTab === 'guardrails' && (
          <div className="space-y-4">
            <div className={`p-3 ${styles.badgeBg} ${styles.accentText} rounded-xl border ${styles.accentBorder} flex items-start gap-2`}>
              <Icon name="ShieldAlert" size={14} className="shrink-0 mt-0.5" />
              <p className="leading-relaxed text-[11px]">
                <strong>{t('aiworkbench.chatbot.guardrailsInfo')}</strong> {t('aiworkbench.chatbot.guardrailsInfoDesc')}
              </p>
            </div>

            <div className="space-y-2">
              {guardrails.map(gr => {
                const isEnabled = selectedGuardrails.includes(gr.id);
                return (
                  <div
                    key={gr.id}
                    onClick={() => {
                      setSelectedGuardrails(prev =>
                        prev.includes(gr.id) ? prev.filter(x => x !== gr.id) : [...prev, gr.id]
                      );
                    }}
                    className={`p-3.5 rounded-xl border cursor-pointer transition-all flex items-start justify-between gap-4 ${
                      isEnabled ? `${styles.badgeBg} ${styles.accentBorder} shadow-xs` : `${styles.cardBg} ${styles.cardBorder} hover:${styles.inputBg}`
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <span className={`p-1.5 rounded-lg shrink-0 mt-0.5 ${isEnabled ? `${styles.badgeBg} ${styles.accentText}` : `${styles.inputBg} ${styles.cardTextMuted}`}`}>
                        <Icon name={gr.type === 'pii_redaction' ? 'EyeOff' : gr.type === 'human_approval' ? 'KeyRound' : 'FileCheck'} size={14} />
                      </span>
                      <div className="space-y-1">
                        <h4 className={`font-extrabold ${styles.cardText} text-[11px]`}>{gr.name}</h4>
                        <p className={`text-[10px] ${styles.cardTextMuted} leading-normal font-sans`}>{gr.description}</p>
                        {gr.type === 'pii_redaction' && (
                          <div className="flex flex-wrap gap-1 pt-1.5">
                            {gr.parameters.piiTypes?.map(t => (
                              <span key={t} className={`px-1.5 py-0.2 ${styles.appBg} border ${styles.cardBorder} rounded text-[8px] font-mono font-bold ${styles.cardTextMuted}`}>
                                PII: {t}
                              </span>
                            ))}
                          </div>
                        )}
                        {gr.type === 'human_approval' && (
                          <div className="flex flex-wrap gap-1 pt-1.5">
                            {gr.parameters.requiredActionIds?.map(a => (
                              <span key={a} className={`px-1.5 py-0.2 ${styles.badgeBg} ${styles.accentBorder} rounded text-[8px] font-mono font-bold ${styles.accentText}`}>
                                ACTION: {a}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="flex flex-col items-end gap-2 shrink-0">
                      <span className={`px-2 py-0.5 rounded-full text-[8px] font-extrabold uppercase ${
                        gr.severity === 'block' ? 'bg-rose-50 border border-rose-200 text-rose-600' :
                        gr.severity === 'warn' ? 'bg-amber-50 border border-amber-200 text-amber-600' :
                        `${styles.appBg} border ${styles.cardBorder} ${styles.cardTextMuted}`
                      }`}>
                        {gr.severity === 'block' ? t('aiworkbench.chatbot.severityBlock') : gr.severity === 'warn' ? t('aiworkbench.chatbot.severityWarn') : t('aiworkbench.chatbot.severityAudit')}
                      </span>

                      {/* Toggle Switch */}
                      <div className={`w-8 h-4.5 rounded-full p-0.5 transition-all ${isEnabled ? styles.accentBg : `${styles.inputBorder}`}`}>
                        <div className={`w-3.5 h-3.5 rounded-full styles.cardBg transition-all transform ${isEnabled ? 'translate-x-3.5' : 'translate-x-0'}`} />
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* TAB 5: Publishing & Deployment options */}
        {activeWorkspaceTab === 'publish' && (
          <div className="space-y-4 font-sans">
            {/* Compile & Publish zone */}
            <div className={`p-4 ${styles.accentBg} rounded-2xl text-white space-y-4 relative overflow-hidden shadow-md`}>
              <div className="absolute right-0 bottom-0 opacity-10 transform translate-x-4 translate-y-4 scale-150">
                <Icon name="Send" size={120} />
              </div>
              <div className="space-y-1">
                <span className="px-1.5 py-0.5 bg-blue-500 text-white rounded text-[8px] font-extrabold uppercase">
                  Sovereign Compiler v2.4
                </span>
                <h3 className="font-extrabold text-sm">{t('aiworkbench.chatbot.publishTitle')}</h3>
                <p className={`text-[10px] ${styles.cardText}`}>
                  {t('aiworkbench.chatbot.publishDesc')}
                </p>
              </div>
              <button
                onClick={handlePublishChatbot}
                disabled={isPublishing}
                className={`px-4 py-2 styles.cardBg hover:styles.appBg styles.cardText font-extrabold rounded-xl text-xs transition-all flex items-center gap-1.5 cursor-pointer shadow-sm ${
                  isPublishing ? 'opacity-70 cursor-not-allowed' : ''
                }`}
              >
                {isPublishing ? (
                  <>
                    <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                    <span>{t('aiworkbench.chatbot.publishCompiling')}</span>
                  </>
                ) : (
                  <>
                    <Icon name="Cpu" size={13} />
                    <span>{t('aiworkbench.chatbot.publishBtn')}</span>
                  </>
                )}
              </button>
            </div>

            {/* Publishing Console logs */}
            {publishingLogs.length > 0 && (
              <div className={`${styles.cardBg} p-3.5 rounded-xl border ${styles.cardBorder} ${styles.cardTextMuted} font-mono text-[9px] space-y-1.5`}>
                <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-1.5`}>
                  <span className={`${styles.cardText} font-bold`}>AIP Copilot Compiler Logs</span>
                  <span className={`text-[8px] ${styles.cardTextMuted}`}>v1.0.5</span>
                </div>
                <div className="space-y-1 max-h-32 overflow-y-auto leading-relaxed">
                  {publishingLogs.map((log, i) => (
                    <p key={i} className={log.includes('🎉') || log.includes('✅') ? 'text-emerald-400 font-bold' : styles.cardTextMuted}>
                      {log}
                    </p>
                  ))}
                </div>
              </div>
            )}

            {/* Integration Snippets */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
              <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
                <h4 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                  <Icon name="Code" size={13} className={styles.accentText} />
                  <span>{t('aiworkbench.chatbot.integrationChannels')}</span>
                </h4>
              </div>

              <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} text-[9px] font-bold`}>
                <button
                  onClick={() => setEmbedTab('iframe')}
                  className={`flex-1 py-1 rounded-md transition-all cursor-pointer ${embedTab === 'iframe' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : styles.cardTextMuted}`}
                >
                  {t('aiworkbench.chatbot.embedIframe')}
                </button>
                <button
                  onClick={() => setEmbedTab('web-component')}
                  className={`flex-1 py-1 rounded-md transition-all cursor-pointer ${embedTab === 'web-component' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : styles.cardTextMuted}`}
                >
                  {t('aiworkbench.chatbot.embedWebComponent')}
                </button>
                <button
                  onClick={() => setEmbedTab('widget-json')}
                  className={`flex-1 py-1 rounded-md transition-all cursor-pointer ${embedTab === 'widget-json' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : styles.cardTextMuted}`}
                >
                  {t('aiworkbench.chatbot.embedWidgetJson')}
                </button>
              </div>

              {embedTab === 'iframe' && (
                <div className="space-y-2">
                  <p className={`text-[10px] ${styles.cardTextMuted} leading-normal`}>
                    {t('aiworkbench.chatbot.embedIframeDesc')}
                  </p>
                  <pre className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg text-[9px] font-mono leading-relaxed ${styles.cardTextMuted} overflow-x-auto select-all`}>
                    {`<iframe \n  src="https://ECOS_AIP_GATEWAY/chatbot/embed/${activeChatbot.id}?auth=sso_jwt"\n  width="100%" \n  height="600px" \n  style="border: none; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);"\n  allow="clipboard-write"\n/>`}
                  </pre>
                </div>
              )}

              {embedTab === 'web-component' && (
                <div className="space-y-2">
                  <p className={`text-[10px] ${styles.cardTextMuted} leading-normal`}>
                    {t('aiworkbench.chatbot.embedWcDesc')}
                  </p>
                  <pre className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg text-[9px] font-mono leading-relaxed ${styles.cardTextMuted} overflow-x-auto select-all`}>
                    {`${t('aiworkbench.chatbot.embedWcComment1')}\n<script src="https://ECOS_AIP_GATEWAY/cdn/aip-widgets-v2.js" async></script>\n\n${t('aiworkbench.chatbot.embedWcComment2')}\n<aip-chatbot-button \n  chatbot-id="${activeChatbot.id}" \n  theme="cosmic-slate"\n  user-role="${t('aiworkbench.chatbot.proposalUserRole')}"\n  data-context="all_ontology"\n  placeholder="${t('aiworkbench.chatbot.wcPlaceholder')}"\n/>`}
                  </pre>
                </div>
              )}

              {embedTab === 'widget-json' && (
                <div className="space-y-2">
                  <p className={`text-[10px] ${styles.cardTextMuted} leading-normal`}>
                    {t('aiworkbench.chatbot.embedJsonDesc')}
                  </p>
                  <pre className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg text-[9px] font-mono leading-relaxed ${styles.cardTextMuted} overflow-x-auto select-all`}>
                    {`{\n  "widgetType": "AIP_CHATBOT_STUDIO",\n  "widgetId": "${activeChatbot.id}",\n  "chatbotName": "${activeChatbot.name}",\n  "engineModel": "${activeChatbot.modelId}",\n  "temperature": ${tempTemperature},\n  "bindings": {\n    "objects": ${JSON.stringify(selectedObjects)},\n    "actions": ${JSON.stringify(selectedActions)},\n    "functions": ${JSON.stringify(selectedFunctions)}\n  },\n  "security": {\n    "guardrails": ${JSON.stringify(selectedGuardrails)},\n    "roleLock": "AOC_DIRECTOR"\n  }\n}`}
                  </pre>
                </div>
              )}

              {/* API Code snippets */}
              <div className={`border-t ${styles.cardBorder} pt-3 mt-1.5 space-y-2`}>
                <div className="flex items-center justify-between">
                  <span className={`text-[11px] font-bold ${styles.cardTextMuted}`}>{t('aiworkbench.chatbot.apiIntegration')}</span>
                  <div className="flex gap-1">
                    <button
                      onClick={() => setApiLang('curl')}
                      className={`px-1.5 py-0.5 text-[8px] font-bold rounded ${apiLang === 'curl' ? `${styles.accentBg} text-white` : `${styles.inputBg} ${styles.cardTextMuted}`}`}
                    >
                      cURL
                    </button>
                    <button
                      onClick={() => setApiLang('typescript')}
                      className={`px-1.5 py-0.5 text-[8px] font-bold rounded ${apiLang === 'typescript' ? `${styles.accentBg} text-white` : `${styles.inputBg} ${styles.cardTextMuted}`}`}
                    >
                      TypeScript
                    </button>
                  </div>
                </div>

                {apiLang === 'curl' ? (
                  <pre className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg text-[9px] font-mono leading-relaxed ${styles.cardTextMuted} overflow-x-auto select-all`}>
                    {`curl -X POST "https://ECOS_AIP_GATEWAY/api/chatbot/${activeChatbot.id}/query" \\\n  -H "Authorization: Bearer ***" \\\n  -H "Content-Type: application/json" \\\n  -d '{\n    "query": "${t('aiworkbench.chatbot.apiExampleQuery')}",\n    "userRole": "AOC_DIRECTOR",\n    "stream": false\n  }'`}
                  </pre>
                ) : (
                  <pre className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg text-[9px] font-mono leading-relaxed ${styles.cardTextMuted} overflow-x-auto select-all`}>
                    {`import { AIPChatbotClient } from '@ecos/aip-sdk';\n\nconst client = new AIPChatbotClient({\n  apiKey: ***  gatewayUrl: 'https://ECOS_AIP_GATEWAY'\n});\n\nasync function runInference() {\n  const response = await client.chatbot.query({\n    chatbotId: '${activeChatbot.id}',\n    query: '${t('aiworkbench.chatbot.apiExampleQuery2')}',\n    userRole: 'AOC_DIRECTOR'\n  });\n  \n  console.log('Chatbot Reply:', response.content);\n  console.log('Thinking CoT:', response.thinkingTrace);\n}`}
                  </pre>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
