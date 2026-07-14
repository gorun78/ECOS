/**
 * CollapseToggle — collapsible section icon
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { ChevronDown } from 'lucide-react';

// ─── Collapse toggle icon ─────────────────────────────────

const CollapseToggle: React.FC<{
  expanded: boolean;
  onToggle: () => void;
}> = ({ expanded, onToggle }) => (
  <button
    onClick={onToggle}
    className="flex items-center gap-1 px-2 py-1 text-xs text-slate-400 hover:text-slate-200 transition-colors"
    title={expanded ? '收起侧栏' : '展开侧栏'}
  >
    <ChevronDown
      size={14}
      className={`transition-transform duration-200 ${expanded ? 'rotate-0' : '-rotate-90'}`}
    />
  </button>
);

export default CollapseToggle;
