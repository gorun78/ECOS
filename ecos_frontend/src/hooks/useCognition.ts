/**
 * useCognition — 场景认知四端点（E1~E4）唯一消费通道（详细设计-07 F07-12 / F07-15 / 3.4-5）。
 *
 * <p>路径：{@code GET /api/v1/business/scenarios/{id}/cognition/{detect|operation-eval|hypotheses|beliefs}}，
 * 单源实现在 {@link services/cognitiveClient}。本 hook 只做 React 侧封装（加载/错误态、
 * 一次性触发、503 不可用透传），<b>不重复实现 fetch、不本地伪造结论</b>（F07-22）。</p>
 *
 * <p>错误语义（对齐 cognitiveClient 的错误矩阵）：引擎不可用 / schema 未就绪 → 503 →
 * {@code unavailable=true}，调用方渲染横幅 + 重试，<b>禁 fallback 造数据</b>。</p>
 *
 * @license Apache-2.0
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  CognitiveApiError,
  detectScenarioMinds,
  fetchOperationEval,
  fetchScenarioBeliefs,
  fetchScenarioHypotheses,
  type BeliefRow,
  type CognMindRow,
  type HypothesisRow,
  type OperationEvalResponse,
} from '../services/cognitiveClient';

/** 认知引擎不可用（503）判定：横幅 + 重试，禁本地伪造。 */
export function isCognitionUnavailable(e: unknown): boolean {
  return e instanceof CognitiveApiError && (e.code === 'ENGINE_DISABLED' || e.code === 'SCHEMA_NOT_READY' || e.status === 503);
}

export interface CognitionSlice<T> {
  data: T | null;
  loading: boolean;
  /** 引擎不可用（503）—— 调用方据此渲染横幅，非普通错误 */
  unavailable: boolean;
  /** traceId（若后端透传）—— 错误态展示诊断码 */
  traceId?: string;
  error: string | null;
  reload: () => void;
}

interface BaseState<T> {
  data: T | null;
  loading: boolean;
  unavailable: boolean;
  traceId?: string;
  error: string | null;
}

function useCognitionEndpoint<T>(
  loader: (scenarioId: string, signal: { cancelled: boolean }) => Promise<T | null>,
  scenarioId: string,
  enabled: boolean,
): CognitionSlice<T> {
  const [state, setState] = useState<BaseState<T>>({ data: null, loading: false, unavailable: false, error: null });
  const [nonce, setNonce] = useState(0);
  const reqSeq = useRef(0);

  useEffect(() => {
    if (!enabled || !scenarioId) {
      setState({ data: null, loading: false, unavailable: false, error: null });
      return;
    }
    const seq = ++reqSeq.current;
    const signal = { cancelled: false };
    setState((prev) => ({ ...prev, loading: true, error: null, unavailable: false }));
    loader(scenarioId, signal)
      .then((data) => {
        if (seq !== reqSeq.current) return;
        setState({ data, loading: false, unavailable: false, error: null });
      })
      .catch((e: unknown) => {
        if (seq !== reqSeq.current) return;
        const ce = e instanceof CognitiveApiError ? e : null;
        setState({
          data: null,
          loading: false,
          unavailable: isCognitionUnavailable(e),
          traceId: ce?.traceId,
          error: e instanceof Error ? e.message : String(e),
        });
      });
    return () => {
      signal.cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioId, enabled, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  return { ...state, reload };
}

/** 四端点聚合 hook：detect / operation-eval / hypotheses / beliefs 各一条独立只读通道。 */
export function useCognition(scenarioId: string, opts: { enabled?: boolean } = {}) {
  const enabled = opts.enabled ?? true;
  const detect = useCognitionEndpoint<CognMindRow[]>(
    (id) => detectScenarioMinds(id),
    scenarioId,
    enabled,
  );
  const operationEval = useCognitionEndpoint<OperationEvalResponse>(
    (id) => fetchOperationEval(id),
    scenarioId,
    enabled,
  );
  const hypotheses = useCognitionEndpoint<HypothesisRow[]>(
    (id) => fetchScenarioHypotheses(id),
    scenarioId,
    enabled,
  );
  const beliefs = useCognitionEndpoint<{ rows: BeliefRow[]; issues?: string[] }>(
    (id) => fetchScenarioBeliefs(id),
    scenarioId,
    enabled,
  );
  return { detect, operationEval, hypotheses, beliefs };
}

export default useCognition;
