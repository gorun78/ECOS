/**
 * NodePalette — draggable node palette sidebar
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { GripVertical } from 'lucide-react';
import { PALETTE_ITEMS } from './constants';

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
  return (
    <div className={`w-44 border-r shrink-0 flex flex-col ${styles.sidebarBorder} ${styles.sidebarBg}`}>
      <div className={`px-3 py-2.5 border-b ${styles.sidebarBorder}`}>
        <span className={`text-[11px] font-bold uppercase tracking-wider ${styles.sidebarText}`}>
          节点工具栏
        </span>
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
        {PALETTE_ITEMS.map((item) => (
          <div
            key={item.type}
            draggable
            onDragStart={(e) => onDragStart(e, item.type)}
            className={`flex items-center gap-2 px-2.5 py-2 ${item.bgColor} border ${item.borderColor} rounded-lg cursor-grab active:cursor-grabbing hover:shadow-md transition-all select-none`}
          >
            <item.icon size={16} className={item.color} />
            <span className={`text-xs font-medium ${styles.cardText}`}>{item.label}</span>
            <GripVertical size={12} className={`ml-auto ${styles.cardTextMuted}`} />
          </div>
        ))}
      </div>
      <div className={`border-t p-2 ${styles.sidebarBorder}`}>
        <div className={`text-[10px] leading-tight ${styles.cardTextMuted}`}>
          可用数据源: <span className={`font-semibold ${styles.cardText}`}>{connectionsCount}</span>
        </div>
        <div className={`text-[10px] leading-tight ${styles.cardTextMuted}`}>
          已保存管道: <span className={`font-semibold ${styles.cardText}`}>{pipelinesCount}</span>
        </div>
      </div>
    </div>
  );
};

export default NodePalette;
