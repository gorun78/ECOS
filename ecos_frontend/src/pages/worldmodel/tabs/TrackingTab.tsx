import React from "react";
import {
  Target, TrendingUp, Loader2, Search, BarChart3, Workflow,
} from "lucide-react";
import type { Goal } from "../../../types";
import { computeProgress, trafficBg, getGoalTypeConfig } from "../helpers";

export interface TrackingTabProps {
  selectedGoal: Goal | null;
  locale: string;
  styles: any;
  trackingData: any[];
  trackingLoading: boolean;
}

// ═══════ TAB 1: Execution Tracking ═══════
export default function TrackingTab({
  selectedGoal, locale, styles, trackingData, trackingLoading,
}: TrackingTabProps) {
  return (
    <div className="flex-1 flex flex-col min-h-0">
      {selectedGoal ? (
        <div className="flex-1 overflow-auto space-y-4">
          {/* Goal detail card */}
          <div className={`border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-3">
              <Target className="w-4 h-4 text-indigo-400" />
              <h3 className="text-sm font-bold">{selectedGoal.name}</h3>
              {(() => { const gt = getGoalTypeConfig(selectedGoal.goal_type); const GTI = gt.icon;
                return <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded border ${gt.bg} ${gt.text} ${gt.border}`}><GTI className="w-3 h-3" />{gt.label}</span>;
              })()}
            </div>
            {selectedGoal.description && (
              <p className={`text-xs mb-3 ${styles.cardTextMuted}`}>{selectedGoal.description}</p>
            )}

            {/* Progress card */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
              <div className={`border rounded p-3 ${styles.cardBorder}`}>
                <span className="text-[10px] uppercase tracking-wider opacity-50">{locale === "zh" ? "进度" : "Progress"}</span>
                <p className="text-2xl font-bold mt-1 text-indigo-400">
                  {Math.round(computeProgress(selectedGoal))}%
                </p>
                <div className="mt-1 h-1.5 rounded-full bg-white/10 overflow-hidden w-full">
                  <div className={`h-full rounded-full ${trafficBg(computeProgress(selectedGoal))}`}
                    style={{ width: `${computeProgress(selectedGoal)}%` }} />
                </div>
              </div>
              <div className={`border rounded p-3 ${styles.cardBorder}`}>
                <span className="text-[10px] uppercase tracking-wider opacity-50">{locale === "zh" ? "当前值 / 目标值" : "Current / Target"}</span>
                <p className="text-lg font-mono mt-1">
                  {typeof selectedGoal.current_value === "number" ? selectedGoal.current_value
                    : typeof selectedGoal.currentValue === "number" ? selectedGoal.currentValue
                    : selectedGoal.currentValue || 0}
                  <span className="opacity-50"> / </span>
                  {typeof selectedGoal.target_value === "number" ? selectedGoal.target_value
                    : typeof selectedGoal.targetValue === "number" ? selectedGoal.targetValue
                    : selectedGoal.targetValue}
                  <span className="text-xs opacity-50 ml-1">{selectedGoal.unit || ""}</span>
                </p>
              </div>
              <div className={`border rounded p-3 ${styles.cardBorder}`}>
                <span className="text-[10px] uppercase tracking-wider opacity-50">{locale === "zh" ? "状态" : "Status"}</span>
                <div className="flex items-center gap-2 mt-1">
                  <span className={`w-3 h-3 rounded-full ${trafficBg(computeProgress(selectedGoal))}`} />
                  <p className="text-sm font-medium">
                    {selectedGoal.status || (computeProgress(selectedGoal) >= 80 ? "on_track" : computeProgress(selectedGoal) >= 50 ? "at_risk" : "behind")}
                  </p>
                </div>
              </div>
            </div>

            {/* Meta info */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
              <div><span className="opacity-50">{locale === "zh" ? "编码" : "Code"}</span><p className="font-mono">{selectedGoal.code || `G-${selectedGoal.id}`}</p></div>
              <div><span className="opacity-50">{locale === "zh" ? "权重" : "Weight"}</span><p className="font-mono">{selectedGoal.weight ?? "—"}</p></div>
              <div><span className="opacity-50">{locale === "zh" ? "组织" : "Org"}</span><p className="font-mono">{selectedGoal.org_id || "—"}</p></div>
              <div><span className="opacity-50">{locale === "zh" ? "负责人" : "Owner"}</span><p className="font-mono">{selectedGoal.owner_user_id || "—"}</p></div>
              <div><span className="opacity-50">{locale === "zh" ? "开始" : "Start"}</span><p className="font-mono">{selectedGoal.start_date ? selectedGoal.start_date.slice(0, 10) : "—"}</p></div>
              <div><span className="opacity-50">{locale === "zh" ? "结束" : "End"}</span><p className="font-mono">{selectedGoal.end_date ? selectedGoal.end_date.slice(0, 10) : "—"}</p></div>
              <div><span className="opacity-50">{locale === "zh" ? "关联工作流" : "Linked Workflow"}</span><p className="font-mono">{selectedGoal.linked_workflow_id || "—"}</p></div>
            </div>
          </div>

          {/* Progress trend — real time-series from ecos_goal_tracking */}
          <div className={`border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-3">
              <BarChart3 className="w-4 h-4 text-indigo-400" />
              <h3 className="text-sm font-bold">{locale === "zh" ? "进度趋势" : "Progress Trend"}</h3>
            </div>
            {trackingLoading ? (
              <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin opacity-50" /></div>
            ) : trackingData.length > 0 ? (
              <div className="space-y-2">
                {trackingData.map((t: any, i: number) => (
                  <div key={i} className="flex items-center gap-3 text-xs">
                    <span className="w-16 text-right opacity-50 font-mono">{t.recordedAt?.slice(5) || t.recorded_at?.slice(5)}</span>
                    <div className="flex-1 h-4 rounded bg-white/5 overflow-hidden">
                      <div className={`h-full rounded transition-all ${t.progress >= 80 ? 'bg-emerald-500' : t.progress >= 50 ? 'bg-amber-500' : 'bg-red-500'}`}
                        style={{ width: `${Math.min(t.progress, 100)}%` }}>
                      </div>
                    </div>
                    <span className="w-10 text-right font-mono font-bold">{t.progress}%</span>
                    {t.note && <span className="opacity-40 truncate max-w-[200px] hidden sm:inline">{t.note}</span>}
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-8 text-center opacity-50">
                <TrendingUp className="w-8 h-8 mb-2 opacity-30" />
                <p className="text-sm">{locale === "zh" ? "选择目标后加载追踪数据" : "Select a goal to load tracking"}</p>
              </div>
            )}
          </div>

          {/* Linked workflows placeholder */}
          <div className={`border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-3">
              <Workflow className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-bold">{locale === "zh" ? "关联工作流" : "Linked Workflows"}</h3>
            </div>
            {selectedGoal.linked_workflow_id ? (
              <p className="text-xs font-mono">ID: {selectedGoal.linked_workflow_id}</p>
            ) : (
              <div className="flex flex-col items-center justify-center py-8 text-center opacity-50">
                <Workflow className="w-8 h-8 mb-2 opacity-30" />
                <p className="text-sm">{locale === "zh" ? "暂无关联工作流" : "No linked workflows"}</p>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center opacity-50">
            <Search className="w-10 h-10 mb-3 mx-auto opacity-30" />
            <p className="text-sm">{locale === "zh" ? "请在「目标树」标签页中选择一个目标以查看执行追踪详情" : "Select a goal in the 'Goal Tree' tab to view execution tracking"}</p>
          </div>
        </div>
      )}
    </div>
  );
}
