/**
 * scenarioCompletenessClient — 场景完整度唯一读通道（后端单源，前端不重算）。
 * GET /api/v1/workspace/scenarios/{id}/completeness
 */
import { apiFetchData } from './httpClient';
import { WORKSPACE_SCENARIOS_BASE } from './scenarioActionClient';

export interface RequiredEdgeVO {
  type: string;
  sourceType: string;
  targetType: string;
}

export interface PresentEdgeVO {
  type: string;
  sourceId: string;
  targetId: string;
  contractCount: number;
  lastVerifiedAt?: string | null;
}

export interface MissingEdgeVO {
  type: string;
  labelKey: string;
  reason: string;
  severity?: 'BLOCKER' | 'WARNING' | 'SUGGESTION';
  blocking?: boolean;
}

export interface IslandVO {
  bindingId: string;
  name: string;
  bindingType: string;
  reason: string;
  severity?: 'BLOCKER' | 'WARNING' | 'SUGGESTION';
  blocking?: boolean;
}

export interface CompletenessReportVO {
  scenarioId: string;
  coverage: number | null;
  verdict: 'OK' | 'PARTIAL' | 'EMPTY_SCENARIO' | 'NOT_APPLICABLE';
  requiredEdges: RequiredEdgeVO[];
  presentEdges: PresentEdgeVO[];
  missingEdges: MissingEdgeVO[];
  islands: IslandVO[];
  probeErrors: string[];
  /** 后端判定：存在阻断级问题（缺必需边/孤岛）时 true。 */
  blocking?: boolean;
}

export class CompletenessApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;

  constructor(message: string, status: number, code: string, traceId?: string) {
    super(message);
    this.name = 'CompletenessApiError';
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}

export function isCompletenessApiError(e: unknown): e is CompletenessApiError {
  return e instanceof CompletenessApiError;
}

export async function getCompleteness(scenarioId: string, refresh = false): Promise<CompletenessReportVO> {
  const qs = refresh ? '?refresh=true' : '';
  try {
    return await apiFetchData<CompletenessReportVO>(
      `${WORKSPACE_SCENARIOS_BASE}/${encodeURIComponent(scenarioId)}/completeness${qs}`,
    );
  } catch (e) {
    const raw = e as { status?: number; code?: string; traceId?: string; message?: string };
    throw new CompletenessApiError(
      raw?.message ?? String(e),
      typeof raw?.status === 'number' ? raw.status : 0,
      raw?.code ?? 'UNKNOWN',
      raw?.traceId,
    );
  }
}
