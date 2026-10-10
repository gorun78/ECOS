/**
 * 本体版本时间线 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Version Timeline（T3）—— 版本列表/详情/两版本 diff。
 * 端点前缀：/api/v1/ecos/versions + /api/v1/ontology/versions/diff。
 * 注：平台级 Git 版本单通道见 services/gitService.ts（规范 §十一.1）；
 * 本组端点属本体版本查询面（ecos_ontology_version 域），非 /api/v1/ecos/git 面。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";

// ================================================================
// 版本管理 (Version Timeline) — T3
// ================================================================

const VERSION_BASE = "/api/v1/ecos/versions";

/** 版本列表项 — 对齐后端 OntologyVersionVO (T16-5 简化端点强类型化) */
export interface VersionItem {
  /** 版本 ID（ver 前缀，diff 端点定位键） */
  id: string;
  /** 版本号（如 1.0.0） */
  versionNo: string;
  /** 状态（Draft / Published / Deprecated） */
  status?: string;
  /** 发布人 */
  publisher?: string;
  /** 发布时间 ISO 字符串 */
  publishedAt?: string;
  /** 创建时间 ISO 字符串 */
  createdAt: string;
}

/**
 * 版本 diff 条目 — 对齐后端 OntologyVersionDiffVO 动态条目契约（T16-4 强类型容器，
 * added/removed/modified 元素保持 {field, value[, newValue]} 动态结构豁免）。
 */
export interface VersionDiffEntry {
  /** 变更的快照顶层字段名（entities / properties / relationships / actions） */
  field: string;
  /** 旧值（added 条目 = 新值；removed 条目 = 旧值；快照动态结构，Object 映射豁免） */
  value: unknown;
  /** 新值（仅 modified 条目填充） */
  newValue?: unknown;
}

/**
 * 版本差异响应 — 对齐后端 OntologyVersionDiffVO 字段（T16-4）。
 * 正常分支: version1/version2 + snapshot1/snapshot2 + added/removed/modified；
 * 空 diff 分支: version1Id/version2Id + 三空列表（NON_NULL 按需输出，故均可选）。
 */
export interface VersionDiff {
  version1?: string;
  version2?: string;
  version1Id?: string;
  version2Id?: string;
  /** 第一版本快照（动态 JSON，T16-4 豁免） */
  snapshot1?: unknown;
  /** 第二版本快照（动态 JSON，T16-4 豁免） */
  snapshot2?: unknown;
  added?: VersionDiffEntry[];
  removed?: VersionDiffEntry[];
  modified?: VersionDiffEntry[];
}

/** 与前一版本 diff 响应 — 对齐后端 OntologyVersionPreviousDiffVO (T16-5) */
export interface VersionPreviousDiff {
  currentVersion: string;
  currentSnapshot?: unknown;
  previousVersion?: string;
  previousSnapshot?: unknown;
}

/**
 * 获取版本历史列表（跨全部 ontology，按创建时间由新到旧）
 * GET /api/v1/ecos/versions
 * 后端返回 OntologyVersionVO 列表（T16-5 强类型）；
 * domainCode 参数按 API 只增不改原则保留，当前后端未消费，
 * 前端如需按 ontology 过滤应在客户端本地过滤。
 */
export async function fetchVersions(domainCode: string): Promise<VersionItem[]> {
  return apiFetchData<VersionItem[]>(
    `${VERSION_BASE}?domainCode=${encodeURIComponent(domainCode)}`
  );
}

/**
 * 获取版本详情（含全量 snapshot）
 * GET /api/v1/ecos/versions/{id}
 * 用于版本 diff 对比：前后端契约铁律 — 列表摘要不渲染编辑态，
 * 对比前必须拉取两侧完整快照。
 */
export async function fetchVersionDetail(id: string): Promise<VersionItem & { snapshot?: unknown }> {
  return apiFetchData<VersionItem & { snapshot?: unknown }>(
    `${VERSION_BASE}/${encodeURIComponent(id)}`
  );
}

/**
 * 获取两个版本之间的 diff（T11 联调真实端点 — 后端 T16-4 强类型）
 * GET /api/v1/ontology/versions/diff?v1={versionId1}&v2={versionId2}
 * 后端契约: VersionDiffController（ontology-engine-impl，PMO-39 批次2 T3），
 * v1/v2 为版本 ID（ver 前缀），非版本号；两版本需分属同一快照可比。
 * 返回 OntologyVersionDiffVO（version1/version2[/version1Id/version2Id]/
 * snapshot1/snapshot2/added/removed/modified），raw 动态值豁免。
 */
export async function fetchOntologyVersionDiff(v1Id: string, v2Id: string): Promise<VersionDiff> {
  return apiFetchData<VersionDiff>(
    `/api/v1/ontology/versions/diff?v1=${encodeURIComponent(v1Id)}&v2=${encodeURIComponent(v2Id)}`
  );
}
