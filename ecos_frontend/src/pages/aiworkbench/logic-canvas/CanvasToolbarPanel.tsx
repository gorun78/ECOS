/**
 * CanvasToolbarPanel — LogicView 画布顶部执行/撤销/删除/日志工具条（React Flow Panel）。
 * 由 LogicView.tsx 机械抽取（H6-T4），JSX 结构与样式与原文逐行一致。
 * @license Apache-2.0
 */
import { Panel } from '@xyflow/react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { Icon } from './LogicIcon';

export default function CanvasToolbarPanel({
  onRun,
  isExecuting,
  nodesCount,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  onDeleteSelected,
  showLogs,
  onToggleLogs,
  logsCount,
  totalDuration,
}: {
  onRun: () => void;
  isExecuting: boolean;
  nodesCount: number;
  canUndo: boolean;
  canRedo: boolean;
  onUndo: () => void;
  onRedo: () => void;
  onDeleteSelected: () => void;
  showLogs: boolean;
  onToggleLogs: () => void;
  logsCount: number;
  totalDuration: number | null;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <Panel position="top-center" className="flex items-center gap-2">
      <button type="button"
        onClick={onRun}
        disabled={isExecuting || nodesCount === 0}
        className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} font-bold rounded-lg shadow-sm transition-colors cursor-pointer flex items-center gap-1.5 text-[11px] ${
          isExecuting ? 'opacity-60 cursor-not-allowed' : ''
        }`}
        title={t('aiworkbench.logic.run.tip')}
      >
        {isExecuting ? (
          <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
        ) : (
          <Icon name="Play" size={12} />
        )}
        <span>{isExecuting ? t('aiworkbench.logic.run.running') : t('aiworkbench.logic.run.label')}</span>
      </button>

      <button type="button"
        onClick={onUndo}
        disabled={canUndo}
        className={`px-2 py-1.5 ${styles.cardBg} ${styles.appBorder} border rounded-lg shadow-sm hover:opacity-80 disabled:opacity-40 cursor-pointer transition-opacity`}
        title="撤销 (Ctrl+Z)"
      >
        <Icon name="Undo2" size={12} className={`${styles.cardTextMuted}`} />
      </button>
      <button type="button"
        onClick={onRedo}
        disabled={canRedo}
        className={`px-2 py-1.5 ${styles.cardBg} ${styles.appBorder} border rounded-lg shadow-sm hover:opacity-80 disabled:opacity-40 cursor-pointer transition-opacity`}
        title="重做 (Ctrl+Y)"
      >
        <Icon name="Redo2" size={12} className={`${styles.cardTextMuted}`} />
      </button>

      <button type="button"
        onClick={onDeleteSelected}
        className={`px-2 py-1.5 ${styles.cardBg} ${styles.dangerBorder} border rounded-lg shadow-sm cursor-pointer transition-opacity hover:opacity-80`}
        title="删除选中节点 (Delete)"
      >
        <Icon name="Trash2" size={12} className={`${styles.dangerText}`} />
      </button>

      <button type="button"
        onClick={onToggleLogs}
        className={`px-2 py-1.5 border rounded-lg shadow-sm cursor-pointer transition-colors text-[10px] font-bold ${
          showLogs ? `${styles.infoBg} ${styles.infoBorder} ${styles.infoText}` : `${styles.cardBg} ${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`
        }`}
      >
        <span className="flex items-center gap-1">
          <Icon name="Terminal" size={11} />
          {t('aiworkbench.logic.run.logsLabel', { count: logsCount })}
        </span>
      </button>

      {totalDuration != null && (
        <span className={`px-2 py-1 ${styles.cardBg} ${styles.appBorder} border rounded-lg text-[10px] font-mono ${styles.cardTextMuted} shadow-sm`}>
          {t('aiworkbench.logic.run.totalDuration', { ms: totalDuration })}
        </span>
      )}
    </Panel>
  );
}
