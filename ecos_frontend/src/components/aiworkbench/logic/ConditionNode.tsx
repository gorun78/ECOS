/**
 * Condition Node — GitBranch icon, if condition(JSONPath)/then/else
 * @license Apache-2.0
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from 'reactflow';
import { GitBranch } from 'lucide-react';
import type { LogicNodeData, LogicConditionConfig } from '../../../types/aiworkbench';

function ConditionNode({ data, selected }: NodeProps<LogicNodeData>) {
  const config = data.config as LogicConditionConfig;
  const statusColor = {
    idle: 'bg-gray-400',
    running: 'bg-blue-500 animate-pulse',
    success: 'bg-emerald-500',
    error: 'bg-red-500',
  }[data.status];

  return (
    <div
      className={`group min-w-[200px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-blue-500 shadow-lg ring-2 ring-blue-200' : 'border-indigo-200 hover:border-indigo-300'
      }`}
      style={{ borderRadius: '50%' }}
    >
      {/* Actually use diamond shape via clipPath, but keep readability with rounded */}
    </div>
  );
}

// Re-export with proper diamond style
function ConditionNodeDiamond({ data, selected }: NodeProps<LogicNodeData>) {
  const config = data.config as LogicConditionConfig;
  const statusColor = {
    idle: 'bg-gray-400',
    running: 'bg-blue-500 animate-pulse',
    success: 'bg-emerald-500',
    error: 'bg-red-500',
  }[data.status];

  return (
    <div
      className={`group min-w-[190px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-blue-500 shadow-lg ring-2 ring-blue-200' : 'border-indigo-200 hover:border-indigo-300'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-indigo-500 !w-3 !h-3" />
      <div className="flex items-center gap-2 px-3 py-2.5 border-b border-indigo-100 bg-indigo-50 rounded-t-[10px]">
        <span className="p-1 bg-indigo-500 text-white rounded-md">
          <GitBranch size={14} />
        </span>
        <span className="text-xs font-bold text-indigo-800 truncate flex-1">{data.label}</span>
        <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${statusColor}`} />
      </div>
      <div className="px-3 py-2 space-y-1 text-[10px] text-slate-500">
        <div className="font-mono text-slate-700 break-all line-clamp-1">{config.conditionExpr}</div>
        <div className="flex justify-between gap-2">
          <span className="px-1.5 py-0.5 bg-emerald-50 text-emerald-600 border border-emerald-200 rounded font-bold text-[9px]">
            ✓ {config.thenBranch}
          </span>
          <span className="px-1.5 py-0.5 bg-red-50 text-red-600 border border-red-200 rounded font-bold text-[9px]">
            ✗ {config.elseBranch}
          </span>
        </div>
        {data.duration != null && (
          <div className="flex justify-between">
            <span className="font-semibold">耗时:</span>
            <span className="font-mono text-slate-700">{data.duration}ms</span>
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Bottom} id="then" className="!bg-emerald-500 !w-3 !h-3" />
      <Handle type="source" position={Position.Right} id="else" className="!bg-red-500 !w-3 !h-3" />
    </div>
  );
}

export default memo(ConditionNodeDiamond);
