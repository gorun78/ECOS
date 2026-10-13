/**
 * BlueprintLayerStack — ECOS 六层拓扑核心设计蓝图的可交互层级堆叠图。
 * 颜色全部走 useTheme tokens；降级（示意数据）时挂 DemoBadge。
 * @license Apache-2.0
 */
import { Layers } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { BlueprintLayer } from "./blueprintData";
import DemoBadge from "./DemoBadge";

export default function BlueprintLayerStack({
  blueprintLayers,
  selectedInfoLayerId,
  completedDiagnostics,
  degraded,
  onSelect,
}: {
  blueprintLayers: BlueprintLayer[];
  selectedInfoLayerId: string | null;
  completedDiagnostics: Record<string, boolean>;
  degraded: boolean;
  onSelect: (layerId: string) => void;
}) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs space-y-4`}>
      <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
        <h3 className="font-bold text-sm tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
          <Layers className={`w-4 h-4 ${styles.accentText}`} />
          <span>{t("cognition.cos.stack.title")}</span>
          {degraded && <DemoBadge />}
        </h3>
        <span className={`font-mono text-[9px] uppercase tracking-wider ${styles.cardTextMuted}`}>{t("cognition.cos.stack.subtitle")}</span>
      </div>

      {/* Stack representation mimicking the actual image with responsive hover points */}
      <div className="flex flex-col gap-2 pt-2">

        {/* Horizontal layers flow */}
        {blueprintLayers.map((layer, index) => {
          const isSelected = selectedInfoLayerId === layer.id;
          const isFinished = completedDiagnostics[layer.id];
          const IconComponent = layer.icon;

          return (
            <div
              key={layer.id}
              onClick={() => onSelect(layer.id)}
              className={`border rounded-xl p-3 flex flex-col md:flex-row md:items-center justify-between gap-3 cursor-pointer transition-all duration-150 relative overflow-hidden select-none ${
                isSelected
                  ? `${styles.badgeBg} ${styles.accentBorder} shadow-2xs font-semibold`
                  : `${styles.sidebarHoverBg} ${styles.cardBg} ${styles.cardBorder}`
              }`}
            >
              {/* Interactive depth progress layer bar */}
              <div className={`absolute top-0 bottom-0 left-0 ${styles.badgeBg} opacity-40`} style={{ width: `${layer.coverage}%` }}></div>

              <div className="flex items-start md:items-center gap-3 relative z-10 flex-grow min-w-0">
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border ${
                  isSelected ? `${styles.accentBg} text-white ${styles.accentBorder}` : `${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`
                }`}>
                  <IconComponent className="w-4 h-4" />
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`text-[10px] font-mono font-extrabold uppercase ${styles.accentText}`}>
                      {t("cognition.cos.stack.phase", { n: 15 - index * 2 })}
                    </span>
                    <span className="text-xs font-bold truncate leading-none" style={{ color: "var(--cardText)" }}>
                      {locale === "zh" ? layer.nameZh.split(" (")[0] : layer.nameEn}
                    </span>
                    {isFinished && (
                      <span className={`text-[8.5px] px-1.5 py-0.5 rounded-sm font-mono font-bold uppercase leading-none border ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
                        {t("cognition.cos.stack.verified")}
                      </span>
                    )}
                  </div>

                  <p className={`text-[10.5px] mt-1.5 truncate leading-none ${styles.cardTextMuted}`}>
                    {locale === "zh" ? layer.coverLabelZh : layer.coverLabelEn}
                  </p>
                </div>
              </div>

              {/* Coverage Progress Indicator */}
              <div className="flex items-center gap-4 relative z-10 shrink-0 select-none">
                <div className="text-right font-mono">
                  <div className="text-xs font-extrabold" style={{ color: "var(--cardText)" }}>{layer.coverage}%</div>
                  <div className={`text-[8px] uppercase tracking-wider ${styles.cardTextMuted}`}>{t("cognition.cos.stack.alignment")}</div>
                </div>
                <span
                  className={`w-1.5 h-1.5 rounded-full ${isFinished ? styles.successText : styles.warningText}`}
                  style={{ backgroundColor: "currentColor" }}
                />
              </div>

            </div>
          );
        })}

      </div>
    </div>
  );
}
