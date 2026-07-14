import React from "react";
import {
  ClipboardList, AlertCircle, BarChart3, CheckCircle2,
} from "lucide-react";
import type { Goal } from "../../../types";
import { computeProgress, trafficBg, trafficColor } from "../helpers";

export interface ReviewTabProps {
  selectedGoal: Goal | null;
  locale: string;
  styles: any;
}

// ═══════ TAB 4: Review ═══════
export default function ReviewTab({
  selectedGoal, locale, styles,
}: ReviewTabProps) {
  return (
    <div className="flex-1 flex flex-col min-h-0">
      {selectedGoal ? (
        <div className="flex-1 overflow-auto space-y-4">
          {/* Summary card */}
          <div className={`border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-3">
              <ClipboardList className="w-4 h-4 text-indigo-400" />
              <h3 className="text-sm font-bold">{locale === "zh" ? "复盘概要" : "Review Summary"}</h3>
            </div>
            <p className="text-sm font-semibold mb-2">{selectedGoal.name}</p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
              <div className={`border rounded p-3 ${styles.cardBorder}`}>
                <span className="text-[10px] uppercase tracking-wider opacity-50">
                  {locale === "zh" ? "达成率" : "Achievement Rate"}
                </span>
                <p className={`text-2xl font-bold mt-1 ${trafficColor(computeProgress(selectedGoal))}`}>
                  {Math.round(computeProgress(selectedGoal))}%
                </p>
              </div>
              <div className={`border rounded p-3 ${styles.cardBorder}`}>
                <span className="text-[10px] uppercase tracking-wider opacity-50">
                  {locale === "zh" ? "偏差" : "Deviation"}
                </span>
                <p className="text-lg font-mono mt-1">
                  {(() => {
                    const cur = typeof selectedGoal.current_value === "number" ? selectedGoal.current_value
                      : typeof selectedGoal.currentValue === "number" ? selectedGoal.currentValue
                      : parseFloat(String(selectedGoal.currentValue || "0"));
                    const tgt = typeof selectedGoal.target_value === "number" ? selectedGoal.target_value
                      : typeof selectedGoal.targetValue === "number" ? selectedGoal.targetValue
                      : parseFloat(String(selectedGoal.targetValue || "1"));
                    const dev = tgt ? tgt - cur : 0;
                    return <span className={dev > 0 ? "text-red-400" : "text-emerald-400"}>
                      {dev >= 0 ? "-" : "+"}{Math.abs(dev).toFixed(1)}{selectedGoal.unit || ""}
                    </span>;
                  })()}
                </p>
              </div>
              <div className={`border rounded p-3 ${styles.cardBorder}`}>
                <span className="text-[10px] uppercase tracking-wider opacity-50">
                  {locale === "zh" ? "状态" : "Status"}
                </span>
                <p className="text-sm font-medium mt-1">{selectedGoal.status || "—"}</p>
              </div>
            </div>
          </div>

          {/* Deviation analysis placeholder */}
          <div className={`border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-3">
              <AlertCircle className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-bold">{locale === "zh" ? "偏差分析" : "Deviation Analysis"}</h3>
            </div>
            <div className="flex flex-col items-center justify-center py-10 text-center opacity-50">
              <BarChart3 className="w-10 h-10 mb-2 opacity-30" />
              <p className="text-sm">
                {locale === "zh"
                  ? "偏差分析报告将在 Phase 3 实现"
                  : "Deviation analysis report will be implemented in Phase 3"}
              </p>
              <p className="text-xs mt-1">
                {locale === "zh"
                  ? "即将支持根因分析、影响链路追踪与改进建议"
                  : "Root cause analysis, impact chain tracing, and improvement suggestions coming soon"}
              </p>
            </div>
          </div>

          {/* Timeline */}
          <div className={`border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <div className="flex items-center gap-2 mb-3">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-bold">{locale === "zh" ? "关键里程碑" : "Key Milestones"}</h3>
            </div>
            <div className="space-y-3">
              {selectedGoal.start_date && (
                <div className="flex items-center gap-3 text-xs">
                  <div className="w-2 h-2 rounded-full bg-emerald-400 flex-shrink-0" />
                  <div>
                    <p className="font-medium">{locale === "zh" ? "开始" : "Start"}</p>
                    <p className="opacity-50 font-mono">{selectedGoal.start_date.slice(0, 10)}</p>
                  </div>
                </div>
              )}
              <div className="flex items-center gap-3 text-xs">
                <div className={`w-2 h-2 rounded-full flex-shrink-0 ${trafficBg(computeProgress(selectedGoal))}`} />
                <div>
                  <p className="font-medium">{locale === "zh" ? "当前" : "Current"}</p>
                  <p className="opacity-50 font-mono">{Math.round(computeProgress(selectedGoal))}%</p>
                </div>
              </div>
              {selectedGoal.end_date && (
                <div className="flex items-center gap-3 text-xs">
                  <div className="w-2 h-2 rounded-full bg-indigo-400 flex-shrink-0" />
                  <div>
                    <p className="font-medium">{locale === "zh" ? "截止" : "Deadline"}</p>
                    <p className="opacity-50 font-mono">{selectedGoal.end_date.slice(0, 10)}</p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center opacity-50">
            <ClipboardList className="w-10 h-10 mb-3 mx-auto opacity-30" />
            <p className="text-sm">
              {locale === "zh"
                ? "请在「目标树」标签页中选择一个目标以查看复盘报告"
                : "Select a goal in the 'Goal Tree' tab to view the review report"}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
