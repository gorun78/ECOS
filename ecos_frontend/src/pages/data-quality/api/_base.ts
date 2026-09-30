/**
 * PMO-48 — Data Quality governance API layer · 共享端点常量 + 回落遥测
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import type { DqFallbackRecord } from "./types";

export const DQ_GOV_BASE = "/api/v1/dq";
/** legacy 只读端点前缀 — 与数据工作台「数据质量」页同源 (DqController) */
export const DQ_LEGACY_BASE = "/api/v1/ecos/dq";

declare global {
  // 允许 vitest / Playwright / devtools 读；不污染 DOM API
  interface Window {
    __ecosFallbacks?: DqFallbackRecord[];
  }
}

/**
 * 记录一次"治理 404 → legacy"回落。
 * 纯内部遥测：console.warn + window.__ecosFallbacks push。无用户可见字符串
 * （用户态错误提示由页面级 t(...) 走，本层不硬编码任何中/英文 UI 文案）。
 */
export function recordDqFallback(
  path: string,
  note: string,
  hint?: { status?: number; traceId?: string },
): void {
  if (typeof console !== "undefined") {
    // eslint-disable-next-line no-console
    console.warn(
      `[dq-fallback] ${note} path=${path} status=${hint?.status ?? "n/a"} traceId=${hint?.traceId ?? "n/a"}`,
    );
  }
  if (typeof window === "undefined") return;
  if (!Array.isArray(window.__ecosFallbacks)) window.__ecosFallbacks = [];
  window.__ecosFallbacks.push({
    at: Date.now(),
    layer: "governance->legacy",
    path,
    status: hint?.status,
    traceId: hint?.traceId,
    note,
  });
}
