/**
 * Ontology Node — Database icon, objectType/queryType/filter
 * @license Apache-2.0
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from 'reactflow';
import { Database } from 'lucide-react';
import type { LogicNodeData, LogicOntologyConfig } from '../../../types/aiworkbench';

function OntologyNode({ data, selected }: NodeProps<LogicNodeData>) {
  const config = data.config as LogicOntologyConfig;
  const statusColor = {
    idle: 'bg-gray-400',
    running: 'bg-blue-500 animate-pulse',
    success: 'bg-emerald-500',
    error: 'bg-red-500',
  }[data.status];

  return (
    <div
      className={`group min-w-[220px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-blue-500 shadow-lg ring-2 ring-blue-200' : 'border-cyan-200 hover:border-cyan-300'
      }`}
    >
      <Handle type="target" position={Position.Top} className="!bg-cyan-500 !w-3 !h-3" />
      <div className="flex items-center gap-2 px-3 py-2.5 border-b border-cyan-100 bg-cyan-50 rounded-t-[10px]">
        <span className="p-1 bg-cyan-500 text-white rounded-md">
          <Database size={14} />
        </span>
        <span className="text-xs font-bold text-cyan-800 truncate flex-1">{data.label}</span>
        <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${statusColor}`} />
      </div>
      <div className="px-3 py-2 space-y-1 text-[10px] text-slate-500">
        <div className="flex justify-between">
          <span className="font-semibold">Object:</span>
          <span className="font-mono text-slate-700">{config.objectType}</span>
        </div>
        <div className="flex justify-between">
          <span className="font-semibold">Query:</span>
          <span className="font-mono text-cyan-600 font-bold">{config.queryType}</span>
        </div>
        {data.duration != null && (
          <div className="flex justify-between">
            <span className="font-semibold">耗时:</span>
            <span className="font-mono text-slate-700">{data.duration}ms</span>
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-cyan-500 !w-3 !h-3" />
    </div>
  );
}

export default memo(OntologyNode);
