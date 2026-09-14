/**
 * MonitorPanel 子 Tab 共享类型契约
 *
 * @license Apache-2.0
 */

/** 面板 Tab 标识 (i18n key: dw.monitor.tab.{id}) */
export type MonitorTabId = 'overview' | 'logs' | 'debug' | 'preview';

/** 折叠条状态点 — 由 DebugTab 推过来 (CustomEvent 'dw-monitor-state')。 */
export interface MonitorCollapsedState {
  nodeId: number;
  total: number;
  rows: number;
  elapsedMs: number;
  status:
    | 'idle'
    | 'created'
    | 'running'
    | 'broken'
    | 'success'
    | 'failed'
    | 'stopped'
    | 'paused';
}

/** Live session 摘要 (可选, 由 PipelineFlowEditor 注入 MonitorPanel props)。 */
export interface LiveSessionSummary {
  sessionId: string;
  state: string;
  totalNodes: number;
  completedNodes: number;
  rowsProcessed: number;
  startedAt?: string;
  finishedAt?: string;
}
