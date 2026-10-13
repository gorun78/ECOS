/**
 * RuleReasoningCard — 认知引擎规则推理卡片。
 * 引擎返回 0 条匹配规则时如实呈现（无假成功）；颜色走 useTheme tokens。
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

  const rules: any[] = reasonResult && !reasonResult.error
    ? (Array.isArray(reasonResult.matchedRules) ? reasonResult.matchedRules : (Array.isArray(reasonResult) ? reasonResult : []))
    : [];

  return (
    <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
      <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
        <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
          <Brain className={`w-4 h-4 ${styles.accentText}`} />
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
        className={`w-full ${styles.accentBg} ${styles.accentHover} disabled:opacity-50 text-white rounded-lg text-xs font-bold py-2.5 transition flex items-center justify-center gap-2 cursor-pointer`}
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

      {showReasonPanel && reasonResult && !reasonResult.error && rules.length > 0 && (
        <div className="space-y-2 max-h-64 overflow-y-auto scrollbar-thin">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.reason.matchedRules")}
          </span>
          {rules.map((rule: any, idx: number) => (
            <div key={idx} className={`border rounded-lg p-2.5 ${styles.cardBorder} ${styles.sidebarBg}`}>
              <div className="flex items-center justify-between mb-1">
                <span className={`text-[11px] font-bold font-mono ${styles.accentText}`}>
                  {rule.ruleId || rule.id || rule.name || `Rule #${idx + 1}`}
                </span>
                {rule.priority != null && (
                  <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono font-bold border ${
                    rule.priority === 'high' ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}` :
                    rule.priority === 'medium' ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}` :
                    `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`
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

      {/* 无假成功：0 条匹配规则如实呈现 */}
      {showReasonPanel && reasonResult && !reasonResult.error && rules.length === 0 && (
        <div className={`border rounded-lg p-3 text-xs font-mono ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>
          {t("cognition.cos.reason.empty")}
        </div>
      )}

      {showReasonPanel && reasonResult?.error && (
        <div className={`border rounded-lg p-3 text-xs font-mono ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          {reasonResult.error}
        </div>
      )}
    </div>
  );
}
