/**
 * RuleReasoningCard — 认知引擎规则推理卡片。
 * 由 CognitiveOperatingSystem.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 仅 handleReason 改为 props 回调 onReason。
 * @license Apache-2.0
 */
import { Brain, RefreshCw, Cpu } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function RuleReasoningCard({
  reasonLoading,
  reasonResult,
  showReasonPanel,
  onReason,
}: {
  reasonLoading: boolean;
  reasonResult: any;
  showReasonPanel: boolean;
  onReason: () => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
      <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
        <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
          <Brain className="w-4 h-4 text-purple-500" />
          <span>{t("cognition.cos.reason.title")}</span>
        </h4>
        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
          mode=rule
        </span>
      </div>

      <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
        {t("cognition.cos.reason.desc")}
      </p>

      <button type="button"
        onClick={onReason}
        disabled={reasonLoading}
        className="w-full bg-purple-600 hover:bg-purple-700 disabled:bg-purple-400 text-white rounded-lg text-xs font-bold py-2.5 transition flex items-center justify-center gap-2"
      >
        {reasonLoading ? (
          <>
            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            <span>{t("cognition.cos.reason.reasoning")}</span>
          </>
        ) : (
          <>
            <Cpu className="w-3.5 h-3.5" />
            <span>{t("cognition.cos.reason.run")}</span>
          </>
        )}
      </button>

      {showReasonPanel && reasonResult && !reasonResult.error && (
        <div className="space-y-2 max-h-64 overflow-y-auto scrollbar-thin">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.reason.matchedRules")}
          </span>
          {(Array.isArray(reasonResult.matchedRules) ? reasonResult.matchedRules : (Array.isArray(reasonResult) ? reasonResult : [])).map((rule: any, idx: number) => (
            <div key={idx} className={`border rounded-lg p-2.5 ${styles.cardBorder} bg-black/5 dark:bg-white/5`}>
              <div className="flex items-center justify-between mb-1">
                <span className="text-[11px] font-bold font-mono text-purple-600 dark:text-purple-400">
                  {rule.ruleId || rule.id || rule.name || `Rule #${idx + 1}`}
                </span>
                {rule.priority != null && (
                  <span className={`text-[9px] px-1.5 py-0.2 rounded font-mono font-bold ${
                    rule.priority === 'high' ? 'bg-red-500/10 text-red-500 border border-red-500/20' :
                    rule.priority === 'medium' ? 'bg-amber-500/10 text-amber-500 border border-amber-500/20' :
                    'bg-blue-500/10 text-blue-500 border border-blue-500/20'
                  }`}>
                    {rule.priority}
                  </span>
                )}
              </div>
              <p className={`text-[10px] leading-relaxed ${styles.cardTextMuted}`}>
                {rule.description || rule.condition || rule.summary || JSON.stringify(rule)}
              </p>
            </div>
          ))}
        </div>
      )}

      {showReasonPanel && reasonResult?.error && (
        <div className="bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 rounded-lg p-3 text-red-700 dark:text-red-400 text-xs font-mono">
          {reasonResult.error}
        </div>
      )}
    </div>
  );
}
