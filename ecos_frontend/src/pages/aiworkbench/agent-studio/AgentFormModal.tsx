/**
 * AgentFormModal — 创建 / 编辑 AIP 智能体弹窗表单。
 * 由 AgentStudioView.tsx 机械抽取（PMO-74 H6-T4），JSX 结构、样式与表单字段与原文逐行一致；
 * 表单受控状态仍归父组件所有，此处仅通过 props 透传值与 setter；
 * 仅 setShowCreateModal(false) → onClose、handleSave → onSubmit 改为 props 回调。
 * @license Apache-2.0
 */
import React from 'react';
import { AIPAgent, AIPModel, AIPGuardrail } from '../../../types/aiworkbench';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { Icon } from './Icon';

export default function AgentFormModal({
  editingAgent,
  models,
  guardrails,
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
  onClose,
  onSubmit,
}: {
  editingAgent: AIPAgent | null;
  models: AIPModel[];
  guardrails: AIPGuardrail[];
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
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
      <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden flex flex-col max-h-[90vh]`}>

        <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
          <h3 className={`font-bold ${styles.cardText} text-xs`}>
            {editingAgent ? t('aiworkbench.agent.form.titleEdit') : t('aiworkbench.agent.form.titleCreate')}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className={`${styles.cardTextMuted} cursor-pointer`}
          >
            <Icon name="X" size={15} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="flex-1 overflow-y-auto p-4 space-y-4">
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.name')} <span className="text-red-500">*</span></label>
            <input
              type="text"
              value={formName}
              onChange={e => setFormName(e.target.value)}
              placeholder={t('aiworkbench.agent.form.namePlaceholder')}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
              required
            />
          </div>

          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.role')} <span className="text-red-500">*</span></label>
            <input
              type="text"
              value={formRole}
              onChange={e => setFormRole(e.target.value)}
              placeholder={t('aiworkbench.agent.form.rolePlaceholder')}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
              required
            />
          </div>

          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.desc')}</label>
            <textarea
              value={formDesc}
              onChange={e => setFormDesc(e.target.value)}
              placeholder={t('aiworkbench.agent.form.descPlaceholder')}
              rows={2}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none ${styles.cardBg} ${styles.cardText}`}
            />
          </div>

          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.model')} <span className="text-red-500">*</span></label>
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
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.prompt')} <span className="text-red-500">*</span></label>
            <textarea
              value={formPrompt}
              onChange={e => setFormPrompt(e.target.value)}
              placeholder={t('aiworkbench.agent.form.promptPlaceholder')}
              rows={4}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none font-sans leading-relaxed ${styles.cardBg} ${styles.cardText}`}
              required
            />
          </div>

          {/* Tools assignment */}
          <div className="space-y-1.5">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.tools')}</label>
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
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.agent.form.guardrails')}</label>
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
              onClick={onClose}
              className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
            >
              {t('aiworkbench.chatbot.cancel')}
            </button>
            <button
              type="submit"
              className={`px-4 py-1.5 ${styles.accentBg} ${styles.accentHover} text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-[11px]`}
            >
              {t('aiworkbench.agent.form.confirmDeploy')}
            </button>
          </div>
        </form>

      </div>
    </div>
  );
}
