/**
 * MetricsPanel — Agent economics metrics board.
 * @license Apache-2.0
 */

import { Zap } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { g } from "./helpers";

interface MetricsPanelProps {
  metrics: {
    successRate: number;
    latencyMs: number;
    tokensUsed: number;
    costUSD: number;
    toolCalls: number;
  };
}

export default function MetricsPanel({ metrics }: MetricsPanelProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`lg:col-span-1 border rounded-xl p-5 flex flex-col justify-between overflow-y-auto scrollbar-thin shadow-xs ${styles.cardBg}`} style={{ borderColor: 'var(--card-border, #E2E8F0)' }}>
      <div className="space-y-4">
        <div>
          <span className={`text-[9px] uppercase font-bold font-mono tracking-wider ${styles.cardTextMuted} block leading-none`}>
            {t("agent.metrics.title")}
          </span>
          <h3 className={`text-xs font-bold ${styles.cardText} mt-1.5 uppercase font-mono`}>
            {t("agent.metrics.subtitle")}
          </h3>
        </div>

        <div className="space-y-3.5">
          <div className="p-3.5 rounded-xl border text-center shadow-2xs" style={{ background: 'var(--overlay, rgba(0,0,0,0.04))', borderColor: 'var(--app-border, #E2E8F0)' }}>
            <span className={`text-[9px] uppercase font-bold font-mono ${styles.cardTextMuted} block leading-none`}>
              {t("agent.metrics.successRate")}
            </span>
            <strong className="text-2xl font-extrabold font-sans text-green-600 block mt-1.5">{metrics.successRate.toFixed(1)}%</strong>
          </div>
          <div className="p-3.5 rounded-xl border text-center shadow-2xs" style={{ background: 'var(--overlay, rgba(0,0,0,0.04))', borderColor: 'var(--app-border, #E2E8F0)' }}>
            <span className={`text-[9px] uppercase font-bold font-mono ${styles.cardTextMuted} block leading-none`}>
              {t("agent.metrics.latency")}
            </span>
            <strong className={`text-lg font-bold font-mono ${styles.cardText} block mt-1.5`}>{metrics.latencyMs}ms</strong>
          </div>
          <div className="p-3.5 rounded-xl border text-center shadow-2xs" style={{ background: 'var(--overlay, rgba(0,0,0,0.04))', borderColor: 'var(--app-border, #E2E8F0)' }}>
            <span className={`text-[9px] uppercase font-bold font-mono ${styles.cardTextMuted} block leading-none`}>
              {t("agent.metrics.tokens")}
            </span>
            <strong className={`text-lg font-bold font-mono ${styles.cardText} block mt-1.5`}>{metrics.tokensUsed.toLocaleString()}</strong>
          </div>
          <div className="p-3.5 rounded-xl border text-center shadow-2xs" style={{ background: 'var(--overlay, rgba(0,0,0,0.04))', borderColor: 'var(--app-border, #E2E8F0)' }}>
            <span className={`text-[9px] uppercase font-bold font-mono ${styles.cardTextMuted} block leading-none`}>
              {t("agent.metrics.toolCalls")}
            </span>
            <strong className="text-lg font-bold font-mono text-amber-600 block mt-1.5">{metrics.toolCalls} {g(locale, "Calls", "次")}</strong>
          </div>
        </div>
      </div>

      <div className="pt-4 mt-5 border-t text-[10px] leading-relaxed font-mono select-none" style={{ borderColor: 'var(--app-border, #E2E8F0)', color: 'var(--muted, #94A3B8)' }}>
        <span className="flex items-center gap-1.5 text-[9px] uppercase tracking-wider text-amber-600 font-bold mb-1 leading-none">
          <Zap className="w-3.5 h-3.5 text-amber-500" /> {t("agent.backend.title")}
        </span>
        <span>{t("agent.backend.desc")}</span>
      </div>
    </div>
  );
}
