/**
 * PipelineListPanel — LogicView 左侧 Pipeline 列表（含新建入口）。
 * 由 LogicView.tsx 机械抽取（H6-T4），JSX 结构与样式与原文逐行一致。
 * @license Apache-2.0
 */
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { AIPLogicPipeline } from '../../../types/aiworkbench';
import { Icon } from './LogicIcon';

export default function PipelineListPanel({
  pipelines,
  selectedPipelineId,
  onSelect,
  onCreate,
}: {
  pipelines: AIPLogicPipeline[];
  selectedPipelineId: string;
  onSelect: (id: string) => void;
  onCreate: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
      <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
        <span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.logic.list.title', { count: pipelines.length })}</span>
        <button type="button"
          onClick={onCreate}
          className={`p-1 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} border ${styles.accentBorder} rounded-md transition-colors cursor-pointer`}
          title={t('aiworkbench.logic.list.add')}
        >
          <Icon name="Plus" size={12} />
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
        {pipelines.map(p => {
          const isSelected = selectedPipelineId === p.id;
          return (
            <div
              key={p.id}
              onClick={() => onSelect(p.id)}
              className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1.5 border ${
                isSelected
                  ? `${styles.accentBg} ${styles.accentText} shadow-xs`
                  : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`
              }`}
            >
              <div className="flex items-center gap-1.5 font-bold">
                <Icon name="Cpu" size={12} className={isSelected ? `${styles.accentText} animate-pulse` : `${styles.cardTextMuted}`} />
                <span className="truncate">{p.name}</span>
              </div>
              <p className={`text-[10px] line-clamp-2 leading-relaxed ${isSelected ? `${styles.cardTextMuted} opacity-70` : `${styles.cardTextMuted} opacity-80`}`}>
                {p.description}
              </p>
              <div className={`flex items-center justify-between text-[9px] border-t ${styles.inputBorder}/10 pt-1`}>
                <span className={`font-mono ${isSelected ? `${styles.cardTextMuted} opacity-70` : `${styles.cardTextMuted} opacity-80`}`}>{p.lastUpdated.split(' ')[0]}</span>
                <span className={`px-1 ${styles.successBg} ${styles.successText} rounded font-bold`}>{t('aiworkbench.logic.list.ready')}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
