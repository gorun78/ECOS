/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { AIPAgent, AIPModel, AIPGuardrail } from '../../../types/aiworkbench';
import type { ThemeStyles } from '../../ThemeContext';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface AgentListProps {
  agents: AIPAgent[];
  selectedAgentId: string;
  onSelectAgent: (id: string) => void;
  onStartCreate: () => void;
  showCreateModal: boolean;
  onCloseModal: () => void;
  editingAgent: AIPAgent | null;
  formName: string;
  setFormName: (v: string) => void;
  formRole: string;
  setFormRole: (v: string) => void;
  formDesc: string;
  setFormDesc: (v: string) => void;
  formModel: string;
  setFormModel: (v: string) => void;
  formPrompt: string;
  setFormPrompt: (v: string) => void;
  formTools: string[];
  setFormTools: (v: string[]) => void;
  formGuardrails: string[];
  setFormGuardrails: (v: string[]) => void;
  models: AIPModel[];
  guardrails: AIPGuardrail[];
  onStartEdit: (a: AIPAgent) => void;
  onSave: (e: React.FormEvent) => void;
  styles: ThemeStyles;
}

export default function AgentList({
  agents,
  selectedAgentId,
  onSelectAgent,
  onStartCreate,
  showCreateModal,
  onCloseModal,
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
  models,
  guardrails,
  onStartEdit,
  onSave,
  styles,
}: AgentListProps) {
  const { t } = useLanguage();
  return (
    <>
      {/* Left Agents List */}
      <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
        <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
          <span className={`font-bold ${styles.cardText}`}>{t("aiworkbench.agentStudio.agentWorkshop")} ({agents.length})</span>
          <button
            onClick={onStartCreate}
            className={`p-1 ${styles.badgeBg} hover:opacity-80 ${styles.accentText} ${styles.accentBorder} border rounded-md transition-colors cursor-pointer`}
            title={t("aiworkbench.agentStudio.newAgent")}
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
                onClick={() => onSelectAgent(a.id)}
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1 ${
                  isSelected
                    ? `${styles.accentBg} text-white shadow-xs`
                    : `${styles.cardTextMuted} hover:${styles.inputBg}`
                }`}
              >
                <div className="flex items-center gap-1.5 font-bold">
                  <span className={`p-1 rounded ${isSelected ? 'bg-blue-600 text-white' : `${styles.inputBg} ${styles.cardTextMuted}`}`}>
                    <Icon name={a.avatar} size={11} />
                  </span>
                  <span className="truncate">{a.name}</span>
                </div>
                <p className={`text-[10px] line-clamp-2 leading-relaxed ${styles.cardTextMuted}`}>
                  {a.role}
                </p>
                <div className={`flex items-center justify-between text-[9px] pt-1 mt-0.5 border-t ${styles.inputBorder}/10`}>
                  <span className={`font-mono ${styles.cardTextMuted}`}>{a.modelId.replace('-1.5-pro', '')}</span>
                  <span className={`px-1 ${styles.badgeBg} ${styles.accentText} rounded text-[8px] font-bold`}>ACTIVE</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Create / Edit Agent Modal */}
      {showCreateModal && (
        <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden flex flex-col max-h-[90vh]`}>
            
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>
                {editingAgent ? t("aiworkbench.agentStudio.configureAgent") : t("aiworkbench.agentStudio.deployNewAgent")}
              </h3>
              <button
                type="button"
                onClick={onCloseModal}
                className={`${styles.cardTextMuted} cursor-pointer`}
              >
                <Icon name="X" size={15} />
              </button>
            </div>

            <form onSubmit={onSave} className="flex-1 overflow-y-auto p-4 space-y-4">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.agentName")} <span className="text-red-500">*</span></label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder={t("aiworkbench.agentStudio.agentNamePlaceholder")}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                  required
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.agentRole")} <span className="text-red-500">*</span></label>
                <input
                  type="text"
                  value={formRole}
                  onChange={e => setFormRole(e.target.value)}
                  placeholder={t("aiworkbench.agentStudio.agentRolePlaceholder")}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                  required
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.agentDesc")}</label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder={t("aiworkbench.agentStudio.agentDescPlaceholder")}
                  rows={2}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none ${styles.cardBg} ${styles.cardText}`}
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.agentModel")} <span className="text-red-500">*</span></label>
                <select
                  value={formModel}
                  onChange={e => setFormModel(e.target.value)}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                >
                  {models.map(m => (
                    <option key={m.id} value={m.id}>{m.displayName}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.systemInstructions")} <span className="text-red-500">*</span></label>
                <textarea
                  value={formPrompt}
                  onChange={e => setFormPrompt(e.target.value)}
                  placeholder={t("aiworkbench.agentStudio.systemInstructionsPlaceholder")}
                  rows={4}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none font-sans leading-relaxed ${styles.cardBg} ${styles.cardText}`}
                  required
                />
              </div>

              {/* Tools assignment */}
              <div className="space-y-1.5">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.privilegeTools")}</label>
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
                          className={`rounded ${styles.accentText} ${styles.inputBorder} h-3 w-3`}
                        />
                        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{tool}</span>
                      </label>
                    );
                  })}
                </div>
              </div>

              {/* Safety Guardrails */}
              <div className="space-y-1.5">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.agentStudio.associateGuardrails")}</label>
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
                          className={`rounded ${styles.accentText} ${styles.inputBorder} h-3 w-3`}
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
                  onClick={onCloseModal}
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
                >
                  {t("aiworkbench.agentStudio.cancel")}
                </button>
                <button
                  type="submit"
                  className={`px-4 py-1.5 ${styles.accentBg} ${styles.accentHover} text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-[11px]`}
                >
                  {t("aiworkbench.agentStudio.confirmDeploy")}
                </button>
              </div>
            </form>

          </div>
        </div>
      )}
    </>
  );
}
