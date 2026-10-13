/**
 * DiagnosticConsole — 蓝图层对齐一致性审计终端（Box B，12 列栅格中的 4 列）。
 * 终端面为「恒暗岛」，使用 theme 的 island* tokens；其余颜色走 useTheme tokens；文案走 t()。
 * 日志内容由父页面真实 API 审计结果驱动，本组件只负责呈现。
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
  loading,
  onRun,
}: {
  activeInfoLayer: BlueprintLayer;
  diagnosticActive: string | null;
  diagnosticLogs: string[];
  loading: boolean;
  onRun: (layerId: string) => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`lg:col-span-4 border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs flex flex-col overflow-hidden`}>
      <div className="mb-4 shrink-0 flex items-center justify-between">
        <div>
          <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.console.subtitle")}
          </span>
          <h4 className="text-xs font-extrabold tracking-tight uppercase font-mono mt-1" style={{ color: "var(--cardText)" }}>
            {t("cognition.cos.console.title")}
          </h4>
        </div>
      </div>

      <div className="space-y-4 flex-1">

        {/* Run button */}
        {diagnosticActive === activeInfoLayer.id ? (
          <div className={`w-full text-xs p-3 rounded-lg border font-mono flex items-center justify-center gap-2 font-bold uppercase leading-none h-10 select-none ${styles.badgeBg} ${styles.accentText} ${styles.accentBorder}`}>
            <RefreshCw className={`w-4 h-4 animate-spin ${styles.accentText}`} />
            <span>{t("cognition.cos.diagnostic.aligning")}</span>
          </div>
        ) : (
          <button type="button"
            onClick={() => onRun(activeInfoLayer.id)}
            disabled={loading}
            className={`w-full h-10 ${styles.accentBg} ${styles.accentHover} disabled:opacity-50 text-white rounded-lg text-xs font-bold leading-none cursor-pointer transition flex items-center justify-center gap-2 shadow-xs`}
          >
            <Zap className="w-3.5 h-3.5" />
            <span>{t("cognition.cos.diagnostic.run")}</span>
          </button>
        )}

        {/* Terminal log output — 恒暗终端岛 */}
        <div className={`${styles.islandBg} ${styles.islandBorder} border rounded-xl p-4 font-mono text-[9.5px] ${styles.islandText} whitespace-pre-wrap overflow-y-auto max-h-56 h-48 scrollbar-thin select-text`}>
          <span className={`font-bold block mb-1.5 uppercase tracking-widest text-[8px] leading-none select-none ${styles.islandMuted}`}>
            {t("cognition.cos.console.terminalLabel")}
          </span>

          {diagnosticLogs.length === 0 ? (
            <p className={`italic select-none ${styles.islandMuted}`}>
              {t("cognition.cos.diagnostic.idle")}
            </p>
          ) : (
            <div className="space-y-2">
              {diagnosticLogs.map((log, idx) => (
                <p
                  key={idx}
                  className={`leading-relaxed border-l-2 pl-2 ${idx === diagnosticLogs.length - 1 ? styles.successText : styles.islandMuted}`}
                  style={{ borderColor: "currentColor" }}
                >
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
