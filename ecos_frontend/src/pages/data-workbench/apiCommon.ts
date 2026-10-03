/**
 * Data Workbench — 跨域共享类型与映射适配器（W66 拆分）。
 * 仅放置被 ≥2 个域文件引用的 helper；域内私有 helper 留在各自文件。
 * @license Apache-2.0
 */

/** 前端连接类型 → 后端规范类型名（本地文件的规范名为 FILESYSTEM，后端无 FS 类型） */
const API_TYPE_ALIASES: Record<string, string> = { fs: 'FILESYSTEM' };

/** 归一化连接类型为后端可识别的大写类型名 */
function toApiType(type: string): string {
  return API_TYPE_ALIASES[type.toLowerCase()] ?? type.toUpperCase();
}

export { toApiType };
