/**
 * NodePalettePanel — LogicView 画布左上角节点类型新增面板（React Flow Panel）。
 * 由 LogicView.tsx 机械抽取（H6-T4），JSX 结构与样式与原文逐行一致。
 * @license Apache-2.0
 */
import { Panel } from '@xyflow/react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { LogicNodeType } from '../../../types/aiworkbench';
import { typeLabel } from './graph';

export default function NodePalettePanel({ onAddNode }: { onAddNode: (type: LogicNodeType) => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <Panel position="top-left" className="flex items-center gap-1.5">
      {/* Add nodes dropdown */}
      <div className={`flex ${styles.cardBg} ${styles.appBorder} border rounded-lg shadow-sm overflow-hidden`}>
        {(['llm', 'tool', 'ontology', 'approval', 'condition', 'trigger'] as LogicNodeType[]).map(nodeType => (
          <button type="button"
            key={nodeType}
            onClick={() => onAddNode(nodeType)}
            className={`px-2 py-1.5 text-[10px] font-bold ${styles.cardTextMuted} ${styles.cardBorder} border-r last:border-r-0 cursor-pointer transition-opacity hover:opacity-80`}
            title={t('aiworkbench.logic.addNode', { type: typeLabel(nodeType) })}
          >
            {typeLabel(nodeType)}
          </button>
        ))}
      </div>
    </Panel>
  );
}
