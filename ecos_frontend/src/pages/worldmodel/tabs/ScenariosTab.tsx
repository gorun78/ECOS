import React from "react";
import {
  GitBranch, Plus, Edit3, Trash2,
} from "lucide-react";
import type { Goal, Scenario } from "../../../types";
import EmptyState from "../EmptyState";

export interface ScenariosTabProps {
  scenarios: Scenario[];
  goals: Goal[];
  loading: boolean;
  locale: string;
  styles: any;
  openDlg: (type: string, item?: any) => void;
  handleDelete: (type: string, id: number | string) => void;
  compareIds: (number | string)[];
  setCompareIds: React.Dispatch<React.SetStateAction<(number | string)[]>>;
  doCompare: () => Promise<void>;
  compareResult: any;
}

// ═══════ TAB 3: Scenarios ═══════
export default function ScenariosTab({
  scenarios, goals, loading, locale, styles,
  openDlg, handleDelete, compareIds, setCompareIds, doCompare, compareResult,
}: ScenariosTabProps) {
  return (
    <div className="flex-1 flex flex-col min-h-0">
      <div className="flex items-center gap-2 mb-4 shrink-0 flex-wrap">
        <span className="text-sm font-semibold opacity-80">
          {locale === "zh" ? "情景规划" : "Scenarios"} ({scenarios.length})
        </span>
        <button
          onClick={() => openDlg("scenarios")}
          className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium transition-all
            ${styles.accentBg} ${styles.accentHover} text-white`}
        >
          <Plus className="w-3.5 h-3.5" />
          {locale === "zh" ? "新建情景" : "New Scenario"}
        </button>
        <button
          onClick={doCompare}
          disabled={compareIds.length < 2}
          className={`px-2.5 py-1.5 rounded border text-xs font-medium transition-all
            ${styles.cardBorder} ${styles.cardBg} hover:opacity-80 disabled:opacity-30 disabled:cursor-not-allowed`}
        >
          {locale === "zh" ? "对比" : "Compare"} ({compareIds.length})
        </button>
      </div>

      <div className="flex-1 overflow-auto">
        {scenarios.length === 0 && !loading ? (
          <EmptyState icon={GitBranch} msg={locale === "zh" ? "暂无情景规划" : "No scenarios yet"} />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {scenarios.map((sc) => {
              const sel = compareIds.includes(sc.id);
              return (
                <div key={sc.id}
                  onClick={() => setCompareIds(prev => prev.includes(sc.id) ? prev.filter(x => x !== sc.id) : [...prev, sc.id])}
                  className={`rounded-lg p-4 cursor-pointer transition-all ${
                    sel
                      ? "border-2 border-indigo-500 bg-indigo-500/5"
                      : `border hover:border-indigo-500/50 ${styles.cardBorder} ${styles.cardBg}`
                  }`}
                >
                  <div className="flex justify-between items-start mb-2">
                    <span className="text-sm font-semibold">{sc.name}</span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded border bg-indigo-500/10 text-indigo-400 border-indigo-500/30">
                      {sc.status || "DRAFT"}
                    </span>
                  </div>
                  <p className={`text-xs mb-3 ${styles.cardTextMuted} line-clamp-2`}>
                    {sc.description || "—"}
                  </p>
                  <div className="flex items-center gap-3 text-[11px] opacity-60 mb-3">
                    <span>{locale === "zh" ? "概率" : "Prob"}: {Math.round((sc.probability || 0) * 100)}%</span>
                    <span>{locale === "zh" ? "影响分" : "Impact"}: {sc.impactScore ?? "—"}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className={sel ? "text-indigo-400" : "opacity-30"}>
                      {sel ? (locale === "zh" ? "✓ 已选" : "✓ Selected") : (locale === "zh" ? "点击选择" : "Click to select")}
                    </span>
                    <div className="flex gap-1">
                      <button onClick={(e) => { e.stopPropagation(); openDlg("scenarios", sc); }}
                        className={`p-1 rounded border text-[10px] transition-all ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}>
                        <Edit3 className="w-3 h-3" />
                      </button>
                      <button onClick={(e) => { e.stopPropagation(); handleDelete("scenarios", sc.id); }}
                        className="p-1 rounded border text-[10px] text-red-400 border-red-500/20 hover:bg-red-500/10 transition-all">
                        <Trash2 className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Compare result */}
        {compareResult && (
          <div className={`mt-4 border rounded-lg p-4 ${styles.cardBorder} ${styles.cardBg}`}>
            <h3 className="text-sm font-bold mb-3">{locale === "zh" ? "📊 情景对比" : "Scenario Comparison"}</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className={`border-b ${styles.cardBorder} text-left`}>
                    <th className="px-3 py-2 font-mono text-[10px] uppercase tracking-wider opacity-60">
                      {locale === "zh" ? "目标" : "Goal"}
                    </th>
                    {compareResult.scenarios?.map((sc: any, i: number) => (
                      <th key={i} className="px-3 py-2 font-mono text-[10px] uppercase tracking-wider opacity-60">
                        {sc.name}<br />
                        <span className="font-normal opacity-50">({Math.round((sc.probability || 0) * 100)}%)</span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {goals.map((g) => (
                    <tr key={g.id}>
                      <td className="px-3 py-2 font-medium">{g.name}</td>
                      {compareResult.scenarios?.map((sc: any, si: number) => {
                        const imp = (sc.impacts || []).find((i: any) => i.goalId === g.id);
                        return (
                          <td key={si} className="px-3 py-2 font-mono">
                            {imp ? (
                              <span className={imp.projectedDelta > 0 ? "text-emerald-400" : "text-red-400"}>
                                {imp.projectedDelta > 0 ? "+" : ""}{imp.projectedDelta}{g.unit || ""}
                              </span>
                            ) : "—"}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
