/**
 * Agent Mesh — 多Agent协作平台
 * Agent面板 + Mission工作台 + 甘特图/时间线 + 实时轮询 + SUPERVISOR模式 + 任务统计
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
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
  // §4.5 P1（行237/248）：mission 详情加载失败 = 错误态（message + traceId + 重试），不再吞错续显陈旧列表
  const [missionError, setMissionError] = useState<{ id: string; msg: string; traceId?: string } | null>(null);

  /** 从网关 ApiResponse 错误原文提取 traceId（后端实报才展示，不合成） */
  const extractTraceId = (raw: unknown): string | undefined => {
    const text = raw instanceof Error ? raw.message : String(raw ?? "");
    const m = text.match(/"traceId"\s*:\s*"([^"]+)"/);
    return m?.[1];
  };

  // PMO-43 T3 C-001 (P1): per-mission polling error state. A failed 3s poll
  // (3s interval, fetchAgentMeshMission) surfaced an inline banner + retry
  // instead of silently freezing the task list ("Silently ignore polling
  // errors" was the empty-catch anti-pattern). Retry = re-run pollOnce now.
  const [pollError, setPollError] = useState("");

  // PMO-43 T3: loadErrors tracks per-key fetch failures (agents / missions)
  // so the UI can render "error + retry" state instead of a silent empty list.
  // Mirrors the same pattern already used in aiworkbench/index.tsx.
  type LoadKey = 'agents' | 'missions';
  const [loadErrors, setLoadErrors] = useState<Partial<Record<LoadKey, string>>>({});

  // New mission form
  const [newTitle, setNewTitle] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [newMode, setNewMode] = useState<"SUPERVISOR" | "PIPELINE">("PIPELINE");
  const [pipelineAgents, setPipelineAgents] = useState<{ agentId: string; instruction: string }[]>([
    { agentId: "", instruction: "" }
  ]);

  // ── 实时轮询: Mission RUNNING/PENDING 时每3秒拉取子任务 ──
  // PMO-43 T3 C-001 (P1): a failed poll no longer dies silently. The error
  // is written to per-mission state (pollError) and rendered as an inline
  // banner with a 重试/retry button that re-runs this poll immediately.
  const pollOnce = useCallback(async () => {
    const sel = selectedMission;
    if (!sel) return;
    const mid = sel.id;
    try {
      const data = await fetchAgentMeshMission(mid);
      // Guard against a stale response landing after the user switched mission
      if (selectedMission?.id === mid) {
        setSelectedMission(data.mission);
        setMissionTasks(data.tasks || []);
      }
      setPollError("");
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setPollError(msg);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedMission?.id, selectedMission?.status]);

  // C-001: stable identity — interval callback + manual "重试/Retry" button share it.
  const pollTasks = useCallback(() => pollOnce(), [pollOnce]);

  useEffect(() => {
    const sel = selectedMission;
    const shouldPoll = sel && (
      sel.status === "RUNNING" || sel.status === "PENDING"
    );

    // Clear stale banner when the polled mission (or its poll-able status) switches
    if (!shouldPoll) setPollError("");
    if (!shouldPoll || !sel) return;

    // Initial poll immediately
    pollOnce();

    const interval = setInterval(pollOnce, 3000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedMission?.id, selectedMission?.status, pollOnce]);

  // ── Initial load ──
  useEffect(() => {
    loadAgents();
    loadMissions();
  }, []);

  const loadAgents = async () => {
    // Reset the agents error before retrying so a successful reload clears it.
    setLoadErrors((prev) => ({ ...prev, agents: undefined }));
    try {
      setAgents(await fetchAgentMeshAgents());
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setLoadErrors((prev) => ({ ...prev, agents: msg }));
    }
  };

  const loadMissions = async () => {
    setLoadErrors((prev) => ({ ...prev, missions: undefined }));
    try {
      setMissions(await fetchAgentMeshMissions());
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setLoadErrors((prev) => ({ ...prev, missions: msg }));
    }
  };

  const loadMission = async (id: string) => {
    // §4.5 P1（行237）：原「外层失败→内层兜底再 try/catch 全吞」双层吞错已删除。
    // 进入即清空旧 mission 详情（失败不再续显上一 mission 的陈旧列表），
    // 失败 → 常驻错误态（message + traceId + 重试），成功才回填 mission/tasks。
    setSelectedMission(null);
    setMissionTasks([]);
    setMissionError(null);
    try {
      const data = await fetchAgentMeshMission(id);
      setSelectedMission(data.mission);
      setMissionTasks(data.tasks || await fetchAgentMeshMissionTasks(id));
    } catch (e: unknown) {
      const msg = e instanceof Error ? (e.message || String(e)) : String(e);
      setMissionError({ id, msg, traceId: extractTraceId(e) });
    }
  };

  const createMission = async () => {
    if (!newTitle.trim()) { setError(t("agentMesh.errTitleReq")); return; }
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

  // §4.5 P1（行244/247）：原始枚举直排 + 裸色板 → i18n 徽章 + 主题 token（复用 common 既有 status.* 键）
  const statusLabel = (status: string): string => {
    switch (status) {
      case "COMPLETED": return t("taskPanel.status.succeeded");
      case "FAILED": return t("taskPanel.status.failed");
      case "RUNNING": return t("taskPanel.status.running");
      case "PENDING": return t("taskPanel.status.pending");
      default: return status || t("taskPanel.status.unknown");
    }
  };

  const statusIcon = (status: string) => {
    switch (status) {
      case "COMPLETED": return <Check size={14} className={styles.successText} />;
      case "FAILED": return <X size={14} className={styles.dangerText} />;
      case "RUNNING": return <RefreshCw size={14} className={`${styles.infoText} animate-spin`} />;
      case "PENDING": return <Clock size={14} className={styles.warningText} />;
      default: return <AlertTriangle size={14} className={styles.cardTextMuted} />;
    }
  };

  const statusColor = (status: string) => {
    switch (status) {
      case "COMPLETED": return `${styles.successBg} ${styles.successText}`;
      case "FAILED": return `${styles.dangerBg} ${styles.dangerText}`;
      case "RUNNING": return `${styles.infoBg} ${styles.infoText}`;
      default: return `${styles.warningBg} ${styles.warningText}`;
    }
  };

  // ── Render ──
  return (
    <div className={`flex flex-col lg:flex-row h-full ${styles.appBg} w-full`}>
      {/* Left: Agent Panel */}
      <div className="w-full lg:w-72 border-r flex flex-col" style={{ borderColor: 'var(--app-border, #E2E8F0)' }}>
        <div className="p-4 border-b" style={{ borderColor: 'var(--app-border, #E2E8F0)' }}>
          <div className={`flex items-center gap-2 text-sm font-semibold ${styles.cardText}`}>
            <Cpu size={16} /> {t("agentMesh.registry")}
            {loadErrors.agents && (
              <button type="button"
                onClick={loadAgents}
                className="ml-auto inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-red-50 text-red-600 hover:bg-red-100 dark:bg-red-950/30 dark:text-red-400"
                title={loadErrors.agents}
              >
                <RefreshCw size={11} /> {t("network.retry")}
              </button>
            )}
          </div>
        </div>
        <div className="flex-1 overflow-auto p-3 space-y-2">
          {agents.map(agent => (
            <div key={agent.id}
              className={`p-3 rounded-lg border text-xs ${
                agent.status === "ACTIVE"
                  ? `${styles.accentBorder} ${styles.cardBg}`
                  : `${styles.cardBorder} ${styles.cardBg} opacity-60`
              }`}>
              <div className="flex items-center justify-between mb-1">
                <span className={`font-medium ${styles.cardText}`}>{agent.name}</span>
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                  agent.role === "compliance" ? "bg-purple-100 text-purple-700" :
                  agent.role === "data" ? "bg-blue-100 text-blue-700" :
                  agent.role === "knowledge" ? "bg-green-100 text-green-700" : `${styles.badgeBg} ${styles.cardTextMuted}`
                }`}>{agent.role}</span>
              </div>
              <p className={`text-xs ${styles.cardTextMuted} mb-2 leading-relaxed`}>{agent.description}</p>
              <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted}`}>
                <span>{agent.model}</span>
                <span>max {agent.maxIterations} steps</span>
              </div>
            </div>
          ))}
          {agents.length === 0 && loadErrors.agents ? (
            <div className="text-center py-8">
              <p className="text-xs text-red-500 mb-2">{loadErrors.agents}</p>
              <button type="button"
                onClick={loadAgents}
                className="inline-flex items-center gap-1 px-3 py-1 rounded text-xs bg-red-50 text-red-600 hover:bg-red-100 dark:bg-red-950/30 dark:text-red-400"
              >
                <RefreshCw size={12} /> {t("network.retry")}
              </button>
            </div>
          ) : agents.length === 0 ? (
            <p className={`text-xs ${styles.cardTextMuted} text-center py-8`}>{t("agentMesh.emptyAgent")}</p>
          ) : null}
        </div>
      </div>

      {/* Right: Mission Workbench */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Create Mission Bar */}
        <div className={`p-4 border-b ${styles.cardBg}`} style={{ borderColor: 'var(--app-border, #E2E8F0)' }}>
          <div className="flex flex-col sm:flex-row gap-3 items-start">
            <div className="flex-1 space-y-2">
              <input
                type="text"
                value={newTitle}
                onChange={e => setNewTitle(e.target.value)}
                placeholder={t("agentMesh.placeholder.title")}
                className={`w-full px-3 py-2 text-sm border rounded-lg ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none`}
              />
              <textarea
                value={newDesc}
                onChange={e => setNewDesc(e.target.value)}
                placeholder={t("agentMesh.placeholder.desc")}
                rows={2}
                className={`w-full px-3 py-2 text-sm border rounded-lg ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none resize-none`}
              />
              {/* Mode selector */}
              <div className="flex items-center gap-2">
                <span className={`text-xs ${styles.cardTextMuted}`}>{t("agentMesh.mode")}</span>
                <button type="button" onClick={() => setNewMode("SUPERVISOR")}
                  className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${
                    newMode === "SUPERVISOR"
                      ? "bg-teal-100 text-teal-700"
                      : `${styles.badgeBg} ${styles.cardTextMuted}`
                  }`}>{t("agentMesh.modeSupervisor")}</button>
                <button type="button" onClick={() => setNewMode("PIPELINE")}
                  className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${
                    newMode === "PIPELINE"
                      ? "bg-indigo-100 text-indigo-700"
                      : `${styles.badgeBg} ${styles.cardTextMuted}`
                  }`}>{t("agentMesh.modePipeline")}</button>
              </div>

              {/* SUPERVISOR mode hint */}
              {newMode === "SUPERVISOR" && (
                <div className="flex items-center gap-2 p-2 rounded bg-teal-50 dark:bg-teal-950/20 border border-teal-200 dark:border-teal-800">
                  <Layers size={14} className="text-teal-600" />
                  <span className="text-[11px] text-teal-700 dark:text-teal-400">
                    {t("agentMesh.supervisorHint")}
                  </span>
                </div>
              )}

              {/* Pipeline task config */}
              {newMode === "PIPELINE" && (
                <div className="space-y-2 pl-2 border-l-2 border-indigo-200 dark:border-indigo-800">
                  {pipelineAgents.map((pa, i) => (
                    <div key={i} className="flex flex-col sm:flex-row gap-2 items-start">
                      <span className={`text-[10px] font-mono ${styles.cardTextMuted} pt-2 w-5`}>#{i+1}</span>
                      <select
                        value={pa.agentId}
                        onChange={e => {
                          const updated = [...pipelineAgents];
                          updated[i].agentId = e.target.value;
                          setPipelineAgents(updated);
                        }}
                        className={`w-full sm:w-40 px-2 py-1.5 text-xs border rounded ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                      >
                        <option value="">{t("agentMesh.selectAgent")}</option>
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
                        placeholder={t("agentMesh.subtaskInstr")}
                        className={`flex-1 px-2 py-1.5 text-xs border rounded ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                      />
                      {i === pipelineAgents.length - 1 ? (
                        <button type="button"
                          onClick={() => setPipelineAgents([...pipelineAgents, { agentId: "", instruction: "" }])}
                          className={`p-1.5 ${styles.cardTextMuted} hover:text-indigo-500`}
                        ><Plus size={14} /></button>
                      ) : (
                        <button type="button"
                          onClick={() => setPipelineAgents(pipelineAgents.filter((_, j) => j !== i))}
                          className={`p-1.5 ${styles.cardTextMuted} hover:text-red-500`}
                        ><Trash2 size={14} /></button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <button type="button"
              onClick={createMission}
              disabled={loading}
              className="flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
            >
              <Send size={14} /> {t("agentMesh.create")}
            </button>
          </div>
          {error && <p className="mt-2 text-xs text-red-500">{error}</p>}
        </div>

        {/* Mission List */}
        <div className="flex-1 overflow-auto p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("agentMesh.records")}</h3>
            <button type="button" onClick={loadMissions} className={`p-1 ${styles.cardTextMuted} hover:opacity-80 transition-opacity`}><RefreshCw size={14} /></button>
          </div>

          {/* §4.5 P1（行237/248）：mission 详情加载失败 → 错误态横幅 + traceId + 重试（与真空可区分） */}
          {missionError && (
            <div role="alert" className={`mb-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 rounded-lg border ${styles.dangerBorder} ${styles.dangerBg} px-3 py-2 text-xs ${styles.dangerText}`}>
              <span className="min-w-0 font-semibold break-all">
                {t("aiworkbench.agentMesh.missionLoadFailed")} · {missionError.msg}
                {missionError.traceId && <span className="ml-1 font-mono opacity-70">traceId: {missionError.traceId}</span>}
              </span>
              <button type="button"
                onClick={() => loadMission(missionError.id)}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded border text-[11px] font-medium cursor-pointer shrink-0"
              >
                <RefreshCw size={11} /> {t("network.retry")}
              </button>
            </div>
          )}

          {/* ── Task Stats Panel (shown when mission selected and has tasks) ── */}
          {selectedMission && (
            <TaskStatsPanel mission={selectedMission} tasks={missionTasks} />
          )}

          {missions.length === 0 && loadErrors.missions ? (
            <div className="text-center py-16">
              <p className="text-sm text-red-500 mb-3">{loadErrors.missions}</p>
              <button type="button"
                onClick={loadMissions}
                className="inline-flex items-center gap-1 px-4 py-1.5 rounded text-xs bg-red-50 text-red-600 hover:bg-red-100 dark:bg-red-950/30 dark:text-red-400"
              >
                <RefreshCw size={12} /> {t("network.retry")}
              </button>
            </div>
          ) : missions.length === 0 ? (
            <p className={`text-sm ${styles.cardTextMuted} text-center py-16`}>{t("agentMesh.empty")}</p>
          ) : null}

          {missions.map(mission => (
            <div key={mission.id}
              onClick={() => loadMission(mission.id)}
              className={`mb-2 p-4 rounded-lg border cursor-pointer transition-all ${
                selectedMission?.id === mission.id
                  ? "border-indigo-300 bg-indigo-50"
                  : `${styles.cardBorder} ${styles.cardBg}`
              }`}>
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  {statusIcon(mission.status)}
                  <span className={`font-medium text-sm ${styles.cardText}`}>{mission.title}</span>
                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                    mission.mode === "PIPELINE"
                      ? "bg-purple-100 text-purple-700"
                      : "bg-teal-100 text-teal-700"
                  }`}>{mission.mode === "PIPELINE" ? t("agentMesh.modePipeline") : t("agentMesh.modeSupervisor")}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${statusColor(mission.status)}`}>
                    {statusLabel(mission.status)}
                  </span>
                  {mission.durationMs && <span className={`text-[10px] ${styles.cardTextMuted}`}>{mission.durationMs}ms</span>}
                  {(mission.status === "PENDING" || mission.status === "FAILED") && (
                    <button type="button"
                      onClick={e => { e.stopPropagation(); executeMission(mission.id); }}
                      disabled={loading}
                      className="p-1 text-indigo-500 hover:text-indigo-700 hover:bg-indigo-100 rounded"
                    ><Play size={14} /></button>
                  )}
                </div>
              </div>
              {mission.description && (
                <p className={`text-xs ${styles.cardTextMuted} mb-2`}>{mission.description}</p>
              )}
              {mission.errorMessage && (
                <p className="text-xs text-red-500 mt-1">{mission.errorMessage}</p>
              )}

              {/* C-001 (P1): inline poll-failure banner with immediate retry.
                  Shown while the mission is polling (RUNNING/PENDING) and a
                  fetchAgentMeshMission round-trip failed. */}
              {selectedMission?.id === mission.id && pollError && (
                <div className="mt-2 flex items-center gap-2 p-2 rounded bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-900">
                  <AlertTriangle size={13} className="text-red-500 shrink-0" />
                  <span className="text-[11px] text-red-600 dark:text-red-400 flex-1 min-w-0 truncate">
                    {t("agentMesh.pollError")} · {pollError}
                    {extractTraceId(pollError) && (
                      <span className="ml-1 font-mono opacity-70">traceId: {extractTraceId(pollError)}</span>
                    )}
                  </span>
                  <button type="button"
                    onClick={(e) => { e.stopPropagation(); pollTasks(); }}
                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/40"
                  >
                    <RefreshCw size={11} /> {t("network.retry")}
                  </button>
                </div>
              )}

              {/* Expanded: Gantt Timeline + Task list */}
              {selectedMission?.id === mission.id && missionTasks.length > 0 && (
                <>
                  {/* Gantt Timeline */}
                  <GanttTimeline tasks={missionTasks} mode={mission.mode} />

                  {/* Task detail list */}
                  <div className="mt-3 pt-3 border-t space-y-2" style={{ borderColor: 'var(--app-border, #E2E8F0)' }}>
                    <h4 className={`text-[11px] font-semibold ${styles.cardTextMuted} uppercase tracking-wide`}>{t("agentMesh.subtasks")}</h4>
                    {missionTasks.map(task => (
                      <div key={task.id} className="flex items-start gap-2 p-2 rounded" style={{ background: 'var(--overlay, rgba(0,0,0,0.04))' }}>
                        {statusIcon(task.status)}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className={`text-xs font-medium ${styles.cardText}`}>
                              {task.agentName || task.agentId}
                            </span>
                            <span className={`px-1.5 py-0.5 rounded text-[10px] ${statusColor(task.status)}`}>
                              {statusLabel(task.status)}
                            </span>
                            {task.durationMs && <span className={`text-[10px] ${styles.cardTextMuted}`}>{task.durationMs}ms</span>}
                          </div>
                          <p className={`text-[11px] ${styles.cardTextMuted} mt-0.5 truncate`}>{task.instruction}</p>
                          {task.resultSummary && (
                            <p className={`text-[11px] ${styles.cardText} mt-1 line-clamp-2`}>{task.resultSummary}</p>
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
