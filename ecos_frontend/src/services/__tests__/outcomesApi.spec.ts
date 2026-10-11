// @vitest-environment node
/**
 * cognitive/outcomesApi 契约测试（node 环境，详设-09 v1.2 §十一 H-8 四联视图数据面）。
 *
 * <p>断言对齐后端 {@code OutcomeEvaluateController}
 * （`@RequestMapping("/api/v1/cognitive")` + `@GetMapping("/outcomes")` /
 * `@GetMapping("/outcomes/by-run/{runId}")`，空集 = 200 + []）与
 * {@code WorldModelController} 的显式 501 stub。覆盖：路径单源派生、数组缺失一律空集
 * （DG-6）、不适用行保留且不补零（铁律 v2.0 :14 禁前端造数值）、501 经 callJson 归一为
 * stub=true（禁本地合成推演结果）。全程 stub fetch，不依赖 jsdom。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CognitiveApiError, COGNITIVE_BASE, WORLD_MODEL_BASE } from '../cognitiveClient';
import {
  OUTCOMES_BASE_PATH,
  fetchOutcomesByRun,
  fetchOutcomesByScenario,
  outcomesByRunPath,
  outcomesListPath,
  runWorldModelScenarioSimulation,
  worldModelUrl,
} from '../cognitive/outcomesApi';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (): string | null => null },
    text: () => Promise.resolve(JSON.stringify(body)),
  } as unknown as Response;
}

function stubFetch(handler: (url: string, init?: RequestInit) => Response) {
  const spy = vi.fn(async (input: unknown, init?: RequestInit) => handler(String(input), init));
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('outcomesApi — 路径单源（对齐后端 cognitive 域）', () => {
  it('基址由 COGNITIVE_BASE 派生，逐字等于后端 /api/v1/cognitive/outcomes', () => {
    expect(COGNITIVE_BASE).toBe('/api/v1/cognitive');
    expect(OUTCOMES_BASE_PATH).toBe(`${COGNITIVE_BASE}/outcomes`);
    expect(OUTCOMES_BASE_PATH).toBe('/api/v1/cognitive/outcomes');
  });

  it('by-run 路径对 runId 做 URL 编码（防路径注入）', () => {
    expect(outcomesByRunPath('run:01')).toBe('/api/v1/cognitive/outcomes/by-run/run%3A01');
    expect(outcomesByRunPath('r 1/../x')).toBe('/api/v1/cognitive/outcomes/by-run/r%201%2F..%2Fx');
  });

  it('列表路径 scenarioId 必填、status 可选，均经 URLSearchParams 编码', () => {
    expect(outcomesListPath('sc 1')).toBe('/api/v1/cognitive/outcomes?scenarioId=sc+1');
    expect(outcomesListPath('sc1', 'NEEDS_RECONCILE')).toBe(
      '/api/v1/cognitive/outcomes?scenarioId=sc1&status=NEEDS_RECONCILE',
    );
  });

  it('world-model 基址单源，子路径只做拼接', () => {
    expect(WORLD_MODEL_BASE).toBe('/api/v1/world-model');
    expect(worldModelUrl('/scenarios')).toBe('/api/v1/world-model/scenarios');
  });
});

describe('outcomesApi — 读取归一（无假真理）', () => {
  it('空集 = 200 + [] ⇒ 长度 0（与故障严格可分，DG-6）', async () => {
    stubFetch(() => jsonResponse(200, { code: 200, data: [] }));
    expect(await fetchOutcomesByRun('r1')).toEqual([]);
  });

  it('行内数组字段缺失一律空集；标量缺失留 undefined/null，禁补 0 或均值', async () => {
    stubFetch(() =>
      jsonResponse(200, { code: 200, data: [{ evaluationId: 'e1', status: 'EVALUATED' }] }),
    );
    const [row] = await fetchOutcomesByRun('r1');
    expect(row.evaluationId).toBe('e1');
    expect(row.metrics).toEqual([]);
    expect(row.diagnoses).toEqual([]);
    expect(row.updateCandidates).toEqual([]);
    expect(row.warnings).toEqual([]);
    expect(row.blockers).toEqual([]);
    expect(row.calculationRefs).toEqual([]);
    expect(row.mae).toBeNull();
    expect(row.mape).toBeNull();
    expect(row.totalSampleCount).toBeUndefined();
  });

  it('applicable=false 行原样保留（含 null 数值与原因），不剔除也不填值', async () => {
    stubFetch(() =>
      jsonResponse(200, {
        code: 200,
        data: [
          {
            metrics: [
              { metricRef: 'M1', predicted: '100.00', actual: '98.00', absoluteError: '2.00', applicable: true },
              { metricRef: 'M2', predicted: null, actual: null, absoluteError: null, applicable: false, notApplicableReason: '样本期未满' },
            ],
            pendingHypotheses: undefined,
          },
        ],
      }),
    );
    const [row] = await fetchOutcomesByRun('r1');
    expect(row.metrics).toHaveLength(2);
    expect(row.metrics[0].predicted).toBe('100.00');
    expect(row.metrics[1].applicable).toBe(false);
    expect(row.metrics[1].absoluteError).toBeNull();
    expect(row.metrics[1].notApplicableReason).toBe('样本期未满');
  });

  it('请求 URL = by-run 路径编码结果；scenario 维度带查询串', async () => {
    const spy = stubFetch(() => jsonResponse(200, { code: 200, data: [] }));
    await fetchOutcomesByRun('sc:9');
    expect(spy.mock.calls[0][0]).toBe('/api/v1/cognitive/outcomes/by-run/sc%3A9');
    await fetchOutcomesByScenario('sc9', 'PENDING');
    expect(spy.mock.calls[1][0]).toBe('/api/v1/cognitive/outcomes?scenarioId=sc9&status=PENDING');
  });

  it('world-model 501 stub ⇒ CognitiveApiError{code:STUB,stub:true}，绝不本地合成结果', async () => {
    const spy = stubFetch(() =>
      jsonResponse(501, { code: 501, message: 'not implemented', data: { stub: true } }),
    );
    const err = await runWorldModelScenarioSimulation({ scenarioId: 'sc1' }).catch((e) => e);
    expect(err instanceof CognitiveApiError).toBe(true);
    expect((err as CognitiveApiError).code).toBe('STUB');
    expect((err as CognitiveApiError).stub).toBe(true);
    expect((err as CognitiveApiError).status).toBe(501);
    expect(spy.mock.calls[0][0]).toBe('/api/v1/world-model/scenarios');
    expect((spy.mock.calls[0][1] as RequestInit).method).toBe('POST');
  });

  it('404 归一 NOT_FOUND（run 未命中不降级为空集，故障与空集可分）', async () => {
    stubFetch(() => jsonResponse(404, { code: 404, message: 'run not found' }));
    const err = await fetchOutcomesByRun('missing').catch((e) => e);
    expect((err as CognitiveApiError).code).toBe('NOT_FOUND');
    expect((err as CognitiveApiError).status).toBe(404);
  });
});
