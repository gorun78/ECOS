/**
 * PipelineFlowEditor — visual drag-and-drop Pipeline DAG editor.
 * Based on @xyflow/react (v12.11.0).
 *
 * PMO-3J T3: save/load aligned with the backend PipelineNode structure.
 * Node types follow the P2-01 enumeration (SOURCE_JDBC / SOURCE_CSV /
 * SOURCE_REST / SOURCE_CDC / TRANSFORM_SQL / OUTPUT_OBJECT).
 *
 * @license Apache-2.0
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  useNodesState,
  useEdgesState,
  addEdge,
  Connection,
  Node,
  Edge,
  MarkerType,
  type NodeChange,
  type EdgeChange,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  Play, Save, GitBranch, Trash2, ArrowLeft, Bolt,
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { getErrorMessage } from './helpers';

// ─── Types (extracted) ─────────────────────────────────────
import type { NodeConfig, PipelineFlowEditorProps, PipelineSaveNode, PipelineSaveEdge } from './pipeline-editor/types';
import type { PipelineNode } from './types';
import type { Breakpoint } from './pipelineDebugApi';

// ─── Extracted sub-components ──────────────────────────────
import NodePalette from './pipeline-editor/NodePalette';
import FlowCanvas from './pipeline-editor/FlowCanvas';
import PropertyPanel from './pipeline-editor/PropertyPanel';
import Toast from './pipeline-editor/Toast';
import DebugPanel from './pipeline-editor/DebugPanel';
import type { DebugState } from './pipeline-editor/DebugPanel';
import { runPreFlightCheck } from './pipeline-editor/pipelineValidation';
import MonitorPanel from './pipeline-editor/MonitorPanel';
import GitVersionPanel from './pipeline-editor/GitVersionPanel';

// ─── Helpers: ReactFlow ↔ backend PipelineNode conversion ──

/** Read a numeric position from a backend node (tolerant of field-name variants). */
function readPosition(n: PipelineNode): { x: number; y: number } {
  const anyN = n as unknown as Record<string, unknown>;
  const x = (anyN.positionX as number) ?? (anyN.x as number) ?? (anyN.left as number) ?? 0;
  const y = (anyN.positionY as number) ?? (anyN.y as number) ?? (anyN.top as number) ?? 0;
  return { x: Number(x) || 0, y: Number(y) || 0 };
}

/** Convert a backend PipelineNode (+ its dependsOn) into ReactFlow node/edges. */
function backendNodeToFlow(n: PipelineNode, idx: number): { node: Node; edges: PipelineSaveEdge[] } {
  const id = n.id || `node-${idx}`;
  const { x, y } = readPosition(n);
  const nodeConfig: NodeConfig = {
    label: n.name || n.type || id,
    nodeType: (n.type as NodeConfig['nodeType']) || 'TRANSFORM_SQL',
    config: (n.config as NodeConfig['config']) || {},
    nodeStatus: 'idle',
  };
  const node: Node = {
    id,
    type: n.type || 'TRANSFORM_SQL',
    position: { x, y },
    data: nodeConfig as unknown as Record<string, unknown>,
  };
  // dependsOn → edges (each upstream id becomes an edge from upstream → this)
  const dependsOn = (n as unknown as { dependsOn?: string[] }).dependsOn || [];
  const edges: PipelineSaveEdge[] = dependsOn
    .filter((from) => Boolean(from))
    .map((from) => ({ from, to: id }));
  return { node, edges };
}

// ─── Main Component ───────────────────────────────────────

const PipelineFlowEditor: React.FC<PipelineFlowEditorProps> = ({
  connections,
  pipelines,
  onSave,
  onExecute,
  showToast,
  computeEngine,
  editingPipeline,
  onBack,
  onDirtyChange,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  // ── 未保存修改跟踪（切管道/新建/清空前挽留）──
  const [dirty, setDirty] = useState(false);
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);
  const markDirty = useCallback(() => setDirty(true), []);
  // ── 清空画布确认门 ──
  const [clearConfirmOpen, setClearConfirmOpen] = useState(false);
  const [pipelineName, setPipelineName] = useState(editingPipeline?.name || t('dw.pipeline.editor.defaultName'));
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(null);
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<unknown>(null);
  // Wave 3 (lower) T2: local pre-check failures — drives red borders on
  // the canvas + toast messages on save/execute.
  const [invalidNodeIds, setInvalidNodeIds] = useState<Set<string>>(new Set());
  // Wave 3 (lower) T3: breakpoint + debug-session state. The session itself
  // is owned by the backend in Wave3-upper; this placeholder drives the
  // UI React-side (red dots on canvas, control-bar enable/disable).
  const [breakpoints, setBreakpoints] = useState<Breakpoint[]>([]);
  const [debugState, setDebugState] = useState<DebugState>('idle');
  const [debugOpen, setDebugOpen] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  // PMO-52 T3b: Git Version History drawer state — null = unchecked, true = open,
  // false = explicitly closed by user. When !editingPipeline?.id the GitBranch
  // button is disabled with a "save first" tooltip, not the drawer.
  const [gitVersionOpen, setGitVersionOpen] = useState(false);
  const breakpointSeq = useRef(0);

  const breakpointNodeIds = React.useMemo(
    () => new Set(breakpoints.filter((b) => b.enabled).map((b) => b.nodeId)),
    [breakpoints]
  );

  // ── Wave 3 (lower) T3: breakpoint ops (idempotent add/remove/toggle) ──
  const addBreakpoint = useCallback((nodeId: string) => {
    setBreakpoints((prev) =>
      prev.some((b) => b.nodeId === nodeId)
        ? prev
        : [...prev, { id: `bp-${Date.now()}-${++breakpointSeq.current}`, nodeId, enabled: true }]
    );
  }, []);
  const removeBreakpoint = useCallback((id: string) => {
    setBreakpoints((prev) => prev.filter((b) => b.id !== id));
  }, []);
  const toggleBreakpoint = useCallback((id: string, enabled: boolean) => {
    setBreakpoints((prev) => prev.map((b) => (b.id === id ? { ...b, enabled } : b)));
  }, []);
  const updateBreakpointCondition = useCallback((id: string, condition: string) => {
    setBreakpoints((prev) => prev.map((b) => (b.id === id ? { ...b, condition } : b)));
  }, []);

  // ── Toast helper ──
  const showLocalToast = useCallback(
    (type: 'success' | 'error' | 'info', msg: string) => {
      setToast({ type, msg });
      setTimeout(() => setToast(null), 3000);
      showToast?.(type, msg);
    },
    [showToast]
  );

  // ── Wave 3 (lower) T3: debug control handlers — 只认真实后端会话。
  // 后端不可达 / 无 sessionId ⇒ 显式报错并保持 idle，绝不伪造本地调试会话。
  const mapSessionState = useCallback((state?: string): DebugState => {
    if (state === 'broken' || state === 'paused' || state === 'awaiting') return 'paused';
    if (state === 'completed' || state === 'failed' || state === 'stopped') return 'idle';
    return 'running';
  }, []);
  const handleDebugStart = useCallback(async () => {
    if (breakpoints.length === 0) {
      showLocalToast('error', t('dw.pipeline.debug.startDisabled'));
      return;
    }
    try {
      const { startDebugSession } = await import('./pipelineDebugApi');
      const adhocNodes = nodes.map((n) => {
        const cfg = n.data as unknown as { nodeType?: string; config?: Record<string, unknown> };
        const typeRaw = (cfg?.nodeType as string | number | undefined) ?? n.type ?? 'TRANSFORM_SQL';
        return {
          nodeId: n.id,
          type: typeof typeRaw === 'string' ? typeRaw : String(typeRaw),
          config: (cfg?.config || {}) as Record<string, unknown>,
          dependsOn: edges.filter((e) => e.target === n.id).map((e) => e.source),
        };
      });
      const snap = await startDebugSession({
        definitionId: editingPipeline?.id,
        definition: !editingPipeline?.id
          ? {
              name: pipelineName,
              nodes: adhocNodes,
            }
          : undefined,
        breakpoints: breakpoints.map((b) => ({ nodeId: b.nodeId, condition: b.condition })),
      });
      if (snap?.sessionId) {
        setSessionId(snap.sessionId);
        setDebugState(mapSessionState(snap.state));
        return;
      }
    } catch (e) {
      console.warn('[pipeline-debug] startDebugSession failed:', e);
    }
    // 后端不可达：明示降级，不伪造会话、不进入 running
    setSessionId(null);
    setDebugState('idle');
    showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
  }, [breakpoints.length, showLocalToast, t, nodes, edges, editingPipeline?.id, pipelineName, mapSessionState]);
  const handleDebugStepOver = useCallback(async () => {
    if (debugState !== 'paused' && debugState !== 'running') return;
    if (!sessionId) {
      showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
      return;
    }
    try {
      const { stepDebugSession } = await import('./pipelineDebugApi');
      const snap = await stepDebugSession(sessionId);
      if (!snap) {
        showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
        return;
      }
      setDebugState(mapSessionState(snap.state));
    } catch (e) {
      console.warn('[pipeline-debug] stepDebugSession failed:', e);
      showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
    }
  }, [debugState, sessionId, showLocalToast, t, mapSessionState]);
  const handleDebugContinue = useCallback(async () => {
    if (debugState !== 'paused' || !sessionId) {
      if (debugState !== 'idle' && !sessionId) showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
      return;
    }
    try {
      const { continueDebugSession } = await import('./pipelineDebugApi');
      const snap = await continueDebugSession(sessionId);
      if (!snap) {
        showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
        return;
      }
      setDebugState(mapSessionState(snap.state));
    } catch (e) {
      console.warn('[pipeline-debug] continueDebugSession failed:', e);
      showLocalToast('error', t('dw.pipeline.debug.backendUnavailable'));
    }
  }, [debugState, sessionId, showLocalToast, t, mapSessionState]);
  const handleDebugStop = useCallback(async () => {
    if (debugState === 'idle') return;
    if (sessionId) {
      try {
        const { stopDebugSession } = await import('./pipelineDebugApi');
        await stopDebugSession(sessionId);
      } catch (e) {
        console.warn('[pipeline-debug] stopDebugSession (placeholder) failed:', e);
      }
    }
    setDebugState('idle');
    setSessionId(null);
    setNodes((nds) => nds.map((n) => ({
      ...n,
      data: { ...(n.data as Record<string, unknown>), nodeStatus: 'idle' },
    })));
  }, [debugState, sessionId, setNodes]);
  const handleDebugReset = useCallback(() => {
    setBreakpoints([]);
    setDebugState('idle');
    setSessionId(null);
    setInvalidNodeIds(new Set());
    setNodes((nds) => nds.map((n) => ({
      ...n,
      data: { ...(n.data as Record<string, unknown>), nodeStatus: 'idle' },
    })));
  }, [setNodes]);

  // ── Sync pipelineName when the editing-pipeline identity changes ──
  // Wave 3 (lower) 缺陷③: previously guarded by `if (editingPipeline?.name)`
  // so `editingPipeline` switching to `null` (New) left the old name in the
  // input field. Now we reset to the placeholder when no pipeline is selected.
  useEffect(() => {
    const displayName = editingPipeline?.name || t('dw.pipeline.editor.defaultName');
    setPipelineName(displayName);
  }, [editingPipeline?.id, editingPipeline?.name, t]);

  // ── Load editingPipeline.nodes → canvas (T3) ──
  // Reconstruct ReactFlow nodes/edges from the backend PipelineNode[].
  //
  // 🔴 依赖必须是「定义内容签名」而不是仅 id：列表接口只返回摘要（nodes 为空），
  // 编辑器会先以摘要渲染一次、详情接口随后才带回真节点；若只依赖 id，
  // 详情到达时 id 未变 → 本效果不再执行 → 画布恒空（Wave 3 缺陷① 残留）。
  const loadedDefKey = `${editingPipeline?.id ?? ''}#${Array.isArray(editingPipeline?.nodes) ? editingPipeline.nodes.length : 0}`;
  useEffect(() => {
    const apiNodes = editingPipeline?.nodes;
    setDirty(false); // 切换定义 ⇒ 脏状态复位
    // 新建态（无 id）：清空画布，避免残留上一条管道的内容
    if (!editingPipeline?.id) {
      setNodes([]);
      setEdges([]);
      return;
    }
    if (Array.isArray(apiNodes) && apiNodes.length > 0) {
      const flowNodes: Node[] = [];
      const flowEdges: Edge[] = [];
      apiNodes.forEach((n, idx) => {
        const { node, edges: depEdges } = backendNodeToFlow(n, idx);
        flowNodes.push(node);
        depEdges.forEach((de) => {
          flowEdges.push({
            id: `e-${de.from}-${de.to}`,
            source: de.from,
            target: de.to,
            animated: true,
            style: { stroke: '#94a3b8', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 16, height: 16 },
          });
        });
      });
      setNodes(flowNodes);
      setEdges(flowEdges);
    } else {
      // 所选管道确为空（或仍处摘要态）：清空，保证画布与所选管道一致
      setNodes([]);
      setEdges([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadedDefKey]);

  // ── Node counter ──
  const nodeCounter = useRef(0);
  const nextNodeId = useCallback(() => {
    nodeCounter.current += 1;
    return `node-${Date.now()}-${nodeCounter.current}`;
  }, []);

  // ── 变更跟踪包装（移动/删除节点 = 未保存修改；纯选中不算）──
  const handleNodesChange = useCallback(
    (changes: NodeChange[]) => {
      if (changes.some((c) => c.type === 'position' || c.type === 'remove' || c.type === 'replace')) markDirty();
      onNodesChange(changes);
    },
    [markDirty, onNodesChange]
  );
  const handleEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      if (changes.some((c) => c.type === 'add' || c.type === 'remove' || c.type === 'replace')) markDirty();
      onEdgesChange(changes);
    },
    [markDirty, onEdgesChange]
  );

  // ── onConnect ──
  const onConnect = useCallback(
    (connection: Connection) => {
      markDirty();
      setEdges((eds) =>
        addEdge(
          { ...connection, animated: true, style: { stroke: '#94a3b8', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 16, height: 16 } },
          eds
        )
      );
    },
    [setEdges, markDirty]
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
      const instance = reactFlowInstance as { screenToFlowPosition: (p: { x: number; y: number }) => { x: number; y: number } };
      const position = instance.screenToFlowPosition({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top,
      });

      // T3: data must carry an initialised `config: {}` (P2-01 schema fields).
      const newNode: Node = {
        id: nextNodeId(),
        type: paletteType,
        position,
        data: {
          label: paletteType,
          nodeType: paletteType,
          config: {},
          nodeStatus: 'idle',
        } as unknown as Record<string, unknown>,
      };
      setNodes((nds) => [...nds, newNode]);
      markDirty();
      showLocalToast('info', t('dw.pipeline.editor.nodeAdded', { type: paletteType }));
    },
    [reactFlowInstance, nextNodeId, setNodes, showLocalToast, t, markDirty]
  );

  // ── Node click → select ──
  const onNodeClick = useCallback((_event: React.MouseEvent, node: Node) => {
    setSelectedNode(node);
  }, []);
  const onPaneClick = useCallback(() => setSelectedNode(null), []);

  // ── Update node config ──
  const updateNodeConfig = useCallback(
    (nodeId: string, partialConfig: Partial<NodeConfig>) => {
      setNodes((nds) =>
        nds.map((n) => {
          if (n.id !== nodeId) return n;
          const prev = n.data as unknown as NodeConfig;
          // Merge nested `config` object when partialConfig provides one.
          const merged: NodeConfig = { ...prev, ...partialConfig };
          if (partialConfig.config && prev.config) {
            merged.config = { ...prev.config, ...partialConfig.config };
          }
          if (partialConfig.nodeType && n.type !== partialConfig.nodeType) {
            return { ...n, type: partialConfig.nodeType, data: merged as unknown as Record<string, unknown> };
          }
          return { ...n, data: merged as unknown as Record<string, unknown> };
        })
      );
      setSelectedNode((prev) => {
        if (prev?.id === nodeId) {
          const prevData = prev.data as unknown as NodeConfig;
          const merged: NodeConfig = { ...prevData, ...partialConfig };
          if (partialConfig.config && prevData.config) {
            merged.config = { ...prevData.config, ...partialConfig.config };
          }
          return { ...prev, data: merged as unknown as Record<string, unknown> };
        }
        return prev;
      });
      markDirty();
    },
    [setNodes, markDirty]
  );

  // ── Delete node ──
  const deleteNode = useCallback(
    (nodeId: string) => {
      setNodes((nds) => nds.filter((n) => n.id !== nodeId));
      setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
      setSelectedNode(null);
      markDirty();
      showLocalToast('info', t('dw.pipeline.editor.nodeDeleted'));
    },
    [setNodes, setEdges, showLocalToast, t, markDirty]
  );

  // ── Clear canvas（危险操作：先确认，禁 window.confirm）──
  const clearCanvas = useCallback(() => {
    if (nodes.length === 0 && edges.length === 0) return;
    setClearConfirmOpen(true);
  }, [nodes, edges]);
  const doClearCanvas = useCallback(() => {
    setClearConfirmOpen(false);
    setNodes([]);
    setEdges([]);
    setSelectedNode(null);
    nodeCounter.current = 0;
    markDirty();
    showLocalToast('info', t('dw.pipeline.editor.canvasCleared'));
  }, [setNodes, setEdges, showLocalToast, t, markDirty]);

  // ── Wave 3 (lower) T2: pre-flight validation (invalid fields, DAG cycle,
  // source→sink reachability) ──
  // Failures mark the offending node IDs in `invalidNodeIds` (canvas recolors
  // them via FlowCanvas's `danger` prop), and we toast a friendly summary.
  const runValidation = useCallback((): boolean => {
    const result = runPreFlightCheck(nodes, edges.map((e) => ({ source: e.source, target: e.target })));
    if (result.ok) {
      setInvalidNodeIds(new Set());
      return true;
    }
    setInvalidNodeIds(result.invalidNodeIds);
    // Localized summary: prefer the first issue type to keep it short.
    const cycle = result.issues.find((i) => i.key === 'cycle');
    const orphan = result.issues.find((i) => i.key === 'reachability');
    const missing = result.issues.filter((i) => i.key === 'missingConfig');
    const missingCount = missing.length;
    let msg = '';
    if (missingCount > 0) {
      msg = t('dw.pipeline.validation.missingConfigMany', { n: missingCount });
      const first = missing[0];
      const fieldName = t(`dw.pipeline.validation.field.${first.reason}`);
      msg += ` (${fieldName}: ${first.nodeId.slice(0, 12)}…)`;
    } else if (cycle) {
      msg = t('dw.pipeline.validation.cycle', { n: result.invalidNodeIds.size });
    } else if (orphan) {
      msg = t('dw.pipeline.validation.orphaned', { n: result.invalidNodeIds.size });
    } else {
      msg = t('dw.pipeline.editor.canvasEmpty');
    }
    showLocalToast('error', msg);
    return false;
  }, [nodes, edges, t, showLocalToast]);

  // ── PMO-52 T3b: Git 版本恢复 — 后端回滚端点尚未上线。
  // 无假真理：明示「不可用」，不做任何乐观状态变更。──
  const handleGitVersionRestore = useCallback(
    (): void => {
      showLocalToast('error', t('dw.pipeline.git.restoreUnavailable'));
    },
    [showLocalToast, t]
  );

  // ── Save (T3): convert ReactFlow nodes/edges → backend PipelineNode shape ──
  const handleSave = useCallback(async () => {
    if (!pipelineName.trim()) {
      showLocalToast('error', t('dw.pipeline.editor.nameRequired'));
      return;
    }
    if (!runValidation()) return;
    const saveNodes: PipelineSaveNode[] = nodes.map((n) => {
      const cfg = n.data as unknown as NodeConfig;
      return {
        id: n.id,
        nodeId: n.id,
        type: cfg.nodeType || (n.type as string) || 'TRANSFORM_SQL',
        config: (cfg.config || {}) as Record<string, unknown>,
        positionX: n.position.x,
        positionY: n.position.y,
      };
    });
    const saveEdges: PipelineSaveEdge[] = edges.map((e) => ({ from: e.source, to: e.target }));
    // 无假真理：等待父级持久化完成后再报成功；父级返回 false 视为失败。
    let ok: void | boolean = undefined;
    try {
      ok = await Promise.resolve(onSave({
        id: editingPipeline?.id,
        name: pipelineName.trim(),
        nodes: saveNodes,
        edges: saveEdges,
        computeEngine,
      }));
    } catch (e) {
      showLocalToast('error', t('dw.pipeline.editor.saveFailed', { error: getErrorMessage(e) }));
      return;
    }
    if (ok === false) return; // 父级已提示失败
    setDirty(false);
    showLocalToast('success', t('dw.pipeline.editor.saved'));
  }, [pipelineName, nodes, edges, computeEngine, onSave, showLocalToast, t, editingPipeline?.id, runValidation]);

  // ── Execute ──
  const handleExecute = useCallback(async () => {
    if (nodes.length === 0) {
      showLocalToast('error', t('dw.pipeline.editor.canvasEmpty'));
      return;
    }
    if (!runValidation()) return;
    // 无 id 绝不能拿管道名当 id 发给后端（假执行）。
    const pipelineId = editingPipeline?.id;
    if (!pipelineId) {
      showLocalToast('error', t('dw.pipeline.editor.saveBeforeExecute'));
      return;
    }
    // 不预设全体 running；等待触发结果后按真实状态收敛。
    let res: { executionId?: string; status?: string } | null | void = null;
    try {
      res = await Promise.resolve(onExecute(pipelineId));
    } catch (e) {
      showLocalToast('error', t('dw.pipeline.editor.executeFailed', { error: getErrorMessage(e) }));
      return;
    }
    if (!res) {
      showLocalToast('error', t('dw.pipeline.editor.executeUnreachable'));
      return;
    }
    const st = (res.status || '').toLowerCase();
    const bad = ['failed', 'error', 'cancelled', 'canceled', 'timeout'].includes(st);
    const done = ['success', 'succeeded', 'completed', 'finished', 'done'].includes(st);
    const paint = (nodeStatus: 'running' | 'success' | 'error') =>
      setNodes((nds) => nds.map((n) => ({ ...n, data: { ...(n.data as Record<string, unknown>), nodeStatus } })));
    if (bad) {
      paint('error');
      showLocalToast('error', t('dw.pipeline.editor.executeFailedStatus', { status: res.status || 'failed' }));
    } else if (done) {
      paint('success');
      showLocalToast('success', t('dw.pipeline.editor.executeSuccess'));
    } else {
      paint('running');
      showLocalToast('info', t('dw.pipeline.editor.executeTriggered'));
    }
  }, [nodes, onExecute, setNodes, showLocalToast, t, editingPipeline?.id, runValidation]);

  // ── Drag start from palette ──
  const onDragStart = useCallback((event: React.DragEvent<HTMLDivElement>, nodeType: string) => {
    event.dataTransfer.setData('application/pipelinenodetype', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  }, []);

  // ── Keyboard shortcuts ──
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setSelectedNode(null);
      if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); handleSave(); }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleSave]);

  // ── Render ──
  return (
    <div className={`h-full flex flex-col ${styles.cardBg}`}>
      {/* ── Top Toolbar ── */}
      <div className={`flex items-center justify-between px-4 py-2 border-b shrink-0 ${styles.appBg} ${styles.appText} ${styles.appBorder}`}>
        <div className="flex items-center gap-3">
          {onBack && (
            <button type="button" onClick={onBack} className={`flex items-center gap-1 text-xs ${styles.cardTextMuted} hover:opacity-80 transition-colors`} title={t('dw.pipeline.editor.backToList')}>
              <ArrowLeft size={14} /> {t('dw.pipeline.editor.backToList')}
            </button>
          )}
          <button
            type="button"
            disabled={!editingPipeline?.id}
            onClick={() => {
              if (!editingPipeline?.id) {
                showLocalToast('info', t('dw.pipeline.git.saveFirst'));
                return;
              }
              setGitVersionOpen((v) => !v);
            }}
            title={
              editingPipeline?.id
                ? t('dw.pipeline.git.title')
                : t('dw.pipeline.git.saveFirst')
            }
            className={`p-1 rounded transition-colors ${
              gitVersionOpen
                ? styles.accentBg
                : styles.cardTextMuted
            } ${editingPipeline?.id
              ? 'hover:opacity-80 cursor-pointer'
              : 'opacity-50 cursor-not-allowed'
            }`}
          >
            <GitBranch size={18} />
          </button>
          <input
            type="text" value={pipelineName}
            onChange={(e) => { setPipelineName(e.target.value); markDirty(); }}
            className={`bg-transparent border-b ${styles.cardBorder} px-1 py-0.5 text-sm font-medium ${styles.cardText} outline-none transition-colors w-48`}
            placeholder={t('dw.pipeline.editor.namePlaceholder')}
          />
          <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
            editingPipeline ? `${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}` : `${styles.successBg} ${styles.successText} border ${styles.successBorder}`
          }`}>
            {editingPipeline ? t('dw.pipeline.editor.editing') : t('dw.pipeline.editor.creating')}
          </span>
          {editingPipeline && (() => {
            // 状态徽章：未知枚举不再渲染空白徽章 ⇒ 兜底显示原始状态
            const st = editingPipeline.status as string;
            const badgeClass =
              st === 'active' || st === 'success' ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` :
              st === 'draft' ? `${styles.sidebarBg} ${styles.cardTextMuted} ${styles.cardBorder}` :
              st === 'running' ? `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}` :
              st === 'error' ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}` :
              `${styles.sidebarBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
            const labelKeyByStatus: Record<string, string> = {
              active: 'dw.pipeline.status.active',
              draft: 'dw.pipeline.status.draft',
              running: 'dw.pipeline.status.running',
              success: 'dw.pipeline.status.success',
              error: 'dw.pipeline.status.error',
            };
            const label = labelKeyByStatus[st] ? t(labelKeyByStatus[st]) : (st || '—');
            return (
              <span className={`text-[10px] px-1.5 py-0.5 rounded-full border font-medium ${badgeClass}`}>
                {label}
              </span>
            );
          })()}
        </div>
        <div className="flex items-center gap-2">
          <button type="button" onClick={clearCanvas} className={`flex items-center gap-1 px-2.5 py-1 text-xs transition-colors ${styles.cardTextMuted} hover:opacity-80`} title={t('dw.pipeline.editor.clearCanvas')}>
            <Trash2 size={13} /> {t('dw.pipeline.editor.clear')}
          </button>
          <button type="button" onClick={() => setDebugOpen((v) => !v)}
            className={`flex items-center gap-1 px-2.5 py-1 text-xs transition-colors rounded ${
              debugOpen
                ? `${styles.accentBg} ${styles.accentText}`
                : `${styles.cardTextMuted} hover:opacity-80`
            }`}
            title={t('dw.pipeline.debug.title')}
          >
            <Bolt size={13} className={debugState === 'running' ? 'animate-pulse' : ''} />
            {breakpoints.length > 0 && (
              <span className={`text-[9px] font-mono ${debugOpen ? styles.accentText : styles.cardTextMuted}`}>{breakpoints.length}</span>
            )}
          </button>
          <button type="button" onClick={handleExecute} className={`flex items-center gap-1.5 px-3 py-1 ${styles.successBg} hover:opacity-90 ${styles.cardText} rounded-lg text-xs font-medium transition-colors`}>
            <Play size={13} /> {t('dw.pipeline.editor.execute')}
          </button>
          <button type="button" onClick={handleSave} className={`flex items-center gap-1.5 px-3 py-1 ${styles.accentBg} hover:opacity-90 ${styles.cardText} rounded-lg text-xs font-medium transition-colors`}>
            <Save size={13} /> {t('dw.pipeline.editor.save')}
          </button>
        </div>
      </div>

      {/* ── Main Content ── */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex-1 flex overflow-hidden">
          <NodePalette
            styles={styles}
            connectionsCount={connections.length}
            pipelinesCount={pipelines.length}
            onDragStart={onDragStart}
          />
          <div className="flex-1" ref={reactFlowWrapper}>
            <FlowCanvas
              nodes={nodes}
              edges={edges}
              onNodesChange={handleNodesChange}
              onEdgesChange={handleEdgesChange}
              onConnect={onConnect}
              onInit={setReactFlowInstance}
              onNodeClick={onNodeClick}
              onPaneClick={onPaneClick}
              onDragOver={onDragOver}
              onDrop={onDrop}
              styles={styles}
              invalidNodeIds={invalidNodeIds}
              breakpointNodeIds={breakpointNodeIds}
            />
          </div>
          <PropertyPanel
            node={selectedNode}
            connections={connections}
            onUpdateNode={updateNodeConfig}
            onDeleteNode={deleteNode}
            onClose={() => setSelectedNode(null)}
          />
          {debugOpen && (
            <div className={`w-72 shrink-0 border-l ${styles.cardBorder}`}>
              <DebugPanel
                enabled={true}
                breakpoints={breakpoints}
                state={debugState}
                currentNodeId={selectedNode?.id ?? null}
                onAddBreakpoint={addBreakpoint}
                onRemoveBreakpoint={removeBreakpoint}
                onToggleBreakpoint={toggleBreakpoint}
                onUpdateCondition={updateBreakpointCondition}
                onStart={handleDebugStart}
                onStepOver={handleDebugStepOver}
                onContinue={handleDebugContinue}
                onStop={handleDebugStop}
                onReset={handleDebugReset}
              />
            </div>
          )}
          {gitVersionOpen && editingPipeline?.id && (
            <GitVersionPanel
              pipelineId={editingPipeline.id}
              pipelineName={pipelineName}
              onClose={() => setGitVersionOpen(false)}
              onRestore={handleGitVersionRestore}
            />
          )}
        </div>

        {/* ── Monitor Panel (画布下方停靠面板) ── */}
        <MonitorPanel>
        </MonitorPanel>
      </div>

      {/* ── Toast ── */}
      {toast && <Toast type={toast.type} message={toast.msg} onClose={() => setToast(null)} />}

      {/* ── 清空画布确认 ── */}
      <ConfirmDialog
        visible={clearConfirmOpen}
        variant="danger"
        title={t('dw.pipeline.editor.clearCanvasConfirmTitle')}
        message={t('dw.pipeline.editor.clearCanvasConfirmMessage')}
        confirmText={t('dw.pipeline.editor.clear')}
        cancelText={t('common.cancel')}
        onConfirm={doClearCanvas}
        onCancel={() => setClearConfirmOpen(false)}
      />
    </div>
  );
};

export default PipelineFlowEditor;
