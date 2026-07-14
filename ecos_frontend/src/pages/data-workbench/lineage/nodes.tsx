/**
 * LineageMapView — custom ReactFlow nodes
 * Extracted from LineageMapView.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { CheckCircle, Loader2, XCircle, AlertCircle, Clock, Database, ArrowRightLeft, GitBranch, Table2, Box } from 'lucide-react';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import type { SourceNodeData, IngestNodeData, PipelineNodeData, DatasetNodeData, OntologyNodeData } from './types';

// ─── 状态徽章 ────────────────────────────────────────────────

const statusColors: Record<string, string> = {
  connected: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  active: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  disconnected: 'bg-slate-500/20 text-slate-400 border-slate-500/40',
  error: 'bg-red-500/20 text-red-400 border-red-500/40',
  pending: 'bg-amber-500/20 text-amber-400 border-amber-500/40',
  testing: 'bg-blue-500/20 text-blue-400 border-blue-500/40',
  running: 'bg-blue-500/20 text-blue-400 border-blue-500/40',
  success: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  completed: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  failed: 'bg-red-500/20 text-red-400 border-red-500/40',
  paused: 'bg-slate-500/20 text-slate-400 border-slate-500/40',
  draft: 'bg-slate-500/20 text-slate-400 border-slate-500/40',
  ok: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  warning: 'bg-amber-500/20 text-amber-400 border-amber-500/40',
  passed: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
};

const statusIcons: Record<string, React.FC<{ size?: number }>> = {
  connected: CheckCircle,
  active: CheckCircle,
  running: Loader2,
  success: CheckCircle,
  completed: CheckCircle,
  error: XCircle,
  failed: XCircle,
  disconnected: AlertCircle,
  pending: Clock,
  paused: AlertCircle,
  draft: AlertCircle,
  ok: CheckCircle,
  warning: AlertCircle,
  passed: CheckCircle,
};

export function StatusBadge({ status }: { status: string }) {
  const colors = statusColors[status] || statusColors.disconnected;
  const Icon = statusIcons[status];
  return (
    <span
      className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full border text-[9px] font-medium ${colors}`}
    >
      {Icon && <Icon size={10} />}
      {status}
    </span>
  );
}

// ─── 节点颜色主题 ────────────────────────────────────────────

const nodeThemes = {
  source: {
    border: 'border-blue-500/50',
    hoverBorder: 'hover:border-blue-400',
    selectedRing: 'ring-blue-500',
    accentBg: 'bg-blue-500/15',
    accentText: 'text-blue-400',
    iconColor: 'text-blue-400',
  },
  ingest: {
    border: 'border-purple-500/50',
    hoverBorder: 'hover:border-purple-400',
    selectedRing: 'ring-purple-500',
    accentBg: 'bg-purple-500/15',
    accentText: 'text-purple-400',
    iconColor: 'text-purple-400',
  },
  pipeline: {
    border: 'border-amber-500/50',
    hoverBorder: 'hover:border-amber-400',
    selectedRing: 'ring-amber-500',
    accentBg: 'bg-amber-500/15',
    accentText: 'text-amber-400',
    iconColor: 'text-amber-400',
  },
  dataset: {
    border: 'border-emerald-500/50',
    hoverBorder: 'hover:border-emerald-400',
    selectedRing: 'ring-emerald-500',
    accentBg: 'bg-emerald-500/15',
    accentText: 'text-emerald-400',
    iconColor: 'text-emerald-400',
  },
  ontology: {
    border: 'border-rose-500/50',
    hoverBorder: 'hover:border-rose-400',
    selectedRing: 'ring-rose-500',
    accentBg: 'bg-rose-500/15',
    accentText: 'text-rose-400',
    iconColor: 'text-rose-400',
  },
} as const;

// ─── 自定义节点：数据源 ──────────────────────────────────────

export function LineageSourceNode({ data, selected }: NodeProps<Node<SourceNodeData>>) {
  const theme = nodeThemes.source;
  return (
    <div
      className={`
        relative w-[200px] rounded-xl border bg-[#1e293b]
        ${theme.border} shadow-lg shadow-black/30
        transition-all duration-150 cursor-pointer
        ${theme.hoverBorder}
        ${selected ? `ring-2 ${theme.selectedRing} ring-offset-1 ring-offset-[#0f172a]` : ''}
      `}
    >
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-blue-500 !border-2 !border-[#0f172a] !right-[-6px]"
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <Database size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-[12px] font-bold text-white truncate leading-tight">
              {data.label}
            </h3>
            <p className="text-[10px] text-slate-400 truncate mt-0.5">{data.sublabel}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 mt-2">
          <span className={`text-[9px] px-1.5 py-0.5 rounded font-medium ${theme.accentBg} ${theme.accentText}`}>
            {data.type}
          </span>
          <StatusBadge status={data.status} />
        </div>
        {data.tablesAvailable > 0 && (
          <div className="mt-2 pt-2 border-t border-slate-700/50">
            <span className="text-[9px] text-slate-500">{data.tablesAvailable} 张表可用</span>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── 自定义节点：同步入湖 ────────────────────────────────────

export function LineageIngestNode({ data, selected }: NodeProps<Node<IngestNodeData>>) {
  const theme = nodeThemes.ingest;
  return (
    <div
      className={`
        relative w-[200px] rounded-xl border bg-[#1e293b]
        ${theme.border} shadow-lg shadow-black/30
        transition-all duration-150 cursor-pointer
        ${theme.hoverBorder}
        ${selected ? `ring-2 ${theme.selectedRing} ring-offset-1 ring-offset-[#0f172a]` : ''}
      `}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-purple-500 !border-2 !border-[#0f172a] !left-[-6px]"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-purple-500 !border-2 !border-[#0f172a] !right-[-6px]"
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <ArrowRightLeft size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-[12px] font-bold text-white truncate leading-tight">
              {data.label}
            </h3>
            <p className="text-[10px] text-slate-400 truncate mt-0.5">{data.sublabel}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 mt-2">
          {data.syncMode && (
            <span className={`text-[9px] px-1.5 py-0.5 rounded font-medium ${theme.accentBg} ${theme.accentText}`}>
              {data.syncMode}
            </span>
          )}
          <StatusBadge status={data.status} />
        </div>
        {data.recordsSynced !== undefined && data.recordsSynced > 0 && (
          <div className="mt-2 pt-2 border-t border-slate-700/50">
            <span className="text-[9px] text-slate-500">
              {data.recordsSynced.toLocaleString()} 条已同步
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── 自定义节点：ETL管道 ─────────────────────────────────────

export function LineagePipelineNode({ data, selected }: NodeProps<Node<PipelineNodeData>>) {
  const theme = nodeThemes.pipeline;
  return (
    <div
      className={`
        relative w-[200px] rounded-xl border bg-[#1e293b]
        ${theme.border} shadow-lg shadow-black/30
        transition-all duration-150 cursor-pointer
        ${theme.hoverBorder}
        ${selected ? `ring-2 ${theme.selectedRing} ring-offset-1 ring-offset-[#0f172a]` : ''}
      `}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-[#0f172a] !left-[-6px]"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-[#0f172a] !right-[-6px]"
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <GitBranch size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-[12px] font-bold text-white truncate leading-tight">
              {data.label}
            </h3>
            <p className="text-[10px] text-slate-400 truncate mt-0.5">{data.sublabel}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 mt-2">
          <StatusBadge status={data.status} />
        </div>
        <div className="mt-2 pt-2 border-t border-slate-700/50 flex items-center gap-3">
          <span className="text-[9px] text-slate-500">{data.nodeCount} 节点</span>
          {(data.expressionsCount ?? 0) > 0 && (
            <span className="text-[9px] text-slate-500">{data.expressionsCount} 表达式</span>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── 自定义节点：数据集 ──────────────────────────────────────

export function LineageDatasetNode({ data, selected }: NodeProps<Node<DatasetNodeData>>) {
  const theme = nodeThemes.dataset;
  const colCount = data.columns?.length || 0;
  return (
    <div
      className={`
        relative w-[200px] rounded-xl border bg-[#1e293b]
        ${theme.border} shadow-lg shadow-black/30
        transition-all duration-150 cursor-pointer
        ${theme.hoverBorder}
        ${selected ? `ring-2 ${theme.selectedRing} ring-offset-1 ring-offset-[#0f172a]` : ''}
      `}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-emerald-500 !border-2 !border-[#0f172a] !left-[-6px]"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-emerald-500 !border-2 !border-[#0f172a] !right-[-6px]"
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <Table2 size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-[12px] font-bold text-white truncate leading-tight">
              {data.label}
            </h3>
            <p className="text-[10px] text-slate-400 truncate mt-0.5">{data.sublabel}</p>
          </div>
        </div>
        <div className="mt-2 pt-2 border-t border-slate-700/50 flex items-center gap-3">
          {data.rowCount !== undefined && (
            <span className="text-[9px] text-slate-500">
              {data.rowCount.toLocaleString()} 行
            </span>
          )}
          {colCount > 0 && (
            <span className="text-[9px] text-slate-500">{colCount} 列</span>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── 自定义节点：Ontology实体 ────────────────────────────────

export function LineageOntologyNode({ data, selected }: NodeProps<Node<OntologyNodeData>>) {
  const theme = nodeThemes.ontology;
  return (
    <div
      className={`
        relative w-[200px] rounded-xl border bg-[#1e293b]
        ${theme.border} shadow-lg shadow-black/30
        transition-all duration-150 cursor-pointer
        ${theme.hoverBorder}
        ${selected ? `ring-2 ${theme.selectedRing} ring-offset-1 ring-offset-[#0f172a]` : ''}
      `}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-rose-500 !border-2 !border-[#0f172a] !left-[-6px]"
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <Box size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-[12px] font-bold text-white truncate leading-tight">
              {data.label}
            </h3>
            <p className="text-[10px] text-slate-400 truncate mt-0.5">{data.sublabel}</p>
          </div>
        </div>
        <div className="mt-2">
          <span className={`text-[9px] px-1.5 py-0.5 rounded font-medium ${theme.accentBg} ${theme.accentText}`}>
            {data.domain}
          </span>
        </div>
        {data.propertiesCount > 0 && (
          <div className="mt-2 pt-2 border-t border-slate-700/50">
            <span className="text-[9px] text-slate-500">{data.propertiesCount} 属性</span>
          </div>
        )}
      </div>
    </div>
  );
}


export const nodeTypes = { source: LineageSourceNode, ingest: LineageIngestNode, pipeline: LineagePipelineNode, dataset: LineageDatasetNode, ontology: LineageOntologyNode };
