/**
 * WorkflowDesigner helpers — Node type definitions, icons, constants.
 * @license Apache-2.0
 */

import { User, Bot, Split, Flag, Circle } from "lucide-react";

// ── Node type definitions (colors/icons only — labels via t()) ──
export const NODE_TYPES: Record<string, { icon: any; color: string; dot: string }> = {
  start: { icon: Flag, color: "border-green-400 bg-green-50", dot: "bg-green-500" },
  end: { icon: Circle, color: "border-red-400 bg-red-50", dot: "bg-red-500" },
  human_task: { icon: User, color: "border-blue-400 bg-blue-50", dot: "bg-blue-500" },
  agent_node: { icon: Bot, color: "border-purple-400 bg-purple-50", dot: "bg-purple-500" },
  condition_gateway: { icon: Split, color: "border-amber-400 bg-amber-50", dot: "bg-amber-500" },
};

// ── MiniMap color mapping ──
export const MINIMAP_COLORS: Record<string, string> = {
  start: "#4ADE80", end: "#F87171", human_task: "#60A5FA",
  agent_node: "#A78BFA", condition_gateway: "#FBBF24",
};

export const MINIMAP_FALLBACK = "#94A3B8";

// ── BoxIcon fallback ──
export function BoxIcon() {
  return <div className="w-4 h-4 bg-slate-300 rounded" />;
}
