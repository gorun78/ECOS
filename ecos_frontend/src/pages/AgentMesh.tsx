/**
 * Agent Mesh — 多Agent协作平台
 * Agent面板 + Mission工作台 + 甘特图/时间线 + 实时轮询 + SUPERVISOR模式 + 任务统计
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef, useMemo } from "react";
import {
  Cpu, Play, Send, Plus, RefreshCw, Trash2, ArrowRight, Check, X,
  Clock, AlertTriangle, BarChart3, GanttChart, Layers, Timer, Hash,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import {
  fetchAgentMeshAgents, fetchAgentMeshMissions, fetchAgentMeshMission,
  fetchAgentMeshMissionTasks, createAgentMeshMission, executeAgentMeshMission,
  AgentMeshAgent, AgentMeshMission, AgentMeshTask,
} from "../api";
import GanttTimeline from "./AgentMesh/GanttTimeline";
import TaskStatsPanel from "./AgentMesh/TaskStatsPanel";
import { STATUS_BG_COLORS, STATUS_BAR_COLORS } from "./AgentMesh/helpers";

// ═══════════════════════════════════════════════════════════
//  AgentMesh Page
// ═══════════════════════════════════════════════════════════

export default function AgentMesh() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [agents, setAgents] = useState<AgentMeshAgent[]>([]);
  const [missions, setMissions] = useState<AgentMeshMission[]>([]);
  const [selectedMission, setSelectedMission] = useState<AgentMeshMission | null>(null);
  const [missionTasks, setMissionTasks] = useState<AgentMeshTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // Ref to track the current polling mission ID to avoid stale closures
  const pollingRef = useRef<string | null>(null);

  // New mission form
  const [newTitle, setNewTitle] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [newMode, setNewMode] = useState<"SUPERVISOR" | "PIPELINE">("PIPELINE");
  const [pipelineAgents, setPipelineAgents] = useState<{ agentId: string; instruction: string }[]>([
    { agentId: "", instruction: "" }
  ]);

  // ── 实时轮询: Mission RUNNING/PENDING 时每3秒拉取子任务 ──
  useEffect(() => {
    const missionId = selectedMission?.id ?? null;
    const shouldPoll = selectedMission && (
      selectedMission.status === "RUNNING" || selectedMission.status === "PENDING"
    );

    // Update tracking ref
    pollingRef.current = shouldPoll ? missionId : null;

    if (!shouldPoll || !missionId) return;

    const pollTasks = async () => {
      try {
        const data = await fetchAgentMeshMission(missionId);
        setSelectedMission(data.mission);
        setMissionTasks(data.tasks || []);
      } catch (e) {
        // Silently ignore polling errors
      }
    };

    // Initial poll immediately
    pollTasks();

    const interval = setInterval(pollTasks, 3000);
    return () => clearInterval(interval);
  }, [selectedMission?.id, selectedMission?.status]);

  // ── Initial load ──
  useEffect(() => {
    loadAgents();
    loadMissions();
  }, []);

  const loadAgents = async () => {
    try { setAgents(await fetchAgentMeshAgents()); } catch (e) {}
  };

  const loadMissions = async () => {
    try { setMissions(await fetchAgentMeshMissions()); } catch (e) {}
  };

  const loadMission = async (id: string) => {
    try {
      const data = await fetchAgentMeshMission(id);
      setSelectedMission(data.mission);
      setMissionTasks(data.tasks || await fetchAgentMeshMissionTasks(id));
    } catch (e) {
      // fallback: try tasks separately
      try {
        setMissionTasks(await fetchAgentMeshMissionTasks(id));
      } catch (_) {}
    }
  };

  const createMission = async () => {
    if (!newTitle.trim()) { setError("请输入任务标题"); return; }
    setLoading(true);
    setError("");
    try {
      const body: any = {
        title: newTitle,
        description: newDesc,
        mode: newMode,
        inputParams: {},
      };
      // PIPELINE模式传子任务配置；SUPERVISOR模式由Coordinator自动拆解
      if (newMode === "PIPELINE") {
        body.tasks = pipelineAgents.filter(a => a.agentId).map((a, i) => ({
          agentId: a.agentId,
          agentName: agents.find(ag => ag.id === a.agentId)?.name || a.agentId,
          instruction: a.instruction || `Step ${i + 1}`,
        }));
      }
      // SUPERVISOR模式: 不传tasks，后端由Coordinator自动分配
      const mission = await createAgentMeshMission(body);
      setMissions(prev => [mission, ...prev]);
      setNewTitle(""); setNewDesc("");
      setPipelineAgents([{ agentId: "", instruction: "" }]);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const executeMission = async (id: string) => {
    setLoading(true);
    try {
      const mission = await executeAgentMeshMission(id);
      setSelectedMission(mission);
      await loadMission(id);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const statusIcon = (status: string) => {
    switch (status) {
      case "COMPLETED": return <Check size={14} className="text-green-500" />;
      case "FAILED": return <X size={14} className="text-red-500" />;
      case "RUNNING": return <RefreshCw size={14} className="text-blue-500 animate-spin" />;
      case "PENDING": return <Clock size={14} className="text-yellow-500" />;
      default: return <AlertTriangle size={14} className="text-gray-400" />;
    }
  };

  const statusColor = (status: string) => {
    switch (status) {
      case "COMPLETED": return "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400";
      case "FAILED": return "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400";
      case "RUNNING": return "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400";
      default: return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400";
    }
  };

  // ── Render ──
  return (
    <div className="flex flex-col lg:flex-row h-full bg-slate-50 dark:bg-slate-950 w-full">
      {/* Left: Agent Panel */}
      <div className="w-full lg:w-72 border-r border-slate-200 dark:border-slate-800 flex flex-col">
        <div className="p-4 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-300">
            <Cpu size={16} /> Agent 注册表
          </div>
        </div>
        <div className="flex-1 overflow-auto p-3 space-y-2">
          {agents.map(agent => (
            <div key={agent.id}
              className={`p-3 rounded-lg border text-xs cursor-pointer transition-colors ${
                agent.status === "ACTIVE"
                  ? "border-indigo-200 bg-white dark:border-indigo-800 dark:bg-slate-900 hover:border-indigo-400"
                  : "border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50 opacity-60"
              }`}>
              <div className="flex items-center justify-between mb-1">
                <span className="font-medium text-slate-800 dark:text-slate-200">{agent.name}</span>
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                  agent.role === "compliance" ? "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400" :
                  agent.role === "data" ? "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400" :
                  agent.role === "knowledge" ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400" :
                  "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
                }`}>{agent.role}</span>
              </div>
              <p className="text-slate-500 dark:text-slate-400 mb-2 leading-relaxed">{agent.description}</p>
              <div className="flex items-center justify-between text-[10px] text-slate-400 dark:text-slate-500">
                <span>{agent.model}</span>
                <span>max {agent.maxIterations} steps</span>
              </div>
            </div>
          ))}
          {agents.length === 0 && (
            <p className="text-xs text-slate-400 text-center py-8">无已注册Agent</p>
          )}
        </div>
      </div>

      {/* Right: Mission Workbench */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Create Mission Bar */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
          <div className="flex flex-col sm:flex-row gap-3 items-start">
            <div className="flex-1 space-y-2">
              <input
                type="text"
                value={newTitle}
                onChange={e => setNewTitle(e.target.value)}
                placeholder="Mission 标题，如：供应商准入合规审查"
                className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none"
              />
              <textarea
                value={newDesc}
                onChange={e => setNewDesc(e.target.value)}
                placeholder="Mission 描述（可选）"
                rows={2}
                className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none resize-none"
              />
              {/* Mode selector */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-500">模式:</span>
                <button onClick={() => setNewMode("SUPERVISOR")}
                  className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${
                    newMode === "SUPERVISOR"
                      ? "bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-300"
                      : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
                  }`}>SUPERVISOR</button>
                <button onClick={() => setNewMode("PIPELINE")}
                  className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${
                    newMode === "PIPELINE"
                      ? "bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300"
                      : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
                  }`}>PIPELINE</button>
              </div>

              {/* SUPERVISOR mode hint */}
              {newMode === "SUPERVISOR" && (
                <div className="flex items-center gap-2 p-2 rounded bg-teal-50 dark:bg-teal-950/20 border border-teal-200 dark:border-teal-800">
                  <Layers size={14} className="text-teal-600" />
                  <span className="text-[11px] text-teal-700 dark:text-teal-400">
                    Supervisor 模式：Coordinator 自动拆解任务并分配给 Specialist Agent 执行
                  </span>
                </div>
              )}

              {/* Pipeline task config */}
              {newMode === "PIPELINE" && (
                <div className="space-y-2 pl-2 border-l-2 border-indigo-200 dark:border-indigo-800">
                  {pipelineAgents.map((pa, i) => (
                    <div key={i} className="flex flex-col sm:flex-row gap-2 items-start">
                      <span className="text-[10px] font-mono text-slate-400 pt-2 w-5">#{i+1}</span>
                      <select
                        value={pa.agentId}
                        onChange={e => {
                          const updated = [...pipelineAgents];
                          updated[i].agentId = e.target.value;
                          setPipelineAgents(updated);
                        }}
                        className="w-full sm:w-40 px-2 py-1.5 text-xs border border-slate-300 dark:border-slate-700 rounded bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                      >
                        <option value="">选择 Agent</option>
                        {agents.filter(a => a.status === "ACTIVE").map(a => (
                          <option key={a.id} value={a.id}>{a.name} ({a.role})</option>
                        ))}
                      </select>
                      <input
                        type="text"
                        value={pa.instruction}
                        onChange={e => {
                          const updated = [...pipelineAgents];
                          updated[i].instruction = e.target.value;
                          setPipelineAgents(updated);
                        }}
                        placeholder="子任务指令"
                        className="flex-1 px-2 py-1.5 text-xs border border-slate-300 dark:border-slate-700 rounded bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                      />
                      {i === pipelineAgents.length - 1 ? (
                        <button
                          onClick={() => setPipelineAgents([...pipelineAgents, { agentId: "", instruction: "" }])}
                          className="p-1.5 text-slate-400 hover:text-indigo-500"
                        ><Plus size={14} /></button>
                      ) : (
                        <button
                          onClick={() => setPipelineAgents(pipelineAgents.filter((_, j) => j !== i))}
                          className="p-1.5 text-slate-400 hover:text-red-500"
                        ><Trash2 size={14} /></button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <button
              onClick={createMission}
              disabled={loading}
              className="flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
            >
              <Send size={14} /> 创建
            </button>
          </div>
          {error && <p className="mt-2 text-xs text-red-500">{error}</p>}
        </div>

        {/* Mission List */}
        <div className="flex-1 overflow-auto p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300">Mission 记录</h3>
            <button onClick={loadMissions} className="p-1 text-slate-400 hover:text-indigo-500"><RefreshCw size={14} /></button>
          </div>

          {/* ── Task Stats Panel (shown when mission selected and has tasks) ── */}
          {selectedMission && (
            <TaskStatsPanel mission={selectedMission} tasks={missionTasks} />
          )}

          {missions.length === 0 && (
            <p className="text-sm text-slate-400 text-center py-16">暂无 Mission，创建一个开始协作</p>
          )}

          {missions.map(mission => (
            <div key={mission.id}
              onClick={() => loadMission(mission.id)}
              className={`mb-2 p-4 rounded-lg border cursor-pointer transition-all ${
                selectedMission?.id === mission.id
                  ? "border-indigo-300 bg-indigo-50 dark:border-indigo-700 dark:bg-indigo-950/30"
                  : "border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 hover:border-slate-300"
              }`}>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  {statusIcon(mission.status)}
                  <span className="font-medium text-sm text-slate-800 dark:text-slate-200">{mission.title}</span>
                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                    mission.mode === "PIPELINE"
                      ? "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400"
                      : "bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400"
                  }`}>{mission.mode}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${statusColor(mission.status)}`}>
                    {mission.status}
                  </span>
                  {mission.durationMs && <span className="text-[10px] text-slate-400">{mission.durationMs}ms</span>}
                  {(mission.status === "PENDING" || mission.status === "FAILED") && (
                    <button
                      onClick={e => { e.stopPropagation(); executeMission(mission.id); }}
                      disabled={loading}
                      className="p-1 text-indigo-500 hover:text-indigo-700 hover:bg-indigo-100 dark:hover:bg-indigo-900/30 rounded"
                    ><Play size={14} /></button>
                  )}
                </div>
              </div>
              {mission.description && (
                <p className="text-xs text-slate-500 dark:text-slate-400 mb-2">{mission.description}</p>
              )}
              {mission.errorMessage && (
                <p className="text-xs text-red-500 mt-1">{mission.errorMessage}</p>
              )}

              {/* Expanded: Gantt Timeline + Task list */}
              {selectedMission?.id === mission.id && missionTasks.length > 0 && (
                <>
                  {/* Gantt Timeline */}
                  <GanttTimeline tasks={missionTasks} mode={mission.mode} />

                  {/* Task detail list */}
                  <div className="mt-3 pt-3 border-t border-slate-200 dark:border-slate-800 space-y-2">
                    <h4 className="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">子任务详情</h4>
                    {missionTasks.map(task => (
                      <div key={task.id} className="flex items-start gap-2 p-2 rounded bg-slate-50 dark:bg-slate-950/50">
                        {statusIcon(task.status)}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                              {task.agentName || task.agentId}
                            </span>
                            <span className={`px-1.5 py-0.5 rounded text-[10px] ${statusColor(task.status)}`}>
                              {task.status}
                            </span>
                            {task.durationMs && <span className="text-[10px] text-slate-400">{task.durationMs}ms</span>}
                          </div>
                          <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5 truncate">{task.instruction}</p>
                          {task.resultSummary && (
                            <p className="text-[11px] text-slate-600 dark:text-slate-300 mt-1 line-clamp-2">{task.resultSummary}</p>
                          )}
                          {task.errorMessage && (
                            <p className="text-[11px] text-red-500 mt-0.5">{task.errorMessage}</p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
