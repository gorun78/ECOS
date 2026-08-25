/**
 * EmptyCanvas — placeholder for PipelineFlowEditor
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { Workflow } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';

// ─── Empty state ──────────────────────────────────────────

const EmptyCanvas: React.FC = () => (
  <div className={`flex flex-col items-center justify-center h-full ${styles.cardTextMuted} pointer-events-none select-none`}>
    <Workflow size={64} className={`mb-4 ${styles.cardTextMuted}`} />
    <p className="text-sm font-medium">拖拽节点到画布开始构建 Pipeline</p>
    <p className="text-xs mt-1">从左侧工具栏拖入 Source、Transform、Join、Aggregate、Sink 节点</p>
  </div>
);


export default EmptyCanvas;

// TODO: useTheme insertion needed
