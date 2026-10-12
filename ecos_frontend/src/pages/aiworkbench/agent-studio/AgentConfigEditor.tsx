/**
 * AgentConfigEditor — Agent Studio 中央 Agent 设定卡（Header + Prompt / Tools / Guardrails 面板）。
 * 由 AgentStudioView.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 仅 handleStartEdit(selectedAgent) / handleDelete(selectedAgent.id) 改为 props 回调 onEdit / onDelete，
 * selectedAgent 更名为 prop agent。
 * @license Apache-2.0
 */
import { AIPAgent, AIPGuardrail } from '../../../types/aiworkbench';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { Icon } from './Icon';

export default function AgentConfigEditor({
  agent,
  guardrails,
  onEdit,
  onDelete,
}: {
  agent: AIPAgent;
  guardrails: AIPGuardrail[];
  onEdit: () => void;
  onDelete: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`flex-1 flex flex-col h-full ${styles.inputBg} overflow-y-auto p-5 space-y-4`}>

      {/* Agent Header */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs flex items-start justify-between`}>
        <div className="flex gap-3">
          <span className={`p-3 rounded-xl ${styles.badgeBg} ${styles.accentText} shrink-0`}>
            <Icon name={agent.avatar} size={20} />
          </span>
          <div className="space-y-1">
            <h2 className={`text-sm font-black ${styles.cardText}`}>{agent.name}</h2>
            <p className={`text-xs font-bold ${styles.accentText}`}>{agent.role}</p>
            <p className={`text-[11px] ${styles.cardTextMuted} max-w-lg leading-relaxed`}>{agent.description}</p>
          </div>
        </div>

        <div className="flex gap-2">
          <button
            type="button"
            onClick={onEdit}
            className={`px-2.5 py-1.5 ${styles.appBg} ${styles.accentHover} ${styles.cardTextMuted} border ${styles.cardBorder} rounded-lg transition-all cursor-pointer flex items-center gap-1`}
          >
            <Icon name="Settings2" size={11} />
            <span>{t('aiworkbench.as.manageAgent')}</span>
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="px-2.5 py-1.5 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-lg transition-all cursor-pointer flex items-center gap-1"
          >
            <Icon name="XCircle" size={11} />
            <span>{t('aiworkbench.as.deregister')}</span>
          </button>
        </div>
      </div>

      {/* Config Panels */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

        {/* Box 1: Prompt Guidelines */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
          <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
            <Icon name="Sliders" size={12} className={styles.accentText} />
            <span>{t('aiworkbench.as.systemPersona')}</span>
          </h3>
          <div className={`h-56 overflow-y-auto ${styles.inputBg} p-3 border ${styles.cardBorder} rounded-lg text-[11px] ${styles.cardTextMuted} font-sans leading-relaxed whitespace-pre-line`}>
            {agent.systemPrompt}
          </div>
        </div>

        {/* Box 2: Tool Actions & Guardrails */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs flex flex-col justify-between space-y-4`}>

          <div className="space-y-3">
            <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
              <Icon name="Boxes" size={12} className={styles.accentText} />
              <span>{t('aiworkbench.as.toolsPlugin')}</span>
            </h3>

            <div className="space-y-2 max-h-32 overflow-y-auto">
              {agent.assignedTools.actionIds.map(act => (
                <div key={act} className="flex items-center gap-2 p-1.5 bg-amber-500/5 border border-amber-200/50 rounded-lg">
                  <span className="p-0.5 rounded bg-amber-100 text-amber-600">
                    <Icon name="Zap" size={10} />
                  </span>
                  <div className="flex-1">
                    <p className={`font-bold text-[10px] ${styles.cardText}`}>{act}</p>
                    <p className={`text-[9px] ${styles.cardTextMuted} font-mono`}>Ontology Action Write-Back</p>
                  </div>
                  <span className="px-1.5 bg-amber-500/10 text-amber-600 text-[8px] font-bold rounded">{t('aiworkbench.as.elevated')}</span>
                </div>
              ))}
              {agent.assignedTools.functionIds.map(fn => (
                <div key={fn} className="flex items-center gap-2 p-1.5 bg-blue-500/5 border border-blue-200/50 rounded-lg">
                  <span className="p-0.5 rounded bg-blue-100 text-blue-600">
                    <Icon name="Code" size={10} />
                  </span>
                  <div className="flex-1">
                    <p className={`font-bold text-[10px] ${styles.cardText}`}>{fn}</p>
                    <p className={`text-[9px] ${styles.cardTextMuted} font-mono`}>Ontology Function Query</p>
                  </div>
                  <span className={`px-1.5 ${styles.badgeBg} ${styles.accentText} text-[8px] font-bold rounded`}>{t('aiworkbench.as.readonly')}</span>
                </div>
              ))}
            </div>
          </div>

          <div className={`space-y-3 pt-3 border-t ${styles.cardBorder}`}>
            <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
              <Icon name="ShieldAlert" size={12} className="text-rose-500" />
              <span>{t('aiworkbench.as.activeGuardrails')}</span>
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {agent.guardrailIds.map(grid => {
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
  );
}
