/**
 * LayerDetailPanel — 选中蓝图层的详细规格与匹配代码页面板（Box A，12 列栅格中的 8 列）。
 * 由 CognitiveOperatingSystem.tsx 机械抽取（PMO-74 H6-T4）；本批次完成 token 化与 i18n，
 * 降级（示意数据）时在标题行挂 DemoBadge。
 * @license Apache-2.0
 */
import { Globe, CheckCircle } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { BlueprintLayer } from "./blueprintData";
import DemoBadge from "./DemoBadge";

export default function LayerDetailPanel({
  activeInfoLayer,
  degraded,
}: {
  activeInfoLayer: BlueprintLayer;
  degraded: boolean;
}) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`lg:col-span-8 border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 shadow-2xs space-y-4`}>
      <div className="flex items-center justify-between border-b border-dashed pb-3" style={{ borderColor: "var(--cardBorder)" }}>
        <div>
          <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block mb-1 ${styles.accentText}`}>
            {t("cognition.cos.layer.inspections")}
          </span>
          <h4 className="text-sm font-extrabold tracking-tight flex items-center gap-2" style={{ color: "var(--cardText)" }}>
            {locale === "zh" ? activeInfoLayer.nameZh : activeInfoLayer.nameEn}
            {degraded && <DemoBadge />}
          </h4>
        </div>
        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{t("cognition.cos.layer.mapping")}</span>
      </div>

      <div className="space-y-4">
        {/* Matching description */}
        <p className={`text-xs leading-relaxed ${styles.cardTextMuted}`}>
          {locale === "zh" ? activeInfoLayer.systemDescriptionZh : activeInfoLayer.systemDescriptionEn}
        </p>

        {/* Matching Code Location and Links */}
        <div className={`p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${styles.badgeBg} ${styles.cardBorder}`}>
          <div className="space-y-1">
            <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>{t("cognition.cos.layer.matchedPage")}</span>
            <strong className="text-xs font-bold uppercase tracking-tight flex items-center gap-1.5" style={{ color: "var(--cardText)" }}>
              <Globe className={`w-3.5 h-3.5 ${styles.accentText}`} />
              {locale === "zh" ? activeInfoLayer.matchedCodePage : activeInfoLayer.matchedCodePageEn}
            </strong>
          </div>
        </div>

        {/* List of sub-elements defined in the blueprint diagram nodes */}
        <div className="space-y-2">
          <span className={`text-[10px] font-mono font-bold uppercase tracking-wider block ${styles.cardTextMuted}`}>
            {t("cognition.cos.layer.blueprintItems")}
          </span>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {(locale === "zh" ? activeInfoLayer.blueprintItemsZh : activeInfoLayer.blueprintItemsEn).map((item, id) => (
              <div key={id} className={`border rounded-lg p-2.5 flex items-center gap-2 ${styles.cardBorder} ${styles.cardBg}`}>
                <CheckCircle className={`w-3.5 h-3.5 shrink-0 ${styles.accentText}`} />
                <span className="text-[11px] font-medium leading-none" style={{ color: "var(--cardText)" }}>{item}</span>
              </div>
            ))}
          </div>
        </div>

      </div>

    </div>
  );
}
