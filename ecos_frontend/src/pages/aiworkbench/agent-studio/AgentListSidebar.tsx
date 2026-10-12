/**
 * AgentListSidebar — 智能助手工坊左侧 Agent 列表。
 * 由 AgentStudioView.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 仅 setSelectedAgentId / handleStartCreate 改为 props 回调 onSelect / onCreate。
 * @license Apache-2.0
 */
import { AIPAgent } from '../../../types/aiworkbench';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { Icon } from './Icon';

export default function AgentListSidebar({
  agents,
  selectedAgentId,
  onSelect,
  onCreate,
}: {
  agents: AIPAgent[];
  selectedAgentId: string;
  onSelect: (id: string) => void;
  onCreate: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
      <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
        <span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.as.listTitle')} ({agents.length})</span>
        <button
          type="button"
          onClick={onCreate}
          className={`p-1 ${styles.badgeBg} hover:opacity-80 ${styles.accentText} ${styles.accentBorder} border rounded-md transition-colors cursor-pointer`}
          title={t('aiworkbench.as.addNew')}
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
              onClick={() => onSelect(a.id)}
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
  );
}
