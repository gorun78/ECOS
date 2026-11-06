import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiFetchEnvelope = vi.fn();
vi.mock('../httpClient', () => ({
  API_BASE: '/api',
  apiFetchEnvelope: (...args: unknown[]) => apiFetchEnvelope(...args),
}));

import { getBlueprintGaps } from '../scenarioBlueprintClient';

function ok(data: unknown) {
  return { status: 200, ok: true, text: '', traceId: 't-1', envelope: { code: 0 }, data };
}

describe('scenarioBlueprintClient.getBlueprintGaps', () => {
  beforeEach(() => {
    apiFetchEnvelope.mockReset();
  });

  it('calls the server-classified gaps endpoint with optional severity', async () => {
    apiFetchEnvelope.mockResolvedValue(ok({ counts: { BLOCKER: 2, WARNING: 1 }, openBlockers: 1, blocking: true }));

    const summary = await getBlueprintGaps('sc-1', 'bp-1', 'BLOCKER');

    expect(apiFetchEnvelope).toHaveBeenCalledWith(
      '/api/v1/business/scenarios/sc-1/blueprints/bp-1/gaps?severity=BLOCKER',
      expect.any(Object),
    );
    expect(summary.counts.BLOCKER).toBe(2);
    expect(summary.counts.SUGGESTION).toBe(0);
    expect(summary.blocking).toBe(true);
    expect(summary.openBlockers).toBe(1);
  });

  it('keeps absent severity as no query string and unknown counts as zero', async () => {
    apiFetchEnvelope.mockResolvedValue(ok({ blocking: false }));

    const summary = await getBlueprintGaps('sc-1', 'bp-1');

    expect(apiFetchEnvelope).toHaveBeenCalledWith(
      '/api/v1/business/scenarios/sc-1/blueprints/bp-1/gaps',
      expect.any(Object),
    );
    expect(summary.severityFilter).toBeNull();
    expect(summary.counts.UNKNOWN).toBe(0);
    expect(summary.blocking).toBe(false);
  });
});
