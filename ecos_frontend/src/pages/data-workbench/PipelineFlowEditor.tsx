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
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  Play, Save, GitBranch, Trash2, Zap, Cpu, ArrowLeft,
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

// ─── Types (extracted) ─────────────────────────────────────
import type { NodeConfig, PipelineFlowEditorProps, PipelineSaveNode, PipelineSaveEdge } from './pipeline-editor/types';
import type { PipelineNode } from './types';

// ─── Extracted sub-components ──────────────────────────────
import NodePalette from './pipeline-editor/NodePalette';
import FlowCanvas from './pipeline-editor/FlowCanvas';
import PropertyPanel from './pipeline-editor/PropertyPanel';
import Toast from './pipeline-editor/Toast';

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
  onEngineChange,
  editingPipeline,
  onBack,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [pipelineName, setPipelineName] = useState(editingPipeline?.name || t('dw.pipeline.editor.defaultName'));
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(null);
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<unknown>(null);

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
    if (editingPipeline?.name) setPipelineName(editingPipeline.name);
  }, [editingPipeline]);

  // ── Load editingPipeline.nodes → canvas (T3) ──
  // Reconstruct ReactFlow nodes/edges from the backend PipelineNode[].
  useEffect(() => {
    const apiNodes = editingPipeline?.nodes;
    if (apiNodes && Array.isArray(apiNodes) && apiNodes.length > 0) {
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
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingPipeline?.id]);

  // ── Node counter ──
  const nodeCounter = useRef(0);
  const nextNodeId = useCallback(() => {
    nodeCounter.current += 1;
    return `node-${Date.now()}-${nodeCounter.current}`;
  }, []);

  // ── onConnect ──
  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) =>
        addEdge(
          { ...connection, animated: true, style: { stroke: '#94a3b8', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 16, height: 16 } },
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
      showLocalToast('info', t('dw.pipeline.editor.nodeAdded', { type: paletteType }));
    },
    [reactFlowInstance, nextNodeId, setNodes, showLocalToast, t]
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
    },
    [setNodes]
  );

  // ── Delete node ──
  const deleteNode = useCallback(
    (nodeId: string) => {
      setNodes((nds) => nds.filter((n) => n.id !== nodeId));
      setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
      setSelectedNode(null);
      showLocalToast('info', t('dw.pipeline.editor.nodeDeleted'));
    },
    [setNodes, setEdges, showLocalToast, t]
  );

  // ── Clear canvas ──
  const clearCanvas = useCallback(() => {
    if (nodes.length === 0 && edges.length === 0) return;
    setNodes([]);
    setEdges([]);
    setSelectedNode(null);
    nodeCounter.current = 0;
    showLocalToast('info', t('dw.pipeline.editor.canvasCleared'));
  }, [nodes, edges, setNodes, setEdges, showLocalToast, t]);

  // ── Save (T3): convert ReactFlow nodes/edges → backend PipelineNode shape ──
  const handleSave = useCallback(() => {
    if (!pipelineName.trim()) {
      showLocalToast('error', t('dw.pipeline.editor.nameRequired'));
      return;
    }
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
    onSave({
      id: editingPipeline?.id,
      name: pipelineName.trim(),
      nodes: saveNodes,
      edges: saveEdges,
      computeEngine,
    });
    showLocalToast('success', t('dw.pipeline.editor.saved'));
  }, [pipelineName, nodes, edges, computeEngine, onSave, showLocalToast, t, editingPipeline?.id]);

  // ── Execute ──
  const handleExecute = useCallback(() => {
    if (nodes.length === 0) {
      showLocalToast('error', t('dw.pipeline.editor.canvasEmpty'));
      return;
    }
    setNodes((nds) =>
      nds.map((n) => ({ ...n, data: { ...(n.data as Record<string, unknown>), nodeStatus: 'running' } }))
    );
    onExecute(editingPipeline?.id || pipelineName.trim() || 'untitled');
    showLocalToast('info', t('dw.pipeline.editor.executeTriggered'));
  }, [nodes, pipelineName, onExecute, setNodes, showLocalToast, t, editingPipeline?.id]);

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
            <button onClick={onBack} className={`flex items-center gap-1 text-xs ${styles.cardTextMuted} hover:${styles.infoText} transition-colors`} title={t('dw.pipeline.editor.backToList')}>
              <ArrowLeft size={14} /> {t('dw.pipeline.editor.backToList')}
            </button>
          )}
          <GitBranch size={18} className={`${styles.infoText}`} />
          <input
            type="text" value={pipelineName}
            onChange={(e) => setPipelineName(e.target.value)}
            className={`bg-transparent border-b ${styles.cardBorder} px-1 py-0.5 text-sm font-medium ${styles.cardText} outline-none focus:${styles.infoBorder} transition-colors w-48`}
            placeholder={t('dw.pipeline.editor.namePlaceholder')}
          />
          <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
            editingPipeline ? `${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}` : `${styles.successBg} ${styles.successText} border ${styles.successBorder}`
          }`}>
            {editingPipeline ? t('dw.pipeline.editor.editing') : t('dw.pipeline.editor.creating')}
          </span>
          {editingPipeline && (
            <span className={`text-[10px] px-1.5 py-0.5 rounded-full border font-medium ${
              editingPipeline.status === 'active' ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` :
              editingPipeline.status === 'draft' ? `${styles.sidebarBg} ${styles.cardTextMuted} ${styles.cardBorder}` :
              editingPipeline.status === 'running' ? `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}` :
              editingPipeline.status === 'error' ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}` :
              `${styles.successBg} ${styles.successText} ${styles.successBorder}`
            }`}>
              {editingPipeline.status === 'active' && t('dw.pipeline.status.active')}
              {editingPipeline.status === 'draft' && t('dw.pipeline.status.draft')}
              {editingPipeline.status === 'running' && t('dw.pipeline.status.running')}
              {editingPipeline.status === 'success' && t('dw.pipeline.status.success')}
              {editingPipeline.status === 'error' && t('dw.pipeline.status.error')}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <div className={`flex items-center gap-1.5 rounded-lg p-0.5 ${styles.cardBg}`}>
            <button onClick={() => onEngineChange('memory')}
              className={`flex items-center gap-1 px-3 py-1 text-xs rounded-md transition-colors ${computeEngine === 'memory' ? `${styles.accentBg} ${styles.cardText} shadow` : `${styles.cardTextMuted} hover:${styles.cardText}`}`}>
              <Zap size={12} /> Memory
            </button>
            <button onClick={() => onEngineChange('doris')}
              className={`flex items-center gap-1 px-3 py-1 text-xs rounded-md transition-colors ${computeEngine === 'doris' ? `${styles.accentBg} ${styles.cardText} shadow` : `${styles.cardTextMuted} hover:${styles.cardText}`}`}>
              <Cpu size={12} /> Doris
            </button>
          </div>
          <div className={`w-px h-5 mx-1 ${styles.appBorder}`} />
          <button onClick={clearCanvas} className={`flex items-center gap-1 px-2.5 py-1 text-xs transition-colors ${styles.cardTextMuted} hover:${styles.cardText}`} title={t('dw.pipeline.editor.clearCanvas')}>
            <Trash2 size={13} /> {t('dw.pipeline.editor.clear')}
          </button>
          <button onClick={handleExecute} className={`flex items-center gap-1.5 px-3 py-1 ${styles.successBg} hover:${styles.successBg} ${styles.cardText} rounded-lg text-xs font-medium transition-colors`}>
            <Play size={13} /> {t('dw.pipeline.editor.execute')}
          </button>
          <button onClick={handleSave} className={`flex items-center gap-1.5 px-3 py-1 ${styles.accentBg} hover:${styles.accentBg} ${styles.cardText} rounded-lg text-xs font-medium transition-colors`}>
            <Save size={13} /> {t('dw.pipeline.editor.save')}
          </button>
        </div>
      </div>

      {/* ── Main Content ── */}
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
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onInit={setReactFlowInstance}
            onNodeClick={onNodeClick}
            onPaneClick={onPaneClick}
            onDragOver={onDragOver}
            onDrop={onDrop}
            styles={styles}
          />
        </div>
        <PropertyPanel
          node={selectedNode}
          connections={connections}
          onUpdateNode={updateNodeConfig}
          onDeleteNode={deleteNode}
          onClose={() => setSelectedNode(null)}
        />
      </div>

      {/* ── Toast ── */}
      {toast && <Toast type={toast.type} message={toast.msg} onClose={() => setToast(null)} />}
    </div>
  );
};

export default PipelineFlowEditor;
