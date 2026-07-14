/**
 * dataCatalogClient — 数据底座 API 客户端
 *
 * 封装数据映射相关后端 API 调用，提供：
 *   - fetchAllResources()   → GET /api/v1/datanet/metadata/resources/all
 *   - fetchFields()         → GET /api/v1/datanet/metadata/fields/{resourceId}
 *   - fetchPreview()        → GET /api/v1/datanet/metadata/preview/{resourceId}
 *
 * 使用 apiFetchData<T>() 统一处理 token 认证和响应格式解析。
 * 对数据底座 API 不可用的情况做降级处理，抛出友好错误信息。
 *
 * @license Apache-2.0
 */

import { apiFetchData } from "../api";
import type {
  BulkResource,
  WorkbenchDataField,
} from "../types/workbench";

// ── 配置常量 ──────────────────────────────────────────────

/** 数据底座 metadata 基础路径 */
const DATANET_BASE = "/api/v1/datanet/metadata";

// ── 降级错误 ──────────────────────────────────────────────

/** 数据底座降级错误信息 */
export const DATA_CATALOG_UNAVAILABLE = "数据底座暂不可用";

/**
 * 统一错误处理：将网络/API 错误转为中文降级消息
 */
function handleError(err: unknown): never {
  const message =
    err instanceof Error ? err.message : String(err);
  throw new Error(`${DATA_CATALOG_UNAVAILABLE}: ${message}`);
}

// ================================================================
// 公开 API
// ================================================================

/**
 * 获取数据底座全部物理资源
 * GET /api/v1/datanet/metadata/resources/all
 *
 * @returns BulkResource[] 全部物理表/视图列表
 * @throws 数据底座不可用时抛出包含 "数据底座暂不可用" 的错误
 */
export async function fetchAllResources(): Promise<BulkResource[]> {
  try {
    const data = await apiFetchData<BulkResource[]>(
      `${DATANET_BASE}/resources/all`
    );
    return Array.isArray(data) ? data : [];
  } catch (err) {
    handleError(err);
  }
}

/**
 * 获取物理资源的字段列表
 * GET /api/v1/datanet/metadata/fields/{resourceId}
 *
 * @param resourceId 物理资源 ID
 * @returns WorkbenchDataField[] 字段列表
 * @throws 数据底座不可用时抛出包含 "数据底座暂不可用" 的错误
 */
export async function fetchFields(
  resourceId: string
): Promise<WorkbenchDataField[]> {
  try {
    const data = await apiFetchData<WorkbenchDataField[]>(
      `${DATANET_BASE}/fields/${resourceId}`
    );
    return Array.isArray(data) ? data : [];
  } catch (err) {
    handleError(err);
  }
}

/**
 * 获取物理资源的数据预览
 * GET /api/v1/datanet/metadata/preview/{resourceId}?limit={limit}
 *
 * @param resourceId 物理资源 ID
 * @param limit 预览行数上限（默认 50）
 * @returns 数据行数组，每行为 Record<string, any>
 * @throws 数据底座不可用时抛出包含 "数据底座暂不可用" 的错误
 */
export async function fetchPreview(
  resourceId: string,
  limit: number = 50
): Promise<Record<string, any>[]> {
  try {
    const data = await apiFetchData<{ rows?: Record<string, any>[] }>(
      `${DATANET_BASE}/preview/${resourceId}?limit=${limit}`
    );
    // 兼容多种响应格式：{ rows: [...] } / [...]
    if (data && Array.isArray(data)) return data;
    if (data && Array.isArray(data.rows)) return data.rows;
    return [];
  } catch (err) {
    handleError(err);
  }
}

// ================================================================
// 便捷导出对象
// ================================================================

/**
 * dataCatalogClient — 集中导出的 API 客户端对象
 * 可用于 import { dataCatalogClient } from "..." 一次性导入全部方法
 */
export const dataCatalogClient = {
  fetchAllResources,
  fetchFields,
  fetchPreview,
};
