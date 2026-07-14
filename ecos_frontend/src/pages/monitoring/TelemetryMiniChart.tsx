/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { useLanguage } from "../../components/LanguageContext";
import { TwinTelemetry } from "../../api";

// ── SVG Telemetry Mini Chart ───────────────────────────────
export default function TelemetryMiniChart({ data, color = "#818CF8", height = 60 }: { data: TwinTelemetry[]; color?: string; height?: number }) {
  const { locale } = useLanguage();
  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

  if (data.length < 2) {
    return (
      <div className="flex items-center justify-center text-[10px] text-slate-400" style={{ height }}>
        {tl("数据不足，至少需要2个数据点", "Insufficient data, need at least 2 data points")}
      </div>
    );
  }

  const padding = 4;
  const w = 300;
  const h = height;
  const chartW = w - padding * 2;
  const chartH = h - padding * 2;

  const values = data.map(d => d.value);
  const minVal = Math.min(...values);
  const maxVal = Math.max(...values);
  const range = maxVal - minVal || 1;

  const points = data.map((d, i) => {
    const x = padding + (i / (data.length - 1)) * chartW;
    const y = padding + chartH - ((d.value - minVal) / range) * chartH;
    return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
  }).join(' ');

  return (
    <svg width="100%" viewBox={`0 0 ${w} ${h}`} className="overflow-visible" style={{ height }}>
      <path d={points} fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      {data.map((d, i) => {
        const x = padding + (i / (data.length - 1)) * chartW;
        const y = padding + chartH - ((d.value - minVal) / range) * chartH;
        return (
          <circle key={i} cx={x.toFixed(1)} cy={y.toFixed(1)} r="2" fill={color} opacity="0.7" />
        );
      })}
    </svg>
  );
}
