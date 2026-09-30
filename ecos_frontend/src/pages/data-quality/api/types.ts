/**
 * PMO-48 — Data Quality governance API layer · 类型定义
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * R-3 ② (详细设计-02 §F02-08 规格 6 + PMO 裁决): 治理端点 404 回落 legacy 只读端点
 * 是保留行为（V166 迁移完成前），但**必须非静默** —— 触发的每一次回落都要：
 *   1. `console.warn` 一条带 endpoint + traceId 的日志；
 *   2. push 到 `window.__ecosFallbacks` 供遥测/横幅/测试断言。
 *
 * 用户可见提示走 `useLanguage()`/`t(...)` (见 RuleCenterTab 已用的 dw.dqRule.* 键)；
 * 本 hook 只记内部遥测数据，不含用户可见字符串。
 */
export interface DqFallbackRecord {
  at: number;
  layer: "governance->legacy";
  path: string;
  status?: number;
  traceId?: string;
  note: string;
}

/** T3 规则 VO — 与后端 DqRuleVO 字段对齐(驼峰) */
export interface DqRuleVO {
  id: string;
  ruleName: string;
  ruleCode: string;
  category: string;
  domain: string;
  ruleType: string;
  severity: string;
  targetKind: string;
  targetTable?: string;
  targetField?: string;
  status: string;
  version?: number;
  parametersJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** T3 规则详情 — 列表 VO 字段 + 扩展 */
export interface DqRuleDetailVO extends DqRuleVO {
  description?: string;
  owner?: string;
  parameters?: Record<string, unknown>;
  history?: Array<{ version: number; updatedAt: string; changedBy?: string; diff?: string }>;
}

/** T3 维度注册表 VO — 与后端 DqDimensionRegistryVO 字段对齐 */
export interface DqDimensionRegistryVO {
  dimension: string;
  name: string;
  ruleTypes: string[];
  defaultWeight: number;
  description: string;
}

/** T6 自检结果 VO — 占位接口, T6 落地后按实际后端结构对齐 */
export interface DqSelfCheckResult {
  success: boolean;
  total: number;
  passed: number;
  failed: number;
  checks: Array<{ name: string; passed: boolean; message?: string }>;
  execAt?: string;
}

/** 规则状态机枚举 — 与后端 DqRule status 字段对齐 */
export type DqRuleStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'ACTIVE'
  | 'DEPRECATED'
  | 'SUPERSEDED'
  | 'REJECTED'
  | 'DISABLED';

/** 规则创建/更新 DTO（后端 T7c DqRuleDTO @Data） */
export interface DqRuleDTO {
  ruleName?: string;
  ruleCode?: string;
  category?: string;
  domain?: string;
  ruleType?: string;
  severity?: string;
  targetKind?: string;
  targetTable?: string;
  targetField?: string;
  parameters?: Record<string, unknown>;
}

/** 状态转换请求体 DqRuleActionDTO — 按动作拆分字段 */
export interface DqRuleActionDTO {
  submitter?: string;
  approver?: string;
  rejector?: string;
  operator?: string;
  reason?: string;
}

/** 版本快照 VO — GET /rules/{id}/versions 返回 */
export interface DqRuleVersionVO {
  ruleId: string;
  version: number;
  snapshotJson: string;
  changedBy: string;
  changedAt: string;
  changeNote?: string;
}

/** 状态动作类型 */
export type DqRuleActionType = 'submit' | 'approve' | 'reject' | 'deprecate' | 'supersede' | 'disable';

/** T8 资产评分 VO — 与后端 DqAssetScoreVO 字段对齐(驼峰) */
export interface DqAssetScoreVO {
  assetType: string;
  assetId: string;
  /** 加权汇总分 0.0-1.0 */
  rolledScore: number;
  /** 等级 A/B/C/D/F (后端按阈值表 DqDimension#gradeOf 算) */
  grade: string;
  /** 6 维分数 (dimension name → 0.0-1.0) */
  dimensionScores: Record<string, number>;
  lastEvaluatedAt?: string;
  sampleSize?: number;
  distinctRuleCount?: number;
  weightSum?: number;
}

/** T8 评分趋势 VO — 按天 × 维度行 */
export interface DqScoreTrendVO {
  /** ISO 日期 (YYYY-MM-DD) */
  date: string;
  /** 维度 name 大写 (COMPLETENESS/ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY) */
  dimension: string;
  /** 当日 0.0-1.0 */
  scoreValue: number;
  hits?: number;
}

/** T8 系统健康度 VO */
export interface DqScoreSystemVO {
  overallScore: number;
  count: number;
  grade: string;
  perDimension: Record<string, number>;
}

/* ── 监控调度 / 告警中心 / 工单中心 ───────────────────────────── */

/** T11 调度计划 VO — 与后端 DqScheduleVO 字段对齐（驼峰） */
export interface DqScheduleVO {
  id: string;
  name: string;
  triggerType: string;
  cronExpression?: string;
  eventType?: string;
  ruleIds: string[];
  scopeType: string;
  scopeId: string;
  enabled: boolean;
  maxRuntimeSeconds: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** T12 告警记录 VO — 与后端 DqAlertVO 字段对齐（驼峰） */
export interface DqAlertVO {
  id: string;
  ruleId: string;
  alertLevel: string;
  alertType: string;
  assetId?: string;
  assetName?: string;
  ruleName: string;
  message: string;
  payload?: Record<string, unknown>;
  status: string;
  escalatedTo?: string;
  notifyCount: number;
  lastNotifyAt?: string;
  ackBy?: string;
  ackAt?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolvedNote?: string;
  createdAt: string;
}

/** T12/T13 工单 VO — 与后端 DqWorkOrderVO 字段对齐（驼峰） */
export interface DqWorkOrderVO {
  id: string;
  orderNo: string;
  alertId?: string;
  ruleId: string;
  assetId?: string;
  title: string;
  description: string;
  status: string;
  handlingMode: string;
  severity: string;
  assignedTo?: string;
  assignedAt?: string;
  repairAction?: string;
  repairStatus?: string;
  repairLog?: string;
  verifiedBy?: string;
  verifiedAt?: string;
  verifyPass?: boolean;
  verifyNote?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolutionNote?: string;
  rcaResult?: string;
  rcaConfidence?: number;
  rcaAnalyzedAt?: string;
  retryCount: number;
  createdAt: string;
  updatedAt?: string;
  closedAt?: string;
}

/* ── DQ 报告中心 ─────────────────────────────────────────────── */

/** T15 报告 VO — 与后端 DqReportVO 字段对齐（驼峰） */
export interface DqReportVO {
  id: string;
  reportType: string;   // DAILY / WEEKLY / MONTHLY
  scope: string;        // ALL / NATIVE / OFI / DOMAIN:xxx
  startDate: string;
  endDate: string;
  payloadHtml?: string; // 详情 HTML（内嵌 TEXT）
  pdfObjectKey?: string;
  llmSummary?: string;
  rowCount?: number;
  alertCount?: number;
  avgScore?: number;    // 0.0-1.0
  createdBy?: string;
  createdAt?: string;
}

/** T15 报告列表分页查询 */
export interface DqReportQuery {
  reportType?: string;
  scope?: string;
  pageNum?: number;
  pageSize?: number;
}

/** T15 分页结果 — 与 PageResult 对齐 */
export interface DqReportPageResult {
  data: DqReportVO[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** T15 报告类型（小写传后端；后端 switch 大小写不敏感） */
export type DqReportType = 'daily' | 'weekly' | 'monthly';
