/**
 * TenantManager 简易柱状图 — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import { BarChart3 } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import type { ChartDataPoint } from "./types";

// ── Simple Bar Chart ───────────────────────────────────────────

export function BarChart({
  data,
  maxValue,
  height,
}: {
  data: ChartDataPoint[];
  maxValue?: number;
  height?: number;
}) {
  const { styles } = useTheme();
  const h = height ?? 180;
  const max = maxValue ?? Math.max(...data.map((d) => d.value), 1);

  if (data.length === 0) {
    return (
      <div className={`flex items-center justify-center text-xs ${styles.cardTextMuted}`} style={{ height: h }}>
        <BarChart3 className="w-5 h-5 mr-2 opacity-40" />No data
      </div>
    );
  }

  return (
    <div className="flex items-end gap-1" style={{ height: h + 28 }}>
      {data.map((item, i) => {
        const pct = (item.value / max) * 100;
        return (
          <div key={i} className="flex-1 flex flex-col items-center gap-1 min-w-0">
            <span className="text-[9px] font-mono opacity-60">{item.value}</span>
            <div className={`w-full rounded-t ${item.color}`} style={{ height: `${Math.max(pct, 1)}%`, minHeight: 2 }} />
            <span className="text-[8px] opacity-50 truncate w-full text-center">{item.label}</span>
          </div>
        );
      })}
    </div>
  );
}

export default BarChart;
