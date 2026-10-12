/**
 * ExecutionLogPanel — LogicView 底部执行日志浮层。
 * 由 LogicView.tsx 机械抽取（H6-T4），JSX 结构与样式与原文逐行一致。
 * @license Apache-2.0
 */
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { Icon } from './LogicIcon';

export default function ExecutionLogPanel({
  logs,
  totalDuration,
  onClose,
}: {
  logs: string[];
  totalDuration: number | null;
  onClose: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`absolute bottom-0 left-56 right-0 z-20 ${styles.cardBg} border-t ${styles.cardBorder} shadow-lg`}
         style={{ maxHeight: '200px' }}>
      <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
        <span className={`font-bold ${styles.cardText} text-[10px] flex items-center gap-1`}>
          <Icon name="Terminal" size={11} />
          {t('aiworkbench.logic.logs.title')}
          {totalDuration != null && (
            <span className={`font-mono text-[9px] ${styles.cardTextMuted} ml-2`}>{t('aiworkbench.logic.run.totalDuration', { ms: totalDuration })}</span>
          )}
        </span>
        <button type="button"
          onClick={onClose}
          className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}
        >
          <Icon name="ChevronDown" size={12} />
        </button>
      </div>
      <div className="overflow-y-auto p-2 max-h-[160px] space-y-0.5 font-mono text-[10px]">
        {logs.length === 0 ? (
          <span className={`${styles.cardTextMuted} italic px-2`}>{t('aiworkbench.logic.logs.emptyHint')}</span>
        ) : (
          logs.map((log, i) => (
            <p key={i} className={`px-2 py-0.5 leading-relaxed ${log.includes('✅') ? `${styles.successText}` : log.includes('❌') || log.includes('⛔') ? `${styles.dangerText}` : `${styles.cardTextMuted}`}`}>
              {log}
            </p>
          ))
        )}
      </div>
    </div>
  );
}
