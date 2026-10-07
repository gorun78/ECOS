/**
 * LogicView — React Flow canvas for visual logic orchestration (PMO-18)
 * 组合根：画布/面板/执行引擎已机械抽取到 ./logic-canvas/（H6-T4），行为与单文件版本一致。
 * @license Apache-2.0
 */
import React, { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Controls,
  MiniMap,
  Background,
  BackgroundVariant,
  useNodesState,
  useEdgesState,
  addEdge,
  ReactFlowProvider,
  type Node,
  type Edge,
  type Connection,
  type NodeTypes,
  type NodeChange,
  type EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

/**
 * v11→v12 类型适配层（@xyflow/react 12 不再导出 v11 的三个 callback 类型）:
 * - OnNodesChange = (changes: NodeChange[]) => void
 * - OnEdgesChange = (changes: EdgeChange[]) => void
 * - OnConnect     = (connection: Connection) => void
 * 只为 LogicView 本文件兼容——上一行 import type 已与 v12 真实包签名同步,
 * in-file alias 不引入新模块依赖，不改变对外 props/序列化行为。
 */
type OnNodesChange = (changes: NodeChange[]) => void;
type OnEdgesChange = (changes: EdgeChange[]) => void;
type OnConnect = (connection: Connection) => void;
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import type {
  AIPLogicPipeline,
  AIPModel,
  LogicNodeData,
  LogicEdgeData,
  LogicNodeType,
  LogicNodeStatus,
  LogicNodeConfig,
} from '../../types/aiworkbench';
import LLMNode from '../../components/aiworkbench/logic/LLMNode';
import ToolNode from '../../components/aiworkbench/logic/ToolNode';
import OntologyNode from '../../components/aiworkbench/logic/OntologyNode';
import ApprovalNode from '../../components/aiworkbench/logic/ApprovalNode';
import ConditionNode from '../../components/aiworkbench/logic/ConditionNode';
import TriggerNode from '../../components/aiworkbench/logic/TriggerNode';
import { Icon } from './logic-canvas/LogicIcon';
import { pipelineToGraph, makeNode, typeLabel } from './logic-canvas/graph';
import type { HistoryEntry } from './logic-canvas/types';
import useLogicPipelineRun from './logic-canvas/useLogicPipelineRun';
import PipelineListPanel from './logic-canvas/PipelineListPanel';
import NodePalettePanel from './logic-canvas/NodePalettePanel';
import CanvasToolbarPanel from './logic-canvas/CanvasToolbarPanel';
import NodeConfigDrawer from './logic-canvas/NodeConfigDrawer';
import ExecutionLogPanel from './logic-canvas/ExecutionLogPanel';
import PipelineFormModal from './logic-canvas/PipelineFormModal';

const nodeTypes: NodeTypes = {
  llm: LLMNode,
  tool: ToolNode,
  ontology: OntologyNode,
  approval: ApprovalNode,
  condition: ConditionNode,
  trigger: TriggerNode,
};

interface LogicViewProps {
  pipelines: AIPLogicPipeline[];
  models: AIPModel[];
  onUpdatePipelines: (updated: AIPLogicPipeline[]) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

// ── Main Component ──────────────────────────────────────────

export default function LogicView({
  pipelines,
  models,
  onUpdatePipelines,
  showToast,
}: LogicViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);

  // Pipeline selection
  const [selectedPipelineId, setSelectedPipelineId] = useState<string>(pipelines[0]?.id || '');

  // Modal states (kept from original)
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingPipeline, setEditingPipeline] = useState<AIPLogicPipeline | null>(null);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formInputName, setFormInputName] = useState('');
  const [formInputType, setFormInputType] = useState('string');

  const selectedPipeline = pipelines.find(p => p.id === selectedPipelineId);

  // ── Flow state ──
  // initialGraph 只作 useNodesState/useEdgesState 的 mount-期 initialValues；
  // 切换 pipeline 时的实际同步走下方 useEffect([selectedPipelineId, selectedPipeline])。
  const initialGraph = useMemo(() => pipelineToGraph(selectedPipeline), [selectedPipeline]);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<LogicNodeData>>(initialGraph.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge<LogicEdgeData>>(initialGraph.edges);

  // Rebuild graph when pipeline changes
  useEffect(() => {
    const graph = pipelineToGraph(selectedPipeline);
    setNodes(graph.nodes);
    setEdges(graph.edges);
    setHistory([]);
    setHistoryIndex(-1);
    setSelectedNode(null);
  }, [selectedPipelineId, selectedPipeline]);

  // Undo/redo
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  const pushHistory = useCallback((ns: Node<LogicNodeData>[], es: Edge<LogicEdgeData>[]) => {
    setHistory(prev => {
      const next = prev.slice(0, historyIndex + 1);
      next.push({ nodes: ns, edges: es });
      if (next.length > 50) next.shift();
      return next;
    });
    setHistoryIndex(prev => {
      const next = prev + 1;
      return Math.min(next, 49);
    });
  }, [historyIndex]);

  const handleNodesChange: OnNodesChange = useCallback((changes) => {
    onNodesChange(changes);
    // Push history on drag end
    if (changes.some(c => c.type === 'position' && c.dragging === false)) {
      setNodes(prev => { pushHistory(prev, edges); return prev; });
    }
  }, [onNodesChange, edges, pushHistory]);

  const handleEdgesChange: OnEdgesChange = useCallback((changes) => {
    onEdgesChange(changes);
  }, [onEdgesChange]);

  const onConnect: OnConnect = useCallback((connection: Connection) => {
    setEdges(prev => {
      const next = addEdge({ ...connection, data: {} }, prev);
      pushHistory(nodes, next);
      return next;
    });
  }, [nodes, pushHistory]);

  const handleUndo = useCallback(() => {
    if (historyIndex < 0) return;
    const entry = history[historyIndex];
    if (entry) {
      setNodes(entry.nodes);
      setEdges(entry.edges);
      setHistoryIndex(prev => prev - 1);
    }
  }, [historyIndex, history, setNodes, setEdges]);

  const handleRedo = useCallback(() => {
    if (historyIndex + 1 >= history.length) return;
    const entry = history[historyIndex + 1];
    if (entry) {
      setNodes(entry.nodes);
      setEdges(entry.edges);
      setHistoryIndex(prev => prev + 1);
    }
  }, [historyIndex, history, setNodes, setEdges]);

  // Keyboard shortcuts (挂 deps [handleUndo, handleRedo]；能挂 deps 前提是 useEffect 位于
  // 二者定义之后 — 原位置在 handler 定义之前，deps 数组运行时会 TDZ 报错，故整体翻转)
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        handleUndo();
      } else if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) {
        e.preventDefault();
        handleRedo();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [handleUndo, handleRedo]);

  // ── Config Panel ──
  const [selectedNode, setSelectedNode] = useState<Node<LogicNodeData> | null>(null);
  /** 节点 trace drawer 开关（按 TraceId 收可点开 — 节点执行 trace 区域）*/
  const [traceOpen, setTraceOpen] = useState(false);

  const onNodeDoubleClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node as unknown as Node<LogicNodeData>);
    setTraceOpen(false);
  }, []);

  const updateNodeConfig = useCallback((nodeId: string, config: LogicNodeConfig) => {
    setNodes(prev => {
      const next = prev.map(n =>
        n.id === nodeId ? { ...n, data: { ...n.data, config } } : n
      );
      pushHistory(next, edges);
      return next;
    });
  }, [edges, pushHistory]);

  // ── Add node to canvas ──
  const addCanvasNode = (type: LogicNodeType) => {
    const node = makeNode(type, typeLabel(type), { x: 100 + Math.random() * 200, y: 100 + Math.random() * 200 });
    setNodes(prev => {
      const next = [...prev, node];
      pushHistory(next, edges);
      return next;
    });
    showToast?.('success', t('aiworkbench.logic.node.added', { type: typeLabel(type) }));
  };

  // ── Delete selected nodes ──
  const deleteSelectedNodes = () => {
    setNodes(prev => {
      const selectedIds = new Set(prev.filter(n => n.selected).map(n => n.id));
      const next = prev.filter(n => !n.selected);
      pushHistory(next, edges.filter(e => !selectedIds.has(e.source) && !selectedIds.has(e.target)));
      return next;
    });
    setEdges(prev => {
      const selectedIds = new Set(nodes.filter(n => n.selected).map(n => n.id));
      return prev.filter(e => !selectedIds.has(e.source) && !selectedIds.has(e.target));
    });
  };

  // ── Execution Engine（抽取至 useLogicPipelineRun；位置与原文件一致，effect 次序不变）──
  const {
    isExecuting,
    logs,
    setLogs,
    showLogs,
    setShowLogs,
    totalDuration,
    traceMap,
    startPipelineRun,
  } = useLogicPipelineRun({
    nodes,
    edges,
    selectedPipeline,
    selectedPipelineId,
    setNodes,
    showToast,
  });

  /** 节点边框样式：IDLE 用节点类型色；RUNNING/SUCCESS/FAILED/SKIPPED 用主题 token */
  const nodeBorder = (status: LogicNodeStatus, selected: boolean): string => {
    if (selected) return 'border-blue-500';
    switch (status) {
      case 'running': return `${styles.warningBorder} border-2 animate-pulse`;
      case 'success': return `${styles.successBorder} border-2`;
      case 'error': return `${styles.dangerBorder} border-2`;
      default: return '';
    }
  };

  // ── Pipeline CRUD handlers (kept from original) ──
  const handleStartCreate = () => {
    setEditingPipeline(null);
    setFormName('');
    setFormDesc('');
    setFormInputName('flight_number');
    setFormInputType('string');
    setShowCreateModal(true);
  };

  const handleStartEdit = (p: AIPLogicPipeline) => {
    setEditingPipeline(p);
    setFormName(p.name);
    setFormDesc(p.description);
    setFormInputName(p.inputs[0]?.name || 'flight_number');
    setFormInputType(p.inputs[0]?.type || 'string');
    setShowCreateModal(true);
  };

  const handleDelete = (id: string) => {
    if (!window.confirm(t('aiworkbench.logic.delete.confirm'))) return;
    const updated = pipelines.filter(p => p.id !== id);
    onUpdatePipelines(updated);
    if (selectedPipelineId === id && updated.length > 0) {
      setSelectedPipelineId(updated[0].id);
    }
    showToast?.('success', t('aiworkbench.logic.delete.done'));
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) return;

    if (editingPipeline) {
      const updated = pipelines.map(p => {
        if (p.id === editingPipeline.id) {
          return {
            ...p,
            name: formName.trim(),
            description: formDesc.trim(),
            inputs: [{ name: formInputName, type: formInputType }],
            lastUpdated: new Date().toISOString().replace('T', ' ').slice(0, 16),
          };
        }
        return p;
      });
      onUpdatePipelines(updated);
      showToast?.('success', t('aiworkbench.logic.save.done'));
    } else {
      const newId = `pipe-${Date.now().toString().slice(-4)}`;
      const newPipe: AIPLogicPipeline = {
        id: newId,
        name: formName.trim(),
        description: formDesc.trim(),
        status: 'active',
        creator: t('aiworkbench.logic.modal.creator'),
        lastUpdated: new Date().toISOString().replace('T', ' ').slice(0, 16),
        inputs: [{ name: formInputName, type: formInputType }],
        testInputs: { [formInputName]: 'UA102' },
        blocks: [],
      };
      onUpdatePipelines([...pipelines, newPipe]);
      setSelectedPipelineId(newId);
      showToast?.('success', t('aiworkbench.logic.save.created'));
    }
    setShowCreateModal(false);
  };

  // ── Render ────────────────────────────────────────────────

  return (
    <div className={`flex h-full overflow-hidden select-none ${styles.appBg} text-xs`}>
      {/* Left: Pipeline List */}
      <PipelineListPanel
        pipelines={pipelines}
        selectedPipelineId={selectedPipelineId}
        onSelect={(id) => {
          setSelectedPipelineId(id);
          setSelectedNode(null);
          setLogs([]);
          setShowLogs(false);
        }}
        onCreate={handleStartCreate}
      />

      {/* Center: Canvas */}
      {selectedPipeline ? (
        <div className="flex-1 flex overflow-hidden relative">
          <div className="flex-1 h-full" ref={reactFlowWrapper}>
            <ReactFlowProvider>
              <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={handleNodesChange}
                onEdgesChange={handleEdgesChange}
                onConnect={onConnect}
                onNodeDoubleClick={onNodeDoubleClick}
                nodeTypes={nodeTypes}
                fitView
                deleteKeyCode={['Delete', 'Backspace']}
                className={`${styles.inputBg}`}
              >
                <Controls className={`${styles.cardBg} !border ${styles.appBorder} !rounded-lg !shadow-sm`} />
                <MiniMap
                  className={`!rounded-lg !shadow-sm !border ${styles.appBorder}`}
                  nodeColor={(n: Node<LogicNodeData>) => {
                    const c: Record<LogicNodeType, string> = {
                      llm: '#a855f7', tool: '#f59e0b', ontology: '#06b6d4',
                      approval: '#f43f5e', condition: '#6366f1', trigger: '#14b8a6',
                    };
                    return c[(n.data as LogicNodeData)?.type] || '#94a3b8';
                  }}
                />
                <Background variant={BackgroundVariant.Dots} gap={20} size={1} className={styles.cardTextMuted} />

                {/* Top toolbar */}
                <NodePalettePanel onAddNode={addCanvasNode} />

                <CanvasToolbarPanel
                  onRun={startPipelineRun}
                  isExecuting={isExecuting}
                  nodesCount={nodes.length}
                  canUndo={historyIndex < 0}
                  canRedo={historyIndex + 1 >= history.length}
                  onUndo={handleUndo}
                  onRedo={handleRedo}
                  onDeleteSelected={deleteSelectedNodes}
                  showLogs={showLogs}
                  onToggleLogs={() => setShowLogs(!showLogs)}
                  logsCount={logs.length}
                  totalDuration={totalDuration}
                />
              </ReactFlow>
            </ReactFlowProvider>
          </div>

          {/* Right: Config Panel + Node Trace Drawer */}
          {selectedNode && (
            <NodeConfigDrawer
              node={selectedNode}
              trace={traceMap[selectedNode.id]}
              traceOpen={traceOpen}
              onToggleTrace={() => setTraceOpen(v => !v)}
              onClose={() => setSelectedNode(null)}
              onUpdateConfig={(config) => updateNodeConfig(selectedNode.id, config)}
            />
          )}
        </div>
      ) : (
        <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted}`}>
          <Icon name="Cpu" size={32} className={`${styles.cardTextMuted} animate-bounce mb-2`} />
          <span>{t('aiworkbench.logic.emptyHint')}</span>
        </div>
      )}

      {/* Bottom: Log Panel */}
      {showLogs && (
        <ExecutionLogPanel
          logs={logs}
          totalDuration={totalDuration}
          onClose={() => setShowLogs(false)}
        />
      )}

      {/* Create/Edit Modal */}
      {showCreateModal && (
        <PipelineFormModal
          editingPipeline={editingPipeline}
          formName={formName}
          setFormName={setFormName}
          formDesc={formDesc}
          setFormDesc={setFormDesc}
          formInputName={formInputName}
          setFormInputName={setFormInputName}
          formInputType={formInputType}
          setFormInputType={setFormInputType}
          onSave={handleSave}
          onClose={() => setShowCreateModal(false)}
        />
      )}
    </div>
  );
}
