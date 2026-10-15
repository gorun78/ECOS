import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../helpers/ecos';

/**
 * Read-only live dependency inventory for the ECOS annual forecast pilot.
 * This test intentionally performs GET requests only. It does not create scenarios,
 * forecast runs, candidates, approvals, or data. Statuses are reported rather than
 * normalized into success: 401/403, 404, 501/503 and unexpected 5xx remain visible.
 *
 * Configure ECOS_PILOT_SCENARIO_ID with an existing scenario from the target
 * development/acceptance environment. Do not use a guessed ID.
 */
const scenarioId = process.env.ECOS_PILOT_SCENARIO_ID?.trim();

type Probe = { name: string; path: string };

function classify(status: number): string {
  if (status >= 200 && status < 300) return 'RESPONDING';
  if (status === 401 || status === 403) return 'AUTH_OR_PERMISSION_BLOCKED';
  if (status === 404) return 'NOT_FOUND_OR_ROUTE_MISMATCH';
  if (status === 501 || status === 503) return 'CAPABILITY_UNAVAILABLE';
  if (status >= 500) return 'SERVER_ERROR';
  return 'NON_SUCCESS_RESPONSE';
}

test('read-only preflight: forecast scenario and cognition dependencies', async ({ request }) => {
  test.skip(!scenarioId, 'set ECOS_PILOT_SCENARIO_ID to an existing scenario in the target environment');

  const token = await loginAsAdmin(request);
  const headers = { Authorization: `Bearer ${token}` };
  const id = encodeURIComponent(scenarioId!);
  const probes: Probe[] = [
    { name: 'forecast_scope', path: `/api/v1/workspace/scenarios/${id}/forecast-scope` },
    { name: 'cognition_detect', path: `/api/v1/business/scenarios/${id}/cognition/detect` },
    { name: 'cognition_hypotheses', path: `/api/v1/business/scenarios/${id}/cognition/hypotheses` },
    { name: 'cognition_beliefs', path: `/api/v1/business/scenarios/${id}/cognition/beliefs` },
    { name: 'cognition_operation_eval', path: `/api/v1/business/scenarios/${id}/cognition/operation-eval` },
    { name: 'approved_caliber_current_contract', path: '/api/v1/ontology/calibers/MANAGEMENT_FORECAST/current' },
    { name: 'cognitive_model_registry', path: '/api/v1/cognitive/models' },
  ];

  const report: Array<{ name: string; status: number; classification: string; success?: unknown; code?: unknown }> = [];
  for (const probe of probes) {
    const response = await request.get(probe.path, { headers });
    const body = await response.json().catch(() => null) as
      | { success?: unknown; code?: unknown; message?: unknown }
      | null;
    const row = {
      name: probe.name,
      status: response.status(),
      classification: classify(response.status()),
      success: body?.success,
      code: body?.code,
    };
    report.push(row);
    console.log(`[ECOS_PILOT_PREFLIGHT] ${JSON.stringify(row)}`);
  }

  // The purpose is an honest inventory, not to turn unavailable dependencies into
  // a green build. This assertion only verifies the probe set is complete.
  expect(report.map((r) => r.name)).toEqual(probes.map((p) => p.name));
  console.log('[ECOS_PILOT_PREFLIGHT_SUMMARY]', JSON.stringify(report, null, 2));
});
