/**
 * api.test.ts — data-workbench API contract tests
 *
 * PMO-52 T2: getPipelineDefinition now tolerates BOTH
 *  (a) top-level shape  { id, name, nodes, edges }
 *  (b) nested ApiResponse<T> envelope  { code, success, data: { id, name, nodes, edges } }
 * and must return DataPipeline with non-empty normalized nodes in both shapes.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { getPipelineDefinition, fetchDataPipelines } from './api';

const authHeadersStub = () => {
  const store: Record<string, string> = { token: 'test-token' };
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { for (const k of Object.keys(store)) delete store[k]; },
    length: 1,
    key: (i: number) => (i === 0 ? 'token' : null),
  });
};

/** Mock fetch returning the full body envelope (json.data ?? json) so we can drive both shapes. */
const fetchEnvelope = (body: unknown) =>
  vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => body,
  });

beforeEach(() => {
  authHeadersStub();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('getPipelineDefinition — list/detail pairing (PMO-52 T2)', () => {
  it('top-level shape { id, name, nodes, edges } → DataPipeline with nodes mapped', async () => {
    const body = {
      id: 'p-1',
      name: 'audit-supplier-admission',
      status: 'DRAFT',
      description: 'desc',
      updatedAt: '2026-09-10T00:00:00Z',
      nodes: [
        { id: 'n-1', name: 'src', type: 'SOURCE_JDBC', positionX: 10, positionY: 20, config: { datasourceId: 'd1' } },
        { id: 'n-2', name: 'tx', type: 'TRANSFORM_SQL', positionX: 120, positionY: 20, dependsOn: ['n-1'], config: { sql: 'select 1' } },
      ],
      edges: [{ from: 'n-1', to: 'n-2' }],
    };
    vi.stubGlobal('fetch', fetchEnvelope(body));

    const result = await getPipelineDefinition('p-1');
    expect(result).not.toBeNull();
    expect(result!.id).toBe('p-1');
    expect(result!.name).toBe('audit-supplier-admission');
    expect(result!.nodes).toHaveLength(2);
    expect(result!.nodes[0].id).toBe('n-1');
    expect(result!.nodes[0].type).toBe('SOURCE_JDBC');
    expect(result!.nodes[1].type).toBe('TRANSFORM_SQL');
    // dependsOn → `inputs` (camelCase) so the canvas can render edges
    expect(result!.nodes[1].inputs).toContain('n-1');
  });

  it('nested ApiResponse<T> envelope { code, success, data: {...} } → same DataPipeline', async () => {
    const inner: Record<string, unknown> = {
      id: 'p-2',
      name: 'etl-orders',
      status: 'ACTIVE',
      updatedAt: '2026-09-10T00:00:00Z',
      nodes: [
        { id: 'm-1', nodeId: 'm-1', nodeType: 'SOURCE_CSV', positionX: 5, positionY: 5, config: { filePath: '/x' } },
      ],
      edges: [] as Array<Record<string, unknown>>,
    };
    vi.stubGlobal('fetch', fetchEnvelope({
      code: 200,
      success: true,
      message: 'ok',
      data: inner,
    }));

    const result = await getPipelineDefinition('p-2');
    expect(result).not.toBeNull();
    // mapPipelineDef(data)
    expect(result!.id).toBe('p-2');
    expect(result!.name).toBe('etl-orders');
    expect(result!.status).toBe('active'); // mapPipelineStatus ACTIVE→active
    // nodes mapped via normalizeBackendPipelineNode — must preserve camelCase nodeType→type
    expect(result!.nodes).toHaveLength(1);
    expect(result!.nodes[0].id).toBe('m-1');
    expect(result!.nodes[0].type).toBe('SOURCE_CSV');
  });

  it('500 → returns null (defensive, no throw)', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    }));
    expect(await getPipelineDefinition('missing')).toBeNull();
  });

  it('URL encodes id (guards against XSS / path traversal)', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', fetchEnvelope(null));
    const url = await getPipelineDefinition('x/../a b');
    expect(url).toBeNull();
    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls[0]?.[0]).toContain('%2F');
  });
});

describe('fetchDataPipelines — list contract unchanged', () => {
  it('data array of defs → lists, undefined nodes become []', async () => {
    vi.stubGlobal('fetch', fetchEnvelope([
      { id: 'p-1', name: 'a', status: 'DRAFT', updatedAt: '2026-09-10' },
      { id: 'p-2', name: 'b', status: 'active', updatedAt: '2026-09-10' },
    ]));
    const list = await fetchDataPipelines();
    expect(list.length).toBe(2);
    expect(list[0].id).toBe('p-1');
    expect(list[1].status).toBe('active');
  });
});
