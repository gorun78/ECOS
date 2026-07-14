/**
 * GanttTimeline — Pure CSS Gantt chart for Agent Mesh missions.
 * @license Apache-2.0
 */

import React, { useMemo } from "react";
import { ArrowRight, GanttChart } from "lucide-react";
import { AgentMeshTask } from "../../api";
import { STATUS_BAR_COLORS } from "./helpers";

interface GanttTimelineProps {
  tasks: AgentMeshTask[];
  mode: string;
}

export default function GanttTimeline({ tasks, mode }: GanttTimelineProps) {
  if (tasks.length === 0) return null;

  const sorted = [...tasks].sort((a, b) => a.seq - b.seq);
  const maxDuration = Math.max(...sorted.map(t => t.durationMs || 0), 1);

  const phases = useMemo(() => {
    const result: { label: string; tasks: AgentMeshTask[]; icon: string }[] = [];
    if (mode === "SUPERVISOR") {
      const coordinator = sorted.filter(t => t.agentId?.includes("coordinator") || t.agentName?.includes("Coordinator"));
      const specialists = sorted.filter(t => !coordinator.includes(t));
      if (coordinator.length > 0) {
        result.push({ label: "拆解 (Coordinator)", tasks: coordinator, icon: "🧠" });
      }
      if (specialists.length > 0) {
        result.push({ label: "执行 (Specialists)", tasks: specialists, icon: "⚡" });
      }
    } else {
      result.push({ label: "流水线执行", tasks: sorted, icon: "🔗" });
    }
    return result;
  }, [sorted, mode]);

  return (
    <div className="mt-3 pt-3 border-t border-slate-200 dark:border-slate-800">
      <div className="flex items-center gap-1.5 mb-3">
        <GanttChart size={14} className="text-indigo-500" />
        <h4 className="text-[11px] font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">
          时间线 / 甘特图
        </h4>
        <span className="text-[10px] text-slate-400 ml-auto">
          总耗时 {sorted.reduce((sum, t) => sum + (t.durationMs || 0), 0)}ms
        </span>
      </div>

      <div className="space-y-3">
        {phases.map((phase, pi) => (
          <div key={pi}>
            <div className="flex items-center gap-1.5 mb-1.5">
              <span className="text-xs">{phase.icon}</span>
              <span className="text-[10px] font-medium text-slate-500 dark:text-slate-400">
                {phase.label}
              </span>
            </div>
            <div className="space-y-1.5">
              {phase.tasks.map(task => {
                const barWidth = task.durationMs
                  ? Math.max((task.durationMs / maxDuration) * 100, 15)
                  : 100;
                return (
                  <div key={task.id} className="flex items-center gap-2 group">
                    <div className="w-28 min-w-[7rem] text-right">
                      <span className="text-[11px] font-medium text-slate-700 dark:text-slate-300 truncate block">
                        {task.agentName || task.agentId}
                      </span>
                    </div>
                    <div className="flex items-center text-slate-300 dark:text-slate-600">
                      <ArrowRight size={12} />
                    </div>
                    <div className="flex-1 min-w-0 relative">
                      <div className="h-7 w-full bg-slate-100 dark:bg-slate-800 rounded overflow-hidden relative">
                        <div className="absolute inset-0 flex">
                          {Array.from({ length: 10 }).map((_, i) => (
                            <div key={i} className="flex-1 border-r border-slate-200/50 dark:border-slate-700/50 last:border-r-0" />
                          ))}
                        </div>
                        <div
                          className={`h-full ${STATUS_BAR_COLORS[task.status] || "bg-slate-400"} rounded transition-all duration-500 relative`}
                          style={{ width: `${barWidth}%` }}
                        >
                          {task.status === "RUNNING" && (
                            <div className="absolute inset-0 overflow-hidden rounded">
                              <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent animate-[shimmer_1.5s_infinite]" />
                            </div>
                          )}
                          <div className="absolute inset-0 flex items-center px-2">
                            <span className="text-[10px] text-white font-medium truncate drop-shadow-sm">
                              {task.instruction || task.status}
                            </span>
                            {task.durationMs ? (
                              <span className="text-[10px] text-white/80 ml-auto tabular-nums">
                                {task.durationMs}ms
                              </span>
                            ) : null}
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="w-16 min-w-[4rem] text-center">
                      <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-medium ${
                        task.status === "COMPLETED"
                          ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                          : task.status === "FAILED"
                          ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                          : task.status === "RUNNING"
                          ? "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400"
                          : "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400"
                      }`}>
                        {task.status}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <div className="flex items-center gap-3 mt-3 pt-2 border-t border-slate-100 dark:border-slate-800">
        <span className="text-[10px] text-slate-400">图例:</span>
        {["COMPLETED", "RUNNING", "FAILED", "PENDING"].map(s => (
          <div key={s} className="flex items-center gap-1">
            <div className={`w-3 h-3 rounded ${STATUS_BAR_COLORS[s]}`} />
            <span className="text-[10px] text-slate-500">{s}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
