import React from "react";
import {
  Target, Flag, Building2, Gauge, Workflow, Bot,
} from "lucide-react";
import type { Goal } from "../../types";

// ── Local helpers ──────────────────────────────

/** Compute percentage from current/target values (handle string or number) */
export function computeProgress(g: Goal): number {
  const cur = typeof g.current_value === "number" ? g.current_value
    : (typeof g.currentValue === "number" ? g.currentValue : parseFloat(String(g.currentValue || "0")));
  const tgt = typeof g.target_value === "number" ? g.target_value
    : (typeof g.targetValue === "number" ? g.targetValue : parseFloat(String(g.targetValue || "1")));
  if (!tgt || isNaN(cur) || isNaN(tgt)) return 0;
  return Math.min(100, Math.max(0, (cur / tgt) * 100));
}

/** Traffic-light color based on percentage */
export function trafficColor(pct: number): string {
  if (pct >= 80) return "text-emerald-400";
  if (pct >= 50) return "text-amber-400";
  return "text-red-400";
}

export function trafficBg(pct: number): string {
  if (pct >= 80) return "bg-emerald-400";
  if (pct >= 50) return "bg-amber-400";
  return "bg-red-400";
}

export function trafficBorder(pct: number): string {
  if (pct >= 80) return "border-emerald-500/30";
  if (pct >= 50) return "border-amber-500/30";
  return "border-red-500/30";
}

/** Goal-type badge style */
export const GOAL_TYPE_CONFIG: Record<string, { icon: React.FC<{ className?: string }>; label: string; bg: string; text: string; border: string }> = {
  STRATEGIC: { icon: Flag, label: "战略", bg: "bg-indigo-500/10", text: "text-indigo-400", border: "border-indigo-500/30" },
  OKR:       { icon: Building2, label: "OKR", bg: "bg-violet-500/10", text: "text-violet-400", border: "border-violet-500/30" },
  KPI:       { icon: Gauge, label: "KPI", bg: "bg-cyan-500/10", text: "text-cyan-400", border: "border-cyan-500/30" },
  WORKFLOW:  { icon: Workflow, label: "工作流", bg: "bg-amber-500/10", text: "text-amber-400", border: "border-amber-500/30" },
  AGENT:     { icon: Bot, label: "Agent", bg: "bg-emerald-500/10", text: "text-emerald-400", border: "border-emerald-500/30" },
};

export function getGoalTypeConfig(gt?: string) {
  return GOAL_TYPE_CONFIG[gt || ""] || { icon: Target, label: gt || "目标", bg: "bg-gray-500/10", text: "text-gray-400", border: "border-gray-500/30" };
}
