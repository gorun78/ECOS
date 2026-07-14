/**
 * AgentMesh helpers — status colors, constants.
 * @license Apache-2.0
 */

export const STATUS_BAR_COLORS: Record<string, string> = {
  COMPLETED: "bg-green-500",
  RUNNING:   "bg-blue-500",
  FAILED:    "bg-red-500",
  PENDING:   "bg-slate-300 dark:bg-slate-600",
  CANCELLED: "bg-gray-400",
};

export const STATUS_BG_COLORS: Record<string, string> = {
  COMPLETED: "bg-green-50 dark:bg-green-950/20 border-green-200 dark:border-green-800",
  RUNNING:   "bg-blue-50 dark:bg-blue-950/20 border-blue-200 dark:border-blue-800",
  FAILED:    "bg-red-50 dark:bg-red-950/20 border-red-200 dark:border-red-800",
  PENDING:   "bg-slate-50 dark:bg-slate-900/50 border-slate-200 dark:border-slate-700",
  CANCELLED: "bg-gray-50 dark:bg-gray-900/30 border-gray-200 dark:border-gray-700",
};
