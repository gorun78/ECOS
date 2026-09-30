/**
 * ParetoOptimizerCard — 认知引擎帕累托多目标寻优卡片。
 * 由 CognitiveOperatingSystem.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 仅 handleOptimize 改为 props 回调 onOptimize。
 * @license Apache-2.0
 */
import { TrendingUp, RefreshCw, Zap } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function ParetoOptimizerCard({
  optimizeLoading,
  optimizeResult,
  onOptimize,
}: {
  optimizeLoading: boolean;
  optimizeResult: any;
  onOptimize: () => void;
}) {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
      <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
        <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
          <TrendingUp className="w-4 h-4 text-amber-500" />
          <span>{locale === "zh" ? "帕累托优化器" : "Pareto Optimizer"}</span>
        </h4>
        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
          {locale === "zh" ? "多目标寻优" : "Multi-Objective Optimization"}
        </span>
      </div>

      <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
        {locale === "zh"
          ? "基于认知引擎执行多目标帕累托前沿解集搜索，在预算、延迟、质量等约束下寻找最优权衡方案。"
          : "Execute multi-objective Pareto frontier search via the cognitive engine, finding optimal trade-offs under budget, latency, and quality constraints."}
      </p>

      <button
        onClick={onOptimize}
        disabled={optimizeLoading}
        className="w-full bg-amber-600 hover:bg-amber-700 disabled:bg-amber-400 text-white rounded-lg text-xs font-bold py-2.5 transition flex items-center justify-center gap-2"
      >
        {optimizeLoading ? (
          <>
            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            <span>{locale === "zh" ? "优化计算中..." : "Optimizing..."}</span>
          </>
        ) : (
          <>
            <Zap className="w-3.5 h-3.5" />
            <span>{locale === "zh" ? "执行帕累托优化" : "Run Pareto Optimizer"}</span>
          </>
        )}
      </button>

      {optimizeResult && !optimizeResult.error && (
        <div className="overflow-x-auto">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="border-b" style={{ borderColor: "var(--cardBorder)" }}>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {locale === "zh" ? "方案ID" : "Solution ID"}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {locale === "zh" ? "层级" : "Rank"}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {locale === "zh" ? "X目标" : "X Objective"}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {locale === "zh" ? "Y目标" : "Y Objective"}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {locale === "zh" ? "Z目标" : "Z Objective"}
                </th>
              </tr>
            </thead>
            <tbody>
              {(Array.isArray(optimizeResult) ? optimizeResult : (optimizeResult.solutions || optimizeResult.frontier || [])).map((sol: any, idx: number) => (
                <tr key={idx} className="border-b hover:bg-black/5 dark:hover:bg-white/5 transition-colors" style={{ borderColor: "var(--cardBorder)" }}>
                  <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                    {sol.solutionId ?? sol.id ?? `#${idx + 1}`}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-[10px] text-amber-500 font-bold">
                    {sol.rank ?? idx + 1}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                    {sol.objectives?.x ?? sol.x ?? sol.objectiveX ?? '—'}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                    {sol.objectives?.y ?? sol.y ?? sol.objectiveY ?? '—'}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                    {sol.objectives?.z ?? sol.z ?? sol.objectiveZ ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {optimizeResult?.error && (
        <div className="bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-lg p-3 text-red-700 dark:text-red-400 text-xs font-mono">
          {optimizeResult.error}
        </div>
      )}
    </div>
  );
}
