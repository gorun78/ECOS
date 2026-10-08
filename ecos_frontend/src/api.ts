/**
 * src/api.ts — 兼容 re-export shim（design-05 B.9-1 拆分收口）。
 *
 * <p>实现体已按域拆至 `src/api/*.ts`（每文件 ≤800 行）；本文件仅保留
 * export-* 兼容面，既有 `from "./api"` / `from "../api"` importer 零改动。</p>
 * <p>HTTP 传输原语单源在 `src/services/httpClient.ts`；认知域路径常量单源在
 * `src/services/cognitiveClient.ts`（X-47/X-50/X-51）。</p>
 */

// HTTP 传输原语已单源化至 src/services/httpClient.ts (PMO-74.6 H6-T2) —
// 此处 re-export 保持既有 `from "./api"` 导入面不变。
export {
  API_BASE,
  NetworkError,
  isErrorResponse,
  notifyNetworkDown,
  markNetworkDown,
  notifyNetworkUp,
  toAbortError,
  setAuthGracePeriod,
  handleAuthExpired,
  checkAuthExpired,
  NoAccessError,
  NO_ACCESS_ERROR_NAME,
  NO_ACCESS_EVENT,
  notifyNoAccess,
  isNoAccessError,
  encodeQueryString,
  apiFetch,
  apiFetchData,
  doFetch,
  authLogin,
  apiHealth,
} from "./services/httpClient";
export type { LoginRequest, LoginResponse } from "./services/httpClient";

// ── 按域拆分实现体（B.9-1）──
export * from "./api/dataClient";
export * from "./api/ontologyClient";
export * from "./api/agentClient";
export * from "./api/securityClient";
export * from "./api/knowledgeClient";
export * from "./api/marketClient";
export * from "./api/iamClient";
export * from "./api/monitorClient";
export * from "./api/dictConfigClient";
export * from "./api/scenarioClient";

// shim 兼容面合计 269 个顶层导出（原 api.ts 2,625 行 / 247 export）。
