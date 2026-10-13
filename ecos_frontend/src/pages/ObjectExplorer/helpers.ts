/**
 * ObjectExplorer helpers — 类型常量
 * FALLBACK_ENTITIES 已移除：实体列表拉取失败时必须呈现错误态+重试，禁止演示兜底实体（无假真理）。
 */
import type { ThemeStyles } from "../../components/ThemeContext";

/** 事件类型 → dw 命名空间双语键（展示端用 t() 解析，未知类型回退原始 eventType） */
export const EVENT_LABEL_KEYS: Record<string, string> = {
  created: "dw.objectExplorer.event.created",
  updated: "dw.objectExplorer.event.updated",
  deleted: "dw.objectExplorer.event.deleted",
  status_changed: "dw.objectExplorer.event.statusChanged",
  workflow_triggered: "dw.objectExplorer.event.workflowTriggered",
};

/**
 * 状态徽章类名：按主题语义 token 映射（裸色板 bg-green-50/text-gray-600 等禁用）。
 * 未知状态回退中性 token，不再伪装成 Draft 样式。
 */
export function statusBadgeClasses(styles: ThemeStyles, status?: string): string {
  switch (status) {
    case "Active":
      return `${styles.successBg} ${styles.successText} border-current`;
    case "Archived":
      return `${styles.warningBg} ${styles.warningText} border-current`;
    default:
      return `${styles.sidebarBg} ${styles.muted} border-current`;
  }
}

/** 时间线事件节点圆点类名（语义 token，替代裸色板） */
export function timelineDotClasses(styles: ThemeStyles, eventType: string): string {
  switch (eventType) {
    case "created":
      return `${styles.successBg} border-current`;
    case "deleted":
      return `${styles.dangerBg} border-current`;
    case "status_changed":
      return `${styles.infoBg} border-current`;
    default:
      return `${styles.appBg} ${styles.appBorder} border`;
  }
}

export const PAGE_SIZE = 20;

export interface Relation {
  id: string;
  relationCode: string;
  sourceObjectId: string;
  targetObjectId: string;
  targetEntityCode?: string;
  targetData?: { name?: string; code?: string };
}
