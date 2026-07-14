/**
 * AgentStudio helpers — trace colors, locale helper.
 * @license Apache-2.0
 */

// ── Trace step type colour map ──
export const TRACE_COLORS: Record<string, { bg: string; border: string; icon: string; label: string }> = {
  thought:      { bg: "bg-blue-950/40",  border: "border-blue-500/50",  icon: "text-blue-400",   label: "Thought" },
  action:       { bg: "bg-amber-950/40", border: "border-amber-500/50", icon: "text-amber-400",  label: "Action" },
  tool_call:    { bg: "bg-amber-950/40", border: "border-amber-500/50", icon: "text-amber-400",  label: "Tool Call" },
  observation:  { bg: "bg-emerald-950/40", border: "border-emerald-500/50", icon: "text-emerald-400", label: "Observation" },
  evaluation:   { bg: "bg-purple-950/40", border: "border-purple-500/50", icon: "text-purple-400", label: "Evaluation" },
  result:       { bg: "bg-green-950/40", border: "border-green-500/50", icon: "text-green-400",  label: "Result" },
  plan:         { bg: "bg-cyan-950/40", border: "border-cyan-500/50", icon: "text-cyan-400",   label: "Plan" },
  decision:     { bg: "bg-rose-950/40", border: "border-rose-500/50", icon: "text-rose-400",   label: "Decision" },
  goal:         { bg: "bg-indigo-950/40", border: "border-indigo-500/50", icon: "text-indigo-400", label: "Goal" },
};

export function getTraceStyle(type: string) {
  return TRACE_COLORS[type] || TRACE_COLORS.result;
}

// ── Locale helper ──
export function g(locale: string, en: string, zh: string): string {
  return locale === "zh" ? zh : en;
}
