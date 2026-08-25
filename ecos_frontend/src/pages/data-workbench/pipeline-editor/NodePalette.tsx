/**
 * NodePalette — draggable node palette sidebar
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node types (PMO-3J T1): 6 node types, SOURCE_CDC disabled.
 * @license Apache-2.0
 */

import React from 'react';
import { GripVertical } from 'lucide-react';
import { buildPaletteItems } from './constants';
import { useLanguage } from '../../../components/LanguageContext';

interface NodePaletteProps {
  styles: Record<string, string>;
  connectionsCount: number;
  pipelinesCount: number;
  onDragStart: (event: React.DragEvent<HTMLDivElement>, nodeType: string) => void;
}

const NodePalette: React.FC<NodePaletteProps> = ({
  styles,
  connectionsCount,
  pipelinesCount,
  onDragStart,
}) => {
  const { t } = useLanguage();
  const items = buildPaletteItems(styles, t);

  return (
    <div className={`w-44 border-r shrink-0 flex flex-col ${styles.sidebarBorder} ${styles.sidebarBg}`}>
      <div className={`px-3 py-2.5 border-b ${styles.sidebarBorder}`}>
        <span className={`text-[11px] font-bold uppercase tracking-wider ${styles.sidebarText}`}>
          {t('dw.pipeline.palette.title')}
        </span>
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
        {items.map((item) => {
          const disabled = item.disabled;
          const cls = disabled
            ? `flex items-center gap-2 px-2.5 py-2 ${item.bgColor} border ${item.borderColor} rounded-lg select-none opacity-50 cursor-not-allowed`
            : `flex items-center gap-2 px-2.5 py-2 ${item.bgColor} border ${item.borderColor} rounded-lg cursor-grab active:cursor-grabbing hover:shadow-md transition-all select-none`;
          return (
            <div
              key={item.type}
              draggable={!disabled}
              onDragStart={disabled ? undefined : (e) => onDragStart(e, item.type)}
              className={cls}
              title={disabled ? t(item.disabledTitleKey || 'dw.pipeline.node.cdcFlagshipOnly') : undefined}
            >
              <item.icon size={16} className={item.color} />
              <span className={`text-xs font-medium ${styles.cardText}`}>{item.label}</span>
              <GripVertical size={12} className={`ml-auto ${styles.cardTextMuted}`} />
            </div>
          );
        })}
      </div>
      <div className={`p-2 border-t ${styles.sidebarBorder}`}>
        <div className={`text-[10px] leading-tight ${styles.cardTextMuted}`}>
          {t('dw.pipeline.palette.connections')}: <span className={`font-semibold ${styles.cardText}`}>{connectionsCount}</span>
        </div>
        <div className={`text-[10px] leading-tight ${styles.cardTextMuted}`}>
          {t('dw.pipeline.palette.pipelines')}: <span className={`font-semibold ${styles.cardText}`}>{pipelinesCount}</span>
        </div>
      </div>
    </div>
  );
};

export default NodePalette;
