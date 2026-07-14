import React from "react";
import {
  Target, Plus, Maximize2, Minimize2, Edit3, Trash2, XCircle,
  Flag, Building2, Gauge, Workflow, Bot, Loader2,
} from "lucide-react";
import type { Goal } from "../../../types";
import GoalTreeNode from "../GoalTreeNode";
import EmptyState from "../EmptyState";
import { computeProgress, trafficBg, getGoalTypeConfig } from "../helpers";

export interface GoalTreeTabProps {
  topLevelGoals: Goal[];
  loading: boolean;
  locale: string;
  styles: any;
  expandedIds: Set<number | string>;
  toggleExpand: (id: number | string) => void;
  setSelectedGoal: (g: Goal | null) => void;
  selectedGoal: Goal | null;
  openDlg: (type: string, item?: any) => void;
  handleDelete: (type: string, id: number | string) => void;
  expandAll: () => void;
  collapseAll: () => void;
}

// ═══════ TAB 0: Goal Tree ═══════
export default function GoalTreeTab({
  topLevelGoals, loading, locale, styles,
  expandedIds, toggleExpand, setSelectedGoal, selectedGoal,
  openDlg, handleDelete, expandAll, collapseAll,
}: GoalTreeTabProps) {
  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Toolbar */}
      <div className="flex items-center gap-2 mb-4 shrink-0 flex-wrap">
        <span className="text-sm font-semibold opacity-80">
          {locale === "zh" ? "目标金字塔" : "Goal Pyramid"} ({topLevelGoals.length})
        </span>

        <button
          onClick={() => openDlg("goals")}
          className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium transition-all
            ${styles.accentBg} ${styles.accentHover} text-white`}
        >
          <Plus className="w-3.5 h-3.5" />
          {locale === "zh" ? "新建目标" : "New Goal"}
        </button>

        <div className="flex-1" />

        <button onClick={expandAll}
          className={`px-2 py-1 rounded border text-[11px] transition-all ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}
          title={locale === "zh" ? "展开全部" : "Expand All"}>
          <Maximize2 className="w-3 h-3 inline mr-1" />
          {locale === "zh" ? "展开全部" : "Expand"}
        </button>
        <button onClick={collapseAll}
          className={`px-2 py-1 rounded border text-[11px] transition-all ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}
          title={locale === "zh" ? "收起全部" : "Collapse All"}>
          <Minimize2 className="w-3 h-3 inline mr-1" />
          {locale === "zh" ? "收起全部" : "Collapse"}
        </button>
      </div>

      {/* Legend */}
      <div className="flex items-center gap-3 mb-3 text-[11px] opacity-60 shrink-0">
        <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-400" /> ≥80%</span>
        <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-400" /> 50-79%</span>
        <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-red-400" /> &lt;50%</span>
        <span className="flex items-center gap-1 ml-2"><Flag className="w-3 h-3 text-indigo-400" />战略</span>
        <span className="flex items-center gap-1"><Building2 className="w-3 h-3 text-violet-400" />OKR</span>
        <span className="flex items-center gap-1"><Gauge className="w-3 h-3 text-cyan-400" />KPI</span>
        <span className="flex items-center gap-1"><Workflow className="w-3 h-3 text-amber-400" />工作流</span>
        <span className="flex items-center gap-1"><Bot className="w-3 h-3 text-emerald-400" />Agent</span>
      </div>

      {/* Tree */}
      <div className={`flex-1 overflow-auto border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
        {loading && topLevelGoals.length === 0 ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin opacity-40" />
          </div>
        ) : topLevelGoals.length === 0 ? (
          <EmptyState icon={Target} msg={locale === "zh" ? "暂无目标，点击「新建目标」创建" : "No goals yet. Click 'New Goal' to create."} />
        ) : (
          topLevelGoals.map((node) => (
            <GoalTreeNode
              key={node.id}
              node={node}
              depth={0}
              expandedIds={expandedIds}
              toggleExpand={toggleExpand}
              onSelect={setSelectedGoal}
              selectedId={selectedGoal?.id ?? null}
              styles={styles}
              onEdit={(g) => openDlg("goals", g)}
              onDelete={(id) => handleDelete("goals", id)}
            />
          ))
        )}
      </div>

      {/* Selected goal detail panel */}
      {selectedGoal && (
        <div className={`mt-4 border rounded-lg p-4 shrink-0 border-indigo-500/30 bg-indigo-500/5`}>
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold">{selectedGoal.name}</span>
              {(() => { const gt = getGoalTypeConfig(selectedGoal.goal_type); const GTI = gt.icon;
                return <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded border flex items-center gap-1 ${gt.bg} ${gt.text} ${gt.border}`}><GTI className="w-3 h-3" />{gt.label}</span>;
              })()}
              <span className={`text-[10px] px-1.5 py-0.5 rounded border ${selectedGoal.status === "ACTIVE" || selectedGoal.status === "on_track" ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30" : "bg-gray-500/10 text-gray-400 border-gray-500/30"}`}>
                {selectedGoal.status || "—"}
              </span>
            </div>
            <div className="flex gap-1">
              <button onClick={() => openDlg("goals", selectedGoal)}
                className={`p-1.5 rounded border text-xs transition-all ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}>
                <Edit3 className="w-3 h-3" />
              </button>
              <button onClick={() => handleDelete("goals", selectedGoal.id)}
                className="p-1.5 rounded border text-xs text-red-400 border-red-500/20 hover:bg-red-500/10 transition-all">
                <Trash2 className="w-3 h-3" />
              </button>
              <button onClick={() => setSelectedGoal(null)}
                className="p-1.5 rounded border text-xs transition-all opacity-50 hover:opacity-100">
                <XCircle className="w-3 h-3" />
              </button>
            </div>
          </div>
          {selectedGoal.description && (
            <p className={`text-xs mb-2 ${styles.cardTextMuted}`}>{selectedGoal.description}</p>
          )}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
            <div>
              <span className="opacity-50">{locale === "zh" ? "进度" : "Progress"}</span>
              <div className="flex items-center gap-2 mt-1">
                <div className="flex-1 h-2 rounded-full bg-white/10 overflow-hidden">
                  <div className={`h-full rounded-full ${trafficBg(computeProgress(selectedGoal))}`}
                    style={{ width: `${computeProgress(selectedGoal)}%` }} />
                </div>
                <span className="font-mono">{Math.round(computeProgress(selectedGoal))}%</span>
              </div>
            </div>
            <div>
              <span className="opacity-50">{locale === "zh" ? "当前/目标" : "Current/Target"}</span>
              <p className="font-mono mt-1">
                {typeof selectedGoal.current_value === "number" ? selectedGoal.current_value
                  : typeof selectedGoal.currentValue === "number" ? selectedGoal.currentValue
                  : selectedGoal.currentValue || 0}
                /{typeof selectedGoal.target_value === "number" ? selectedGoal.target_value
                  : typeof selectedGoal.targetValue === "number" ? selectedGoal.targetValue
                  : selectedGoal.targetValue}{selectedGoal.unit || ""}
              </p>
            </div>
            <div>
              <span className="opacity-50">{locale === "zh" ? "权重" : "Weight"}</span>
              <p className="font-mono mt-1">{selectedGoal.weight ?? "—"}</p>
            </div>
            <div>
              <span className="opacity-50">{locale === "zh" ? "日期" : "Date"}</span>
              <p className="font-mono mt-1 text-[11px]">
                {selectedGoal.start_date ? selectedGoal.start_date.slice(0, 10) : "—"}
                {" → "}
                {selectedGoal.end_date ? selectedGoal.end_date.slice(0, 10) : "—"}
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
