/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 * Gateway diagnostics — single source of truth for types, API calls,
 * and page state. Page components live in tabs/*.
 */
import {
  type GatewayTab,
  type GatewayDiagnostics,
  type DiagnosticsArtifact,
  type DiagnosticsFilter,
  type DiagnosticsRoute,
  type DiagnosticsAnonymous,
  fetchGatewayDiagnostics,
  errorTraceId,
} from "../../../services/platform";
import { isNoAccessError } from "../../../api";

export type { GatewayTab, GatewayDiagnostics, DiagnosticsArtifact, DiagnosticsFilter, DiagnosticsRoute, DiagnosticsAnonymous };

export type DiagnosticsState =
  | { phase: "loading"; prev?: GatewayDiagnostics }
  | { phase: "ready"; data: GatewayDiagnostics; asOf?: string }
  | { phase: "forbidden" }
  | { phase: "error"; message: string; traceId?: string; prev?: GatewayDiagnostics };

/** Narrowing helper — safe access across the discriminated union. */
export function stateData(s: DiagnosticsState): GatewayDiagnostics | undefined {
  if (s.phase === "ready") return s.data;
  if (s.phase === "error") return s.prev;
  if (s.phase === "loading") return s.prev;
  return undefined;
}
export function stateTraceId(s: DiagnosticsState): string | undefined {
  if (s.phase === "ready") return s.data.traceId;
  if (s.phase === "error") return s.traceId;
  return undefined;
}

export const GATEWAY_TAB_IDS: GatewayTab[] = ["filters", "routes", "anonymous", "editions"];

const VALID: Record<string, GatewayTab> = {
  filters: "filters",
  routes: "routes",
  anonymous: "anonymous",
  editions: "editions",
};

/** Coerce an arbitrary ?tab= string to a known tab; default 'filters'. */
export function normalizeTab(value: string | null | undefined): GatewayTab {
  if (!value) return "filters";
  return VALID[value.toLowerCase()] ?? "filters";
}

export interface DiagnosticsResult {
  state: DiagnosticsState;
  setTab: (t: GatewayTab) => void;
  refresh: () => Promise<void>;
  refreshKey: number;
}

export async function probe(
  tab: GatewayTab,
  prev?: GatewayDiagnostics
): Promise<DiagnosticsState> {
  try {
    const data = await fetchGatewayDiagnostics(tab);
    return { phase: "ready", data };
  } catch (e) {
    if (isNoAccessError(e)) {
      // 403 → L3 准入不足（ClearanceInterceptor 经 NoAccessError 上行）
      return { phase: "forbidden" };
    }
    return {
      phase: "error",
      message: String((e as Error)?.message ?? "probe failed"),
      traceId: errorTraceId(e),
      prev,
    };
  }
}
