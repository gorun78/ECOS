/**
 * DiagnosticConsole — 蓝图层对齐一致性审计终端（Box B，12 列栅格中的 4 列）。
 * 由 CognitiveOperatingSystem.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 仅 triggerLayerCheck 改为 props 回调 onRun。
 * @license Apache-2.0
 */
import { RefreshCw, Zap } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { BlueprintLayer } from "./blueprintData";

export default function DiagnosticConsole({
  activeInfoLayer,
  diagnosticActive,
  diagnosticLogs,
  onRun,
}: {
  activeInfoLayer: BlueprintLayer;
  diagnosticActive: string | null;
  diagnosticLogs: string[];
  onRun: (layerId: string) => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`lg:col-span-4 border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs flex flex-col overflow-hidden`}>
      <div className="mb-4 shrink-0 flex items-center justify-between">
        <div>
          <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>Alignment Diagnostics</span>
          <h4 className="text-xs font-extrabold tracking-tight uppercase font-mono mt-1" style={{ color: "var(--cardText)" }}>
            Layer Integrity Auditor
          </h4>
        </div>
      </div>

      <div className="space-y-4 flex-1">

        {/* Run button */}
        {diagnosticActive === activeInfoLayer.id ? (
          <div className="w-full text-xs p-3 rounded-lg border bg-indigo-500/10 text-indigo-600 border-indigo-500/25 font-mono flex items-center justify-center gap-2 font-bold uppercase leading-none h-10 select-none">
            <RefreshCw className="w-4 h-4 animate-spin text-indigo-600 dark:text-indigo-400" />
            <span>{t("cognition.cos.diagnostic.aligning")}</span>
          </div>
        ) : (
          <button
            onClick={() => onRun(activeInfoLayer.id)}
            className="w-full h-10 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold leading-none cursor-pointer transition flex items-center justify-center gap-2 shadow-xs"
          >
            <Zap className="w-3.5 h-3.5" />
            <span>{t("cognition.cos.diagnostic.run")}</span>
          </button>
        )}

        {/* Terminal log output */}
        <div className="bg-[#020202] border border-emerald-500/20 rounded-xl p-4 font-mono text-[9.5px] text-emerald-500 whitespace-pre-wrap overflow-y-auto max-h-56 h-48 scrollbar-thin select-text">
          <span className="text-emerald-500/50 font-bold block mb-1.5 uppercase tracking-widest text-[8px] leading-none select-none">Active Auditor trace terminal:</span>

          {diagnosticLogs.length === 0 ? (
            <p className="text-emerald-700 italic select-none">
              {t("cognition.cos.diagnostic.idle")}
            </p>
          ) : (
            <div className="space-y-2">
              {diagnosticLogs.map((log, idx) => (
                <p key={idx} className="leading-relaxed border-l-2 pl-2" style={{ borderColor: idx === diagnosticLogs.length - 1 ? "#22C55E" : "rgba(16,185,129,0.3)" }}>
                  {log}
                </p>
              ))}
            </div>
          )}
        </div>

      </div>

    </div>
  );
}
