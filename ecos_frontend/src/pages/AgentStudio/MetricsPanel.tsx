/**
 * MetricsPanel — Agent economics metrics board.
 * @license Apache-2.0
 */

import { Zap } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
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

  return (
    <div className="lg:col-span-1 bg-white border border-slate-200 rounded-xl p-5 flex flex-col justify-between overflow-y-auto scrollbar-thin shadow-xs">
      <div className="space-y-4">
        <div>
          <span className="text-[9px] uppercase font-bold font-mono tracking-wider text-slate-400 block leading-none">
            {t("agent.metrics.title")}
          </span>
          <h3 className="text-xs font-bold text-slate-800 mt-1.5 uppercase font-mono">
            {t("agent.metrics.subtitle")}
          </h3>
        </div>

        <div className="space-y-3.5">
          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-center shadow-2xs">
            <span className="text-[9px] uppercase font-bold font-mono text-slate-450 block leading-none">
              {t("agent.metrics.successRate")}
            </span>
            <strong className="text-2xl font-extrabold font-sans text-green-600 block mt-1.5">{metrics.successRate.toFixed(1)}%</strong>
          </div>
          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-center shadow-2xs">
            <span className="text-[9px] uppercase font-bold font-mono text-slate-450 block leading-none">
              {t("agent.metrics.latency")}
            </span>
            <strong className="text-lg font-bold font-mono text-slate-800 block mt-1.5">{metrics.latencyMs}ms</strong>
          </div>
          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-center shadow-2xs">
            <span className="text-[9px] uppercase font-bold font-mono text-slate-450 block leading-none">
              {t("agent.metrics.tokens")}
            </span>
            <strong className="text-lg font-bold font-mono text-slate-800 block mt-1.5">{metrics.tokensUsed.toLocaleString()}</strong>
          </div>
          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-center shadow-2xs">
            <span className="text-[9px] uppercase font-bold font-mono text-slate-450 block leading-none">
              {t("agent.metrics.toolCalls")}
            </span>
            <strong className="text-lg font-bold font-mono text-amber-600 block mt-1.5">{metrics.toolCalls} {g(locale, "Calls", "次")}</strong>
          </div>
        </div>
      </div>

      <div className="pt-4 mt-5 border-t border-slate-100 text-[10px] text-slate-450 leading-relaxed font-mono select-none">
        <span className="flex items-center gap-1.5 text-[9px] uppercase tracking-wider text-amber-600 font-bold mb-1 leading-none">
          <Zap className="w-3.5 h-3.5 text-amber-500" /> {t("agent.backend.title")}
        </span>
        <span>{t("agent.backend.desc")}</span>
      </div>
    </div>
  );
}
