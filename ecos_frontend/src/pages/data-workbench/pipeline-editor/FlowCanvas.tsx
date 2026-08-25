/**
 * FlowCanvas — ReactFlow canvas with custom pipeline node types
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React, { useCallback } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  addEdge,
  Connection,
  Node,
  Edge,
  MarkerType,
  BackgroundVariant,
  type NodeTypes,
  type NodeProps,
  type HandleProps,
  Handle,
  Position,
} from '@xyflow/react';
import {
  Database, Settings, ArrowLeftRight, BarChart3,
  HardDrive, Loader2, CheckCircle, AlertCircle,
} from 'lucide-react';
import type { NodeStatus, NodeConfig } from './types';
import EmptyCanvas from './EmptyCanvas';
import { useTheme } from '../../../components/ThemeContext';

// ─── Status badge ─────────────────────────────────────────

const StatusBadge: React.FC<{ status: NodeStatus }> = React.memo(({ status }) => {
  const { styles } = useTheme();
  switch (status) {
    case 'running':
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.accentText} ${styles.infoBg} px-1.5 py-0.5 rounded-full`}>
          <Loader2 size={10} className="animate-spin" /> running
        </span>
      );
    case 'success':
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.successText} ${styles.successBg} px-1.5 py-0.5 rounded-full`}>
          <CheckCircle size={10} /> success
        </span>
      );
    case 'error':
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.dangerText} ${styles.dangerBg} px-1.5 py-0.5 rounded-full`}>
          <AlertCircle size={10} /> error
        </span>
      );
    default:
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.muted} ${styles.sidebarBg} px-1.5 py-0.5 rounded-full`}>
          <CheckCircle size={10} /> idle
        </span>
      );
  }
});

// ─── Custom Handle ────────────────────────────────────────

const NodeHandle: React.FC<HandleProps & { position: Position }> = React.memo(
  ({ position, ...rest }) => {
    const { styles } = useTheme();
    return (
    <Handle
      {...rest}
      position={position}
      className={`!w-3 !h-3 !border-2 !${styles.cardBorder} !${styles.sidebarBg} hover:!${styles.infoBg} transition-colors`}
    />
    );
  }
);

// ─── SourceNode ───────────────────────────────────────────

const SourceNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const { styles } = useTheme();
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  return (
    <div
      className={`relative min-w-[180px] rounded-xl border-2 ${styles.cardBg} shadow-md transition-shadow ${
        selected ? `${styles.infoBorder} shadow-lg ring-2 ${styles.accentBorder}` : styles.infoBorder
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className={`flex items-center gap-2 px-3 py-2 ${styles.infoBg} rounded-t-xl border-b ${styles.accentBorder}`}>
        <Database size={16} className={`${styles.accentText} flex-shrink-0`} />
        <span className={`text-xs font-semibold ${styles.accentText} truncate flex-1`}>
          {config.label || 'Source'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className={`px-3 py-2 text-xs ${styles.cardTextMuted}`}>
        {config.sourceTable ? (
          <div className="truncate" title={config.sourceTable}>
            表: <span className={`font-mono ${styles.accentText}`}>{config.sourceTable}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>选择数据源表...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── TransformNode ────────────────────────────────────────

const TransformNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const { styles } = useTheme();
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  const ruleCount = config.transformRules?.length || 0;
  return (
    <div
      className={`relative min-w-[180px] rounded-xl border-2 ${styles.cardBg} shadow-md transition-shadow ${
        selected ? `${styles.successBorder} shadow-lg ring-2 ${styles.successBorder}` : styles.successBorder
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className={`flex items-center gap-2 px-3 py-2 ${styles.successBg} rounded-t-xl border-b ${styles.successBorder}`}>
        <Settings size={16} className={`${styles.successText} flex-shrink-0`} />
        <span className={`text-xs font-semibold ${styles.successText} truncate flex-1`}>
          {config.label || 'Transform'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className={`px-3 py-2 text-xs ${styles.cardTextMuted}`}>
        {ruleCount > 0 ? (
          <div>
            转换规则: <span className={`font-semibold ${styles.successText}`}>{ruleCount} 条</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>配置转换规则...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── JoinNode ─────────────────────────────────────────────

const JoinNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const { styles } = useTheme();
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  const condCount = config.joinConditions?.length || 0;
  return (
    <div
      className={`relative min-w-[200px] rounded-xl border-2 ${styles.cardBg} shadow-md transition-shadow ${
        selected ? `${styles.infoBorder} shadow-lg ring-2 ${styles.infoBorder}` : styles.infoBorder
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className={`flex items-center gap-2 px-3 py-2 ${styles.infoBg} rounded-t-xl border-b ${styles.infoBorder}`}>
        <ArrowLeftRight size={16} className={`${styles.infoText} flex-shrink-0`} />
        <span className={`text-xs font-semibold ${styles.infoText} truncate flex-1`}>
          {config.label || 'Join'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className={`px-3 py-2 text-xs ${styles.cardTextMuted} space-y-1`}>
        <div>
          类型: <span className={`font-semibold ${styles.infoText}`}>{config.joinType || 'INNER'}</span>
        </div>
        {condCount > 0 ? (
          <div>
            条件: <span className={`font-semibold ${styles.infoText}`}>{condCount} 条</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>配置 JOIN 条件...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── AggregateNode ────────────────────────────────────────

const AggregateNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const { styles } = useTheme();
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  const groupByCols = config.aggregateGroupBy?.length || 0;
  return (
    <div
      className={`relative min-w-[200px] rounded-xl border-2 ${styles.cardBg} shadow-md transition-shadow ${
        selected ? `${styles.warningBorder} shadow-lg ring-2 ${styles.warningBorder}` : styles.warningBorder
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className={`flex items-center gap-2 px-3 py-2 ${styles.warningBg} rounded-t-xl border-b ${styles.warningBorder}`}>
        <BarChart3 size={16} className={`${styles.warningText} flex-shrink-0`} />
        <span className={`text-xs font-semibold ${styles.warningText} truncate flex-1`}>
          {config.label || 'Aggregate'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className={`px-3 py-2 text-xs ${styles.cardTextMuted}`}>
        {groupByCols > 0 ? (
          <div>
            GROUP BY: <span className={`font-semibold ${styles.warningText}`}>{groupByCols} 列</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>配置分组列...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── SinkNode ─────────────────────────────────────────────

const SinkNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const { styles } = useTheme();
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  return (
    <div
      className={`relative min-w-[180px] rounded-xl border-2 ${styles.cardBg} shadow-md transition-shadow ${
        selected ? `${styles.cardBorder} shadow-lg ring-2 ${styles.inputBorder}` : styles.inputBorder
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className={`flex items-center gap-2 px-3 py-2 ${styles.cardBg} rounded-t-xl border-b ${styles.cardBorder}`}>
        <HardDrive size={16} className={`${styles.cardTextMuted} flex-shrink-0`} />
        <span className={`text-xs font-semibold ${styles.cardText} truncate flex-1`}>
          {config.label || 'Sink'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className={`px-3 py-2 text-xs ${styles.cardTextMuted}`}>
        {config.targetTable ? (
          <div className="truncate" title={config.targetTable}>
            目标: <span className={`font-mono ${styles.cardTextMuted}`}>{config.targetTable}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>选择目标表...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── Node type registry ───────────────────────────────────

export const CUSTOM_NODE_TYPES: NodeTypes = {
  source: SourceNode,
  transform: TransformNode,
  join: JoinNode,
  aggregate: AggregateNode,
  sink: SinkNode,
};

// ─── MiniMap color helper ─────────────────────────────────

export const miniMapNodeColor = (node: Node): string => {
  switch (node.type) {
    case 'source': return '#3b82f6';
    case 'transform': return '#10b981';
    case 'join': return '#8b5cf6';
    case 'aggregate': return '#f97316';
    case 'sink': return '#64748b';
    default: return '#94a3b8';
  }
};

// ─── FlowCanvas Props ─────────────────────────────────────

interface FlowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: ReturnType<typeof import('@xyflow/react').useNodesState>[2];
  onEdgesChange: ReturnType<typeof import('@xyflow/react').useEdgesState>[2];
  onConnect: (connection: Connection) => void;
  onInit: (instance: unknown) => void;
  onNodeClick: (event: React.MouseEvent, node: Node) => void;
  onPaneClick: () => void;
  onDragOver: (event: React.DragEvent<HTMLDivElement>) => void;
  onDrop: (event: React.DragEvent<HTMLDivElement>) => void;
  styles: Record<string, string>;
}

const FlowCanvas: React.FC<FlowCanvasProps> = ({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onInit,
  onNodeClick,
  onPaneClick,
  onDragOver,
  onDrop,
  styles,
}) => {
  const onConnectWrapped = useCallback(
    (connection: Connection) => {
      onConnect(connection);
    },
    [onConnect]
  );

  return (
    <div className="flex-1 h-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnectWrapped}
        onInit={onInit}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        onDragOver={onDragOver}
        onDrop={onDrop}
        nodeTypes={CUSTOM_NODE_TYPES}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        multiSelectionKeyCode={['Shift', 'Meta', 'Control']}
        selectionKeyCode="Shift"
        snapToGrid
        snapGrid={[16, 16]}
        defaultEdgeOptions={{
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 2 },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 16, height: 16 },
        }}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1.5} color="#e2e8f0" />
        <Controls
          className={`!rounded-lg !shadow-sm ${styles.cardBg} ${styles.cardBorder}`}
          position="bottom-right"
        />
        <MiniMap
          nodeColor={miniMapNodeColor}
          maskColor="rgba(0,0,0,0.05)"
          className={`!rounded-lg ${styles.sidebarBg} ${styles.sidebarBorder}`}
          position="bottom-left"
        />
        {nodes.length === 0 && (
          <Panel position="top-center">
            <EmptyCanvas />
          </Panel>
        )}
        <Panel position="top-left" className="!ml-2 !mt-2">
          <div className={`backdrop-blur-sm border rounded-lg px-3 py-1.5 text-xs shadow-sm ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}>
            节点: <span className={`font-semibold ${styles.cardText}`}>{nodes.length}</span>
            <span className={`mx-1.5 ${styles.cardTextMuted}`}>|</span>
            连线: <span className={`font-semibold ${styles.cardText}`}>{edges.length}</span>
          </div>
        </Panel>
      </ReactFlow>
    </div>
  );
};

export default FlowCanvas;
