/**
 * AgentMesh helpers — status colors, constants.
 * @license Apache-2.0
 */

export const STATUS_BAR_COLORS: Record<string, string> = {
  COMPLETED: "bg-green-500",
  RUNNING:   "bg-blue-500",
  FAILED:    "bg-red-500",
  PENDING:   "bg-zinc-300 dark:bg-zinc-600",
  CANCELLED: "bg-gray-400",
};

export const STATUS_BG_COLORS: Record<string, string> = {
  COMPLETED: "bg-green-50 border-green-200",
  RUNNING:   "bg-blue-50 border-blue-200",
  FAILED:    "bg-red-50 border-red-200",
  PENDING:   "bg-zinc-50 border-zinc-200 dark:bg-zinc-800 dark:border-zinc-700",
  CANCELLED: "bg-gray-50 border-gray-200",
};
