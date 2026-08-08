/**
 * PipelineFlowEditor — 可视化拖拽 Pipeline DAG 编辑器
 * 基于 @xyflow/react (v12.11.0) 的完整拖拽式数据管道编排器。
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

// ─── Types (extracted) ─────────────────────────────────────
import type { NodeConfig, PipelineFlowEditorProps } from './pipeline-editor/types';

// ─── Extracted sub-components ──────────────────────────────
import NodePalette from './pipeline-editor/NodePalette';
import FlowCanvas from './pipeline-editor/FlowCanvas';
import PropertyPanel from './pipeline-editor/PropertyPanel';
import Toast from './pipeline-editor/Toast';

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
  const [pipelineName, setPipelineName] = useState(editingPipeline?.name || '新建 Pipeline');
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(null);
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<any>(null);

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
      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top,
      });

      const newNode: Node = {
        id: nextNodeId(),
        type: paletteType,
        position,
        data: { label: paletteType, nodeType: paletteType, nodeStatus: 'idle' },
      };
      setNodes((nds) => [...nds, newNode]);
      showLocalToast('info', `已添加 ${paletteType} 节点`);
    },
    [reactFlowInstance, nextNodeId, setNodes, showLocalToast]
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
          const newData = { ...(n.data as unknown as NodeConfig), ...partialConfig };
          if (partialConfig.nodeType && n.type !== partialConfig.nodeType) {
            return { ...n, type: partialConfig.nodeType, data: newData };
          }
          return { ...n, data: newData };
        })
      );
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

  // ── Clear canvas ──
  const clearCanvas = useCallback(() => {
    if (nodes.length === 0 && edges.length === 0) return;
    setNodes([]);
    setEdges([]);
    setSelectedNode(null);
    nodeCounter.current = 0;
    showLocalToast('info', '画布已清空');
  }, [nodes, edges, setNodes, setEdges, showLocalToast]);

  // ── Save ──
  const handleSave = useCallback(() => {
    if (!pipelineName.trim()) { showLocalToast('error', '请输入 Pipeline 名称'); return; }
    onSave({ name: pipelineName.trim(), nodes, edges, computeEngine });
    showLocalToast('success', 'Pipeline 已保存');
  }, [pipelineName, nodes, edges, computeEngine, onSave, showLocalToast]);

  // ── Execute ──
  const handleExecute = useCallback(() => {
    if (nodes.length === 0) { showLocalToast('error', '画布为空，请先添加节点'); return; }
    setNodes((nds) =>
      nds.map((n) => ({ ...n, data: { ...(n.data as unknown as NodeConfig), nodeStatus: 'running' } }))
    );
    onExecute(pipelineName.trim() || 'untitled');
    showLocalToast('info', 'Pipeline 已提交执行');
  }, [nodes, pipelineName, onExecute, setNodes, showLocalToast]);

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
            <button onClick={onBack} className="flex items-center gap-1 text-xs text-slate-400 hover:text-blue-400 transition-colors" title="返回列表">
              <ArrowLeft size={14} /> 返回列表
            </button>
          )}
          <GitBranch size={18} className="text-blue-400" />
          <input
            type="text" value={pipelineName}
            onChange={(e) => setPipelineName(e.target.value)}
            className="bg-transparent border-b border-slate-600 px-1 py-0.5 text-sm font-medium text-white outline-none focus:border-blue-400 transition-colors w-48"
            placeholder="Pipeline 名称"
          />
          <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
            editingPipeline ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
          }`}>
            {editingPipeline ? '编辑中' : '新建'}
          </span>
          {editingPipeline && (
            <span className={`text-[10px] px-1.5 py-0.5 rounded-full border font-medium ${
              editingPipeline.status === 'active' ? 'bg-green-500/20 text-green-400 border-green-500/30' :
              editingPipeline.status === 'draft' ? 'bg-slate-500/20 text-slate-400 border-slate-500/30' :
              editingPipeline.status === 'running' ? 'bg-blue-500/20 text-blue-400 border-blue-500/30' :
              editingPipeline.status === 'error' ? 'bg-red-500/20 text-red-400 border-red-500/30' :
              'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
            }`}>
              {editingPipeline.status === 'active' && '● 活跃'}
              {editingPipeline.status === 'draft' && '◌ 草稿'}
              {editingPipeline.status === 'running' && '◉ 运行中'}
              {editingPipeline.status === 'success' && '✓ 成功'}
              {editingPipeline.status === 'error' && '✕ 错误'}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <div className={`flex items-center gap-1.5 rounded-lg p-0.5 ${styles.cardBg}`}>
            <button onClick={() => onEngineChange('memory')}
              className={`flex items-center gap-1 px-3 py-1 text-xs rounded-md transition-colors ${computeEngine === 'memory' ? 'bg-blue-600 text-white shadow' : `${styles.cardTextMuted} hover:${styles.cardText}`}`}>
              <Zap size={12} /> Memory
            </button>
            <button onClick={() => onEngineChange('doris')}
              className={`flex items-center gap-1 px-3 py-1 text-xs rounded-md transition-colors ${computeEngine === 'doris' ? 'bg-blue-600 text-white shadow' : `${styles.cardTextMuted} hover:${styles.cardText}`}`}>
              <Cpu size={12} /> Doris
            </button>
          </div>
          <div className={`w-px h-5 mx-1 ${styles.appBorder}`} />
          <button onClick={clearCanvas} className={`flex items-center gap-1 px-2.5 py-1 text-xs transition-colors ${styles.cardTextMuted} hover:${styles.cardText}`} title="清空画布">
            <Trash2 size={13} /> 清空
          </button>
          <button onClick={handleExecute} className="flex items-center gap-1.5 px-3 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-medium transition-colors">
            <Play size={13} /> 执行
          </button>
          <button onClick={handleSave} className="flex items-center gap-1.5 px-3 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-xs font-medium transition-colors">
            <Save size={13} /> 保存
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
