/**
 * LineageMapView — 全链路数据血缘地图 + 右侧4Tab信息面板
 *
 * 基于 @xyflow/react v12.11.0 实现五阶段横向布局的血缘地图：
 *   阶段1: 物理数据源 (DataConnection)
 *   阶段2: 数据同步入湖 (DataSyncTask)
 *   阶段3: ETL融合管道 (DataPipeline)
 *   阶段4: 清洗数据集 (Dataset)
 *   阶段5: Ontology业务实体 (ObjectType)
 *
 * 右侧信息面板4个Tab：数据集信息 / 选择关系 / 转换规格 / 执行信息
 *
 * @license Apache-2.0
 */

import React, { useState, useMemo, useCallback } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  Handle,
  Position,
  MarkerType,
  type Node,
  type Edge,
  type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  Database,
  ArrowRightLeft,
  GitBranch,
  Table2,
  Box,
  Clock,
  AlertCircle,
  CheckCircle,
  XCircle,
  Loader2,
  Play,
  Columns,
  Link2,
  Code2,
  FileCode,
  BarChart3,
  X,
  Activity,
  Server,
  Shield,
  Hash,
  Calendar,
  Type,
  RefreshCw,
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import type {
  DataConnection,
  DataSyncTask,
  DataPipeline,
  DataHealthCheck,
  ObjectType,
  Dataset,
} from './types';

// ─── Types (extracted) ─────────────────────────────────────
import type { LineageMapViewProps } from './lineage/types';

import type { SourceNodeData, IngestNodeData, PipelineNodeData, DatasetNodeData, OntologyNodeData } from './lineage/types';

// ─── Custom nodes (extracted) ──────────────────────────────
import { StatusBadge, LineageSourceNode, LineageIngestNode, LineagePipelineNode, LineageDatasetNode, LineageOntologyNode, nodeTypes } from './lineage/nodes';


// ─── 布局常量 ────────────────────────────────────────────────

const STAGE_LAYOUT = {
  source:    { x: 40,   label: '物理数据源' },
  ingest:    { x: 310,  label: '数据同步入湖' },
  pipeline:  { x: 580,  label: 'ETL融合管道' },
  dataset:   { x: 850,  label: '清洗数据集' },
  ontology:  { x: 1120, label: 'Ontology实体' },
} as const;

const NODE_GAP_Y = 120;
const NODE_WIDTH = 200;
const STAGE_HEADER_HEIGHT = 36;

// ─── 阶段标识色 ──────────────────────────────────────────────

const stageColors: Record<string, string> = {
  source:   'text-blue-400 border-blue-500/40 bg-blue-500/10',
  ingest:   'text-purple-400 border-purple-500/40 bg-purple-500/10',
  pipeline: 'text-amber-400 border-amber-500/40 bg-amber-500/10',
  dataset:  'text-emerald-400 border-emerald-500/40 bg-emerald-500/10',
  ontology: 'text-rose-400 border-rose-500/40 bg-rose-500/10',
};

// ─── MiniMap 节点颜色 ────────────────────────────────────────

const miniMapNodeColor = (node: Node) => {
  const type = node.type || '';
  if (type.includes('Source')) return '#3b82f6';
  if (type.includes('Ingest')) return '#a855f7';
  if (type.includes('Pipeline')) return '#f59e0b';
  if (type.includes('Dataset')) return '#10b981';
  if (type.includes('Ontology')) return '#f43f5e';
  return '#64748b';
};

// ─── 默认边样式 ──────────────────────────────────────────────

const defaultEdgeOptions = {
  style: { stroke: '#475569', strokeWidth: 1.5, strokeDasharray: '6,3' },
  markerEnd: {
    type: MarkerType.ArrowClosed,
    color: '#475569',
    width: 14,
    height: 14,
  },
  animated: false,
};

// ─── Tab 定义 ────────────────────────────────────────────────

type TabId = 'dataset' | 'relations' | 'transform' | 'execution';

interface TabDef {
  id: TabId;
  label: string;
  icon: React.FC<{ size?: number }>;
}

const TABS: TabDef[] = [
  { id: 'dataset',    label: '数据集信息', icon: Table2 },
  { id: 'relations',  label: '选择关系',   icon: Link2 },
  { id: 'transform',  label: '转换规格',   icon: Code2 },
  { id: 'execution',  label: '执行信息',   icon: Activity },
];

// ─── 工具函数：获取节点关联数据 ──────────────────────────────

function getRelatedUpstream(
  nodeId: string,
  connections: DataConnection[],
  syncTasks: DataSyncTask[],
  pipelines: DataPipeline[],
  datasets: Dataset[],
  objectTypes: ObjectType[],
): { type: string; name: string; detail?: string }[] {
  const results: { type: string; name: string; detail?: string }[] = [];

  // If selected node is a syncTask, find its source connection
  const syncTask = syncTasks.find(t => t.id === nodeId);
  if (syncTask) {
    const conn = connections.find(c => c.id === syncTask.sourceConnectionId);
    if (conn) results.push({ type: '数据源', name: conn.name, detail: `${conn.type} @ ${conn.config.host}` });
  }

  // If selected node is a pipeline, find upstream syncTasks
  const pipeline = pipelines.find(p => p.id === nodeId);
  if (pipeline?.sourceConnections) {
    pipeline.sourceConnections.forEach(cId => {
      const conn = connections.find(c => c.id === cId);
      if (conn) results.push({ type: '数据源', name: conn.name, detail: conn.type });
    });
  }

  // If selected node is a dataset, find upstream pipelines
  const dataset = datasets.find(d => d.id === nodeId);
  if (dataset) {
    pipelines.forEach(p => {
      if (p.targetDataset === dataset.id) {
        results.push({ type: 'ETL管道', name: p.name, detail: p.status });
      }
    });
    syncTasks.forEach(t => {
      if (t.targetDatasetId === dataset.id) {
        results.push({ type: '同步任务', name: t.name, detail: t.status });
      }
    });
  }

  // If selected node is an objectType, find upstream datasets
  const objType = objectTypes.find(o => o.id === nodeId);
  if (objType) {
    datasets.forEach(d => {
      if (d.ontologyNodeId === objType.id) {
        results.push({ type: '数据集', name: d.name, detail: d.path });
      }
    });
  }

  return results;
}

function getRelatedDownstream(
  nodeId: string,
  connections: DataConnection[],
  syncTasks: DataSyncTask[],
  pipelines: DataPipeline[],
  datasets: Dataset[],
  objectTypes: ObjectType[],
): { type: string; name: string; detail?: string }[] {
  const results: { type: string; name: string; detail?: string }[] = [];

  // If selected node is a connection, find syncTasks using it
  const conn = connections.find(c => c.id === nodeId);
  if (conn) {
    syncTasks.filter(t => t.sourceConnectionId === conn.id).forEach(t => {
      results.push({ type: '同步任务', name: t.name, detail: t.status });
    });
  }

  // If selected node is a syncTask, find downstream
  const syncTask = syncTasks.find(t => t.id === nodeId);
  if (syncTask) {
    if (syncTask.targetDatasetId) {
      const ds = datasets.find(d => d.id === syncTask.targetDatasetId);
      if (ds) results.push({ type: '数据集', name: ds.name, detail: ds.path });
    }
  }

  // If selected node is a pipeline, find target datasets
  const pipeline = pipelines.find(p => p.id === nodeId);
  if (pipeline?.targetDataset) {
    const ds = datasets.find(d => d.id === pipeline.targetDataset);
    if (ds) results.push({ type: '数据集', name: ds.name, detail: ds.path });
  }

  // If selected node is a dataset, find downstream objectTypes
  const dataset = datasets.find(d => d.id === nodeId);
  if (dataset?.ontologyNodeId) {
    const obj = objectTypes.find(o => o.id === dataset.ontologyNodeId);
    if (obj) results.push({ type: 'Ontology实体', name: obj.name, detail: obj.domain });
  }

  return results;
}

// ─── 主组件 ──────────────────────────────────────────────────


export default function LineageMapView({
  connections,
  syncTasks,
  pipelines,
  healthChecks,
  objectTypes,
  datasets,
}: LineageMapViewProps) {
  const { styles } = useTheme();
  // ── 选中状态 ──
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabId>('dataset');
  const [panelOpen, setPanelOpen] = useState(false);

  // ── 从 props 构建节点 ──
  const initialNodes: Node[] = useMemo(() => {
    const nodes: Node[] = [];
    let yOffset = 0;

    // ── 阶段1: 数据源 ──
    connections.forEach((conn, i) => {
      nodes.push({
        id: conn.id,
        type: 'lineageSourceNode',
        position: {
          x: STAGE_LAYOUT.source.x,
          y: STAGE_HEADER_HEIGHT + NODE_GAP_Y * i,
        },
        data: {
          label: conn.name,
          sublabel: `${conn.config.host}:${conn.config.port}`,
          type: conn.type,
          status: conn.status,
          tablesAvailable: conn.tablesAvailable?.length || 0,
          raw: conn,
        } satisfies SourceNodeData,
      });
    });
    yOffset = Math.max(yOffset, connections.length);

    // ── 阶段2: 同步任务 ──
    syncTasks.forEach((task, i) => {
      nodes.push({
        id: task.id,
        type: 'lineageIngestNode',
        position: {
          x: STAGE_LAYOUT.ingest.x,
          y: STAGE_HEADER_HEIGHT + NODE_GAP_Y * i,
        },
        data: {
          label: task.name,
          sublabel: task.sourceTable || task.cronExpression || task.schedule || '',
          status: task.status,
          syncMode: task.syncMode,
          recordsSynced: task.recordsSynced ?? task.rowsSynced,
          raw: task,
        } satisfies IngestNodeData,
      });
    });
    yOffset = Math.max(yOffset, syncTasks.length);

    // ── 阶段3: ETL管道 ──
    pipelines.forEach((pipe, i) => {
      nodes.push({
        id: pipe.id,
        type: 'lineagePipelineNode',
        position: {
          x: STAGE_LAYOUT.pipeline.x,
          y: STAGE_HEADER_HEIGHT + NODE_GAP_Y * i,
        },
        data: {
          label: pipe.name,
          sublabel: pipe.computeEngine || '',
          status: pipe.status,
          nodeCount: pipe.nodes?.length || 0,
          expressionsCount: pipe.expressionsCount,
          raw: pipe,
        } satisfies PipelineNodeData,
      });
    });
    yOffset = Math.max(yOffset, pipelines.length);

    // ── 阶段4: 数据集 ──
    datasets.forEach((ds, i) => {
      nodes.push({
        id: ds.id,
        type: 'lineageDatasetNode',
        position: {
          x: STAGE_LAYOUT.dataset.x,
          y: STAGE_HEADER_HEIGHT + NODE_GAP_Y * i,
        },
        data: {
          label: ds.name,
          sublabel: ds.path,
          columns: ds.columns,
          rowCount: ds.rowCount,
          raw: ds,
        } satisfies DatasetNodeData,
      });
    });
    yOffset = Math.max(yOffset, datasets.length);

    // ── 阶段5: Ontology实体 ──
    objectTypes.forEach((obj, i) => {
      nodes.push({
        id: obj.id,
        type: 'lineageOntologyNode',
        position: {
          x: STAGE_LAYOUT.ontology.x,
          y: STAGE_HEADER_HEIGHT + NODE_GAP_Y * i,
        },
        data: {
          label: obj.displayName || obj.name,
          sublabel: obj.name,
          domain: obj.domain,
          propertiesCount: obj.properties?.length || 0,
          raw: obj,
        } satisfies OntologyNodeData,
      });
    });

    return nodes;
  }, [connections, syncTasks, pipelines, datasets, objectTypes]);

  // ── 从 props 构建边 ──
  const initialEdges: Edge[] = useMemo(() => {
    const edges: Edge[] = [];
    let edgeIdx = 0;

    // Connection → SyncTask
    syncTasks.forEach(task => {
      if (task.sourceConnectionId) {
        edges.push({
          id: `e-${edgeIdx++}`,
          source: task.sourceConnectionId,
          target: task.id,
          type: 'default',
          style: { stroke: '#3b82f6', strokeWidth: 1.5, strokeDasharray: '6,3' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#3b82f6', width: 12, height: 12 },
        });
      }
    });

    // SyncTask → Pipeline (通过 dataset 关联)
    const syncTaskDatasetMap = new Map<string, string[]>();
    syncTasks.forEach(t => {
      if (t.targetDatasetId) {
        const tasks = syncTaskDatasetMap.get(t.targetDatasetId) || [];
        tasks.push(t.id);
        syncTaskDatasetMap.set(t.targetDatasetId, tasks);
      }
    });

    pipelines.forEach(pipe => {
      if (pipe.targetDataset && syncTaskDatasetMap.has(pipe.targetDataset)) {
        const tasks = syncTaskDatasetMap.get(pipe.targetDataset)!;
        tasks.forEach(taskId => {
          edges.push({
            id: `e-${edgeIdx++}`,
            source: taskId,
            target: pipe.id,
            type: 'default',
            style: { stroke: '#a855f7', strokeWidth: 1.5, strokeDasharray: '6,3' },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#a855f7', width: 12, height: 12 },
          });
        });
      }
    });

    // Pipeline → Dataset
    pipelines.forEach(pipe => {
      if (pipe.targetDataset) {
        edges.push({
          id: `e-${edgeIdx++}`,
          source: pipe.id,
          target: pipe.targetDataset,
          type: 'default',
          style: { stroke: '#f59e0b', strokeWidth: 1.5, strokeDasharray: '6,3' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#f59e0b', width: 12, height: 12 },
        });
      }
    });

    // SyncTask → Dataset (当 syncTask 直接产出 dataset)
    syncTasks.forEach(task => {
      if (task.targetDatasetId) {
        const exists = edges.some(e => e.source === task.id && e.target === task.targetDatasetId);
        if (!exists) {
          edges.push({
            id: `e-${edgeIdx++}`,
            source: task.id,
            target: task.targetDatasetId,
            type: 'default',
            style: { stroke: '#a855f7', strokeWidth: 1.5, strokeDasharray: '6,3' },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#a855f7', width: 12, height: 12 },
          });
        }
      }
    });

    // Dataset → ObjectType
    datasets.forEach(ds => {
      if (ds.ontologyNodeId) {
        edges.push({
          id: `e-${edgeIdx++}`,
          source: ds.id,
          target: ds.ontologyNodeId,
          type: 'default',
          style: { stroke: '#10b981', strokeWidth: 1.5, strokeDasharray: '6,3' },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#10b981', width: 12, height: 12 },
        });
      }
    });

    return edges;
  }, [connections, syncTasks, pipelines, datasets, objectTypes]);

  // ── ReactFlow 状态 ──
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  // ── 节点点击处理 ──
  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      setSelectedNodeId(node.id);
      setPanelOpen(true);
    },
    []
  );

  // ── 获取选中节点数据 ──
  const selectedNode = useMemo(
    () => nodes.find(n => n.id === selectedNodeId) || null,
    [nodes, selectedNodeId]
  );

  const upstreamRelations = useMemo(() => {
    if (!selectedNodeId) return [];
    return getRelatedUpstream(selectedNodeId, connections, syncTasks, pipelines, datasets, objectTypes);
  }, [selectedNodeId, connections, syncTasks, pipelines, datasets, objectTypes]);

  const downstreamRelations = useMemo(() => {
    if (!selectedNodeId) return [];
    return getRelatedDownstream(selectedNodeId, connections, syncTasks, pipelines, datasets, objectTypes);
  }, [selectedNodeId, connections, syncTasks, pipelines, datasets, objectTypes]);

  // ── 获取选中节点的健康检查 ──
  const nodeHealthChecks = useMemo(() => {
    if (!selectedNodeId) return [];
    return healthChecks.filter(
      hc => hc.datasetId === selectedNodeId || hc.targetTable === selectedNodeId
    );
  }, [selectedNodeId, healthChecks]);

  // ── 解析选中节点的完整数据 ──
  const selectedRawData = useMemo(() => {
    if (!selectedNode) return null;
    const nodeType = selectedNode.type;
    if (nodeType === 'lineageSourceNode') return { kind: 'source', data: selectedNode.data.raw as DataConnection };
    if (nodeType === 'lineageIngestNode') return { kind: 'ingest', data: selectedNode.data.raw as DataSyncTask };
    if (nodeType === 'lineagePipelineNode') return { kind: 'pipeline', data: selectedNode.data.raw as DataPipeline };
    if (nodeType === 'lineageDatasetNode') return { kind: 'dataset', data: selectedNode.data.raw as Dataset };
    if (nodeType === 'lineageOntologyNode') return { kind: 'ontology', data: selectedNode.data.raw as ObjectType };
    return null;
  }, [selectedNode]);

  // ── 关闭面板 ──
  const closePanel = useCallback(() => {
    setPanelOpen(false);
    setSelectedNodeId(null);
  }, []);

  // ── 渲染 ──
  return (
    <div className="flex h-full w-full relative">
      {/* ── ReactFlow 画布区域 ── */}
      <div className={`flex-1 transition-all duration-300 ${panelOpen ? 'mr-[400px]' : ''}`}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={handleNodeClick}
          onPaneClick={() => { /* 点击空白不关闭面板，方便对比查看 */ }}
          nodeTypes={nodeTypes}
          defaultEdgeOptions={defaultEdgeOptions}
          fitView
          fitViewOptions={{ padding: 0.3, duration: 300, maxZoom: 1.5 }}
          minZoom={0.1}
          maxZoom={2}
          deleteKeyCode={['Backspace', 'Delete']}
          panOnScroll
          selectNodesOnDrag={false}
          panOnDrag={[1]}
          elevateNodesOnSelect
          proOptions={{ hideAttribution: true }}
          className="!bg-[#0f172a]"
        >
          {/* ── 背景网格 ── */}
          <Background
            color="#1e293b"
            gap={24}
            size={0.5}
            className="opacity-50"
          />

          {/* ── MiniMap ── */}
          <MiniMap
            nodeColor={miniMapNodeColor}
            nodeStrokeWidth={2}
            nodeBorderRadius={6}
            pannable
            zoomable
            maskColor="rgba(15, 23, 42, 0.7)"
            className="!bg-[#0f172a] !border !border-slate-800 !rounded-lg"
            maskStrokeColor="#334155"
            maskStrokeWidth={1}
          />

          {/* ── Controls ── */}
          <Controls
            className={`
              !bg-[#0f172a] !border !border-slate-800 !rounded-xl
              !shadow-lg !shadow-black/30
              [&_button]:!bg-[#1e293b] [&_button]:!border-slate-700
              [&_button]:!text-slate-300 [&_button:hover]:!bg-slate-700
              [&_svg]:!fill-slate-300
            `}
          />
        </ReactFlow>

        {/* ── 阶段标识标签（悬浮覆盖） ── */}
        {Object.entries(STAGE_LAYOUT).map(([key, layout]) => (
          <div
            key={key}
            className="absolute top-3 z-10 pointer-events-none"
            style={{ left: layout.x }}
          >
            <span
              className={`inline-block px-2.5 py-1 rounded-md text-[10px] font-bold border ${stageColors[key] || 'text-slate-400 border-slate-600 bg-slate-800/50'}`}
            >
              {layout.label}
            </span>
          </div>
        ))}
      </div>

      {/* ── 右侧信息面板 ── */}
      {panelOpen && selectedNode && (
        <div className={`absolute right-0 top-0 bottom-0 w-[400px] shadow-2xl shadow-black/30 border-l flex flex-col z-20 animate-slide-in ${styles.cardBg} ${styles.appBorder}`}>
          {/* 面板头部 */}
          <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.appBorder} ${styles.sidebarBg}`}>
            <div className="flex items-center gap-2.5 min-w-0">
              <div className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center ${styles.sidebarBg}`}>
                <Database size={16} className="text-slate-600" />
              </div>
              <div className="min-w-0">
                <h3 className={`text-sm font-semibold truncate ${styles.cardText}`}>
                  {String(selectedNode.data?.label || '未命名')}
                </h3>
                <p className="text-[10px] text-slate-500 truncate">
                  {selectedNode.type?.replace('lineage', '').replace('Node', '') || ''}
                </p>
              </div>
            </div>
            <button
              onClick={closePanel}
              className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition"
            >
              <X size={16} />
            </button>
          </div>

          {/* Tab 导航 */}
          <div className="flex border-b border-slate-200 bg-slate-50">
            {TABS.map(tab => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`
                    flex-1 flex items-center justify-center gap-1.5 px-2 py-2.5
                    text-[11px] font-medium transition-colors
                    ${activeTab === tab.id
                      ? 'text-blue-600 border-b-2 border-blue-600 bg-white'
                      : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100'
                    }
                  `}
                >
                  <Icon size={13} />
                  {tab.label}
                </button>
              );
            })}
          </div>

          {/* Tab 内容区 */}
          <div className="flex-1 overflow-y-auto p-4">
            {/* Tab1: 数据集信息 */}
            {activeTab === 'dataset' && (
              <DatasetInfoPanel
                nodeData={selectedNode.data}
                nodeType={selectedNode.type || ''}
                healthChecks={nodeHealthChecks}
                rawData={selectedRawData}
              />
            )}

            {/* Tab2: 选择关系 */}
            {activeTab === 'relations' && (
              <RelationsPanel
                upstream={upstreamRelations}
                downstream={downstreamRelations}
              />
            )}

            {/* Tab3: 转换规格 */}
            {activeTab === 'transform' && (
              <TransformPanel
                selectedNode={selectedNode}
                rawData={selectedRawData}
              />
            )}

            {/* Tab4: 执行信息 */}
            {activeTab === 'execution' && (
              <ExecutionPanel
                rawData={selectedRawData}
                healthChecks={nodeHealthChecks}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── 子面板：数据集信息 ──────────────────────────────────────

interface PanelProps {
  nodeData?: any;
  nodeType?: string;
  healthChecks?: DataHealthCheck[];
  rawData?: any;
}

function DatasetInfoPanel({ nodeData, nodeType, healthChecks, rawData }: PanelProps) {
  // Determine SLA status from healthChecks
  const slaStatus = useMemo(() => {
    if (!healthChecks || healthChecks.length === 0) return { label: '无检查', color: 'text-slate-400 bg-slate-100' };
    const hasError = healthChecks.some(hc => hc.status === 'error' || hc.status === 'failed');
    const hasWarning = healthChecks.some(hc => hc.status === 'warning');
    if (hasError) return { label: '异常', color: 'text-red-600 bg-red-50' };
    if (hasWarning) return { label: '警告', color: 'text-amber-600 bg-amber-50' };
    return { label: '健康', color: 'text-emerald-600 bg-emerald-50' };
  }, [healthChecks]);

  // Schema fields derived from data
  const schemaFields = useMemo(() => {
    const fields: { name: string; type: string }[] = [];

    if (nodeType === 'lineageDatasetNode' && nodeData?.columns) {
      (nodeData.columns as string[]).forEach(col => {
        fields.push({ name: col, type: 'string' });
      });
    }

    if (nodeType === 'lineagePipelineNode' && rawData?.kind === 'pipeline') {
      const pipe = rawData.data as DataPipeline;
      pipe.nodes?.forEach(node => {
        if (node.outputColumns) {
          node.outputColumns.forEach(col => {
            if (!fields.find(f => f.name === col.name)) {
              fields.push(col);
            }
          });
        }
      });
    }

    if (nodeType === 'lineageOntologyNode' && rawData?.kind === 'ontology') {
      const obj = rawData.data as ObjectType;
      obj.properties?.forEach((prop: any) => {
        fields.push({
          name: prop.name || prop.code || '',
          type: prop.type || prop.propertyType || 'string',
        });
      });
    }

    return fields;
  }, [nodeData, nodeType, rawData]);

  // 类型图标映射
  const typeIcons: Record<string, React.FC<{ size?: number; className?: string }>> = {
    STRING: Type,
    VARCHAR: Type,
    TEXT: Type,
    INTEGER: Hash,
    BIGINT: Hash,
    DECIMAL: Hash,
    DOUBLE: Hash,
    FLOAT: Hash,
    DATE: Calendar,
    DATETIME: Calendar,
    TIMESTAMP: Calendar,
    BOOLEAN: Code2,
    BOOL: Code2,
  };

  return (
    <div className="space-y-4">
      {/* 基本信息 */}
      <Section title="基本信息">
        <InfoRow label="名称" value={nodeData?.label || '-'} />
        {nodeData?.sublabel && <InfoRow label="路径/标识" value={nodeData.sublabel} />}
        {nodeData?.rowCount !== undefined && (
          <InfoRow label="行数" value={nodeData.rowCount.toLocaleString()} />
        )}
        {(nodeData?.columns?.length || 0) > 0 && (
          <InfoRow label="列数" value={`${nodeData.columns.length}`} />
        )}
      </Section>

      {/* SLA 状态 */}
      <Section title="SLA 状态">
        <div className="flex items-center gap-2">
          <span className={`px-2 py-0.5 rounded text-xs font-medium ${slaStatus.color}`}>
            {slaStatus.label}
          </span>
          <span className="text-xs text-slate-500">
            {healthChecks?.length || 0} 个检查规则
          </span>
        </div>
        {healthChecks && healthChecks.length > 0 && (
          <div className="mt-2 space-y-1.5">
            {healthChecks.map(hc => (
              <div key={hc.id} className="flex items-center gap-2 text-xs">
                <StatusBadge status={hc.status} />
                <span className="text-slate-700 truncate">{hc.name}</span>
                <span className="text-slate-400 text-[10px]">{hc.checkType}</span>
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* Schema 字段列表 */}
      {schemaFields.length > 0 && (
        <Section title={`Schema 字段 (${schemaFields.length})`}>
          <div className="space-y-1 max-h-[200px] overflow-y-auto">
            {schemaFields.map((field, i) => {
              const TypeIcon = typeIcons[field.type?.toUpperCase()] || Type;
              return (
                <div
                  key={`${field.name}-${i}`}
                  className="flex items-center gap-2 px-2 py-1.5 rounded-md bg-slate-50 text-xs"
                >
                  <TypeIcon size={12} className="text-slate-400 shrink-0" />
                  <span className="font-mono text-slate-700 truncate flex-1">{field.name}</span>
                  <span className="text-[10px] text-slate-500 uppercase">{field.type}</span>
                </div>
              );
            })}
          </div>
        </Section>
      )}

      {/* 数据源连接信息 */}
      {nodeType === 'lineageSourceNode' && rawData?.kind === 'source' && (
        <>
          <Section title="连接配置">
            <InfoRow label="主机" value={(rawData.data as DataConnection).config.host} />
            <InfoRow label="端口" value={`${(rawData.data as DataConnection).config.port}`} />
            {(rawData.data as DataConnection).config.database && (
              <InfoRow label="数据库" value={(rawData.data as DataConnection).config.database} />
            )}
            {(rawData.data as DataConnection).config.schema && (
              <InfoRow label="Schema" value={(rawData.data as DataConnection).config.schema} />
            )}
          </Section>
          <Section title="可用表">
            <div className="space-y-1 max-h-[150px] overflow-y-auto">
              {(rawData.data as DataConnection).tablesAvailable?.map((t: any) => (
                <div key={t.name} className="flex items-center gap-2 px-2 py-1 text-xs text-slate-700">
                  <Table2 size={11} className="text-slate-400 shrink-0" />
                  <span className="truncate flex-1">{t.name}</span>
                  <span className="text-[10px] text-slate-500">{t.rowCount?.toLocaleString()} 行</span>
                </div>
              ))}
            </div>
          </Section>
        </>
      )}
    </div>
  );
}

// ─── 子面板：选择关系 ────────────────────────────────────────

interface RelationsPanelProps {
  upstream: { type: string; name: string; detail?: string }[];
  downstream: { type: string; name: string; detail?: string }[];
}

function RelationsPanel({ upstream, downstream }: RelationsPanelProps) {
  return (
    <div className="space-y-4">
      {/* 上游数据源 */}
      <Section title={`上游数据源 (${upstream.length})`}>
        {upstream.length === 0 ? (
          <p className="text-xs text-slate-400 italic">无上游依赖</p>
        ) : (
          <div className="space-y-2">
            {upstream.map((item, i) => (
              <div
                key={`up-${i}`}
                className="flex items-center gap-2.5 px-3 py-2 rounded-lg bg-blue-50 border border-blue-100"
              >
                <ArrowRightLeft size={14} className="text-blue-400 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-slate-700 truncate">{item.name}</p>
                  {item.detail && (
                    <p className="text-[10px] text-slate-500 truncate">{item.detail}</p>
                  )}
                </div>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-100 text-blue-600 font-medium">
                  {item.type}
                </span>
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* 下游消费者 */}
      <Section title={`下游消费者 (${downstream.length})`}>
        {downstream.length === 0 ? (
          <p className="text-xs text-slate-400 italic">无下游消费</p>
        ) : (
          <div className="space-y-2">
            {downstream.map((item, i) => (
              <div
                key={`down-${i}`}
                className="flex items-center gap-2.5 px-3 py-2 rounded-lg bg-emerald-50 border border-emerald-100"
              >
                <ArrowRightLeft size={14} className="text-emerald-400 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-slate-700 truncate">{item.name}</p>
                  {item.detail && (
                    <p className="text-[10px] text-slate-500 truncate">{item.detail}</p>
                  )}
                </div>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-600 font-medium">
                  {item.type}
                </span>
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* 关联的 Ontology 实体 */}
      <Section title="关联 Ontology 实体">
        <div className="space-y-2">
          {downstream
            .filter(item => item.type === 'Ontology实体')
            .map((item, i) => (
              <div
                key={`onto-${i}`}
                className="flex items-center gap-2.5 px-3 py-2 rounded-lg bg-rose-50 border border-rose-100"
              >
                <Box size={14} className="text-rose-400 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium text-slate-700 truncate">{item.name}</p>
                  {item.detail && (
                    <p className="text-[10px] text-slate-500 truncate">域: {item.detail}</p>
                  )}
                </div>
              </div>
            ))}
          {downstream.filter(item => item.type === 'Ontology实体').length === 0 && (
            <p className="text-xs text-slate-400 italic">无关联 Ontology 实体</p>
          )}
        </div>
      </Section>
    </div>
  );
}

// ─── 子面板：转换规格 ────────────────────────────────────────

interface TransformPanelProps {
  selectedNode: Node | null;
  rawData?: any;
}

function TransformPanel({ selectedNode, rawData }: TransformPanelProps) {
  const nodeType = selectedNode?.type || '';

  // Extract transform info
  const transforms = useMemo(() => {
    const result: { name: string; expression?: string; sql?: string; jsCode?: string }[] = [];

    if (rawData?.kind === 'pipeline') {
      const pipe = rawData.data as DataPipeline;
      pipe.nodes?.forEach(node => {
        const name = node.name || `Node_${node.id}`;
        const item: any = { name };

        if (node.config?.expression) item.expression = node.config.expression;
        if (node.config?.sql) item.sql = node.config.sql;
        if (node.config?.jsCode || node.config?.javascript) item.jsCode = node.config.jsCode || node.config.javascript;
        if (node.type === 'join' && node.join) {
          item.expression = `JOIN ${node.join.type} ON ${node.join.on?.join(', ')}`;
        }
        if (node.type === 'filter' && node.config?.condition) {
          item.expression = `FILTER: ${node.config.condition}`;
        }
        if (node.type === 'project' && node.config?.columns) {
          item.expression = `SELECT: ${Array.isArray(node.config.columns) ? node.config.columns.join(', ') : node.config.columns}`;
        }

        result.push(item);
      });
    }

    if (rawData?.kind === 'ingest') {
      const task = rawData.data as DataSyncTask;
      result.push({
        name: `同步: ${task.sourceTable || 'source'} → ${task.targetDatasetId || 'target'}`,
        expression: `模式: ${task.syncMode || 'unknown'}`,
        sql: task.cronExpression ? `调度: ${task.cronExpression}` : undefined,
      });
    }

    return result;
  }, [rawData]);

  if (transforms.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-40 text-slate-400">
        <FileCode size={32} className="mb-2 opacity-30" />
        <p className="text-xs">该节点无转换规格</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {transforms.map((t, i) => (
        <div
          key={i}
          className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2"
        >
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 rounded bg-amber-100 flex items-center justify-center">
              <GitBranch size={11} className="text-amber-600" />
            </div>
            <span className="text-xs font-semibold text-slate-700">{t.name}</span>
          </div>

          {t.expression && (
            <div>
              <span className="text-[10px] font-medium text-slate-500 uppercase">表达式</span>
              <pre className="mt-0.5 p-2 rounded bg-slate-800 text-[10px] text-emerald-300 font-mono overflow-x-auto">
                {t.expression}
              </pre>
            </div>
          )}

          {/* Doris SQL */}
          {t.sql && (
            <div>
              <span className="text-[10px] font-medium text-amber-600 uppercase flex items-center gap-1">
                <Server size={10} /> Doris SQL
              </span>
              <pre className="mt-0.5 p-2 rounded bg-slate-800 text-[10px] text-blue-300 font-mono overflow-x-auto">
                {t.sql}
              </pre>
            </div>
          )}

          {/* Memory JS */}
          {t.jsCode && (
            <div>
              <span className="text-[10px] font-medium text-purple-600 uppercase flex items-center gap-1">
                <Code2 size={10} /> Memory JS
              </span>
              <pre className="mt-0.5 p-2 rounded bg-slate-800 text-[10px] text-purple-300 font-mono overflow-x-auto">
                {t.jsCode}
              </pre>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

// ─── 子面板：执行信息 ────────────────────────────────────────

interface ExecutionPanelProps {
  rawData?: any;
  healthChecks?: DataHealthCheck[];
}

function ExecutionPanel({ rawData, healthChecks }: ExecutionPanelProps) {
  const execInfo = useMemo(() => {
    let lastExecuted: string | undefined;
    let duration: number | undefined;
    let status: string = 'unknown';
    let errorMessage: string | undefined;

    if (rawData?.kind === 'ingest') {
      const task = rawData.data as DataSyncTask;
      lastExecuted = task.lastRunTime || task.lastRun;
      duration = task.durationMs;
      status = task.status;
      errorMessage = task.errorMessage;
    }

    if (rawData?.kind === 'pipeline') {
      const pipe = rawData.data as DataPipeline;
      lastExecuted = pipe.lastExecuted;
      status = pipe.status;
    }

    if (rawData?.kind === 'source') {
      const conn = rawData.data as DataConnection;
      lastExecuted = conn.lastTested || conn.config.lastTested;
      status = conn.status;
    }

    return { lastExecuted, duration, status, errorMessage };
  }, [rawData]);

  const formatDuration = (ms?: number) => {
    if (ms === undefined || ms === null) return '-';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}min`;
  };

  const formatTime = (iso?: string) => {
    if (!iso) return '-';
    try {
      return new Date(iso).toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
      });
    } catch {
      return iso;
    }
  };

  return (
    <div className="space-y-4">
      {/* 执行状态概览 */}
      <Section title="执行状态">
        <div className="grid grid-cols-2 gap-3">
          <StatCard
            label="状态"
            value={execInfo.status}
            icon={<Activity size={14} />}
            status={execInfo.status}
          />
          <StatCard
            label="耗时"
            value={formatDuration(execInfo.duration)}
            icon={<Clock size={14} />}
          />
        </div>
      </Section>

      {/* 最近执行 */}
      <Section title="最近执行">
        <InfoRow label="执行时间" value={formatTime(execInfo.lastExecuted)} />
        {execInfo.duration !== undefined && (
          <InfoRow label="执行耗时" value={formatDuration(execInfo.duration)} />
        )}
      </Section>

      {/* 错误信息 */}
      {execInfo.errorMessage && (
        <Section title="错误信息">
          <div className="p-3 rounded-lg bg-red-50 border border-red-100">
            <div className="flex items-start gap-2">
              <XCircle size={14} className="text-red-500 shrink-0 mt-0.5" />
              <p className="text-xs text-red-700 whitespace-pre-wrap">{execInfo.errorMessage}</p>
            </div>
          </div>
        </Section>
      )}

      {/* 质量检查历史 */}
      {healthChecks && healthChecks.length > 0 && (
        <Section title="质量检查">
          <div className="space-y-2">
            {healthChecks.map(hc => (
              <div
                key={hc.id}
                className="flex items-center justify-between px-3 py-2 rounded-lg bg-slate-50 border border-slate-100"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <Shield size={12} className="text-slate-400 shrink-0" />
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-slate-700 truncate">{hc.name}</p>
                    <p className="text-[10px] text-slate-500">{hc.checkType}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {hc.lastChecked && (
                    <span className="text-[10px] text-slate-400">
                      {new Date(hc.lastChecked).toLocaleDateString('zh-CN')}
                    </span>
                  )}
                  <StatusBadge status={hc.status} />
                </div>
              </div>
            ))}
          </div>
        </Section>
      )}

      {/* 无执行记录 */}
      {!execInfo.lastExecuted && !execInfo.errorMessage && (!healthChecks || healthChecks.length === 0) && (
        <div className="flex flex-col items-center justify-center h-32 text-slate-400">
          <RefreshCw size={28} className="mb-2 opacity-30" />
          <p className="text-xs">暂无执行记录</p>
        </div>
      )}
    </div>
  );
}

// ─── 通用子组件 ──────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h4 className="text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-2">
        {title}
      </h4>
      {children}
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-1.5 px-2 rounded-md hover:bg-slate-50">
      <span className="text-xs text-slate-500">{label}</span>
      <span className="text-xs font-medium text-slate-700 truncate max-w-[200px] text-right">
        {value || '-'}
      </span>
    </div>
  );
}

function StatCard({
  label,
  value,
  icon,
  status,
}: {
  label: string;
  value: string;
  icon: React.ReactNode;
  status?: string;
}) {
  const statusColor =
    status === 'success' || status === 'active' || status === 'running' || status === 'connected'
      ? 'text-emerald-600'
      : status === 'error' || status === 'failed'
        ? 'text-red-600'
        : 'text-slate-600';

  return (
    <div className="flex flex-col items-center justify-center p-3 rounded-lg bg-slate-50 border border-slate-100">
      <div className="text-slate-400 mb-1">{icon}</div>
      <span className={`text-lg font-bold ${statusColor}`}>{value}</span>
      <span className="text-[10px] text-slate-500">{label}</span>
    </div>
  );
}
