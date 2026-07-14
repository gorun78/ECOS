/**
 * PipelineBuilder — DAG Pipeline Visual Orchestrator
 * Fetches real workflow definitions from backend, renders interactive DAG canvas.
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Play, RefreshCw, Activity, Settings, Zap, Code,
  Plus, GitBranch, Clock, ArrowRight, CheckCircle2,
  XCircle, Circle, AlertCircle, Layers, Info
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { useDict } from "../hooks/useDict";
import { fetchPipelines, createPipeline, executePipeline, getExecution } from "../api";
import { commit as gitCommit } from "../services/gitService";

interface WorkflowNode {
  id: string;
  name: string;
  type?: string;
  status?: string;
  description?: string;
}

interface WorkflowEdge {
  id: string;
  sourceId: string;
  targetId: string;
  label?: string;
}

interface PipelineData {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

export default function PipelineBuilder() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel: getStatusLabel } = useDict("pipeline_status", locale);
  const { getLabel: getTypeLabel } = useDict("task_type", locale);
  const [pipeline, setPipeline] = useState<PipelineData>({ nodes: [], edges: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedNode, setSelectedNode] = useState<WorkflowNode | null>(null);
  const [activeJobStatus, setActiveJobStatus] = useState<"Idle" | "Running" | "Success" | "Failed">("Idle");
  const [simulationLogs, setSimulationLogs] = useState<string[]>([]);
  const [dagSql, setDagSql] = useState("");

  // Create pipeline modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newPipeline, setNewPipeline] = useState({ name: "", description: "", source: "" });

  const handleCreatePipeline = async () => {
    if (!newPipeline.name.trim()) return;
    try {
      setError(null);
      const created = await createPipeline({
        name: newPipeline.name.trim(),
        description: newPipeline.description.trim() || undefined,
        nodes: [],
        edges: [],
      });
      const newNode: WorkflowNode = {
        id: created.id || `pipeline_${Date.now()}`,
        name: created.name,
        type: "pipeline",
        status: created.status || "idle",
        description: created.description || "",
      };
      setPipeline(prev => ({ nodes: [...prev.nodes, newNode], edges: prev.edges }));
      setShowCreateModal(false);
      setNewPipeline({ name: "", description: "", source: "" });
      gitCommit('data-workbench', {
        message: `pipeline: ${newPipeline.name.trim()} created`,
        authorName: 'system',
        authorEmail: 'system@ecos.local'
      }).catch(() => {});
    } catch (e: any) {
      setError(e.message || "Failed to create pipeline");
    }
  };

  const fetchPipeline = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const d = await fetchPipelines(50);
      if (d && Array.isArray(d.data)) {
        const nodes: WorkflowNode[] = d.data.map((w: any) => ({
          id: w.id || w.pipelineId || "",
          name: w.name || "Unnamed",
          type: w.type || "pipeline",
          status: w.status || "idle",
          description: w.description || "",
        }));
        setPipeline({ nodes, edges: [] });
      } else {
        setPipeline({ nodes: [], edges: [] });
      }
    } catch (e: any) {
      setError(e.message || "Failed to load pipeline definitions");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPipeline();
  }, [fetchPipeline]);

  const handleRunPipeline = async () => {
    if (pipeline.nodes.length === 0) return;
    // Execute each pipeline definition
    setActiveJobStatus("Running");
    setSimulationLogs([]);
    const logs: string[] = [];
    logs.push(`[${new Date().toLocaleTimeString()}] Starting execution of ${pipeline.nodes.length} pipeline(s)...`);
    setSimulationLogs([...logs]);

    for (let i = 0; i < pipeline.nodes.length; i++) {
      const node = pipeline.nodes[i];
      try {
        logs.push(`[${new Date().toLocaleTimeString()}] Stage ${i + 1}: Executing <${node.name}>...`);
        setSimulationLogs([...logs]);
        const exec = await executePipeline(node.id);
        logs.push(`[${new Date().toLocaleTimeString()}] Stage ${i + 1}: <${node.name}> — execution ${exec.id} status: ${exec.status}`);
        setSimulationLogs([...logs]);
      } catch (e: any) {
        logs.push(`[${new Date().toLocaleTimeString()}] Stage ${i + 1}: <${node.name}> — FAILED: ${e.message}`);
        setSimulationLogs([...logs]);
        setActiveJobStatus("Failed");
        return;
      }
    }
    logs.push(`[${new Date().toLocaleTimeString()}] SUCCESS: All ${pipeline.nodes.length} pipelines executed.`);
    setSimulationLogs([...logs]);
    setActiveJobStatus("Success");
    gitCommit('data-workbench', {
      message: `pipeline: ${pipeline.nodes.length} pipeline(s) executed`,
      authorName: 'system',
      authorEmail: 'system@ecos.local'
    }).catch(() => {});
  };

  const getStatusIcon = (status?: string) => {
    switch (status) {
      case "running": return <RefreshCw className="w-3 h-3 animate-spin text-blue-500" />;
      case "completed":
      case "success": return <CheckCircle2 className="w-3 h-3 text-emerald-500" />;
      case "failed": return <XCircle className="w-3 h-3 text-red-500" />;
      default: return <Circle className="w-3 h-3 text-slate-300" />;
    }
  };

  return (
    <div className={`flex-1 ${styles.appBg} ${styles.appText} flex flex-col h-full font-sans overflow-hidden animate-fade-in`}>

      {/* Error Banner */}
      {error && (
        <div className="bg-red-50 border-b border-red-200 p-3 flex items-center gap-2 text-red-700 text-sm shrink-0">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button onClick={() => { setError(null); fetchPipeline(); }}
            className="ml-auto px-2 py-1 text-xs border border-red-300 rounded hover:bg-red-100">
            {locale === "zh" ? "重试" : "Retry"}
          </button>
        </div>
      )}

      {/* Header */}
      <div className={`${styles.cardBg} border-b ${styles.cardBorder} p-5 shrink-0 flex items-center justify-between gap-4`}>
        <div>
          <h1 className={`text-xl font-bold ${styles.cardText} flex items-center gap-2 font-sans`}>
            <GitBranch className="text-indigo-600 w-5 h-5 shrink-0" />
            {t("pipeline.title")}
          </h1>
          <p className={`text-xs ${styles.muted} mt-1 max-w-2xl leading-relaxed`}>
            {pipeline.nodes.length > 0
              ? `${pipeline.nodes.length} ${locale === "zh" ? "个DAG节点" : "DAG nodes"} · ${pipeline.edges.length || 0} ${locale === "zh" ? "条边" : "edges"}`
              : t("pipeline.desc")}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {activeJobStatus === "Running" ? (
            <div className="flex items-center gap-2 text-xs text-blue-700 bg-blue-50 border border-blue-200 px-3.5 py-1.5 rounded-lg font-mono font-semibold">
              <RefreshCw className="w-3.5 h-3.5 animate-spin text-blue-600" />
              <span>{locale === "zh" ? "执行中..." : "Running..."}</span>
            </div>
          ) : activeJobStatus === "Success" ? (
            <div className="flex items-center gap-2 text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 px-3.5 py-1.5 rounded-lg font-mono font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
              <span>{locale === "zh" ? "完成" : "Success"}</span>
            </div>
          ) : (
            <>
              <button onClick={fetchPipeline}
                className="text-xs text-slate-500 hover:text-slate-700 p-2 rounded-lg hover:bg-slate-100"
                title={locale === "zh" ? "刷新" : "Refresh"}>
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
              <button onClick={handleRunPipeline}
                disabled={pipeline.nodes.length === 0}
                className={`${styles.accentBg} ${styles.accentHover} disabled:bg-slate-300 text-white font-semibold rounded-lg px-4 py-2 text-xs flex items-center gap-2 cursor-pointer transition shadow-xs shrink-0 font-sans`}>
                <Play className="w-3.5 h-3.5 text-white" />
                {t("pipeline.btn.compile")}
              </button>
            </>
          )}
        </div>
      </div>

      {/* Create Pipeline Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className={`${styles.cardBg} rounded-xl shadow-2xl w-full max-w-md mx-4 p-6 border ${styles.cardBorder}`}>
            <div className="flex items-center justify-between mb-4">
              <h2 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
                <GitBranch className="w-5 h-5 text-indigo-600" />
                {locale === "zh" ? "创建新流水线" : "Create New Pipeline"}
              </h2>
              <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-slate-100 rounded">
                <span className="text-slate-400 text-lg">&times;</span>
              </button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-semibold text-slate-600 block mb-1">
                  {locale === "zh" ? "流水线名称" : "Pipeline Name"}
                </label>
                <input type="text" value={newPipeline.name}
                  onChange={e => setNewPipeline(prev => ({...prev, name: e.target.value}))}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400"
                  placeholder={locale === "zh" ? "如: Customer360 ETL Pipeline" : "e.g. Customer360 ETL Pipeline"} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-600 block mb-1">
                  {locale === "zh" ? "描述" : "Description"}
                </label>
                <textarea value={newPipeline.description} rows={2}
                  onChange={e => setNewPipeline(prev => ({...prev, description: e.target.value}))}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400 resize-none"
                  placeholder={locale === "zh" ? "描述这条流水线的用途..." : "Describe what this pipeline does..."} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-600 block mb-1">
                  {locale === "zh" ? "数据源类型" : "Source Type"}
                </label>
                <select value={newPipeline.source}
                  onChange={e => setNewPipeline(prev => ({...prev, source: e.target.value}))}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400 bg-white">
                  <option value="">{locale === "zh" ? "选择数据源..." : "Select source..."}</option>
                  <option value="spark">Spark ETL</option>
                  <option value="flink">Flink Streaming</option>
                  <option value="batch">Batch SQL</option>
                  <option value="python">Python Script</option>
                  <option value="custom">{locale === "zh" ? "自定义" : "Custom"}</option>
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-5">
              <button onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg transition">
                {locale === "zh" ? "取消" : "Cancel"}
              </button>
              <button onClick={handleCreatePipeline} disabled={!newPipeline.name.trim()}
                className="px-4 py-2 text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg transition">
                {locale === "zh" ? "创建流水线" : "Create Pipeline"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Canvas */}
      <div className="flex-1 overflow-hidden grid grid-cols-1 lg:grid-cols-3 gap-5 p-6 min-h-0">

        {/* DAG Visual Canvas */}
        <div className={`lg:col-span-2 border ${styles.cardBorder} rounded-xl ${styles.cardBg} p-5 flex flex-col overflow-hidden shadow-xs`}>
          <div className="flex items-center justify-between mb-4 shrink-0 text-[10px] font-mono text-slate-450 font-bold uppercase tracking-widest leading-none">
            <span>{locale === "zh" ? "DAG 可视化编排" : "DAG Visual Orchestration"}</span>
            <div className="flex items-center gap-1.5 text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-1 rounded-md font-sans text-[10px]">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              <span>{locale === "zh" ? "工作区" : "Workspace"}</span>
            </div>
          </div>

          {loading ? (
            <div className="flex-1 flex items-center justify-center">
              <RefreshCw className="w-8 h-8 text-slate-300 animate-spin" />
            </div>
          ) : pipeline.nodes.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center text-center py-16">
              <GitBranch className="w-12 h-12 text-slate-300 mb-4" />
              <h3 className={`text-base font-bold ${styles.cardText} mb-2 font-sans`}>
                {locale === "zh" ? "暂无流水线定义" : "No Pipeline Definitions"}
              </h3>
              <p className={`text-xs ${styles.muted} max-w-md leading-relaxed mb-6`}>
                {locale === "zh"
                  ? "尚未定义任何DAG流水线。通过代码工作台创建Spark作业，或连接外部数据源以构建您的第一条数据流水线。"
                  : "No DAG pipelines defined yet. Create Spark jobs via Code Workbook or connect external data sources to build your first data pipeline."}
              </p>
              <button onClick={() => setShowCreateModal(true)}
                className="bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 shadow-sm">
                <Plus className="w-3.5 h-3.5" />
                {locale === "zh" ? "创建第一条流水线" : "Create First Pipeline"}
              </button>
            </div>
          ) : (
            <div className="flex-1 overflow-auto space-y-2">
              {pipeline.nodes.map((node, idx) => (
                <div key={node.id}
                  onClick={() => setSelectedNode(node)}
                  className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-all
                    ${selectedNode?.id === node.id
                      ? "border-indigo-300 bg-indigo-50 shadow-sm"
                      : "border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white"}`}>
                  <div className="w-8 h-8 rounded bg-indigo-100 flex items-center justify-center text-indigo-600 shrink-0">
                    {getStatusIcon(node.status)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className={`text-sm font-semibold ${styles.cardText} truncate`}>{node.name}</div>
                    {node.description && (
                      <div className={`text-[11px] ${styles.muted} truncate`}>{node.description}</div>
                    )}
                  </div>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-200 text-slate-500 font-mono">
                    {getTypeLabel(node.type || "stage") !== (node.type || "stage") ? getTypeLabel(node.type || "stage") : node.type || "stage"}
                  </span>
                  {idx < pipeline.nodes.length - 1 && (
                    <ArrowRight className="w-4 h-4 text-slate-300 shrink-0" />
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Spark Console */}
          <div className="h-32 bg-[#0F172A] border border-[#1E293B] rounded-xl p-4 font-mono text-[10px] text-slate-300 overflow-y-auto shrink-0 shadow-inner mt-4">
            <span className="text-[#3B82F6] font-bold block mb-1 uppercase tracking-wider text-[8.5px]">Spark compiler log:</span>
            {simulationLogs.length === 0 ? (
              <p className="text-slate-500 italic">No active job runs. Click Compile & Execute to trigger Spark drivers.</p>
            ) : (
              <div className="space-y-1">
                {simulationLogs.map((log, idx) => (
                  <p key={idx} className={log.includes("SUCCESS") || log.includes("100% Passed") ? "text-green-400" : "text-slate-300"}>
                    {log}
                  </p>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Configuration Sidebar */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 flex flex-col overflow-y-auto min-w-[240px] shadow-xs`}>
          {selectedNode ? (
            <div className="space-y-4">
              <div className={`flex items-center gap-2 text-xs font-bold ${styles.cardText}`}>
                <Settings className="w-3.5 h-3.5 text-indigo-500" />
                <span>{locale === "zh" ? "节点详情" : "Node Details"}</span>
              </div>
              <div className="space-y-3 text-xs">
                <div>
                  <label className="text-[10px] text-slate-400 uppercase tracking-wider">{locale === "zh" ? "名称" : "Name"}</label>
                  <p className={`font-semibold ${styles.cardText} mt-0.5`}>{selectedNode.name}</p>
                </div>
                <div>
                  <label className="text-[10px] text-slate-400 uppercase tracking-wider">ID</label>
                  <p className={`font-mono ${styles.muted} mt-0.5 text-[11px]`}>{selectedNode.id}</p>
                </div>
                <div>
                  <label className="text-[10px] text-slate-400 uppercase tracking-wider">{locale === "zh" ? "类型" : "Type"}</label>
                  <p className={`${styles.cardText} mt-0.5`}>
                    {getTypeLabel(selectedNode.type || "") !== (selectedNode.type || "")
                      ? getTypeLabel(selectedNode.type || "")
                      : selectedNode.type || "—"}
                  </p>
                </div>
                <div>
                  <label className="text-[10px] text-slate-400 uppercase tracking-wider">{locale === "zh" ? "状态" : "Status"}</label>
                  <div className="flex items-center gap-1.5 mt-0.5">{getStatusIcon(selectedNode.status)}
                    <span className={`${styles.cardText}`}>
                      {getStatusLabel(selectedNode.status || "idle") !== (selectedNode.status || "idle")
                        ? getStatusLabel(selectedNode.status || "idle")
                        : selectedNode.status || "idle"}
                    </span>
                  </div>
                </div>
              </div>
              <button onClick={() => setSelectedNode(null)}
                className="text-[10px] text-slate-400 hover:text-slate-600 mt-2">
                {locale === "zh" ? "取消选择" : "Deselect"}
              </button>
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-center py-16">
              <Info className="w-10 h-10 text-slate-300 mb-3" />
              <p className={`text-xs ${styles.muted} max-w-xs leading-relaxed font-sans`}>
                {pipeline.nodes.length > 0
                  ? (locale === "zh" ? "选择一个DAG节点查看和编辑属性。" : "Select a DAG node to inspect and edit properties.")
                  : (locale === "zh" ? "创建第一条流水线后，节点将在此显示并可配置。" : "Nodes will appear here once your first pipeline is created.")}
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
