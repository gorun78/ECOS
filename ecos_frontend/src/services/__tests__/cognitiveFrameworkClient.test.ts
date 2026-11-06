import { describe, expect, it, vi, beforeEach } from 'vitest';

const apiFetchData = vi.fn();
vi.mock('../httpClient', () => ({
  API_BASE: '/api',
  apiFetchData: (...args: unknown[]) => apiFetchData(...args),
}));

import {
  CognitiveFrameworkApiError,
  listFrameworks,
  selectFrameworks,
  validatePlan,
} from '../cognitiveFrameworkClient';

describe('cognitiveFrameworkClient', () => {
  beforeEach(() => {
    apiFetchData.mockReset();
  });

  it('lists published frameworks through the single cognitive path', async () => {
    apiFetchData.mockResolvedValue([{ frameworkId: 'CF-01-01', version: '1.0.0', name: 'x', category: 'c', status: 'PUBLISHED' }]);
    const rows = await listFrameworks({ status: 'PUBLISHED', problemType: 'problem_framing' });
    expect(rows).toHaveLength(1);
    expect(apiFetchData).toHaveBeenCalledWith(
      '/api/v1/cognitive/frameworks?status=PUBLISHED&problemType=problem_framing',
    );
  });

  it('posts selection body and returns hard-filter result', async () => {
    apiFetchData.mockResolvedValue({ selectedFrameworks: [], excludedFrameworks: [], missingInputs: [], status: 'NEEDS_INPUT_OR_HUMAN' });
    const result = await selectFrameworks({ scenarioId: 's1', problemType: 'p' });
    expect(result.status).toBe('NEEDS_INPUT_OR_HUMAN');
    expect(apiFetchData).toHaveBeenCalledWith(
      '/api/v1/cognitive/frameworks/select',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('maps backend status/code/traceId to CognitiveFrameworkApiError', async () => {
    apiFetchData.mockRejectedValue(Object.assign(new Error('engine down'), {
      status: 503,
      code: 'ECOS-COG-500E',
      traceId: 'trace-1',
    }));
    await expect(validatePlan({ steps: [] })).rejects.toMatchObject({
      name: 'CognitiveFrameworkApiError',
      status: 503,
      code: 'ECOS-COG-500E',
      traceId: 'trace-1',
    });
    await expect(validatePlan({ steps: [] })).rejects.toBeInstanceOf(CognitiveFrameworkApiError);
  });
});
