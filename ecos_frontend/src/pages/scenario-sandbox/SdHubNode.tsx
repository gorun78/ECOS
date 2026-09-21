/**
 * SdHubNode.tsx — 沙盘中央枢纽节点
 *
 * 视觉：场景 icon + 名称 + 状态徽章 + 不确定性概率条 + 认知四件套 4 路灯矩阵 + 激活心智 chip
 * Handles：4 组（上/下/左/右），top/right = target，其余 source（资源入 / 资源出 / 心智入 / insight 出）
 *
 * @license Apache-2.0
 */

import React, { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import {
  Sparkles,
  Stethoscope,
  LineChart,
  Compass,
  ShieldCheck,
  Brain,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { SdBHubData, CognitiveEp } from "./types";

// ── 认知四件套 → 图标 + t() key 映射 ─────────────────────────

const EP_CONFIG: Record<
  CognitiveEp,
  {
    icon: React.ComponentType<{ size?: number; className?: string }>;
    i18nKey: `scenario.sandbox.insight.${CognitiveEp}`;
  }
> = {
  diagnose: { icon: Stethoscope, i18nKey: "scenario.sandbox.insight.diagnose" },
  forecast: { icon: LineChart, i18nKey: "scenario.sandbox.insight.forecast" },
  simulate: { icon: Compass, i18nKey: "scenario.sandbox.insight.simulate" },
  policy: { icon: ShieldCheck, i18nKey: "scenario.sandbox.insight.policy" },
};

type EpKey = CognitiveEp;

function EpLight({
  ep,
  enabled,
  styles,
  t,
}: {
  ep: EpKey;
  enabled: boolean;
  styles: import("../../components/ThemeContext").ThemeStyles;
  t: (k: string) => string;
}) {
  const cfg = EP_CONFIG[ep];
  const Icon = cfg.icon;
  const iconCls = enabled ? styles.successText : styles.muted;
  const dotCls = enabled ? styles.successBg : styles.dangerBg;
  return (
    <div
      className={`flex flex-col items-center justify-center gap-0.5 p-1 rounded-md border text-[9px] ${styles.cardBg} ${styles.cardBorder}`}
      style={{ width: 32, height: 44 }}
    >
      <Icon size={14} className={iconCls} />
      <span className={`${iconCls} font-medium truncate w-full text-center leading-tight`}>
        {t(cfg.i18nKey)}
      </span>
      <span className={`inline-block w-1.5 h-1.5 rounded-full ${dotCls}`} />
    </div>
  );
}

function SdHubNodeComponent({ data, selected }: NodeProps<Node<SdBHubData>>) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const statusLabel =
    data.status === "DRAFT" ? t("scenario.sandbox.status.DRAFT") :
    data.status === "ACTIVE" ? t("scenario.sandbox.status.ACTIVE") :
    data.status === "SUSPENDED" ? t("scenario.sandbox.status.SUSPENDED") :
    t("scenario.sandbox.status.COMPLETED");

  const statusCls =
    data.status === "ACTIVE" ? styles.successText :
    data.status === "DRAFT" ? styles.warningText :
    data.status === "SUSPENDED" ? styles.dangerText :
    styles.infoText;

  const selectedRing = selected ? "ring-2 ring-offset-1 ring-offset-black/30" : "";
  const belief = Array.isArray(data.beliefProb) ? data.beliefProb.slice(0, 6) : [];
  const ep = data.epEnabled ?? { diagnose: false, forecast: false, simulate: false, policy: false };
  const activeMind = data.activeMindId ? `#${data.activeMindId.slice(-6)}` : null;

  return (
    <div
      className={`
        relative w-[260px] rounded-xl border-2
        ${styles.cardBg} ${styles.accentBorder}
        shadow-lg transition-all duration-150
        cursor-grab active:cursor-grabbing
        ${selectedRing}
      `}
    >
      {/* 4 组 handles：上 target（资源入）/ 下 source（mind 出）/ 左 target / 右 source（insight 出） */}
      <Handle
        type="target"
        position={Position.Top}
        id="h-top"
        className="!w-3 !h-3 !bg-black/30 !border-2 !border-white/60 !top-[-6px]"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="h-bottom"
        className="!w-3 !h-3 !bg-black/30 !border-2 !border-white/60 !bottom-[-6px]"
      />
      <Handle
        type="target"
        position={Position.Left}
        id="h-left"
        className="!w-3 !h-3 !bg-black/30 !border-2 !border-white/60 !left-[-6px]"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="h-right"
        className="!w-3 !h-3 !bg-black/30 !border-2 !border-white/60 !right-[-6px]"
      />

      {/* header ─────────────────────────── */}
      <div className="px-4 pt-3">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-lg ${styles.accentBg}`}>
            <Sparkles size={16} className={styles.inputText} />
          </div>
          <div className="min-w-0 flex-1">
            <h3 className={`text-[14px] font-bold leading-tight ${styles.cardText}`}>
              {t("scenario.sandbox.hub.scenario")}
            </h3>
            <p className={`text-[10px] font-mono mt-0.5 ${styles.cardTextMuted}`}>
              {data.scenarioId}
            </p>
          </div>
          <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${statusCls}`}>
            {statusLabel}
          </span>
        </div>
      </div>

      {/* belief 概率条 ───────────────────── */}
      <div className="px-4 mt-3">
        <div className={`text-[10px] font-semibold tracking-wide ${styles.cardTextMuted} uppercase`}>
          {t("scenario.sandbox.hub.belief")}
        </div>
        {data.beliefsVariable && (
          <div className={`text-[10px] font-mono ${styles.muted} mt-0.5 truncate`}>
            {data.beliefsVariable}
          </div>
        )}
        {belief.length > 0 && (
          <div className="flex items-end gap-1 mt-1.5">
            {belief.map((p, idx) => {
              const pct = Math.max(0, Math.min(1, p || 0));
              return (
                <div
                  key={idx}
                  className="flex-1 flex flex-col items-center gap-0.5"
                >
                  <div
                    className={`w-full rounded-sm ${styles.accentBg}`}
                    style={{ height: `${Math.max(4, Math.round(pct * 28))}px` }}
                  />
                  <span className={`text-[9px] font-mono ${styles.muted}`}>
                    {Math.round(pct * 100)}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 认知四件套 ──────────────────────── */}
      <div className="px-4 mt-3">
        <div className={`text-[10px] font-semibold tracking-wide ${styles.cardTextMuted} uppercase`}>
          {t("scenario.sandbox.hub.fourEp")}
        </div>
        <div className="grid grid-cols-4 gap-1.5 mt-1.5">
          {(Object.keys(EP_CONFIG) as EpKey[]).map((epKey) => (
            <EpLight key={epKey} ep={epKey} enabled={ep[epKey]} styles={styles} t={t} />
          ))}
        </div>
      </div>

      {/* activeMind chip ─────────────────── */}
      {activeMind ? (
        <div className="px-4 pb-3 mt-2">
          <div className={`flex items-center gap-1.5 px-2 py-1 rounded-md ${styles.badgeBg} ${styles.cardBorder}`}>
            <Brain size={11} className={styles.accentText} />
            <span className={`text-[10px] font-medium ${styles.accentText}`}>
              {t("scenario.sandbox.mind.active")} {activeMind}
            </span>
          </div>
        </div>
      ) : (
        <div className="px-4 pb-3 mt-2">
          <div className={`text-[10px] ${styles.muted}`}>{t("scenario.sandbox.mind.empty")}</div>
        </div>
      )}
    </div>
  );
}

export default memo(SdHubNodeComponent);
