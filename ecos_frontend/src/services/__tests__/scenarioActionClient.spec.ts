// @vitest-environment node
/**
 * scenarioActionClient 纯逻辑契约测试（node 环境，详设-09 v1.2 §十一 H-8 动作回链）。
 *
 * <p>断言对齐后端 {@code ScenarioActionController}（workspace {@code /api/v1/workspace}）
 * 与 {@code ActionProposalValidator} 五必填键名；覆盖路径单源、载荷投影（禁携带 approver）、
 * 错误归一（400 逐字段 / 503 引擎不可用 / 网络失败 / 200 伪失败必抛）。全程 stub fetch，
 * 不依赖 jsdom（本机 Node 20.18.1 下 jsdom 链报 ERR_REQUIRE_ESM，见 vite.config.ts 注记）。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { API_BASE } from '../httpClient';
import {
  ACTION_REQUIRED_FIELDS,
  actionApproveUrl,
  approveScenarioAction,
  createScenarioAction,
  emptyActionDraft,
  formatExpectedImpact,
  isScenarioActionError,
  listScenarioActions,
  pickActionId,
  scenarioActionsUrl,
  scenarioDecisionsUrl,
  ScenarioActionError,
  toActionPayload,
  validateActionDraft,
  type ActionDraft,
} from '../scenarioActionClient';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => (k.toLowerCase() === 'x-trace-id' ? 'trace-srv-1' : null) },
    text: () => Promise.resolve(JSON.stringify(body)),
  } as unknown as Response;
}

function stubFetch(handler: (url: string, init?: RequestInit) => Response | Promise<Response>) {
  const spy = vi.fn(async (input: unknown, init?: RequestInit) =>
    handler(String(input), init),
  );
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

const draft = (over: Partial<ActionDraft> = {}): ActionDraft => ({
  ...emptyActionDraft('run-9'),
  ownerUserId: 'u-owner',
  dueDate: '2026-12-31',
  expectedImpact: '1234567.89',
  controlMetric: 'M-OPEX-RATIO',
  ...over,
});

describe('scenarioActionClient — 路径单源', () => {
  it('三条 URL 一律派生自 API_BASE，id 经 encodeURIComponent', () => {
    expect(scenarioActionsUrl('sc:01')).toBe(`${API_BASE}/v1/workspace/scenarios/sc%3A01/actions`);
    expect(scenarioDecisionsUrl('sc 1/../x')).toBe(
      `${API_BASE}/v1/workspace/scenarios/sc%201%2F..%2Fx/decisions`,
    );
    expect(actionApproveUrl('a1')).toBe(`${API_BASE}/v1/workspace/actions/a1/approve`);
  });
});

describe('scenarioActionClient — 五必填与载荷投影', () => {
  it('必填键名与后端 ActionProposalValidator 逐字对齐（顺序即表单提示顺序）', () => {
    expect([...ACTION_REQUIRED_FIELDS]).toEqual([
      'owner',
      'dueDate',
      'approver',
      'expectedImpact',
      'controlMetric',
    ]);
  });

  it('validateActionDraft 缺啥报啥，纯空白等同缺失，approver 取登录主体', () => {
    expect(validateActionDraft(draft(), 'admin')).toEqual([]);
    expect(validateActionDraft(draft({ ownerUserId: '  ' }), 'admin')).toEqual(['owner']);
    expect(validateActionDraft(draft(), '')).toEqual(['approver']);
    expect(validateActionDraft(emptyActionDraft(), '')).toEqual([
      'owner',
      'dueDate',
      'approver',
      'expectedImpact',
      'controlMetric',
    ]);
  });

  it('初值金额留空而非 0（禁 0 填充假值）', () => {
    expect(emptyActionDraft().expectedImpact).toBe('');
  });

  it('toActionPayload 绝不携带 approver，金额以字符串原样透传，空白可选项裁剪', () => {
    const p = toActionPayload(draft({ forecastRunId: '  ', description: '  ' }));
    expect(p).not.toHaveProperty('approver');
    expect(p.expectedImpact).toBe('1234567.89');
    expect(typeof p.expectedImpact).toBe('string');
    expect(p).not.toHaveProperty('forecastRunId');
    expect(p).not.toHaveProperty('description');
    expect(toActionPayload(draft()).forecastRunId).toBe('run-9');
  });

  it('pickActionId：非字符串/空白一律 null（不伪造回链 ID）', () => {
    expect(pickActionId({ actionId: 'a-1' })).toBe('a-1');
    expect(pickActionId({ actionId: 123 })).toBeNull();
    expect(pickActionId({ actionId: '  ' })).toBeNull();
    expect(pickActionId(undefined)).toBeNull();
  });

  it('formatExpectedImpact：千分位分组保小数，空值 null 而非 0，非数值原样', () => {
    expect(formatExpectedImpact('1234567.89')).toBe('1,234,567.89');
    expect(formatExpectedImpact(-9876)).toBe('-9,876');
    expect(formatExpectedImpact('')).toBeNull();
    expect(formatExpectedImpact(null)).toBeNull();
    expect(formatExpectedImpact(undefined)).toBeNull();
    expect(formatExpectedImpact('待定')).toBe('待定');
  });
});

describe('scenarioActionClient — 传输与错误归一', () => {
  it('200 创建：透传 data，URL 与方法/载荷正确', async () => {
    const spy = stubFetch(() =>
      jsonResponse(200, { code: 200, message: 'ok', data: { actionId: 'a-7', status: 'PROPOSED' } }),
    );
    const res = await createScenarioAction('sc1', toActionPayload(draft()));
    expect(res.actionId).toBe('a-7');
    const [url, init] = spy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(scenarioActionsUrl('sc1'));
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).not.toHaveProperty('approver');
  });

  it('400 ACTION_FIELDS_MISSING：missingFields 结构化回传并过滤未知键', async () => {
    stubFetch(() =>
      jsonResponse(400, {
        code: 400,
        errorCode: 'ACTION_FIELDS_MISSING',
        message: 'missing',
        data: { missingFields: ['owner', 'NOT_A_FIELD', 'controlMetric'] },
      }),
    );
    const err = await createScenarioAction('sc1', toActionPayload(draft())).catch((e) => e);
    expect(isScenarioActionError(err)).toBe(true);
    expect((err as ScenarioActionError).code).toBe('ACTION_FIELDS_MISSING');
    expect((err as ScenarioActionError).missingFields).toEqual(['owner', 'controlMetric']);
    expect((err as ScenarioActionError).traceId).toBe('trace-srv-1');
  });

  it('503 COGNITIVE_UNAVAILABLE 归一为引擎不可用；403 走 status 兜底', async () => {
    stubFetch(() => jsonResponse(503, { errorCode: 'COGNITIVE_UNAVAILABLE', message: 'down' }));
    const e1 = await listScenarioActions('sc1').catch((e) => e);
    expect((e1 as ScenarioActionError).code).toBe('COGNITIVE_UNAVAILABLE');

    stubFetch(() => jsonResponse(403, { message: 'denied' }));
    const e2 = await approveScenarioAction('a1').catch((e) => e);
    expect((e2 as ScenarioActionError).code).toBe('FORBIDDEN');
    expect((e2 as ScenarioActionError).status).toBe(403);
  });

  it('run_mode 误用错误码归一为 BASELINE_RUN_MODE_MISMATCH（卷07 F07-09 语义）', async () => {
    stubFetch(() => jsonResponse(409, { errorCode: 'BASELINE_RUN_MODE_MISMATCH', message: '演练基线' }));
    const e = await approveScenarioAction('a1').catch((e2) => e2);
    expect((e as ScenarioActionError).code).toBe('BASELINE_RUN_MODE_MISMATCH');
  });

  it('网络失败 ⇒ NETWORK/status 0；200 但信封 code 非成功 ⇒ 抛错，禁伪装成功', async () => {
    stubFetch(() => {
      throw new Error('ECONNREFUSED');
    });
    const net = await listScenarioActions('sc1').catch((e) => e);
    expect((net as ScenarioActionError).code).toBe('NETWORK');
    expect((net as ScenarioActionError).status).toBe(0);

    stubFetch(() => jsonResponse(200, { code: 500, message: '内部失败', data: null }));
    const fake = await createScenarioAction('sc1', toActionPayload(draft())).catch((e) => e);
    expect(isScenarioActionError(fake)).toBe(true);
    expect((fake as ScenarioActionError).message).toBe('内部失败');
  });

  it('RLS 空集与故障严格可分：200 + {items:[]} ⇒ []，未知形状 ⇒ []（DG-6 禁造元素）', async () => {
    stubFetch(() => jsonResponse(200, { code: 200, data: { items: [] } }));
    expect(await listScenarioActions('sc1')).toEqual([]);
    stubFetch(() => jsonResponse(200, { code: 200, data: { unexpected: 1 } }));
    expect(await listScenarioActions('sc1')).toEqual([]);
  });
});
