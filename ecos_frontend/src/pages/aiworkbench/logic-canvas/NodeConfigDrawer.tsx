/**
 * NodeConfigDrawer — LogicView 右侧节点配置抽屉（含节点 trace 展开区）。
 * 由 LogicView.tsx 机械抽取（H6-T4），JSX 结构与样式与原文逐行一致。
 * @license Apache-2.0
 */
import type { Node } from '@xyflow/react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { LogicNodeData, LogicNodeConfig } from '../../../types/aiworkbench';
import { Icon } from './LogicIcon';
import ConfigForm from './ConfigForm';
import type { NodeTrace } from './types';

export default function NodeConfigDrawer({
  node,
  trace,
  traceOpen,
  onToggleTrace,
  onClose,
  onUpdateConfig,
}: {
  node: Node<LogicNodeData>;
  trace?: NodeTrace;
  traceOpen: boolean;
  onToggleTrace: () => void;
  onClose: () => void;
  onUpdateConfig: (config: LogicNodeConfig) => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`w-72 ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0 overflow-y-auto`}>
      <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between sticky top-0 z-10`}>
        <span className={`font-bold ${styles.cardText} text-[11px]`}>
          {t('aiworkbench.logic.config.title', { label: node.data.label })}
        </span>
        <div className="flex items-center gap-2">
          <button type="button"
            onClick={onToggleTrace}
            className={`${traceOpen ? `${styles.infoText} ${styles.infoBg}` : `${styles.cardTextMuted} ${styles.badgeBg}`} px-1.5 py-0.5 rounded text-[10px] font-bold cursor-pointer transition-colors`}
            title={t('aiworkbench.logic.trace.toggle')}
          >
            <span className="flex items-center gap-1">
              <Icon name="Cite" size={11} />
              {t('aiworkbench.logic.trace.tab')}
            </span>
          </button>
          <button type="button"
            onClick={onClose}
            className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}
          >
            <Icon name="X" size={14} />
          </button>
        </div>
      </div>
      {traceOpen && (
        <div className="p-3 space-y-2 text-[10px]">
          <p className={`font-bold ${styles.cardText}`}>{t('aiworkbench.logic.trace.title')}</p>
          <div className={`rounded-lg border ${styles.cardBorder} p-2 space-y-1.5 ${styles.inputBg}`}>
            {(() => {
              const tr = trace;
              if (!tr) {
                return <p className={`${styles.cardTextMuted} italic`}>{t('aiworkbench.logic.trace.empty')}</p>;
              }
              return (
                <>
                  {tr.input !== undefined && (
                    <div className="space-y-0.5">
                      <p className={`font-mono text-[9px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.logic.trace.input')}</p>
                      <pre className={`whitespace-pre-wrap break-all font-mono text-[10px] ${styles.cardText}`}>{tr.input}</pre>
                    </div>
                  )}
                  {tr.output !== undefined && (
                    <div className="space-y-0.5">
                      <p className={`font-mono text-[9px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.logic.trace.output')}</p>
                      <pre className={`whitespace-pre-wrap break-all font-mono text-[10px] ${styles.cardText}`}>{tr.output}</pre>
                    </div>
                  )}
                  {tr.latencyMs != null && (
                    <div className="flex items-center justify-between">
                      <span className={`font-mono text-[9px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.logic.trace.latency')}</span>
                      <span className={`font-mono font-bold ${styles.infoText}`}>{tr.latencyMs}ms</span>
                    </div>
                  )}
                  {tr.errorMessage && (
                    <div className={`p-2 rounded-md border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} text-[10px] flex items-start gap-2`}>
                      <Icon name="AlertTriangle" size={12} className="mt-0.5 shrink-0" />
                      <pre className="whitespace-pre-wrap break-all font-mono flex-1">{tr.errorMessage}</pre>
                    </div>
                  )}
                </>
              );
            })()}
          </div>
        </div>
      )}
      <ConfigForm
        node={node}
        onUpdate={onUpdateConfig}
        styles={styles}
      />
    </div>
  );
}
