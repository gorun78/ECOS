/**
 * WorkflowDesigner — 可视化流程设计器
 * React Flow 画布 + 5种节点类型 + 属性面板 + 保存/发布/测试
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  ReactFlow, Controls, Background, MiniMap,
  useNodesState, useEdgesState, addEdge, Connection,
  Node, Edge,
  MarkerType,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  Play, Save, Send, Plus, AlertCircle,
  CheckCircle, Loader2, ArrowLeft, GitBranch,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import {
  fetchWorkflows, fetchWorkflow, createWorkflow,
  updateWorkflow, publishWorkflow, testWorkflow,
} from "../api";
import DesignerNode from "./WorkflowDesigner/DesignerNode";
import TestDialog from "./WorkflowDesigner/TestDialog";
import PropertyPanel from "./WorkflowDesigner/PropertyPanel";
import { NODE_TYPES, MINIMAP_COLORS, MINIMAP_FALLBACK } from "./WorkflowDesigner/helpers";

// ── React Flow node types registration ──
const nodeTypes = {
  start: DesignerNode,
  end: DesignerNode,
  human_task: DesignerNode,
  agent_node: DesignerNode,
  condition_gateway: DesignerNode,
};

// ── Main Component ──
export default function WorkflowDesigner() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [workflows, setWorkflows] = useState<any[]>([]);
  const [selectedWfId, setSelectedWfId] = useState<string | null>(null);
  const [wfDetail, setWfDetail] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<{ type: "ok" | "err"; msg: string } | null>(null);

  // React Flow state
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [rfInstance, setRfInstance] = useState<any>(null);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  // Test dialog
  const [showTestDialog, setShowTestDialog] = useState(false);
  const [testResult, setTestResult] = useState<any>(null);
  const [saving, setSaving] = useState(false);
  const [view, setView] = useState<"list" | "designer">("list");

  const showToast = (type: "ok" | "err", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  };

  // Load workflow list
  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchWorkflows();
      setWorkflows(data.data || []);
    } catch (e: any) { showToast("err", e.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { loadList(); }, []);

  // Load workflow detail into canvas
  const openDesigner = async (wfId: string) => {
    setSelectedWfId(wfId);
    setView("designer");
    try {
      const detail = await fetchWorkflow(wfId);
      setWfDetail(detail);

      const rfNodes: Node[] = (detail.nodes || []).map((n: any) => {
        const nt = (n.nodeType || "TASK").toLowerCase();
        const nodeType = nt === "start" ? "start" : nt === "end" ? "end" :
          nt === "gateway" ? "condition_gateway" : n.nodeType === "AGENT_NODE" ? "agent_node" : "human_task";
        return {
          id: n.id, type: nodeType,
          position: { x: Math.random() * 400 + 50, y: Math.random() * 300 + 50 },
          data: { label: n.name || n.code || n.id, ...(n.config || {}), nodeType: n.nodeType, assignee: n.assigneeRole },
        };
      });

      const rfEdges: Edge[] = (detail.edges || []).map((e: any) => ({
        id: e.id, source: e.source, target: e.target, animated: false,
        markerEnd: { type: MarkerType.ArrowClosed, color: "#94A3B8" },
        data: { label: e.label || "", condition: e.condition || "" },
        label: e.label || (e.condition || ""),
      }) as any);

      setNodes(rfNodes);
      setEdges(rfEdges);
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleCreate = async () => {
    try {
      const created = await createWorkflow();
      showToast("ok", t("wf.toast.created"));
      loadList();
      openDesigner(created.id);
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleSave = async () => {
    if (!selectedWfId || !rfInstance) return;
    setSaving(true);
    try {
      const cn = rfInstance.getNodes();
      const ce = rfInstance.getEdges();
      const saved = await updateWorkflow(selectedWfId, {
        name: wfDetail?.name || t("wf.designer.no_name"),
        nodes: cn.map((n: Node) => ({ id: n.id, type: n.type, code: n.id, name: n.data.label || n.id, data: n.data, position: n.position })),
        edges: ce.map((e: Edge) => ({ id: e.id, source: e.source, target: e.target, data: e.data || {} })),
      });
      setWfDetail(saved);
      showToast("ok", t("wf.toast.saved"));
    } catch (e: any) { showToast("err", e.message); }
    finally { setSaving(false); }
  };

  const handlePublish = async () => {
    if (!selectedWfId) return;
    try {
      const result = await publishWorkflow(selectedWfId);
      setWfDetail((prev: any) => ({ ...prev, status: result.status }));
      showToast("ok", t("wf.toast.published"));
    } catch (e: any) { showToast("err", e.message); }
  };

  const handleTest = async () => {
    if (!selectedWfId) return;
    setShowTestDialog(true);
    setTestResult(null);
    try {
      const result = await testWorkflow(selectedWfId, { entity_type: "Supplier", entity_id: "SUP-001" });
      setTestResult(result);
    } catch (e: any) { setTestResult({ error: e.message }); }
  };

  // Add node from palette
  const addNode = (type: string) => {
    setNodes(nds => [...nds, {
      id: `${type}_${Date.now()}`,
      type,
      position: { x: 100 + Math.random() * 300, y: 100 + Math.random() * 200 },
      data: { label: t(`wf.node_type.${type}`) },
    }]);
  };

  const onConnect = useCallback((params: Connection) => {
    setEdges(eds => addEdge({
      ...params,
      markerEnd: { type: MarkerType.ArrowClosed, color: "#94A3B8" },
      label: "",
    } as any, eds));
  }, [setEdges]);

  const updateNodeData = (nodeId: string, key: string, value: any) => {
    setNodes(nds => nds.map(n => n.id === nodeId ? { ...n, data: { ...n.data, [key]: value } } : n));
    setSelectedNode((prev: any) => prev ? { ...prev, data: { ...prev.data, [key]: value } } : null);
  };

  const deleteSelectedNode = () => {
    if (!selectedNode) return;
    setNodes(nds => nds.filter(n => n.id !== selectedNode.id));
    setEdges(eds => eds.filter(e => e.source !== selectedNode.id && e.target !== selectedNode.id));
    setSelectedNode(null);
  };

  // ── Render ──
  return (
    <div className="flex h-full bg-slate-50 font-sans text-slate-800 w-full">
      {/* Toast */}
      {toast && (
        <div className={`absolute top-4 right-4 z-50 px-4 py-2.5 rounded-lg text-xs font-semibold shadow-lg flex items-center gap-2 animate-in slide-in-from-top-2 ${
          toast.type === "ok" ? "bg-green-50 border border-green-200 text-green-700" : "bg-red-50 border border-red-200 text-red-700"
        }`}>
          {toast.type === "ok" ? <CheckCircle className="w-3.5 h-3.5" /> : <AlertCircle className="w-3.5 h-3.5" />}
          {toast.msg}
        </div>
      )}

      {view === "list" ? (
        /* ── Workflow List ── */
        <div className="flex-1 p-4 sm:p-6 overflow-y-auto">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
                <GitBranch className="w-5 h-5 text-indigo-500" /> {t("wf.title")}
              </h2>
              <p className="text-xs text-slate-400 mt-1">{t("wf.desc")}</p>
            </div>
            <button
              onClick={handleCreate}
              className="bg-indigo-500 hover:bg-indigo-600 text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 transition"
            >
              <Plus className="w-3.5 h-3.5" /> {t("wf.btn.create")}
            </button>
          </div>

          {loading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="w-6 h-6 text-slate-400 animate-spin" />
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {workflows.map(wf => (
                <div
                  key={wf.id}
                  onClick={() => openDesigner(wf.id)}
                  className="bg-white border border-slate-200 hover:border-indigo-300 rounded-xl p-4 cursor-pointer transition shadow-sm hover:shadow"
                >
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="text-sm font-bold text-slate-800">{wf.name}</h3>
                    <span className={`text-[10px] px-2 py-0.5 rounded-full font-semibold ${
                      wf.status === "PUBLISHED" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                    }`}>
                      {wf.status === "PUBLISHED" ? t("wf.status.published") : t("wf.status.draft")}
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-400 font-mono">{wf.code}</div>
                  <div className="text-[11px] text-slate-500 mt-1">{wf.workflowType} · {wf.description || "—"}</div>
                </div>
              ))}
              {workflows.length === 0 && (
                <div className="col-span-2 text-center py-16 text-xs text-slate-400">
                  <GitBranch className="w-8 h-8 mx-auto mb-2 text-slate-300" />
                  {t("wf.empty.desc")}
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        /* ── Designer View ── */
        <div className="flex flex-col lg:flex-row flex-1">
          {/* Left palette */}
          <div className="w-full lg:w-40 shrink-0 bg-white border-r border-slate-200 flex flex-col">
            <div className="p-3 border-b border-slate-200">
              <button onClick={() => setView("list")} className="text-xs text-slate-500 hover:text-slate-800 flex items-center gap-1 transition">
                <ArrowLeft className="w-3 h-3" /> {t("wf.btn.back")}
              </button>
            </div>
            <div className="p-2 space-y-1">
              <div className="text-[10px] text-slate-400 font-semibold uppercase px-2 py-1">{t("wf.palette.title")}</div>
              {Object.entries(NODE_TYPES).map(([type, def]) => {
                const Icon = def.icon;
                return (
                  <div
                    key={type}
                    draggable
                    onDragStart={(e) => e.dataTransfer.setData("nodeType", type)}
                    onClick={() => addNode(type)}
                    className={`flex items-center gap-2 px-2.5 py-2 rounded-lg border cursor-grab hover:shadow-sm transition active:cursor-grabbing ${def.color}`}
                  >
                    <div className="p-1 rounded bg-white/70"><Icon className="w-3.5 h-3.5 text-slate-600" /></div>
                    <span className="text-[11px] font-semibold text-slate-700">{t(`wf.node_type.${type}`)}</span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Center canvas */}
          <div className="flex-1 relative min-h-[300px] lg:min-h-0">
            <div className="absolute top-3 left-3 z-10 flex items-center gap-2">
              <span className="text-xs font-bold text-slate-700 bg-white/90 backdrop-blur px-3 py-1.5 rounded-lg border border-slate-200 shadow-sm">
                {wfDetail?.name || t("wf.designer.untitled")}
                <span className={`ml-2 text-[10px] px-1.5 py-0.5 rounded-full ${
                  wfDetail?.status === "PUBLISHED" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                }`}>
                  {wfDetail?.status === "PUBLISHED" ? t("wf.status.published") : t("wf.status.draft")}
                </span>
              </span>
            </div>
            <div className="absolute top-3 right-3 z-10 flex flex-wrap items-center gap-2">
              <button onClick={handleSave} disabled={saving} className="bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-lg px-3 py-1.5 text-xs font-semibold flex items-center gap-1.5 shadow-sm transition disabled:opacity-50">
                {saving ? <Loader2 className="w-3 h-3 animate-spin" /> : <Save className="w-3 h-3" />}{t("wf.btn.save")}
              </button>
              <button onClick={handlePublish} disabled={wfDetail?.status === "PUBLISHED"} className="bg-indigo-500 hover:bg-indigo-600 text-white rounded-lg px-3 py-1.5 text-xs font-semibold flex items-center gap-1.5 shadow-sm transition disabled:opacity-50">
                <Send className="w-3 h-3" /> {t("wf.btn.publish")}
              </button>
              <button onClick={handleTest} className="bg-emerald-500 hover:bg-emerald-600 text-white rounded-lg px-3 py-1.5 text-xs font-semibold flex items-center gap-1.5 shadow-sm transition">
                <Play className="w-3 h-3" /> {t("wf.btn.test")}
              </button>
            </div>
            <ReactFlow
              nodes={nodes} edges={edges}
              onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={(_, node) => setSelectedNode(node)}
              onPaneClick={() => setSelectedNode(null)}
              onInit={setRfInstance} nodeTypes={nodeTypes} fitView
              deleteKeyCode={["Backspace", "Delete"]}
              className="bg-slate-50"
            >
              <Controls className="!rounded-lg !shadow-sm !border !border-slate-200" />
              <Background gap={20} size={1} color="#E2E8F0" />
              <MiniMap className="!rounded-lg !shadow-sm !border !border-slate-200"
                nodeColor={(n) => MINIMAP_COLORS[n.type || ""] || MINIMAP_FALLBACK}
              />
            </ReactFlow>
          </div>

          {/* Right: Property Panel */}
          <div className="w-full lg:w-80 shrink-0 bg-white border-l border-slate-200 overflow-y-auto">
            <PropertyPanel selectedNode={selectedNode} onUpdateData={updateNodeData} onDelete={deleteSelectedNode} />
          </div>

          {/* Test Dialog */}
          {showTestDialog && (
            <TestDialog testResult={testResult} onClose={() => { setShowTestDialog(false); setTestResult(null); }} />
          )}
        </div>
      )}
    </div>
  );
}
