/**
 * SdInsightCard.tsx — 认知四件套结果卡片节点
 *
 * 视觉：4 件套对应 icon + ep 名称 + payloadHash 缩略显示
 * ok=false 时边框为 danger 色；ok=true 时 success 色。
 * 本期 P3b stub：runInsight 触发后才填充 summary / hash。
 *
 * @license Apache-2.0
 */

import React, { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import {
  Stethoscope,
  LineChart,
  Compass,
  ShieldCheck,
  Check,
  XCircle,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { SdBInsightData, CognitiveEp } from "./types";

// ── 4 件套 → icon + t() key 映射 ─────────────────────────

const EP_META: Record<
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

function SdInsightCardComponent({ data, selected }: NodeProps<Node<SdBInsightData>>) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const meta = EP_META[data.ep] ?? EP_META.diagnose;
  const EpIcon = meta.icon;
  const ok = data.ok;
  const cardBorderCls = ok ? styles.successBorder : styles.dangerBorder;
  const borderLeft = ok ? `${cardBorderCls}` : styles.dangerBorder;

  const selectedRing = selected ? "ring-2 ring-offset-1 ring-offset-black/30" : "";
  const shortHash = data.payloadHash
    ? data.payloadHash.length > 10
      ? `${data.payloadHash.slice(0, 6)}…${data.payloadHash.slice(-4)}`
      : data.payloadHash
    : null;

  return (
    <div
      className={`
        relative w-[200px] rounded-lg border-l-4
        ${styles.cardBg} ${styles.cardBorder} ${borderLeft}
        shadow-md transition-all duration-150
        cursor-grab active:cursor-grabbing
        ${selectedRing}
      `}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!w-2.5 !h-2.5 !bg-black/30 !border-2 !border-white/60 !top-[-5px]"
      />

      {/* header */}
      <div className="px-3 py-2.5 flex items-start gap-2">
        <div
          className={`shrink-0 p-1.5 rounded-md ${ok ? styles.successBg : styles.dangerBg}`}
        >
          <EpIcon
            size={14}
            className={ok ? styles.successText : styles.dangerText}
          />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className={`text-[12px] font-bold leading-tight ${styles.cardText}`}>
            {t(meta.i18nKey)}
          </h3>
          {shortHash && (
            <p
              className={`text-[10px] font-mono mt-0.5 truncate ${styles.muted}`}
              title={data.payloadHash}
            >
              {shortHash}
            </p>
          )}
        </div>
        {ok ? (
          <Check size={13} className={`shrink-0 ${styles.successText}`} />
        ) : (
          <XCircle size={13} className={`shrink-0 ${styles.dangerText}`} />
        )}
      </div>

      {/* summary */}
      <div
        className={`px-3 pb-2.5 text-[10px] leading-normal line-clamp-3 ${styles.cardTextMuted}`}
      >
        {data.summary || t("scenario.sandbox.insight.stub")}
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-2.5 !h-2.5 !bg-black/30 !border-2 !border-white/60 !bottom-[-5px]"
      />
    </div>
  );
}

export default memo(SdInsightCardComponent);
