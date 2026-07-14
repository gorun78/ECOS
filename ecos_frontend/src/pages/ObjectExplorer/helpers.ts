/**
 * ObjectExplorer helpers — 类型常量
 */
export const FALLBACK_ENTITIES = ["Customer", "Supplier", "Invoice"];

export const EVENT_LABELS: Record<string, string> = {
  created: "创建", updated: "更新", deleted: "删除",
  status_changed: "状态变更", workflow_triggered: "流程触发",
};

export const STATUS_COLORS: Record<string, string> = {
  Draft: "bg-gray-100 text-gray-600 border-gray-300",
  Active: "bg-green-50 text-green-700 border-green-300",
  Archived: "bg-amber-50 text-amber-700 border-amber-300",
};

export const PAGE_SIZE = 20;

export interface Relation {
  id: string;
  relationCode: string;
  sourceObjectId: string;
  targetObjectId: string;
  targetEntityCode?: string;
  targetData?: Record<string, any>;
}
