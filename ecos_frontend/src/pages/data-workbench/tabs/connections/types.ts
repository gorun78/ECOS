/* Extracted from ConnectionsTab.tsx — 连接 Tab 内部共享的 state 形状定义 */

/** 同步采集任务状态（CollectProgressPanel / 轮询用；原 ConnectionsTab 内联形状） */
export interface CollectStatusState {
  status?: string; progress?: number; message?: string; statusMessage?: string;
  processedRecords?: number; totalRecords?: number;
  collectedTables?: number; totalTables?: number; tablesOk?: number; tablesFailed?: number; errorMessage?: string;
}

/** 活跃采集任务（对接异步任务中心） */
export interface ActiveCollectTask {
  taskId: string; status: string; progress: number; startTime?: string;
}

/** 采集差异记录（采集完成后展示最近 N 次 diff 摘要） */
export interface CollectDiffRecord {
  collectedAt: string; taskId?: string; diffSummary?: string; diffMarkdown?: string;
  gitCommit?: string; tablesTotal?: number;
}
