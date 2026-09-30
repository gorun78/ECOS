/**
 * taskPanelModel — TaskPanel 类型 / 常量 / 纯函数（自 TaskPanel.tsx 原样迁出，H6-T4）
 * 无任何 React 依赖，供 TaskPanel 及其子展示组件共用。
 * @license Apache-2.0
 */

import { GitBranch, Bot, Zap, SlidersHorizontal } from "lucide-react";

// ── 类型定义 ──────────────────────────────────────────────

export type TaskStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";

/** 四类分组标识 */
export type TaskCategory = "pipeline" | "agent" | "realtime" | "management";

export interface TaskItem {
  taskId: string;
  taskName: string;
  taskType: string;
  description: string;
  priority: string;
  createTime: string;
  createdBy: string;
  parameters: Record<string, any> | null;
}

export interface TaskStatusInfo {
  status: TaskStatus;
  progress: number;
  startedAt: string | null;
  completedAt: string | null;
}

export interface TaskDetail {
  task: TaskItem;
  status: TaskStatusInfo;
}

export interface TaskStats {
  total: number;
  running: number;
  pending: number;
  succeeded: number;
  failed: number;
  cancelled: number;
}

// ── 四类分组常量 ──────────────────────────────────────────

export interface CategoryConfig {
  key: TaskCategory;
  /** i18n key for the category label */
  labelKey: string;
  /** 该分类包含的任务类型 */
  types: string[];
  /** Tailwind 颜色（用于卡片左侧色条 + 标题） */
  color: string;
  /** 背景色 */
  bg: string;
  /** 文字色 */
  textColor: string;
  /** Badge 背景 + 文字 */
  badgeBg: string;
  badgeText: string;
  /** lucide-react 图标组件 */
  Icon: typeof GitBranch;
}

export const CATEGORIES: CategoryConfig[] = [
  {
    key: "pipeline",
    labelKey: "taskPanel.category.pipeline",
    types: ["DORIS_SQL", "ETL", "DATA_SYNC", "PIPELINE"],
    color: "border-l-blue-500",
    bg: "bg-blue-50/60 dark:bg-blue-900/20",
    textColor: "text-blue-600 dark:text-blue-400",
    badgeBg: "bg-blue-100 dark:bg-blue-900/40",
    badgeText: "text-blue-700 dark:text-blue-300",
    Icon: GitBranch,
  },
  {
    key: "agent",
    labelKey: "taskPanel.category.agent",
    types: ["AGENT", "AI_AGENT", "LLM_TASK", "KG_SYNC"],
    color: "border-l-purple-500",
    bg: "bg-purple-50/60 dark:bg-purple-900/20",
    textColor: "text-purple-600 dark:text-purple-400",
    badgeBg: "bg-purple-100 dark:bg-purple-900/40",
    badgeText: "text-purple-700 dark:text-purple-300",
    Icon: Bot,
  },
  {
    key: "realtime",
    labelKey: "taskPanel.category.realtime",
    types: ["REALTIME", "STREAMING", "MONITOR", "ALERT", "TELEMETRY"],
    color: "border-l-green-500",
    bg: "bg-green-50/60 dark:bg-green-900/20",
    textColor: "text-green-600 dark:text-green-400",
    badgeBg: "bg-green-100 dark:bg-green-900/40",
    badgeText: "text-green-700 dark:text-green-300",
    Icon: Zap,
  },
  {
    key: "management",
    labelKey: "taskPanel.category.management",
    types: ["DATA_QUALITY", "REPORT", "MAINTENANCE", "BACKUP", "CONFIG", "ADMIN"],
    color: "border-l-gray-400",
    bg: "bg-gray-50/60 dark:bg-gray-800/40",
    textColor: "text-gray-600 dark:text-gray-400",
    badgeBg: "bg-gray-100 dark:bg-gray-700",
    badgeText: "text-gray-700 dark:text-gray-300",
    Icon: SlidersHorizontal,
  },
];

/** taskType → TaskCategory 快速查找表 */
export const TYPE_TO_CATEGORY: Record<string, TaskCategory> = {};
for (const cat of CATEGORIES) {
  for (const t of cat.types) {
    TYPE_TO_CATEGORY[t] = cat.key;
    // 同时注册小写变体
    TYPE_TO_CATEGORY[t.toLowerCase()] = cat.key;
  }
}

/** 根据 taskType 获取分类配置，未匹配返回 null */
export function getCategory(taskType: string): CategoryConfig | null {
  const key = TYPE_TO_CATEGORY[taskType] || TYPE_TO_CATEGORY[taskType.toUpperCase()];
  if (!key) return null;
  return CATEGORIES.find(c => c.key === key) || null;
}

// ── 状态颜色映射 ──────────────────────────────────────────

export const STATUS_COLORS: Record<TaskStatus, { bg: string; text: string; dot: string }> = {
  PENDING:   { bg: "bg-gray-100 dark:bg-gray-800", text: "text-gray-700 dark:text-gray-300", dot: "bg-gray-400" },
  RUNNING:   { bg: "bg-blue-100 dark:bg-blue-900/40",  text: "text-blue-700 dark:text-blue-300",  dot: "bg-blue-500" },
  SUCCEEDED: { bg: "bg-green-100 dark:bg-green-900/40",text: "text-green-700 dark:text-green-300",dot: "bg-green-500" },
  FAILED:    { bg: "bg-red-100 dark:bg-red-900/40",   text: "text-red-700 dark:text-red-300",   dot: "bg-red-500" },
  CANCELLED: { bg: "bg-yellow-100 dark:bg-yellow-900/40",text: "text-yellow-700 dark:text-yellow-300",dot: "bg-yellow-500" },
};

export const STATUS_LABEL_KEYS: Record<TaskStatus, string> = {
  PENDING: "taskPanel.status.pending", RUNNING: "taskPanel.status.running", SUCCEEDED: "taskPanel.status.succeeded", FAILED: "taskPanel.status.failed", CANCELLED: "taskPanel.status.cancelled",
};

export const PRIORITY_LABEL_KEYS: Record<string, string> = {
  HIGH: "taskPanel.priority.high", MEDIUM: "taskPanel.priority.medium", LOW: "taskPanel.priority.low", CRITICAL: "taskPanel.priority.critical",
};

// ── 纯函数辅助 ────────────────────────────────────────────

export const formatTime = (ts: string | null, localeHint?: string) => {
  if (!ts) return "—";
  try {
    // Use the active UI locale so timestamps follow the language toggle
    const loc = localeHint || (typeof window !== "undefined" ? (localStorage.getItem("ecos_locale") === "en" ? "en-US" : "zh-CN") : "zh-CN");
    return new Date(ts).toLocaleString(loc);
  } catch { return ts; }
};
