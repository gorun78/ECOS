/**
 * DataLineage — custom ReactFlow nodes
 * Extracted from DataLineage.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { CheckCircle, Loader2, XCircle, AlertCircle, Clock, Database, ArrowRightLeft, GitBranch, Table2, Box } from 'lucide-react';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import type { SourceNodeData, IngestNodeData, PipelineNodeData, DatasetNodeData, OntologyNodeData } from './types';
import { useTheme } from '../../../components/ThemeContext';

// ─── 状态徽章 ────────────────────────────────────────────────

const statusColors: Record<string, string> = {
  connected: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
  active: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
  disconnected: `${styles.sidebarBg} ${styles.cardTextMuted} ${styles.cardBorder}`,
  error: `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`,
  pending: `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`,
  testing: `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`,
  running: `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`,
  success: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
  completed: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
  failed: `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`,
  paused: `${styles.sidebarBg} ${styles.cardTextMuted} ${styles.cardBorder}`,
  draft: `${styles.sidebarBg} ${styles.cardTextMuted} ${styles.cardBorder}`,
  ok: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
  warning: `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`,
  passed: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
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
  const { styles } = useTheme();
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
    border: `${styles.infoBorder}`,
    hoverBorder: `hover:${styles.infoBorder}`,
    selectedRing: `${styles.infoBorder}`,
    accentBg: `${styles.infoBg}`,
    accentText: `${styles.infoText}`,
    iconColor: `${styles.infoText}`,
  },
  ingest: {
    border: `${styles.infoBorder}`,
    hoverBorder: `hover:${styles.infoBorder}`,
    selectedRing: `${styles.infoBorder}`,
    accentBg: `${styles.infoBg}`,
    accentText: `${styles.infoText}`,
    iconColor: `${styles.infoText}`,
  },
  pipeline: {
    border: `${styles.warningBorder}`,
    hoverBorder: `hover:${styles.warningBorder}`,
    selectedRing: `${styles.warningBorder}`,
    accentBg: `${styles.warningBg}`,
    accentText: `${styles.warningText}`,
    iconColor: `${styles.warningText}`,
  },
  dataset: {
    border: `${styles.successBorder}`,
    hoverBorder: `hover:${styles.successBorder}`,
    selectedRing: `${styles.successBorder}`,
    accentBg: `${styles.successBg}`,
    accentText: `${styles.successText}`,
    iconColor: `${styles.successText}`,
  },
  ontology: {
    border: `${styles.dangerBorder}`,
    hoverBorder: `hover:${styles.dangerBorder}`,
    selectedRing: `${styles.dangerBorder}`,
    accentBg: `${styles.dangerBg}`,
    accentText: `${styles.dangerText}`,
    iconColor: `${styles.dangerText}`,
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
        className={`!w-3 !h-3 !${styles.infoBg} !border-2 !border-[#0f172a] !right-[-6px]`}
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <Database size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className={`text-[12px] font-bold ${styles.cardText} truncate leading-tight`}>
              {data.label}
            </h3>
            <p className={`text-[10px] ${styles.cardTextMuted} truncate mt-0.5`}>{data.sublabel}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 mt-2">
          <span className={`text-[9px] px-1.5 py-0.5 rounded font-medium ${theme.accentBg} ${theme.accentText}`}>
            {data.type}
          </span>
          <StatusBadge status={data.status} />
        </div>
        {data.tablesAvailable > 0 && (
          <div className={`mt-2 pt-2 border-t ${styles.cardBorder}`}>
            <span className={`text-[9px] ${styles.muted}`}>{data.tablesAvailable} 张表可用</span>
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
        className={`!w-3 !h-3 !${styles.infoBg} !border-2 !border-[#0f172a] !left-[-6px]`}
      />
      <Handle
        type="source"
        position={Position.Right}
        className={`!w-3 !h-3 !${styles.infoBg} !border-2 !border-[#0f172a] !right-[-6px]`}
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <ArrowRightLeft size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className={`text-[12px] font-bold ${styles.cardText} truncate leading-tight`}>
              {data.label}
            </h3>
            <p className={`text-[10px] ${styles.cardTextMuted} truncate mt-0.5`}>{data.sublabel}</p>
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
          <div className={`mt-2 pt-2 border-t ${styles.cardBorder}`}>
            <span className={`text-[9px] ${styles.muted}`}>
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
        className={`!w-3 !h-3 !${styles.warningBg} !border-2 !border-[#0f172a] !left-[-6px]`}
      />
      <Handle
        type="source"
        position={Position.Right}
        className={`!w-3 !h-3 !${styles.warningBg} !border-2 !border-[#0f172a] !right-[-6px]`}
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <GitBranch size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className={`text-[12px] font-bold ${styles.cardText} truncate leading-tight`}>
              {data.label}
            </h3>
            <p className={`text-[10px] ${styles.cardTextMuted} truncate mt-0.5`}>{data.sublabel}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 mt-2">
          <StatusBadge status={data.status} />
        </div>
        <div className={`mt-2 pt-2 border-t ${styles.cardBorder} flex items-center gap-3`}>
          <span className={`text-[9px] ${styles.muted}`}>{data.nodeCount} 节点</span>
          {(data.expressionsCount ?? 0) > 0 && (
            <span className={`text-[9px] ${styles.muted}`}>{data.expressionsCount} 表达式</span>
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
        className={`!w-3 !h-3 !${styles.successBg} !border-2 !border-[#0f172a] !left-[-6px]`}
      />
      <Handle
        type="source"
        position={Position.Right}
        className={`!w-3 !h-3 !${styles.successBg} !border-2 !border-[#0f172a] !right-[-6px]`}
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <Table2 size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className={`text-[12px] font-bold ${styles.cardText} truncate leading-tight`}>
              {data.label}
            </h3>
            <p className={`text-[10px] ${styles.cardTextMuted} truncate mt-0.5`}>{data.sublabel}</p>
          </div>
        </div>
        <div className={`mt-2 pt-2 border-t ${styles.cardBorder} flex items-center gap-3`}>
          {data.rowCount !== undefined && (
            <span className={`text-[9px] ${styles.muted}`}>
              {data.rowCount.toLocaleString()} 行
            </span>
          )}
          {colCount > 0 && (
            <span className={`text-[9px] ${styles.muted}`}>{colCount} 列</span>
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
        className={`!w-3 !h-3 !${styles.dangerBg} !border-2 !border-[#0f172a] !left-[-6px]`}
      />
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${theme.accentBg} ${theme.accentText}`}>
            <Box size={14} />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className={`text-[12px] font-bold ${styles.cardText} truncate leading-tight`}>
              {data.label}
            </h3>
            <p className={`text-[10px] ${styles.cardTextMuted} truncate mt-0.5`}>{data.sublabel}</p>
          </div>
        </div>
        <div className="mt-2">
          <span className={`text-[9px] px-1.5 py-0.5 rounded font-medium ${theme.accentBg} ${theme.accentText}`}>
            {data.domain}
          </span>
        </div>
        {data.propertiesCount > 0 && (
          <div className={`mt-2 pt-2 border-t ${styles.cardBorder}`}>
            <span className={`text-[9px] ${styles.muted}`}>{data.propertiesCount} 属性</span>
          </div>
        )}
      </div>
    </div>
  );
}


export const nodeTypes = { source: LineageSourceNode, ingest: LineageIngestNode, pipeline: LineagePipelineNode, dataset: LineageDatasetNode, ontology: LineageOntologyNode };
