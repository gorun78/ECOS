/**
 * useCognitionNoFabrication.test.tsx — V-COG-121：认知失败时前端不伪造结果
 *
 * 矩阵原判 PARTIAL（「不本地合成 verdict/answer/groundedDocs 属静态断言，未逐字段验」）。
 * 本测试把断言变成可执行用例：四个认知端点全部失败时，每个切片必须
 *   ① `data === null`（不得保留旧值或合成空对象冒充结果）
 *   ② `unavailable` 依错误类型如实置位（CognitiveApiError 503 → true）
 *   ③ 不残留 `verdict` / `answer` / `groundedDocs` 任何键
 * 正向对照：#3 证明「成功时 data 必须等于服务端返回值」，排除「永远 null」的假绿。
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, waitFor, cleanup } from '@testing-library/react';

vi.mock('../services/cognitiveClient', () => {
  class CognitiveApiError extends Error {
    code: string;
    status: number;
    traceId?: string;
    constructor(code: string, status: number, message: string, traceId?: string) {
      super(message);
      this.code = code;
      this.status = status;
      this.traceId = traceId;
    }
  }
  const boom = () => Promise.reject(new CognitiveApiError('ENGINE_DISABLED', 503, 'engine down', 'trace-cog-1'));
  return {
    CognitiveApiError,
    detectScenarioMinds: vi.fn(boom),
    fetchScenarioBeliefs: vi.fn(boom),
    fetchScenarioHypotheses: vi.fn(boom),
    fetchOperationEval: vi.fn(boom),
  };
});

import { useCognition } from '../hooks/useCognition';

describe('V-COG-121 认知失败不伪造', () => {
  afterEach(() => cleanup());

  it('四端点 503：所有切片 data 均为 null、unavailable=true、无合成结论字段', async () => {
    const { result } = renderHook(() => useCognition('sc001'));

    await waitFor(() => {
      const { detect, operationEval, hypotheses, beliefs } = result.current;
      expect(detect.loading || operationEval.loading || hypotheses.loading || beliefs.loading).toBe(false);
      expect(detect.unavailable).toBe(true);
    }, { timeout: 5000 });

    const slices = result.current;
    for (const [name, slice] of Object.entries(slices)) {
      expect(slice.data, `${name} 失败时 data 必须为 null`).toBeNull();
      expect(slice.unavailable, `${name} 失败时 unavailable 必须为 true`).toBe(true);
      expect(slice.error, `${name} 失败须带错误信息`).toBeTruthy();
      const asRecord = (slice.data ?? {}) as Record<string, unknown>;
      for (const key of ['verdict', 'answer', 'groundedDocs', 'rows']) {
        expect(asRecord).not.toHaveProperty(key);
      }
    }
  });

  it('正向对照：成功时切片 data 必须等于服务端返回值（排除「永远 null」假绿）', async () => {
    const client = await import('../services/cognitiveClient') as unknown as Record<string, unknown>;
    const minds = [{ mindId: 'm-1', isActive: true }];
    const beliefRows = { rows: [{ id: 'b-1' }] };
    (client.detectScenarioMinds as any).mockResolvedValueOnce(minds);
    (client.fetchScenarioBeliefs as any).mockResolvedValueOnce(beliefRows);
    (client.fetchScenarioHypotheses as any).mockResolvedValueOnce([{ id: 'h-1' }]);
    (client.fetchOperationEval as any).mockResolvedValueOnce({ verdict: 'FORECAST', answer: 'real' });

    const { result } = renderHook(() => useCognition('sc002'));

    await waitFor(() => {
      expect(result.current.beliefs.data).toEqual(beliefRows);
    }, { timeout: 5000 });

    expect(result.current.beliefs.unavailable).toBe(false);
    expect(result.current.beliefs.error).toBeNull();
    expect(result.current.detect.data).toEqual(minds);
  });
});
