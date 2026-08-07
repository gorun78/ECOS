/**
 * Trigger Node — Clock icon, cron expression/timezone
 * @license Apache-2.0
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from 'reactflow';
import { Clock } from 'lucide-react';
import type { LogicNodeData, LogicTriggerConfig } from '../../../types/aiworkbench';

function TriggerNode({ data, selected }: NodeProps<LogicNodeData>) {
  const config = data.config as LogicTriggerConfig;
  const statusColor = {
    idle: 'bg-gray-400',
    running: 'bg-blue-500 animate-pulse',
    success: 'bg-emerald-500',
    error: 'bg-red-500',
  }[data.status];

  return (
    <div
      className={`group min-w-[200px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-blue-500 shadow-lg ring-2 ring-blue-200' : 'border-teal-200 hover:border-teal-300'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-teal-500 !w-3 !h-3" />
      <div className="flex items-center gap-2 px-3 py-2.5 border-b border-teal-100 bg-teal-50 rounded-t-[10px]">
        <span className="p-1 bg-teal-500 text-white rounded-md">
          <Clock size={14} />
        </span>
        <span className="text-xs font-bold text-teal-800 truncate flex-1">{data.label}</span>
        <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${statusColor}`} />
      </div>
      <div className="px-3 py-2 space-y-1 text-[10px] text-slate-500">
        <div className="flex justify-between">
          <span className="font-semibold">Cron:</span>
          <span className="font-mono text-slate-700">{config.cronExpr}</span>
        </div>
        <div className="flex justify-between">
          <span className="font-semibold">TZ:</span>
          <span className="font-mono text-teal-600 font-bold">{config.timezone}</span>
        </div>
        {data.duration != null && (
          <div className="flex justify-between">
            <span className="font-semibold">耗时:</span>
            <span className="font-mono text-slate-700">{data.duration}ms</span>
          </div>
        )}
      </div>
      {/* Trigger is a source-only node */}
      <Handle type="source" position={Position.Bottom} className="!bg-teal-500 !w-3 !h-3" />
    </div>
  );
}

export default memo(TriggerNode);
