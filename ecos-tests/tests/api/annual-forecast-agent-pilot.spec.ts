import { expect, test } from '@playwright/test';
import { call, loginAsAdmin } from '../helpers/ecos';

/**
 * ECOS Pilot agent contract checks.
 *
 * Default mode is non-mutating: only verify that an incomplete forecast-run command
 * is rejected. Real run creation is deliberately opt-in because it writes run records.
 * No fake data, success stubs, or hard-coded business identifiers are used.
 */
const EXECUTE = process.env.ECOS_PILOT_EXECUTE === '1';

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`ECOS_PILOT_EXECUTE=1 requires ${name}`);
  return value;
}

test.describe('ECOS annual forecast agent pilot', () => {
  test('P-03 incomplete forecast run request is rejected without creating a run', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const result = await call(request, 'POST', '/api/v1/workspace/forecast-runs', {
      token,
      data: { scenarioId: process.env.ECOS_PILOT_SCENARIO_ID ?? 'pilot-validation-only' },
    });

    expect(result.status, 'incomplete command must be rejected').toBeGreaterThanOrEqual(400);
    expect(result.status, 'incomplete command must not be accepted').toBeLessThan(500);
    expect(result.json?.success, 'request must not report success').not.toBe(true);
    expect(String(result.json?.message ?? '') + ' ' + String(result.json?.msg ?? ''))
      .toMatch(/缺少|必需|必填|required|invalid|FC-RUN-400/i);
  });

  test('P-01/P-02 opt-in: create a real run, read status/results/evidence, replay idempotently', async ({ request }) => {
    test.skip(!EXECUTE, 'safe default: set ECOS_PILOT_EXECUTE=1 only for an authorized isolated acceptance environment');

    const scenarioId = requiredEnv('ECOS_PILOT_SCENARIO_ID');
    const runType = requiredEnv('ECOS_PILOT_RUN_TYPE');
    const caliberId = requiredEnv('ECOS_PILOT_CALIBER_ID');
    const caliberVersion = requiredEnv('ECOS_PILOT_CALIBER_VERSION');
    const asOfTime = requiredEnv('ECOS_PILOT_AS_OF_TIME');
    const snapshotId = requiredEnv('ECOS_PILOT_SNAPSHOT_ID');
    const formulaVersion = requiredEnv('ECOS_PILOT_FORMULA_VERSION');
    const idempotencyKey = requiredEnv('ECOS_PILOT_IDEMPOTENCY_KEY');
    const token = await loginAsAdmin(request);

    // Content hash must be a stable fingerprint supplied by the verified fixture/build process.
    const contentHash = requiredEnv('ECOS_PILOT_CONTENT_HASH');
    const payload = {
      scenarioId, runType, caliberId, caliberVersion, asOfTime,
      snapshotId, formulaVersion, idempotencyKey, contentHash,
      runMode: 'SANDBOX',
    };

    const created = await call(request, 'POST', '/api/v1/workspace/forecast-runs', { token, data: payload });
    expect(created.status, JSON.stringify(created.json)).toBe(200);
    expect(created.json?.success).toBe(true);
    const runId = String(created.json?.data?.forecastRunId ?? created.json?.data?.runId ?? '');
    expect(runId, 'API did not return a real forecastRunId').not.toBe('');

    const status = await call(request, 'GET', `/api/v1/workspace/forecast-runs/${encodeURIComponent(runId)}`, { token });
    expect(status.status).toBe(200);
    expect(status.json?.success).toBe(true);
    expect(String(status.json?.data?.status ?? '')).not.toBe('');

    const results = await call(request, 'GET', `/api/v1/workspace/forecast-runs/${encodeURIComponent(runId)}/results`, { token });
    expect(results.status, JSON.stringify(results.json)).toBe(200);
    expect(results.json?.success).toBe(true);
    const resultData = results.json?.data ?? {};
    // Quantiles are authoritative service outputs. Missing values are not interpolated here.
    expect(Object.prototype.hasOwnProperty.call(resultData, 'p10')).toBe(true);
    expect(Object.prototype.hasOwnProperty.call(resultData, 'p50')).toBe(true);
    expect(Object.prototype.hasOwnProperty.call(resultData, 'p90')).toBe(true);

    const evidence = await call(request, 'GET', `/api/v1/workspace/forecast-runs/${encodeURIComponent(runId)}/evidence`, { token });
    expect(evidence.status, JSON.stringify(evidence.json)).toBe(200);
    expect(evidence.json?.success).toBe(true);

    const replay = await call(request, 'POST', '/api/v1/workspace/forecast-runs', { token, data: payload });
    expect(replay.status, JSON.stringify(replay.json)).toBe(200);
    expect(replay.json?.success).toBe(true);
    const replayRunId = String(replay.json?.data?.forecastRunId ?? replay.json?.data?.runId ?? '');
    expect(replayRunId, 'idempotent replay did not return a run id').toBe(runId);
  });
});
