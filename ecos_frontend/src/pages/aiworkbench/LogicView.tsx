/**
 * LogicView — React Flow canvas for visual logic orchestration (PMO-18)
 * @license Apache-2.0
 */
import React, { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import ReactFlow, {
  Controls,
  MiniMap,
  Background,
  BackgroundVariant,
  Panel,
  useNodesState,
  useEdgesState,
  addEdge,
  ReactFlowProvider,
  type Node,
  type Edge,
  type Connection,
  type NodeTypes,
  type OnNodesChange,
  type OnEdgesChange,
  type OnConnect,
} from 'reactflow';
import 'reactflow/dist/style.css';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import type {
  AIPLogicPipeline,
  AIPModel,
  LogicNodeData,
  LogicEdgeData,
  LogicNodeType,
  LogicNodeStatus,
  LogicNodeConfig,
  LogicLLMConfig,
  LogicToolConfig,
  LogicOntologyConfig,
  LogicApprovalConfig,
  LogicConditionConfig,
  LogicTriggerConfig,
} from '../../types/aiworkbench';
import LLMNode from '../../components/aiworkbench/logic/LLMNode';
import ToolNode from '../../components/aiworkbench/logic/ToolNode';
import OntologyNode from '../../components/aiworkbench/logic/OntologyNode';
import ApprovalNode from '../../components/aiworkbench/logic/ApprovalNode';
import ConditionNode from '../../components/aiworkbench/logic/ConditionNode';
import TriggerNode from '../../components/aiworkbench/logic/TriggerNode';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

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

type HistoryEntry = { nodes: Node<LogicNodeData>[]; edges: Edge<LogicEdgeData>[] };

// ── Default configs per node type ──────────────────────────

const DEFAULT_CONFIGS: Record<LogicNodeType, LogicNodeConfig> = {
  llm: { model: 'gemini-1.5-pro', temperature: 0.7, maxTokens: 4096, systemPrompt: '' },
  tool: { toolName: 'http_request', parameters: '{}' },
  ontology: { objectType: 'Flight', queryType: 'get', filter: 'id == ""' },
  approval: { approver: 'admin', timeout: 300 },
  condition: { conditionExpr: '$.status == "ok"', thenBranch: '通过', elseBranch: '拒绝' },
  trigger: { cronExpr: '0 0 * * *', timezone: 'Asia/Shanghai' },
};

const NODE_COUNTS = { llm: 0, tool: 0, ontology: 0, approval: 0, condition: 0, trigger: 0 };

function resetNodeCounts() {
  NODE_COUNTS.llm = 0;
  NODE_COUNTS.tool = 0;
  NODE_COUNTS.ontology = 0;
  NODE_COUNTS.approval = 0;
  NODE_COUNTS.condition = 0;
  NODE_COUNTS.trigger = 0;
}

function nextNodeId(type: LogicNodeType): string {
  NODE_COUNTS[type]++;
  return `${type}-${NODE_COUNTS[type]}`;
}

function makeNode(type: LogicNodeType, label: string, position: { x: number; y: number }, overrides?: Partial<LogicNodeConfig>): Node<LogicNodeData> {
  return {
    id: nextNodeId(type),
    type,
    position,
    data: {
      type,
      label,
      status: 'idle',
      config: { ...DEFAULT_CONFIGS[type], ...overrides },
    },
  };
}

// ── Build nodes/edges from pipeline blocks (legacy → canvas) ──
function pipelineToGraph(pipeline: AIPLogicPipeline | undefined): { nodes: Node<LogicNodeData>[]; edges: Edge<LogicEdgeData>[] } {
  if (!pipeline || !pipeline.blocks.length) return { nodes: [], edges: [] };
  resetNodeCounts();

  const nodes: Node<LogicNodeData>[] = [];
  const edges: Edge<LogicEdgeData>[] = [];
  const spacing = 200;

  // Map legacy block types to canvas node types
  const typeMap: Record<string, LogicNodeType> = {
    input: 'trigger',
    query_ontology: 'ontology',
    llm: 'llm',
    ontology_action: 'tool',
    output: 'tool',
  };

  pipeline.blocks.forEach((block, idx) => {
    const ntype = typeMap[block.type] || 'tool';
    const pos = { x: 100, y: 50 + idx * spacing };

    let config: LogicNodeConfig;
    switch (ntype) {
      case 'llm':
        config = {
          model: block.config.modelId || 'gemini-1.5-pro',
          temperature: block.config.temperature ?? 0.7,
          maxTokens: 4096,
          systemPrompt: block.config.systemPrompt || '',
        };
        break;
      case 'ontology':
        config = {
          objectType: block.config.queryTarget || 'Object',
          queryType: 'get',
          filter: block.config.queryFilter || '',
        };
        break;
      case 'trigger':
        config = { cronExpr: '0 0 * * *', timezone: 'Asia/Shanghai' };
        break;
      case 'tool':
        if (block.type === 'ontology_action') {
          config = { toolName: block.config.actionTypeId || 'action', parameters: JSON.stringify(block.config.actionMapping || {}) };
        } else {
          config = { toolName: block.type === 'output' ? 'output_formatter' : 'tool', parameters: '{}' };
        }
        break;
      default:
        config = DEFAULT_CONFIGS[ntype];
    }

    nodes.push({
      id: nextNodeId(ntype),
      type: ntype,
      position: pos,
      data: { type: ntype, label: block.name, status: 'idle', config },
    });

    if (idx > 0) {
      const prevNode = nodes[idx - 1];
      edges.push({
        id: `e-${prevNode.id}-${nodes[idx].id}`,
        source: prevNode.id,
        target: nodes[idx].id,
        animated: false,
        data: {},
      });
    }
  });

  return { nodes, edges };
}

// ── Main Component ──────────────────────────────────────────

export default function LogicView({
  pipelines,
  models,
  onUpdatePipelines,
  showToast,
}: LogicViewProps) {
  const { styles } = useTheme();
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
  const initialGraph = useMemo(() => pipelineToGraph(selectedPipeline), []);
  const [nodes, setNodes, onNodesChange] = useNodesState<LogicNodeData>(initialGraph.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<LogicEdgeData>(initialGraph.edges);

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

  // Keyboard shortcuts
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
  });

  const handleUndo = () => {
    if (historyIndex < 0) return;
    const entry = history[historyIndex];
    if (entry) {
      setNodes(entry.nodes);
      setEdges(entry.edges);
      setHistoryIndex(prev => prev - 1);
    }
  };

  const handleRedo = () => {
    if (historyIndex + 1 >= history.length) return;
    const entry = history[historyIndex + 1];
    if (entry) {
      setNodes(entry.nodes);
      setEdges(entry.edges);
      setHistoryIndex(prev => prev + 1);
    }
  };

  // ── Config Panel ──
  const [selectedNode, setSelectedNode] = useState<Node<LogicNodeData> | null>(null);

  const onNodeDoubleClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node as Node<LogicNodeData>);
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
    showToast?.('success', `已添加${typeLabel(type)}节点`);
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

  // ── Execution Engine ──
  const [isExecuting, setIsExecuting] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [showLogs, setShowLogs] = useState(false);
  const [totalDuration, setTotalDuration] = useState<number | null>(null);

  const executeCanvas = async () => {
    if (nodes.length === 0) return;
    setIsExecuting(true);
    setLogs([]);
    setShowLogs(true);
    setTotalDuration(null);

    // Reset all node statuses
    setNodes(prev => prev.map(n => ({ ...n, data: { ...n.data, status: 'idle' as LogicNodeStatus, duration: undefined as number | undefined } })));

    const execOrder = topologicalSort(nodes, edges);
    const startTime = performance.now();
    const logEntries: string[] = [];
    let allSuccess = true;
    const updatedNodes = [...nodes];

    for (const nodeId of execOrder) {
      const node = updatedNodes.find(n => n.id === nodeId);
      if (!node) continue;

      // Simulate execution
      const nodeStart = performance.now();
      setNodes(prev => prev.map(n => n.id === nodeId ? { ...n, data: { ...n.data, status: 'running' } } : n));
      logEntries.push(`⚡ 正在执行: [${node.data.label}] (${node.data.type})`);

      await new Promise(r => setTimeout(r, 300 + Math.random() * 600));

      const duration = Math.round(performance.now() - nodeStart);

      try {
        // Simulate random errors (5% chance)
        if (Math.random() < 0.05) throw new Error('模拟执行异常');

        setNodes(prev => prev.map(n => n.id === nodeId ? { ...n, data: { ...n.data, status: 'success', duration } } : n));
        logEntries.push(`✅ 完成: [${node.data.label}] — ${duration}ms`);
      } catch {
        setNodes(prev => prev.map(n => n.id === nodeId ? { ...n, data: { ...n.data, status: 'error', duration } } : n));
        logEntries.push(`❌ 失败: [${node.data.label}] — ${duration}ms`);
        allSuccess = false;
        break;
      }
    }

    const total = Math.round(performance.now() - startTime);
    setTotalDuration(total);
    logEntries.push(allSuccess ? `🎉 执行完成! 总耗时: ${total}ms` : `⚠️ 执行中断! 总耗时: ${total}ms`);
    setLogs(logEntries);
    setIsExecuting(false);
    showToast?.(allSuccess ? 'success' : 'error', allSuccess ? '画布执行成功' : '画布执行失败');
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
    if (!window.confirm('确定要删除这个 AIP 逻辑编排流程吗？')) return;
    const updated = pipelines.filter(p => p.id !== id);
    onUpdatePipelines(updated);
    if (selectedPipelineId === id && updated.length > 0) {
      setSelectedPipelineId(updated[0].id);
    }
    showToast?.('success', '已删除逻辑编排流');
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
      showToast?.('success', '逻辑编排修改已保存');
    } else {
      const newId = `pipe-${Date.now().toString().slice(-4)}`;
      const newPipe: AIPLogicPipeline = {
        id: newId,
        name: formName.trim(),
        description: formDesc.trim(),
        status: 'active',
        creator: '系统管理员',
        lastUpdated: new Date().toISOString().replace('T', ' ').slice(0, 16),
        inputs: [{ name: formInputName, type: formInputType }],
        testInputs: { [formInputName]: 'UA102' },
        blocks: [],
      };
      onUpdatePipelines([...pipelines, newPipe]);
      setSelectedPipelineId(newId);
      showToast?.('success', '成功创建 AIP 逻辑编排流程');
    }
    setShowCreateModal(false);
  };

  // ── Render ────────────────────────────────────────────────

  return (
    <div className={`flex h-full overflow-hidden select-none ${styles.appBg} text-xs`}>
      {/* Left: Pipeline List */}
      <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
        <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
          <span className={`font-bold ${styles.cardText}`}>逻辑流列表 ({pipelines.length})</span>
          <button
            onClick={handleStartCreate}
            className="p-1 bg-blue-50 hover:bg-blue-100 text-blue-600 border border-blue-200 rounded-md transition-colors cursor-pointer"
            title="新增逻辑流"
          >
            <Icon name="Plus" size={12} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
          {pipelines.map(p => {
            const isSelected = selectedPipelineId === p.id;
            return (
              <div
                key={p.id}
                onClick={() => {
                  setSelectedPipelineId(p.id);
                  setSelectedNode(null);
                  setLogs([]);
                  setShowLogs(false);
                }}
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1.5 ${
                  isSelected
                    ? `${styles.accentBg} text-white shadow-xs`
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-center gap-1.5 font-bold">
                  <Icon name="Cpu" size={12} className={isSelected ? 'text-blue-400 animate-pulse' : 'text-slate-500'} />
                  <span className="truncate">{p.name}</span>
                </div>
                <p className={`text-[10px] line-clamp-2 leading-relaxed ${isSelected ? 'text-slate-400' : 'text-slate-400'}`}>
                  {p.description}
                </p>
                <div className={`flex items-center justify-between text-[9px] border-t ${styles.inputBorder}/10 pt-1`}>
                  <span className={`font-mono ${isSelected ? 'text-slate-500' : 'text-slate-400'}`}>{p.lastUpdated.split(' ')[0]}</span>
                  <span className="px-1 bg-emerald-500/10 text-emerald-600 rounded">已就绪</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

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
                <Controls className="!bg-white !border !border-slate-200 !rounded-lg !shadow-sm" />
                <MiniMap
                  className="!rounded-lg !shadow-sm !border !border-slate-200"
                  nodeColor={(n) => {
                    const c: Record<LogicNodeType, string> = {
                      llm: '#a855f7', tool: '#f59e0b', ontology: '#06b6d4',
                      approval: '#f43f5e', condition: '#6366f1', trigger: '#14b8a6',
                    };
                    return c[(n.data as LogicNodeData)?.type] || '#94a3b8';
                  }}
                />
                <Background variant={BackgroundVariant.Dots} gap={20} size={1} className="text-slate-300" />

                {/* Top toolbar */}
                <Panel position="top-left" className="flex items-center gap-1.5">
                  {/* Add nodes dropdown */}
                  <div className="flex bg-white border border-slate-200 rounded-lg shadow-sm overflow-hidden">
                    {(['llm', 'tool', 'ontology', 'approval', 'condition', 'trigger'] as LogicNodeType[]).map(t => (
                      <button
                        key={t}
                        onClick={() => addCanvasNode(t)}
                        className="px-2 py-1.5 hover:bg-slate-50 text-[10px] font-bold text-slate-600 hover:text-slate-800 border-r border-slate-200 last:border-r-0 cursor-pointer transition-colors"
                        title={`添加${typeLabel(t)}`}
                      >
                        {typeLabel(t)}
                      </button>
                    ))}
                  </div>
                </Panel>

                <Panel position="top-center" className="flex items-center gap-2">
                  <button
                    onClick={executeCanvas}
                    disabled={isExecuting || nodes.length === 0}
                    className={`px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-lg shadow-sm transition-colors cursor-pointer flex items-center gap-1.5 text-[11px] ${
                      isExecuting ? 'opacity-60 cursor-not-allowed' : ''
                    }`}
                  >
                    {isExecuting ? (
                      <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    ) : (
                      <Icon name="Play" size={12} />
                    )}
                    <span>{isExecuting ? '执行中...' : '执行画布'}</span>
                  </button>

                  <button
                    onClick={handleUndo}
                    disabled={historyIndex < 0}
                    className="px-2 py-1.5 bg-white border border-slate-200 rounded-lg shadow-sm hover:bg-slate-50 disabled:opacity-40 cursor-pointer transition-colors"
                    title="撤销 (Ctrl+Z)"
                  >
                    <Icon name="Undo2" size={12} className="text-slate-600" />
                  </button>
                  <button
                    onClick={handleRedo}
                    disabled={historyIndex + 1 >= history.length}
                    className="px-2 py-1.5 bg-white border border-slate-200 rounded-lg shadow-sm hover:bg-slate-50 disabled:opacity-40 cursor-pointer transition-colors"
                    title="重做 (Ctrl+Y)"
                  >
                    <Icon name="Redo2" size={12} className="text-slate-600" />
                  </button>

                  <button
                    onClick={deleteSelectedNodes}
                    className="px-2 py-1.5 bg-white border border-slate-200 rounded-lg shadow-sm hover:bg-red-50 cursor-pointer transition-colors"
                    title="删除选中节点 (Delete)"
                  >
                    <Icon name="Trash2" size={12} className="text-red-500" />
                  </button>

                  <button
                    onClick={() => setShowLogs(!showLogs)}
                    className={`px-2 py-1.5 border rounded-lg shadow-sm cursor-pointer transition-colors text-[10px] font-bold ${
                      showLogs ? 'bg-blue-50 border-blue-300 text-blue-600' : 'bg-white border-slate-200 text-slate-500 hover:bg-slate-50'
                    }`}
                  >
                    <span className="flex items-center gap-1">
                      <Icon name="Terminal" size={11} />
                      日志 ({logs.length})
                    </span>
                  </button>

                  {totalDuration != null && (
                    <span className="px-2 py-1 bg-white border border-slate-200 rounded-lg text-[10px] font-mono text-slate-500 shadow-sm">
                      总耗时: {totalDuration}ms
                    </span>
                  )}
                </Panel>
              </ReactFlow>
            </ReactFlowProvider>
          </div>

          {/* Right: Config Panel */}
          {selectedNode && (
            <div className={`w-72 ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0 overflow-y-auto`}>
              <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between sticky top-0 z-10`}>
                <span className={`font-bold ${styles.cardText} text-[11px]`}>
                  配置: {selectedNode.data.label}
                </span>
                <button
                  onClick={() => setSelectedNode(null)}
                  className={`${styles.cardTextMuted} hover:text-slate-700 cursor-pointer`}
                >
                  <Icon name="X" size={14} />
                </button>
              </div>
              <ConfigForm
                node={selectedNode}
                onUpdate={(config) => updateNodeConfig(selectedNode.id, config)}
                styles={styles}
              />
            </div>
          )}
        </div>
      ) : (
        <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted}`}>
          <Icon name="Cpu" size={32} className={`${styles.cardTextMuted} animate-bounce mb-2`} />
          <span>请在左侧选择或添加逻辑流进行设计</span>
        </div>
      )}

      {/* Bottom: Log Panel */}
      {showLogs && (
        <div className={`absolute bottom-0 left-56 right-0 z-20 ${styles.cardBg} border-t ${styles.cardBorder} shadow-lg`}
             style={{ maxHeight: '200px' }}>
          <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
            <span className={`font-bold ${styles.cardText} text-[10px] flex items-center gap-1`}>
              <Icon name="Terminal" size={11} />
              执行日志
              {totalDuration != null && (
                <span className="font-mono text-[9px] text-slate-400 ml-2">总耗时: {totalDuration}ms</span>
              )}
            </span>
            <button
              onClick={() => setShowLogs(false)}
              className={`${styles.cardTextMuted} hover:text-slate-700 cursor-pointer`}
            >
              <Icon name="ChevronDown" size={12} />
            </button>
          </div>
          <div className="overflow-y-auto p-2 max-h-[160px] space-y-0.5 font-mono text-[10px]">
            {logs.length === 0 ? (
              <span className={`${styles.cardTextMuted} italic px-2`}>暂无日志，点击"执行画布"开始</span>
            ) : (
              logs.map((log, i) => (
                <p key={i} className={`px-2 py-0.5 leading-relaxed ${log.includes('✅') ? 'text-emerald-600' : log.includes('❌') ? 'text-red-500' : 'text-slate-500'}`}>
                  {log}
                </p>
              ))
            )}
          </div>
        </div>
      )}

      {/* Create/Edit Modal */}
      {showCreateModal && (
        <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden`}>
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>
                {editingPipeline ? '修改逻辑流' : '新增 AIP 逻辑开发流'}
              </h3>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} cursor-pointer`}
              >
                <Icon name="X" size={15} />
              </button>
            </div>
            <form onSubmit={handleSave} className="p-4 space-y-4">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>名称 <span className="text-red-500">*</span></label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder="例如: 机组执勤时间合规评估"
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  required
                />
              </div>
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>描述 <span className="text-red-500">*</span></label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder="说明该逻辑决策流的判定范围和目的"
                  rows={2}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none`}
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className={`block ${styles.cardTextMuted} font-semibold`}>输入参数名</label>
                  <input
                    type="text"
                    value={formInputName}
                    onChange={e => setFormInputName(e.target.value)}
                    placeholder="如: flight_number"
                    className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono`}
                  />
                </div>
                <div className="space-y-1">
                  <label className={`block ${styles.cardTextMuted} font-semibold`}>参数类型</label>
                  <select
                    value={formInputType}
                    onChange={e => setFormInputType(e.target.value)}
                    className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  >
                    <option value="string">String</option>
                    <option value="integer">Integer</option>
                    <option value="boolean">Boolean</option>
                  </select>
                </div>
              </div>
              <div className={`pt-2 border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-[11px]"
                >
                  保存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Helper: Config Form per node type ──────────────────────

function ConfigForm({
  node,
  onUpdate,
  styles,
}: {
  node: Node<LogicNodeData>;
  onUpdate: (config: LogicNodeConfig) => void;
  styles: any;
}) {
  const { type, config } = node.data;

  const handleChange = (newConfig: LogicNodeConfig) => {
    onUpdate({ ...newConfig });
  };

  const inputClass = `w-full px-2 py-1.5 border ${styles.cardBorder} rounded-lg text-[10px] font-mono bg-white`;
  const labelClass = `${styles.cardTextMuted} font-bold text-[10px] block mb-0.5`;
  const textareaClass = `w-full px-2 py-1.5 border ${styles.cardBorder} rounded-lg text-[10px] font-mono resize-none bg-white`;

  switch (type) {
    case 'llm': {
      const c = config as LogicLLMConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>Model</label>
            <input className={inputClass} value={c.model} onChange={e => handleChange({ ...c, model: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>Temperature ({c.temperature})</label>
            <input className={inputClass} type="range" min="0" max="2" step="0.1" value={c.temperature}
              onChange={e => handleChange({ ...c, temperature: parseFloat(e.target.value) })} />
          </div>
          <div>
            <label className={labelClass}>Max Tokens</label>
            <input className={inputClass} type="number" value={c.maxTokens}
              onChange={e => handleChange({ ...c, maxTokens: parseInt(e.target.value) || 4096 })} />
          </div>
          <div>
            <label className={labelClass}>System Prompt</label>
            <textarea className={textareaClass} rows={4} value={c.systemPrompt}
              onChange={e => handleChange({ ...c, systemPrompt: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'tool': {
      const c = config as LogicToolConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>工具名称</label>
            <input className={inputClass} value={c.toolName} onChange={e => handleChange({ ...c, toolName: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>参数 (JSON)</label>
            <textarea className={textareaClass} rows={6} value={c.parameters}
              onChange={e => handleChange({ ...c, parameters: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'ontology': {
      const c = config as LogicOntologyConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>Object Type</label>
            <input className={inputClass} value={c.objectType} onChange={e => handleChange({ ...c, objectType: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>查询类型</label>
            <select className={inputClass} value={c.queryType}
              onChange={e => handleChange({ ...c, queryType: e.target.value as LogicOntologyConfig['queryType'] })}>
              <option value="get">get</option>
              <option value="list">list</option>
              <option value="search">search</option>
              <option value="query">query</option>
            </select>
          </div>
          <div>
            <label className={labelClass}>Filter</label>
            <input className={inputClass} value={c.filter} onChange={e => handleChange({ ...c, filter: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'approval': {
      const c = config as LogicApprovalConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>审批人</label>
            <input className={inputClass} value={c.approver} onChange={e => handleChange({ ...c, approver: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>超时 (秒)</label>
            <input className={inputClass} type="number" value={c.timeout}
              onChange={e => handleChange({ ...c, timeout: parseInt(e.target.value) || 300 })} />
          </div>
        </div>
      );
    }
    case 'condition': {
      const c = config as LogicConditionConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>条件表达式 (JSONPath)</label>
            <input className={inputClass} value={c.conditionExpr}
              onChange={e => handleChange({ ...c, conditionExpr: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>✓ Then 分支</label>
            <input className={inputClass} value={c.thenBranch}
              onChange={e => handleChange({ ...c, thenBranch: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>✗ Else 分支</label>
            <input className={inputClass} value={c.elseBranch}
              onChange={e => handleChange({ ...c, elseBranch: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'trigger': {
      const c = config as LogicTriggerConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>Cron 表达式</label>
            <input className={inputClass} value={c.cronExpr}
              onChange={e => handleChange({ ...c, cronExpr: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>时区</label>
            <input className={inputClass} value={c.timezone}
              onChange={e => handleChange({ ...c, timezone: e.target.value })} />
          </div>
        </div>
      );
    }
    default:
      return <div className="p-3 text-slate-400 text-xs">未知节点类型</div>;
  }
}

// ── Helpers ────────────────────────────────────────────────

const TYPE_LABELS: Record<LogicNodeType, string> = {
  llm: 'LLM',
  tool: 'Tool',
  ontology: 'Ontology',
  approval: '审批',
  condition: '条件',
  trigger: '触发器',
};

function typeLabel(type: LogicNodeType): string {
  return TYPE_LABELS[type];
}

// Topological sort for execution order
function topologicalSort(nodes: Node<LogicNodeData>[], edges: Edge<LogicEdgeData>[]): string[] {
  const inDegree: Record<string, number> = {};
  const adj: Record<string, string[]> = {};

  for (const n of nodes) {
    inDegree[n.id] = 0;
    adj[n.id] = [];
  }
  for (const e of edges) {
    if (inDegree[e.target] !== undefined) {
      inDegree[e.target]++;
    }
    if (adj[e.source]) {
      adj[e.source].push(e.target);
    }
  }

  const queue: string[] = [];
  for (const n of nodes) {
    if (inDegree[n.id] === 0) queue.push(n.id);
  }

  const result: string[] = [];
  while (queue.length > 0) {
    const nodeId = queue.shift()!;
    result.push(nodeId);
    for (const neighbor of adj[nodeId] || []) {
      if (--inDegree[neighbor] === 0) {
        queue.push(neighbor);
      }
    }
  }
  return result;
}
