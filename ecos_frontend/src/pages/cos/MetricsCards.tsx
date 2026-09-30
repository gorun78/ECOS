/**
 * MetricsCards — C2EOS 大盘顶部四张全局蓝图指标卡。
 * 由 CognitiveOperatingSystem.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 数据经 props 传入（globalComplianceVal / completedDiagnostics / cogHealth），locale 与主题在组件内自取。
 * @license Apache-2.0
 */
import { Layers, Activity, FileCheck, Binary } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function MetricsCards({
  globalComplianceVal,
  completedDiagnostics,
  cogHealth,
}: {
  globalComplianceVal: number;
  completedDiagnostics: Record<string, boolean>;
  cogHealth: { activeStreams?: number; status?: string; uptime?: string } | null;
}) {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

      {/* Compliance Ratio */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {locale === "zh" ? "蓝图一致性评级" : "Blueprint Alignment Score"}
          </span>
          <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
            {globalComplianceVal}%
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center font-mono ${
          globalComplianceVal >= 98 
            ? "bg-emerald-500/10 text-emerald-500 border border-emerald-500/25"
            : "bg-amber-500/10 text-amber-500 border border-amber-500/25"
        }`}>
          <FileCheck className="w-5 h-5 animate-pulse" />
        </div>
      </div>

      {/* Active Framework Layers */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {locale === "zh" ? "已对齐验证架构层" : "Architecture Layers Verified"}
          </span>
          <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
            {Object.values(completedDiagnostics).filter(Boolean).length} / 6 Layers
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-indigo-500 bg-indigo-500/10 border border-indigo-500/25`}>
          <Layers className="w-5 h-5" />
        </div>
      </div>

      {/* Core ECOS Components checked */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {locale === "zh" ? "系统注册功能组件" : "Framework Blueprints Monitored"}
          </span>
          <strong className="text-xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>
            36 Components
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-blue-500 bg-blue-500/10 border border-blue-500/25`}>
          <Binary className="w-5 h-5" />
        </div>
      </div>

      {/* Communication status link channels */}
      <div className={`border rounded-xl p-4 flex items-center justify-between shadow-2xs ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="space-y-1">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {locale === "zh" ? "认知系统链路负载" : "Cognitive Network Threads"}
          </span>
          <strong className="text-xl font-extrabold tracking-tight text-emerald-500">
            {cogHealth?.activeStreams != null ? `${cogHealth.activeStreams} Active Streams` : '-- Active Streams'}
          </strong>
        </div>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-emerald-500 bg-emerald-500/10 border border-emerald-500/25`}>
          <Activity className="w-5 h-5" />
        </div>
      </div>

    </div>
  );
}
