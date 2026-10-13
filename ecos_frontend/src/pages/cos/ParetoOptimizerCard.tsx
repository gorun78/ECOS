/**
 * ParetoOptimizerCard — 认知引擎帕累托多目标寻优卡片。
 * 约束入参（预算/延迟）为用户可编辑输入；引擎返回空解集时如实呈现，不伪造结果。
 * 颜色走 useTheme tokens。
 * @license Apache-2.0
 */
import { TrendingUp, RefreshCw, Zap } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function ParetoOptimizerCard({
  optimizeLoading,
  optimizeResult,
  budget,
  latency,
  onBudgetChange,
  onLatencyChange,
  onOptimize,
}: {
  optimizeLoading: boolean;
  optimizeResult: any;
  budget: number;
  latency: number;
  onBudgetChange: (v: number) => void;
  onLatencyChange: (v: number) => void;
  onOptimize: () => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const solutions: any[] = optimizeResult && !optimizeResult.error
    ? (Array.isArray(optimizeResult) ? optimizeResult : (optimizeResult.solutions || optimizeResult.frontier || []))
    : [];

  return (
    <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
      <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
        <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
          <TrendingUp className={`w-4 h-4 ${styles.warningText}`} />
          <span>{t("cognition.cos.pareto.title")}</span>
        </h4>
        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
          {t("cognition.cos.pareto.subtitle")}
        </span>
      </div>

      <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
        {t("cognition.cos.pareto.desc")}
      </p>

      {/* 用户可编辑的约束入参 —— 提交给真实 API，不是页面伪造 */}
      <div className="grid grid-cols-2 gap-3">
        <label className="block space-y-1">
          <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.pareto.budget")}
          </span>
          <input
            type="number"
            min={0}
            value={budget}
            onChange={(e) => onBudgetChange(Number(e.target.value) || 0)}
            className={`w-full rounded-lg border px-2.5 py-1.5 text-xs font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          />
        </label>
        <label className="block space-y-1">
          <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.pareto.latency")}
          </span>
          <input
            type="number"
            min={0}
            value={latency}
            onChange={(e) => onLatencyChange(Number(e.target.value) || 0)}
            className={`w-full rounded-lg border px-2.5 py-1.5 text-xs font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          />
        </label>
      </div>

      <button type="button"
        onClick={onOptimize}
        disabled={optimizeLoading}
        className={`w-full ${styles.warningText} border ${styles.warningBorder} ${styles.warningBg} hover:opacity-90 disabled:opacity-50 rounded-lg text-xs font-bold py-2.5 transition flex items-center justify-center gap-2 cursor-pointer`}
      >
        {optimizeLoading ? (
          <>
            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            <span>{t("cognition.cos.pareto.optimizing")}</span>
          </>
        ) : (
          <>
            <Zap className="w-3.5 h-3.5" />
            <span>{t("cognition.cos.pareto.run")}</span>
          </>
        )}
      </button>

      {optimizeResult && !optimizeResult.error && solutions.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="border-b" style={{ borderColor: "var(--cardBorder)" }}>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {t("cognition.cos.pareto.col.solutionId")}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {t("cognition.cos.pareto.col.rank")}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {t("cognition.cos.pareto.col.x")}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {t("cognition.cos.pareto.col.y")}
                </th>
                <th className="text-left py-1.5 px-2 font-mono font-bold uppercase text-[10px] tracking-wider" style={{ color: "var(--cardText)" }}>
                  {t("cognition.cos.pareto.col.z")}
                </th>
              </tr>
            </thead>
            <tbody>
              {solutions.map((sol: any, idx: number) => (
                <tr key={idx} className={`border-b transition-colors ${styles.sidebarHoverBg}`} style={{ borderColor: "var(--cardBorder)" }}>
                  <td className="py-1.5 px-2 font-mono text-[10px]" style={{ color: "var(--cardText)" }}>
                    {sol.solutionId ?? sol.id ?? `#${idx + 1}`}
                  </td>
                  <td className={`py-1.5 px-2 font-mono text-[10px] ${styles.warningText} font-bold`}>
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

      {/* 无假成功：接口可达但返回空解集时如实呈现 */}
      {optimizeResult && !optimizeResult.error && solutions.length === 0 && (
        <div className={`border rounded-lg p-3 text-xs font-mono ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>
          {t("cognition.cos.pareto.empty")}
        </div>
      )}

      {optimizeResult?.error && (
        <div className={`border rounded-lg p-3 text-xs font-mono ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          {optimizeResult.error}
        </div>
      )}
    </div>
  );
}
