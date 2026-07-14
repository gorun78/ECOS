/**
 * PipelineFlowEditor — 可视化拖拽 Pipeline DAG 编辑器
 * 基于 @xyflow/react (v12.11.0) 的完整拖拽式数据管道编排器。
 *
 * @license Apache-2.0
 */

import React, { useState, useCallback, useRef, useMemo, useEffect } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  useNodesState,
  useEdgesState,
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
import '@xyflow/react/dist/style.css';
import {
  Play, Save, Database, GitBranch, ArrowLeftRight, BarChart3,
  HardDrive, Settings, Plus, Trash2, X, AlertCircle, CheckCircle,
  Loader2, GripVertical, Zap, Cpu, Workflow, ChevronDown, ArrowLeft,
  Download, Upload,
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import type { DataConnection, DataPipeline, TableInfo } from './types';

// ─── Types (extracted) ─────────────────────────────────────
import type { NodeStatus, TransformRule, JoinCondition, NodeConfig, PipelineData, PipelineFlowEditorProps } from './pipeline-editor/types';

// ─── Node palette definitions ─────────────────────────────

interface PaletteItem {
  type: string;
  label: string;
  icon: React.FC<{ size?: number; className?: string }>;
  color: string;
  bgColor: string;
  borderColor: string;
}

const PALETTE_ITEMS: PaletteItem[] = [
  {
    type: 'source',
    label: 'Source 源',
    icon: ({ size, className }) => <Database size={size} className={className} />,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-500',
  },
  {
    type: 'transform',
    label: 'Transform 转换',
    icon: ({ size, className }) => <Settings size={size} className={className} />,
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-50',
    borderColor: 'border-emerald-500',
  },
  {
    type: 'join',
    label: 'Join 关联',
    icon: ({ size, className }) => <ArrowLeftRight size={size} className={className} />,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
    borderColor: 'border-purple-500',
  },
  {
    type: 'aggregate',
    label: 'Aggregate 聚合',
    icon: ({ size, className }) => <BarChart3 size={size} className={className} />,
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-500',
  },
  {
    type: 'sink',
    label: 'Sink 输出',
    icon: ({ size, className }) => <HardDrive size={size} className={className} />,
    color: 'text-slate-600',
    bgColor: 'bg-slate-100',
    borderColor: 'border-slate-400',
  },
];

// ─── Status badge ─────────────────────────────────────────

const StatusBadge: React.FC<{ status: NodeStatus }> = React.memo(({ status }) => {
  switch (status) {
    case 'running':
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded-full">
          <Loader2 size={10} className="animate-spin" /> running
        </span>
      );
    case 'success':
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-emerald-600 bg-emerald-100 px-1.5 py-0.5 rounded-full">
          <CheckCircle size={10} /> success
        </span>
      );
    case 'error':
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-red-600 bg-red-100 px-1.5 py-0.5 rounded-full">
          <AlertCircle size={10} /> error
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded-full">
          <CheckCircle size={10} /> idle
        </span>
      );
  }
});

// ─── Custom Handle ────────────────────────────────────────

const NodeHandle: React.FC<HandleProps & { position: Position }> = React.memo(
  ({ position, ...rest }) => (
    <Handle
      {...rest}
      position={position}
      className="!w-3 !h-3 !border-2 !border-white !bg-slate-400 hover:!bg-blue-500 transition-colors"
    />
  )
);

// ─── SourceNode ───────────────────────────────────────────

const SourceNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  return (
    <div
      className={`relative min-w-[180px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-blue-500 shadow-lg ring-2 ring-blue-200' : 'border-blue-300'
      }`}
    >
      {/* Top handles */}
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      {/* Header */}
      <div className="flex items-center gap-2 px-3 py-2 bg-blue-50 rounded-t-xl border-b border-blue-200">
        <Database size={16} className="text-blue-600 flex-shrink-0" />
        <span className="text-xs font-semibold text-blue-800 truncate flex-1">
          {config.label || 'Source'}
        </span>
        <StatusBadge status={status} />
      </div>
      {/* Body */}
      <div className="px-3 py-2 text-xs text-slate-600">
        {config.sourceTable ? (
          <div className="truncate" title={config.sourceTable}>
            表: <span className="font-mono text-blue-700">{config.sourceTable}</span>
          </div>
        ) : (
          <div className="italic text-slate-400">选择数据源表...</div>
        )}
      </div>
      {/* Bottom handles */}
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── TransformNode ────────────────────────────────────────

const TransformNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  const ruleCount = config.transformRules?.length || 0;

  return (
    <div
      className={`relative min-w-[180px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-emerald-500 shadow-lg ring-2 ring-emerald-200' : 'border-emerald-300'
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className="flex items-center gap-2 px-3 py-2 bg-emerald-50 rounded-t-xl border-b border-emerald-200">
        <Settings size={16} className="text-emerald-600 flex-shrink-0" />
        <span className="text-xs font-semibold text-emerald-800 truncate flex-1">
          {config.label || 'Transform'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className="px-3 py-2 text-xs text-slate-600">
        {ruleCount > 0 ? (
          <div>
            转换规则: <span className="font-semibold text-emerald-700">{ruleCount} 条</span>
          </div>
        ) : (
          <div className="italic text-slate-400">配置转换规则...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── JoinNode ─────────────────────────────────────────────

const JoinNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  const condCount = config.joinConditions?.length || 0;

  return (
    <div
      className={`relative min-w-[200px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-purple-500 shadow-lg ring-2 ring-purple-200' : 'border-purple-300'
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className="flex items-center gap-2 px-3 py-2 bg-purple-50 rounded-t-xl border-b border-purple-200">
        <ArrowLeftRight size={16} className="text-purple-600 flex-shrink-0" />
        <span className="text-xs font-semibold text-purple-800 truncate flex-1">
          {config.label || 'Join'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className="px-3 py-2 text-xs text-slate-600 space-y-1">
        <div>
          类型: <span className="font-semibold text-purple-700">{config.joinType || 'INNER'}</span>
        </div>
        {condCount > 0 ? (
          <div>
            条件: <span className="font-semibold text-purple-700">{condCount} 条</span>
          </div>
        ) : (
          <div className="italic text-slate-400">配置 JOIN 条件...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── AggregateNode ────────────────────────────────────────

const AggregateNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';
  const groupByCols = config.aggregateGroupBy?.length || 0;

  return (
    <div
      className={`relative min-w-[200px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-orange-500 shadow-lg ring-2 ring-orange-200' : 'border-orange-300'
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className="flex items-center gap-2 px-3 py-2 bg-orange-50 rounded-t-xl border-b border-orange-200">
        <BarChart3 size={16} className="text-orange-600 flex-shrink-0" />
        <span className="text-xs font-semibold text-orange-800 truncate flex-1">
          {config.label || 'Aggregate'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className="px-3 py-2 text-xs text-slate-600">
        {groupByCols > 0 ? (
          <div>
            GROUP BY: <span className="font-semibold text-orange-700">{groupByCols} 列</span>
          </div>
        ) : (
          <div className="italic text-slate-400">配置分组列...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── SinkNode ─────────────────────────────────────────────

const SinkNode: React.FC<NodeProps> = React.memo(({ data, selected }) => {
  const config = (data ?? {}) as unknown as NodeConfig;
  const status: NodeStatus = config.nodeStatus || 'idle';

  return (
    <div
      className={`relative min-w-[180px] rounded-xl border-2 bg-white shadow-md transition-shadow ${
        selected ? 'border-slate-500 shadow-lg ring-2 ring-slate-200' : 'border-slate-300'
      }`}
    >
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className="flex items-center gap-2 px-3 py-2 bg-slate-50 rounded-t-xl border-b border-slate-200">
        <HardDrive size={16} className="text-slate-600 flex-shrink-0" />
        <span className="text-xs font-semibold text-slate-800 truncate flex-1">
          {config.label || 'Sink'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className="px-3 py-2 text-xs text-slate-600">
        {config.targetTable ? (
          <div className="truncate" title={config.targetTable}>
            目标: <span className="font-mono text-slate-700">{config.targetTable}</span>
          </div>
        ) : (
          <div className="italic text-slate-400">选择目标表...</div>
        )}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// ─── Node type registry ───────────────────────────────────

const CUSTOM_NODE_TYPES: NodeTypes = {
  source: SourceNode,
  transform: TransformNode,
  join: JoinNode,
  aggregate: AggregateNode,
  sink: SinkNode,
};

// ─── Collapse toggle icon ─────────────────────────────────

const CollapseToggle: React.FC<{
  collapsed: boolean;
  onClick: () => void;
  label: string;
}> = ({ collapsed, onClick, label }) => (
  <button
    onClick={onClick}
    className="flex items-center justify-between w-full px-3 py-2 text-xs font-semibold text-slate-500 hover:bg-slate-100 transition-colors"
  >
    <span>{label}</span>
    <ChevronDown
      size={14}
      className={`transition-transform duration-200 ${collapsed ? '-rotate-90' : 'rotate-0'}`}
    />
  </button>
);

// ─── Property Panel (extracted) ────────────────────────────
import PropertyPanel from './pipeline-editor/PropertyPanel';

// ─── Toast (extracted) ─────────────────────────────────────
import Toast from './pipeline-editor/Toast';

// ─── Empty state (extracted) ───────────────────────────────
import EmptyCanvas from './pipeline-editor/EmptyCanvas';

// ─── Git + Execution Integration ────────────────────────────
import GitCommitDialog from './pipeline-editor/GitCommitDialog';
import PipelineExecutionMonitor from './pipeline-editor/PipelineExecutionMonitor';

// ─── Main Component ───────────────────────────────────────

const PipelineFlowEditor: React.FC<PipelineFlowEditorProps> = ({
  connections,
  pipelines,
  onSave,
  onExecute,
  showToast,
  computeEngine,
  onEngineChange,
  editingPipeline,
  onBack,
}) => {
  const { styles } = useTheme();
  // ── State ──
  const [pipelineName, setPipelineName] = useState(editingPipeline?.name || '新建 Pipeline');
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(
    null
  );
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<any>(null);

  // ── Git integration states ──
  const [showGitDialog, setShowGitDialog] = useState(false);
  const [showGitLoadDialog, setShowGitLoadDialog] = useState(false);
  const [gitLoadUrl, setGitLoadUrl] = useState('');
  const [loadingFromGit, setLoadingFromGit] = useState(false);

  // ── Execution monitor states ──
  const [activeRunId, setActiveRunId] = useState<string | null>(null);
  const [showMonitor, setShowMonitor] = useState(false);

  // ── Toast helper ──
  const showLocalToast = useCallback(
    (type: 'success' | 'error' | 'info', msg: string) => {
      setToast({ type, msg });
      setTimeout(() => setToast(null), 3000);
      showToast?.(type, msg);
    },
    [showToast]
  );

  // ── Sync pipelineName when editingPipeline changes ──
  useEffect(() => {
    if (editingPipeline?.name) {
      setPipelineName(editingPipeline.name);
    }
  }, [editingPipeline]);

  // ── Node counter for IDs ──
  const nodeCounter = useRef(0);
  const nextNodeId = useCallback(() => {
    nodeCounter.current += 1;
    return `node-${Date.now()}-${nodeCounter.current}`;
  }, []);

  // ── onConnect callback ──
  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) =>
        addEdge(
          {
            ...connection,
            animated: true,
            style: { stroke: '#94a3b8', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 16, height: 16 },
          },
          eds
        )
      );
    },
    [setEdges]
  );

  // ── Drag from palette to canvas ──
  const onDragOver = useCallback((event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      const paletteType = event.dataTransfer.getData('application/pipelinenodetype');
      if (!paletteType || !reactFlowWrapper.current || !reactFlowInstance) return;

      const bounds = reactFlowWrapper.current.getBoundingClientRect();
      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top,
      });

      const paletteItem = PALETTE_ITEMS.find((p) => p.type === paletteType);
      const newNode: Node = {
        id: nextNodeId(),
        type: paletteType,
        position,
        data: {
          label: paletteItem?.label.replace(/^(\S+).*/, '$1') || paletteType,
          nodeType: paletteType,
          nodeStatus: 'idle',
        },
      };
      setNodes((nds) => [...nds, newNode]);
      showLocalToast('info', `已添加 ${paletteItem?.label || paletteType} 节点`);
    },
    [reactFlowInstance, nextNodeId, setNodes, showLocalToast]
  );

  // ── Node click → select ──
  const onNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node);
  }, []);

  const onPaneClick = useCallback(() => {
    setSelectedNode(null);
  }, []);

  // ── Update node config ──
  const updateNodeConfig = useCallback(
    (nodeId: string, partialConfig: Partial<NodeConfig>) => {
      setNodes((nds) =>
        nds.map((n) => {
          if (n.id !== nodeId) return n;
          const newData = { ...(n.data as unknown as NodeConfig), ...partialConfig };
          // If nodeType changed, cast to match the new type
          if (partialConfig.nodeType && n.type !== partialConfig.nodeType) {
            return { ...n, type: partialConfig.nodeType, data: newData };
          }
          return { ...n, data: newData };
        })
      );
      // Keep selectedNode in sync
      setSelectedNode((prev) => {
        if (prev?.id === nodeId) {
          return { ...prev, data: { ...(prev.data as unknown as NodeConfig), ...partialConfig } };
        }
        return prev;
      });
    },
    [setNodes]
  );

  // ── Delete node ──
  const deleteNode = useCallback(
    (nodeId: string) => {
      setNodes((nds) => nds.filter((n) => n.id !== nodeId));
      setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
      setSelectedNode(null);
      showLocalToast('info', '节点已删除');
    },
    [setNodes, setEdges, showLocalToast]
  );

  // ── Clear all ──
  const clearCanvas = useCallback(() => {
    if (nodes.length === 0 && edges.length === 0) return;
    setNodes([]);
    setEdges([]);
    setSelectedNode(null);
    nodeCounter.current = 0;
    showLocalToast('info', '画布已清空');
  }, [nodes, edges, setNodes, setEdges, showLocalToast]);

  // ── Save pipeline ──
  const handleSave = useCallback(() => {
    if (!pipelineName.trim()) {
      showLocalToast('error', '请输入 Pipeline 名称');
      return;
    }
    const pipelineData: PipelineData = {
      name: pipelineName.trim(),
      nodes,
      edges,
      computeEngine,
    };
    onSave(pipelineData);
    showLocalToast('success', 'Pipeline 已保存');
  }, [pipelineName, nodes, edges, computeEngine, onSave, showLocalToast]);

  // ── Execute pipeline ──
  const handleExecute = useCallback(() => {
    if (nodes.length === 0) {
      showLocalToast('error', '画布为空，请先添加节点');
      return;
    }
    // Set all nodes to 'running' status
    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: { ...(n.data as unknown as NodeConfig), nodeStatus: 'running' as NodeStatus },
      }))
    );
    onExecute(pipelineName.trim() || 'untitled');
    showLocalToast('info', 'Pipeline 已提交执行');
  }, [nodes, pipelineName, onExecute, setNodes, showLocalToast]);

  // ── Drag from palette start ──
  const onDragStart = (event: React.DragEvent<HTMLDivElement>, nodeType: string) => {
    event.dataTransfer.setData('application/pipelinenodetype', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  // ── Handle escape key to deselect ──
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setSelectedNode(null);
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleSave();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleSave]);

  // ── miniMap node colors ──
  const miniMapNodeColor = useCallback((node: Node) => {
    switch (node.type) {
      case 'source':
        return '#3b82f6';
      case 'transform':
        return '#10b981';
      case 'join':
        return '#8b5cf6';
      case 'aggregate':
        return '#f97316';
      case 'sink':
        return '#64748b';
      default:
        return '#94a3b8';
    }
  }, []);

  // ── Render ──
  return (
    <div className={`h-full flex flex-col ${styles.cardBg}`}>
      {/* ── Top Toolbar ── */}
      <div className={`flex items-center justify-between px-4 py-2 border-b shrink-0 ${styles.appBg} ${styles.appText} ${styles.appBorder}`}>
        <div className="flex items-center gap-3">
          {/* Back button */}
          {onBack && (
            <button
              onClick={onBack}
              className="flex items-center gap-1 text-xs text-slate-400 hover:text-blue-400 transition-colors"
              title="返回列表"
            >
              <ArrowLeft size={14} />
              返回列表
            </button>
          )}

          {/* Pipeline name */}
          <GitBranch size={18} className="text-blue-400" />
          <input
            type="text"
            value={pipelineName}
            onChange={(e) => setPipelineName(e.target.value)}
            className="bg-transparent border-b border-slate-600 px-1 py-0.5 text-sm font-medium text-white outline-none focus:border-blue-400 transition-colors w-48"
            placeholder="Pipeline 名称"
          />

          {/* Edit / New indicator */}
          <span
            className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
              editingPipeline
                ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
            }`}
          >
            {editingPipeline ? '编辑中' : '新建'}
          </span>

          {/* Pipeline status tag (only in edit mode) */}
          {editingPipeline && (
            <span
              className={`text-[10px] px-1.5 py-0.5 rounded-full border font-medium ${
                editingPipeline.status === 'active'
                  ? 'bg-green-500/20 text-green-400 border-green-500/30'
                  : editingPipeline.status === 'draft'
                    ? 'bg-slate-500/20 text-slate-400 border-slate-500/30'
                    : editingPipeline.status === 'running'
                      ? 'bg-blue-500/20 text-blue-400 border-blue-500/30'
                      : editingPipeline.status === 'error'
                        ? 'bg-red-500/20 text-red-400 border-red-500/30'
                        : 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
              }`}
            >
              {editingPipeline.status === 'active' && '● 活跃'}
              {editingPipeline.status === 'draft' && '◌ 草稿'}
              {editingPipeline.status === 'running' && '◉ 运行中'}
              {editingPipeline.status === 'success' && '✓ 成功'}
              {editingPipeline.status === 'error' && '✕ 错误'}
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          {/* Engine switch */}
          <div className={`flex items-center gap-1.5 rounded-lg p-0.5 ${styles.cardBg}`}>
            <button
              onClick={() => onEngineChange('memory')}
              className={`flex items-center gap-1 px-3 py-1 text-xs rounded-md transition-colors ${
                computeEngine === 'memory'
                  ? 'bg-blue-600 text-white shadow'
                  : `${styles.cardTextMuted} hover:${styles.cardText}`
              }`}
            >
              <Zap size={12} />
              Memory
            </button>
            <button
              onClick={() => onEngineChange('doris')}
              className={`flex items-center gap-1 px-3 py-1 text-xs rounded-md transition-colors ${
                computeEngine === 'doris'
                  ? 'bg-blue-600 text-white shadow'
                  : `${styles.cardTextMuted} hover:${styles.cardText}`
              }`}
            >
              <Cpu size={12} />
              Doris
            </button>
          </div>

          <div className={`w-px h-5 mx-1 ${styles.appBorder}`} />

          {/* Clear button */}
          <button
            onClick={clearCanvas}
            className={`flex items-center gap-1 px-2.5 py-1 text-xs transition-colors ${styles.cardTextMuted} hover:${styles.cardText}`}
            title="清空画布"
          >
            <Trash2 size={13} />
            清空
          </button>

          {/* Execute button */}
          <button
            onClick={handleExecute}
            className="flex items-center gap-1.5 px-3 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-medium transition-colors"
          >
            <Play size={13} />
            执行
          </button>

          {/* Save button */}
          <button
            onClick={handleSave}
            className="flex items-center gap-1.5 px-3 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-medium transition-colors"
          >
            <Save size={13} />
            保存
          </button>
        </div>
      </div>

      {/* ── Main Content ── */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left: Node Palette */}
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

          {/* Available tables info */}
          <div className={`border-t p-2 ${styles.sidebarBorder}`}>
            <div className={`text-[10px] leading-tight ${styles.cardTextMuted}`}>
              可用数据源: <span className={`font-semibold ${styles.cardText}`}>{connections.length}</span>
            </div>
            <div className={`text-[10px] leading-tight ${styles.cardTextMuted}`}>
              已保存管道: <span className={`font-semibold ${styles.cardText}`}>{pipelines.length}</span>
            </div>
          </div>
        </div>

        {/* Center: ReactFlow Canvas */}
        <div className="flex-1" ref={reactFlowWrapper}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onInit={setReactFlowInstance}
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
            {/* Grid background */}
            <Background
              variant={BackgroundVariant.Dots}
              gap={20}
              size={1.5}
              color="#e2e8f0"
            />

            {/* Controls */}
            <Controls
              className={`!rounded-lg !shadow-sm ${styles.cardBg} ${styles.cardBorder}`}
              position="bottom-right"
            />

            {/* MiniMap */}
            <MiniMap
              nodeColor={miniMapNodeColor}
              maskColor="rgba(0,0,0,0.05)"
              className={`!rounded-lg ${styles.sidebarBg} ${styles.sidebarBorder}`}
              position="bottom-left"
            />

            {/* Empty state */}
            {nodes.length === 0 && (
              <Panel position="top-center">
                <EmptyCanvas />
              </Panel>
            )}

            {/* Node count badge */}
            <Panel position="top-left" className="!ml-2 !mt-2">
              <div className={`backdrop-blur-sm border rounded-lg px-3 py-1.5 text-xs shadow-sm ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}>
                节点: <span className={`font-semibold ${styles.cardText}`}>{nodes.length}</span>
                <span className={`mx-1.5 ${styles.cardTextMuted}`}>|</span>
                连线: <span className={`font-semibold ${styles.cardText}`}>{edges.length}</span>
              </div>
            </Panel>
          </ReactFlow>
        </div>

        {/* Right: Property Panel */}
        <PropertyPanel
          node={selectedNode}
          connections={connections}
          onUpdateNode={updateNodeConfig}
          onDeleteNode={deleteNode}
          onClose={() => setSelectedNode(null)}
        />
      </div>

      {/* ── Toast ── */}
      {toast && (
        <Toast type={toast.type} message={toast.msg} onClose={() => setToast(null)} />
      )}
    </div>
  );
};

export default PipelineFlowEditor;
