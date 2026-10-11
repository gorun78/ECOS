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

/** E2 必带 mind 的解析通道：E1 detect 首项（isActive 优先）。失败原样上抛（503 ⇒ unavailable）。 */
async function resolveFirstMind(scenarioId: string): Promise<string | undefined> {
  const minds = await detectScenarioMinds(scenarioId);
  const chosen = minds.find((m) => m.isActive) ?? minds[0];
  return chosen?.mindId;
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

/** 四端点聚合 hook：detect / operation-eval / hypotheses / beliefs 各一条独立只读通道。
 *
 * <p>E2 契约（design-05 F05-04，gateway 宿主 `ScenarioCognitionController.operationEval`）
 * `mind` 为**必带**参数：缺失时 Spring 参数解析直接 500（2026-10-11 live 实证
 * traceId a886196d — MissingServletRequestParameterException 'mind'）。因此本 hook
 * 不裸发无 mind 的 E2 请求：优先用调用方传入的 `mindId`；未传则先经 E1 detect
 * 解析场景可用 Mind（取 isActive 首项），detect 失败（503 降级）时 E2 如实转入
 * unavailable 态（同一条降级链，禁伪数据、禁绕过必带参数）。</p> */
export function useCognition(scenarioId: string, opts: { enabled?: boolean; mindId?: string } = {}) {
  const enabled = opts.enabled ?? true;
  const detect = useCognitionEndpoint<CognMindRow[]>(
    (id) => detectScenarioMinds(id),
    scenarioId,
    enabled,
  );
  const operationEval = useCognitionEndpoint<OperationEvalResponse>(
    async (id) => {
      const mind = opts.mindId || (await resolveFirstMind(id));
      if (!mind) {
        // 场景无任何可用 Mind（如 sc002 空态）：不发 E2 请求，返回 null = 如实空态
        return null;
      }
      return fetchOperationEval(id, { mind });
    },
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
