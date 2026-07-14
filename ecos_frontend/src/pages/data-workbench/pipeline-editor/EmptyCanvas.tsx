/**
 * EmptyCanvas — placeholder for PipelineFlowEditor
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { Workflow } from 'lucide-react';

// ─── Empty state ──────────────────────────────────────────

const EmptyCanvas: React.FC = () => (
  <div className="flex flex-col items-center justify-center h-full text-slate-400 pointer-events-none select-none">
    <Workflow size={64} className="mb-4 text-slate-300" />
    <p className="text-sm font-medium">拖拽节点到画布开始构建 Pipeline</p>
    <p className="text-xs mt-1">从左侧工具栏拖入 Source、Transform、Join、Aggregate、Sink 节点</p>
  </div>
);


export default EmptyCanvas;
