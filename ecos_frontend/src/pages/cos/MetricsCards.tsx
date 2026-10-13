/**
 * MetricsCards — C2EOS 大盘顶部四张全局蓝图指标卡。
 * 数据全部来自 props（真实 API 或已明示的示意数据），颜色走 useTheme tokens。
 * @license Apache-2.0
 */
import { Layers, Activity, FileCheck, Binary } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import DemoBadge from "./DemoBadge";

export default function MetricsCards({
  globalComplianceVal,
  completedDiagnostics,
  totalLayers,
  componentsCount,
  cogHealth,
  cogHealthFailed,
  degraded,
}: {
  globalComplianceVal: number;
  completedDiagnostics: Record<string, boolean>;
  totalLayers: number;
  componentsCount: number;
  cogHealth: { activeStreams?: number; status?: string; uptime?: string } | null;
  cogHealthFailed: boolean;
  degraded: boolean;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const verifiedCount = Object.values(completedDiagnostics).filter(Boolean).length;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

      {/* Compliance Ratio */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.metrics.alignmentScore")}
          </span>
          <strong className="text-xl font-extrabold tracking-tight flex items-center gap-1.5" style={{ color: "var(--cardText)" }}>
            {globalComplianceVal}%
            {degraded && <DemoBadge />}
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center font-mono border ${
          globalComplianceVal >= 98
            ? `${styles.successBg} ${styles.successText} ${styles.successBorder}`
            : `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
        }`}>
          <FileCheck className="w-5 h-5 animate-pulse" />
        </div>
      </div>

      {/* Active Framework Layers */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.metrics.layersVerified")}
          </span>
          <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
            {t("cognition.cos.metrics.layersCount", { n: verifiedCount, total: totalLayers })}
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center border ${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}>
          <Layers className="w-5 h-5" />
        </div>
      </div>

      {/* Core ECOS Components checked */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.metrics.blueprintsMonitored")}
          </span>
          <strong className="text-xl font-extrabold tracking-tight flex items-center gap-1.5" style={{ color: "var(--cardText)" }}>
            {t("cognition.cos.metrics.componentsCount", { n: componentsCount })}
            {degraded && <DemoBadge />}
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center border ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>
          <Binary className="w-5 h-5" />
        </div>
      </div>

      {/* Communication status link channels */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1 min-w-0">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.metrics.networkThreads")}
          </span>
          <strong className={`text-sm font-extrabold tracking-tight ${cogHealth?.activeStreams != null ? styles.successText : styles.cardTextMuted}`}>
            {cogHealth?.activeStreams != null
              ? t("cognition.cos.metrics.streamsActive", { n: cogHealth.activeStreams })
              : cogHealthFailed
                ? t("cognition.cos.metrics.streamsNoData")
                : "—"}
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 border ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
          <Activity className="w-5 h-5" />
        </div>
      </div>

    </div>
  );
}
