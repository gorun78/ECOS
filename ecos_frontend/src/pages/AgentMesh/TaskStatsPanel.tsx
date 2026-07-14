/**
 * TaskStatsPanel — Mission task statistics panel.
 * @license Apache-2.0
 */

import { BarChart3, Check, X, RefreshCw, Clock, Timer, Hash } from "lucide-react";
import { AgentMeshMission, AgentMeshTask } from "../../api";

interface TaskStatsPanelProps {
  mission: AgentMeshMission;
  tasks: AgentMeshTask[];
}

export default function TaskStatsPanel({ mission, tasks }: TaskStatsPanelProps) {
  const total = tasks.length;
  const completed = tasks.filter(t => t.status === "COMPLETED").length;
  const failed = tasks.filter(t => t.status === "FAILED").length;
  const running = tasks.filter(t => t.status === "RUNNING").length;
  const pending = tasks.filter(t => t.status === "PENDING").length;
  const totalDuration = tasks.reduce((sum, t) => sum + (t.durationMs || 0), 0);
  const progress = total > 0 ? Math.round((completed / total) * 100) : 0;

  if (total === 0) return null;

  return (
    <div className="mb-3 p-4 rounded-lg border border-indigo-200 dark:border-indigo-800 bg-white dark:bg-slate-900">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <BarChart3 size={16} className="text-indigo-500" />
          <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            Mission 统计 — {mission.title}
          </span>
          <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
            mission.mode === "PIPELINE"
              ? "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400"
              : "bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400"
          }`}>{mission.mode}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-medium text-slate-500 tabular-nums">{progress}%</span>
          <div className="w-24 h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
            <div
              className="h-full bg-indigo-500 rounded-full transition-all duration-500"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
        <div className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700">
          <Hash size={14} className="text-slate-500" />
          <div>
            <div className="text-[10px] text-slate-400">总任务数</div>
            <div className="text-sm font-bold text-slate-700 dark:text-slate-300">{total}</div>
          </div>
        </div>
        <div className="flex items-center gap-2 p-2 rounded-lg bg-green-50 dark:bg-green-950/20 border border-green-200 dark:border-green-800">
          <Check size={14} className="text-green-600" />
          <div>
            <div className="text-[10px] text-green-600/80">已完成</div>
            <div className="text-sm font-bold text-green-700 dark:text-green-400">{completed}</div>
          </div>
        </div>
        <div className="flex items-center gap-2 p-2 rounded-lg bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-800">
          <X size={14} className="text-red-600" />
          <div>
            <div className="text-[10px] text-red-600/80">失败</div>
            <div className="text-sm font-bold text-red-700 dark:text-red-400">{failed}</div>
          </div>
        </div>
        <div className="flex items-center gap-2 p-2 rounded-lg bg-blue-50 dark:bg-blue-950/20 border border-blue-200 dark:border-blue-800">
          <RefreshCw size={14} className={`text-blue-600 ${running > 0 ? "animate-spin" : ""}`} />
          <div>
            <div className="text-[10px] text-blue-600/80">运行中</div>
            <div className="text-sm font-bold text-blue-700 dark:text-blue-400">{running}</div>
          </div>
        </div>
        <div className="flex items-center gap-2 p-2 rounded-lg bg-yellow-50 dark:bg-yellow-950/20 border border-yellow-200 dark:border-yellow-800">
          <Clock size={14} className="text-yellow-600" />
          <div>
            <div className="text-[10px] text-yellow-600/80">等待中</div>
            <div className="text-sm font-bold text-yellow-700 dark:text-yellow-400">{pending}</div>
          </div>
        </div>
        <div className="flex items-center gap-2 p-2 rounded-lg bg-indigo-50 dark:bg-indigo-950/20 border border-indigo-200 dark:border-indigo-800">
          <Timer size={14} className="text-indigo-600" />
          <div>
            <div className="text-[10px] text-indigo-600/80">总耗时</div>
            <div className="text-sm font-bold text-indigo-700 dark:text-indigo-400">
              {totalDuration >= 1000
                ? `${(totalDuration / 1000).toFixed(1)}s`
                : `${totalDuration}ms`}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
