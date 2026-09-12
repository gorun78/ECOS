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
import { useLanguage } from '../../components/LanguageContext';
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
import { apiFetchData } from '../../api';

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

/** 节点 trace：来自后端 executions 端点的节点级别执行数据（P2 后端无该字段时为空） */
interface NodeTrace {
  input?: string;
  output?: string;
  latencyMs?: number;
  errorMessage?: string;
}

/** 后端 PipelineExecution 的节点状态映射（后端仅 running/completed/failed 三种）*/
type BackendRunStatus = 'running' | 'completed' | 'failed';

interface BackendPipelineExec {
  executionId: string;
  pipelineId: string;
  status: BackendRunStatus;
  startedAt: string;
  completedAt?: string;
  result?: { message?: string; pipelineId?: string; params?: Record<string, unknown> };
}

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
  /** 节点 trace drawer 开关（按 TraceId 收可点开 — 节点执行 trace 区域）*/
  const [traceOpen, setTraceOpen] = useState(false);

  const onNodeDoubleClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node as Node<LogicNodeData>);
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

  // ── Execution Engine ──
  const [isExecuting, setIsExecuting] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [showLogs, setShowLogs] = useState(false);
  const [totalDuration, setTotalDuration] = useState<number | null>(null);
  const [traceMap, setTraceMap] = useState<Record<string, NodeTrace>>({});
  /** 全局 AbortController（5min 自动 abort） — 切 pipeline / 卸载时清除 */
  const execAbortRef = useRef<AbortController | null>(null);
  /** 轮询定时器 ID */
  const pollTimerRef = useRef<number | null>(null);
  /** 启动时刻，用于算 totalDuration */
  const runStartRef = useRef<number | null>(null);

  /** 清理进行中的执行（abort + 清 timer + 重置 isExecuting + 节点回 idle） */
  const cleanupExec = useCallback(() => {
    execAbortRef.current?.abort();
    execAbortRef.current = null;
    if (pollTimerRef.current != null) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    runStartRef.current = null;
    if (isExecuting) {
      setIsExecuting(false);
      setTotalDuration(null);
      setNodes(prev => prev.map(n => ({ ...n, data: { ...n.data, status: 'idle' as LogicNodeStatus } })));
    }
  }, [isExecuting, setNodes]);

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

  /**
   * 启动 Pipeline 真执行 + 轮询 executions。
   * 后端端点:
   *   POST /api/v1/aip/studio/pipelines/{pipelineId}/execute  → { executionId, status:'running', ... }
   *   GET  /api/v1/aip/studio/pipelines/{executionId}/executions → { executionId, status, ... }
   * 语义：
   *   - 启动失败（后端不可用 / 404 / 未注册）→ toast error，画布保留，节点保持 idle
   *   - 轮询每 2s 一次，到 completed/failed 或 5min 自动 abort
   *   - 后端 P2 暂无 nodeTraces 字段，状态回写以 pipeline 整体为粒度:
   *     running → 全部 inTopoOrder 标 running，completed → 全 success，failed → 全 error
   */
  const startPipelineRun = useCallback(async () => {
    if (nodes.length === 0 || !selectedPipeline) return;
    cleanupExec();
    setIsExecuting(true);
    setShowLogs(true);
    setLogs([]);
    setTotalDuration(null);
    setTraceMap({});
    runStartRef.current = Date.now();

    // 输入参数：pipeline.inputs 全部用 testInputs 占位
    const inputParams: Record<string, string> = {};
    for (const inp of selectedPipeline.inputs) {
      inputParams[inp.name] = selectedPipeline.testInputs?.[inp.name] || '';
    }

    // ── 1. 启动执行 ──
    let execId = '';
    try {
      const exec = await apiFetchData<BackendPipelineExec & { executionId?: string }>(
        `/api/v1/aip/studio/pipelines/${encodeURIComponent(selectedPipeline.id)}/execute`,
        { method: 'POST', body: JSON.stringify(inputParams) }
      );
      execId = (exec && (exec as BackendPipelineExec).executionId) || '';
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      // graceful: 不清空画布、不抛错 toast
      showToast?.('error', `${t('aiworkbench.logic.run.serviceUnavailable')}: ${msg}`);
      cleanupExec();
      return;
    }
    if (!execId) {
      showToast?.('error', t('aiworkbench.logic.run.failedNoId'));
      cleanupExec();
      return;
    }
    setLogs([t('aiworkbench.logic.run.started', { id: execId })]);

    // ── 2. 轮询 executions（每 2s 一次，5min 总超时）──
    const ac = new AbortController();
    execAbortRef.current = ac;

    const pollOnce = async (): Promise<void> => {
      if (ac.signal.aborted) return;
      try {
        const res = await apiFetchData<BackendPipelineExec>(
          `/api/v1/aip/studio/pipelines/${encodeURIComponent(execId)}/executions`,
          { signal: ac.signal }
        );
        if (!res) return;
        // 后端 P1: 仅 status/startedAt/completedAt/result.message，无 nodeTraces
        // 状态回写：按拓扑顺序遍历 nodes，把整体状态映射到每个节点
        const topo = topologicalSort(nodes, edges);
        const finalStatusMap: Record<string, LogicNodeStatus> = {};
        for (const nodeId of topo) finalStatusMap[nodeId] = res.status === 'completed' ? 'success' : res.status === 'failed' ? 'error' : 'running';
        setNodes(prev => prev.map(n => finalStatusMap[n.id] ? { ...n, data: { ...n.data, status: finalStatusMap[n.id] } } : n));
        setLogs(prev => [...prev, res.status === 'running' ? t('aiworkbench.logic.run.polling', { id: execId }) : t('aiworkbench.logic.run.finished', { msg: res.result?.message || res.status, elapsed: Date.now() - (runStartRef.current || Date.now()) })]);
        if (res.status === 'completed' || res.status === 'failed') {
          if (pollTimerRef.current != null) window.clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
          setIsExecuting(false);
          setTotalDuration(Date.now() - (runStartRef.current || Date.now()));
          if (res.status === 'completed') {
            showToast?.('success', t('aiworkbench.logic.run.success'));
          } else {
            showToast?.('error', t('aiworkbench.logic.run.fail', { msg: res.result?.message || res.status }));
          }
        }
      } catch (e) {
        // 后端 404 / 5xx → 终止轮询
        if (ac.signal.aborted) return;
        const msg = e instanceof Error ? e.message : String(e);
        if (pollTimerRef.current != null) window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
        setNodes(prev => prev.map(n => ({ ...n, data: { ...n.data, status: n.data.status === 'idle' ? 'idle' : 'error' as LogicNodeStatus } })));
        setLogs(prev => [...prev, t('aiworkbench.logic.run.pollFailed', { msg })]);
        showToast?.('error', `${t('aiworkbench.logic.run.serviceUnavailable')}: ${msg}`);
        setIsExecuting(false);
        setTotalDuration(Date.now() - (runStartRef.current || Date.now()));
      }
    };

    pollOnce(); // 立即首查一次
    pollTimerRef.current = window.setInterval(pollOnce, 2000);

    // 5min 整体超时
    window.setTimeout(() => {
      if (ac.signal.aborted) return;
      try { ac.abort(); } catch { /* noop */ }
      if (pollTimerRef.current != null) window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
      setLogs(prev => [...prev, t('aiworkbench.logic.run.timeout')]);
      showToast?.('error', t('aiworkbench.logic.run.timeout'));
      setIsExecuting(false);
    }, 300_000);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes, edges, selectedPipeline, cleanupExec, showToast, t, setNodes]);

  /** 切换 pipeline / 卸载时清理执行 */
  useEffect(() => {
    return () => cleanupExec();
  }, [selectedPipelineId, cleanupExec]);

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
      <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
        <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
          <span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.logic.list.title', { count: pipelines.length })}</span>
          <button
            onClick={handleStartCreate}
            className={`p-1 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} border ${styles.accentBorder} rounded-md transition-colors cursor-pointer`}
            title={t('aiworkbench.logic.list.add')}
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
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1.5 border ${
                  isSelected
                    ? `${styles.accentBg} ${styles.accentText} shadow-xs`
                    : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`
                }`}
              >
                <div className="flex items-center gap-1.5 font-bold">
                  <Icon name="Cpu" size={12} className={isSelected ? `${styles.accentText} animate-pulse` : `${styles.cardTextMuted}`} />
                  <span className="truncate">{p.name}</span>
                </div>
                <p className={`text-[10px] line-clamp-2 leading-relaxed ${isSelected ? `${styles.cardTextMuted} opacity-70` : `${styles.cardTextMuted} opacity-80`}`}>
                  {p.description}
                </p>
                <div className={`flex items-center justify-between text-[9px] border-t ${styles.inputBorder}/10 pt-1`}>
                  <span className={`font-mono ${isSelected ? `${styles.cardTextMuted} opacity-70` : `${styles.cardTextMuted} opacity-80`}`}>{p.lastUpdated.split(' ')[0]}</span>
                  <span className={`px-1 ${styles.successBg} ${styles.successText} rounded font-bold`}>{t('aiworkbench.logic.list.ready')}</span>
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
                <Controls className={`!${styles.cardBg} !border !${styles.appBorder} !rounded-lg !shadow-sm`} />
                <MiniMap
                  className={`!rounded-lg !shadow-sm !border !${styles.appBorder}`}
                  nodeColor={(n) => {
                    const c: Record<LogicNodeType, string> = {
                      llm: '#a855f7', tool: '#f59e0b', ontology: '#06b6d4',
                      approval: '#f43f5e', condition: '#6366f1', trigger: '#14b8a6',
                    };
                    return c[(n.data as LogicNodeData)?.type] || '#94a3b8';
                  }}
                />
                <Background variant={BackgroundVariant.Dots} gap={20} size={1} className={styles.cardTextMuted} />

                {/* Top toolbar */}
                <Panel position="top-left" className="flex items-center gap-1.5">
                  {/* Add nodes dropdown */}
                  <div className={`flex ${styles.cardBg} ${styles.appBorder} border rounded-lg shadow-sm overflow-hidden`}>
                    {(['llm', 'tool', 'ontology', 'approval', 'condition', 'trigger'] as LogicNodeType[]).map(nodeType => (
                      <button
                        key={nodeType}
                        onClick={() => addCanvasNode(nodeType)}
                        className={`px-2 py-1.5 text-[10px] font-bold ${styles.cardTextMuted} ${styles.cardBorder} border-r last:border-r-0 cursor-pointer transition-opacity hover:opacity-80`}
                        title={t('aiworkbench.logic.addNode', { type: typeLabel(nodeType) })}
                      >
                        {typeLabel(nodeType)}
                      </button>
                    ))}
                  </div>
                </Panel>

                <Panel position="top-center" className="flex items-center gap-2">
                  <button
                    onClick={startPipelineRun}
                    disabled={isExecuting || nodes.length === 0}
                    className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} font-bold rounded-lg shadow-sm transition-colors cursor-pointer flex items-center gap-1.5 text-[11px] ${
                      isExecuting ? 'opacity-60 cursor-not-allowed' : ''
                    }`}
                    title={t('aiworkbench.logic.run.tip')}
                  >
                    {isExecuting ? (
                      <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    ) : (
                      <Icon name="Play" size={12} />
                    )}
                    <span>{isExecuting ? t('aiworkbench.logic.run.running') : t('aiworkbench.logic.run.label')}</span>
                  </button>

                  <button
                    onClick={handleUndo}
                    disabled={historyIndex < 0}
                    className={`px-2 py-1.5 ${styles.cardBg} ${styles.appBorder} border rounded-lg shadow-sm hover:opacity-80 disabled:opacity-40 cursor-pointer transition-opacity`}
                    title="撤销 (Ctrl+Z)"
                  >
                    <Icon name="Undo2" size={12} className={`${styles.cardTextMuted}`} />
                  </button>
                  <button
                    onClick={handleRedo}
                    disabled={historyIndex + 1 >= history.length}
                    className={`px-2 py-1.5 ${styles.cardBg} ${styles.appBorder} border rounded-lg shadow-sm hover:opacity-80 disabled:opacity-40 cursor-pointer transition-opacity`}
                    title="重做 (Ctrl+Y)"
                  >
                    <Icon name="Redo2" size={12} className={`${styles.cardTextMuted}`} />
                  </button>

                  <button
                    onClick={deleteSelectedNodes}
                    className={`px-2 py-1.5 ${styles.cardBg} ${styles.dangerBorder} border rounded-lg shadow-sm cursor-pointer transition-opacity hover:opacity-80`}
                    title="删除选中节点 (Delete)"
                  >
                    <Icon name="Trash2" size={12} className={`${styles.dangerText}`} />
                  </button>

                  <button
                    onClick={() => setShowLogs(!showLogs)}
                    className={`px-2 py-1.5 border rounded-lg shadow-sm cursor-pointer transition-colors text-[10px] font-bold ${
                      showLogs ? `${styles.infoBg} ${styles.infoBorder} ${styles.infoText}` : `${styles.cardBg} ${styles.appBorder} ${styles.cardTextMuted} hover:opacity-80`
                    }`}
                  >
                    <span className="flex items-center gap-1">
                      <Icon name="Terminal" size={11} />
                      {t('aiworkbench.logic.run.logsLabel', { count: logs.length })}
                    </span>
                  </button>

                  {totalDuration != null && (
                    <span className={`px-2 py-1 ${styles.cardBg} ${styles.appBorder} border rounded-lg text-[10px] font-mono ${styles.cardTextMuted} shadow-sm`}>
                      {t('aiworkbench.logic.run.totalDuration', { ms: totalDuration })}
                    </span>
                  )}
                </Panel>
              </ReactFlow>
            </ReactFlowProvider>
          </div>

          {/* Right: Config Panel + Node Trace Drawer */}
          {selectedNode && (
            <div className={`w-72 ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0 overflow-y-auto`}>
              <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between sticky top-0 z-10`}>
                <span className={`font-bold ${styles.cardText} text-[11px]`}>
                  {t('aiworkbench.logic.config.title', { label: selectedNode.data.label })}
                </span>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setTraceOpen(v => !v)}
                    className={`${traceOpen ? `${styles.infoText} ${styles.infoBg}` : `${styles.cardTextMuted} ${styles.badgeBg}`} px-1.5 py-0.5 rounded text-[10px] font-bold cursor-pointer transition-colors`}
                    title={t('aiworkbench.logic.trace.toggle')}
                  >
                    <span className="flex items-center gap-1">
                      <Icon name="Cite" size={11} />
                      {t('aiworkbench.logic.trace.tab')}
                    </span>
                  </button>
                  <button
                    onClick={() => setSelectedNode(null)}
                    className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}
                  >
                    <Icon name="X" size={14} />
                  </button>
                </div>
              </div>
              {traceOpen && (
                <div className="p-3 space-y-2 text-[10px]">
                  <p className={`font-bold ${styles.cardText}`}>{t('aiworkbench.logic.trace.title')}</p>
                  <div className={`rounded-lg border ${styles.cardBorder} p-2 space-y-1.5 ${styles.inputBg}`}>
                    {(() => {
                      const tr = traceMap[selectedNode.id];
                      if (!tr) {
                        return <p className={`${styles.cardTextMuted} italic`}>{t('aiworkbench.logic.trace.empty')}</p>;
                      }
                      return (
                        <>
                          {tr.input !== undefined && (
                            <div className="space-y-0.5">
                              <p className={`font-mono text-[9px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.logic.trace.input')}</p>
                              <pre className={`whitespace-pre-wrap break-all font-mono text-[10px] ${styles.cardText}`}>{tr.input}</pre>
                            </div>
                          )}
                          {tr.output !== undefined && (
                            <div className="space-y-0.5">
                              <p className={`font-mono text-[9px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.logic.trace.output')}</p>
                              <pre className={`whitespace-pre-wrap break-all font-mono text-[10px] ${styles.cardText}`}>{tr.output}</pre>
                            </div>
                          )}
                          {tr.latencyMs != null && (
                            <div className="flex items-center justify-between">
                              <span className={`font-mono text-[9px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.logic.trace.latency')}</span>
                              <span className={`font-mono font-bold ${styles.infoText}`}>{tr.latencyMs}ms</span>
                            </div>
                          )}
                          {tr.errorMessage && (
                            <div className={`p-2 rounded-md border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} text-[10px] flex items-start gap-2`}>
                              <Icon name="AlertTriangle" size={12} className="mt-0.5 shrink-0" />
                              <pre className="whitespace-pre-wrap break-all font-mono flex-1">{tr.errorMessage}</pre>
                            </div>
                          )}
                        </>
                      );
                    })()}
                  </div>
                </div>
              )}
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
          <span>{t('aiworkbench.logic.emptyHint')}</span>
        </div>
      )}

      {/* Bottom: Log Panel */}
      {showLogs && (
        <div className={`absolute bottom-0 left-56 right-0 z-20 ${styles.cardBg} border-t ${styles.cardBorder} shadow-lg`}
             style={{ maxHeight: '200px' }}>
          <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
            <span className={`font-bold ${styles.cardText} text-[10px] flex items-center gap-1`}>
              <Icon name="Terminal" size={11} />
              {t('aiworkbench.logic.logs.title')}
              {totalDuration != null && (
                <span className={`font-mono text-[9px] ${styles.cardTextMuted} ml-2`}>{t('aiworkbench.logic.run.totalDuration', { ms: totalDuration })}</span>
              )}
            </span>
            <button
              onClick={() => setShowLogs(false)}
              className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}
            >
              <Icon name="ChevronDown" size={12} />
            </button>
          </div>
          <div className="overflow-y-auto p-2 max-h-[160px] space-y-0.5 font-mono text-[10px]">
            {logs.length === 0 ? (
              <span className={`${styles.cardTextMuted} italic px-2`}>{t('aiworkbench.logic.logs.emptyHint')}</span>
            ) : (
              logs.map((log, i) => (
                <p key={i} className={`px-2 py-0.5 leading-relaxed ${log.includes('✅') ? `${styles.successText}` : log.includes('❌') || log.includes('⛔') ? `${styles.dangerText}` : `${styles.cardTextMuted}`}`}>
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
                {editingPipeline ? t('aiworkbench.logic.modal.editTitle') : t('aiworkbench.logic.modal.createTitle')}
              </h3>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}
              >
                <Icon name="X" size={15} />
              </button>
            </div>
            <form onSubmit={handleSave} className="p-4 space-y-4">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.name')} <span className={`${styles.dangerText}`}>*</span></label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder={t('aiworkbench.logic.modal.namePlaceholder')}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  required
                />
              </div>
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.desc')} <span className={`${styles.dangerText}`}>*</span></label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder={t('aiworkbench.logic.modal.descPlaceholder')}
                  rows={2}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none`}
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.inputName')}</label>
                  <input
                    type="text"
                    value={formInputName}
                    onChange={e => setFormInputName(e.target.value)}
                    placeholder={t('aiworkbench.logic.modal.inputNamePlaceholder')}
                    className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono`}
                  />
                </div>
                <div className="space-y-1">
                  <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.inputType')}</label>
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
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} transition-opacity hover:opacity-80 cursor-pointer text-[11px] font-semibold`}
                >
                  {t('aiworkbench.logic.modal.cancel')}
                </button>
                <button
                  type="submit"
                  className={`px-4 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} rounded-lg transition-opacity font-bold shadow-sm cursor-pointer text-[11px]`}
                >
                  {t('aiworkbench.logic.modal.save')}
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
  const { t } = useLanguage();
  const { type, config } = node.data;

  const handleChange = (newConfig: LogicNodeConfig) => {
    onUpdate({ ...newConfig });
  };

  const inputClass = `w-full px-2 py-1.5 border ${styles.cardBorder} rounded-lg text-[10px] font-mono ${styles.inputBg}`;
  const labelClass = `${styles.cardTextMuted} font-bold text-[10px] block mb-0.5`;
  const textareaClass = `w-full px-2 py-1.5 border ${styles.cardBorder} rounded-lg text-[10px] font-mono resize-none ${styles.inputBg}`;

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
            <label className={labelClass}>{t('aiworkbench.logic.config.tool.name')}</label>
            <input className={inputClass} value={c.toolName} onChange={e => handleChange({ ...c, toolName: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.tool.params')}</label>
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
            <label className={labelClass}>{t('aiworkbench.logic.config.ontology.queryType')}</label>
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
            <label className={labelClass}>{t('aiworkbench.logic.config.approval.approver')}</label>
            <input className={inputClass} value={c.approver} onChange={e => handleChange({ ...c, approver: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.approval.timeout')}</label>
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
            <label className={labelClass}>{t('aiworkbench.logic.config.condition.expr')}</label>
            <input className={inputClass} value={c.conditionExpr}
              onChange={e => handleChange({ ...c, conditionExpr: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.condition.then')}</label>
            <input className={inputClass} value={c.thenBranch}
              onChange={e => handleChange({ ...c, thenBranch: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.condition.else')}</label>
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
            <label className={labelClass}>{t('aiworkbench.logic.config.trigger.cron')}</label>
            <input className={inputClass} value={c.cronExpr}
              onChange={e => handleChange({ ...c, cronExpr: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.trigger.timezone')}</label>
            <input className={inputClass} value={c.timezone}
              onChange={e => handleChange({ ...c, timezone: e.target.value })} />
          </div>
        </div>
      );
    }
    default:
      return <div className={`p-3 ${styles.cardTextMuted} text-xs`}>{t('aiworkbench.logic.unknownNode')}</div>;
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
