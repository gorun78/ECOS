/**
 * R-3 ② (F02-08 规格 6) — 404 回落 legacy 保留但**禁静默** 测试
 *
 * 断言：治理端点 404 时
 *   1. fallback 到 legacy GET /api/v1/ecos/dq/rules 被尝试；
 *   2. console.warn 被调用（非静默）；
 *   3. window.__ecosFallbacks 增加一条记录（遥测钩子）。
 * 治理端点正常返回时不回落、不 warn。
 *
 * (本用例 ensureBrowserGlobals 自装 window/localStorage/CustomEvent, 不依赖
 *  真实 DOM — 标 node env 避开 jsdom 的 ESM/require 环境故障)
 */
// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { DqRuleVO, fetchDqRulesWithFallback, recordDqFallback, type DqFallbackRecord } from './api';

/** 测试内本地扩展 Window 类型（对齐 api.ts 的 declare global） */
interface EcologyFallbackWindow extends Window {
  __ecosFallbacks?: DqFallbackRecord[];
}
function getFallbacks(): DqFallbackRecord[] {
  return (window as EcologyFallbackWindow).__ecosFallbacks ?? [];
}

function clearFallbackState() {
  const w = window as EcologyFallbackWindow;
  w.__ecosFallbacks = [];
}

const LEGACY_URL = '/api/v1/ecos/dq/rules';
const GOV_URL = '/api/v1/dq/rules';

function stubStorage() {
  const store: Record<string, string> = {};
  vi.stubGlobal(
    'localStorage',
    {
      getItem: (k: string) => store[k] ?? null,
      setItem: (k: string, v: string) => {
        store[k] = v;
      },
      removeItem: (k: string) => {
        delete store[k];
      },
      clear: () => {
        for (const k of Object.keys(store)) delete store[k];
      },
      length: 0,
      key: ((): string | null => null),
    },
  );
}

const okEnvelope = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ code: 0, message: 'ok', data }),
});

const notFoundResponse = (body: string) => ({
  ok: false,
  status: 404,
  text: async () => body,
  json: async () => ({ code: 404, message: body }),
});

/** 测试环境最小浏览器面兜底（jsdom 缺失/不可用时按 node 环境运行）。 */
function ensureBrowserGlobals() {
  if (typeof globalThis.window !== "undefined") return;
  const listeners = new Map<string, Array<(e: CustomEvent) => void>>();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const w: any = {
    navigator: { onLine: true },
    dispatchEvent: (e: CustomEvent) => {
      (listeners.get(e.type) ?? []).forEach((fn) => fn(e));
      return true;
    },
    addEventListener: (type: string, fn: (e: CustomEvent) => void) => {
      if (!listeners.has(type)) listeners.set(type, []);
      listeners.get(type)!.push(fn);
    },
    removeEventListener: (type: string, fn: (e: CustomEvent) => void) => {
      const arr = listeners.get(type) ?? [];
      listeners.set(type, arr.filter((x) => x !== fn));
    },
    // F02-08 规格 6 遥测钩子
    __ecosFallbacks: [] as Array<Record<string, unknown>>,
  };
  Object.defineProperty(globalThis, "window", { value: w, configurable: true, writable: true });
  Object.defineProperty(globalThis, "localStorage", {
    value: {
      getItem: (): string | null => null,
      setItem: () => {},
      removeItem: () => {},
      clear: () => {},
    },
    configurable: true,
    writable: true,
  });
  Object.defineProperty(globalThis, "CustomEvent", {
    value: class CE extends Event {
      detail: unknown;
      constructor(type: string, init: { detail?: unknown } = {}) {
        super(type);
        this.detail = init.detail;
      }
    },
    configurable: true,
    writable: true,
  });
}

beforeEach(() => {
  ensureBrowserGlobals();
  stubStorage();
  clearFallbackState();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('fetchDqRulesWithFallback — 404 回落 legacy 且非静默 (R-3 ②)', () => {
  it('治理端点 404 时回落 legacy 并留下 warn + __ecosFallbacks 记录', async () => {
    const legacyData = [
      { id: 1, name: 'r1', ruleType: 'UNIQUENESS', enabled: true },
    ];
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).startsWith(GOV_URL)) {
        return notFoundResponse('{"code":404,"message":"资源不存在","traceId":"tc-404"}');
      }
      if (String(url).startsWith(LEGACY_URL)) return okEnvelope(legacyData);
      throw new Error(`unexpected url ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const eventSpy = vi.fn();
    window.addEventListener('dq-fallback', eventSpy);

    const rules = await fetchDqRulesWithFallback();

    expect(rules).toHaveLength(1);
    expect(rules[0].ruleName).toBe('r1');
    // legacy 回落被尝试
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(LEGACY_URL),
      expect.anything(),
    );
    // 非静默：warn + 遥测记录 + 事件
    expect(warnSpy).toHaveBeenCalled();
    const warned = warnSpy.mock.calls.map((c) => c.join(' ')).join('\n');
    expect(warned).toContain(GOV_URL);
    const fallbacks = getFallbacks();
    expect(fallbacks).toHaveLength(1);
    expect(fallbacks[0]).toMatchObject({ path: `${GOV_URL}`, status: 404 });
    expect(eventSpy).toHaveBeenCalledTimes(1);

    window.removeEventListener('dq-fallback', eventSpy);
  });

  it('治理端点正常返回时不回落 legacy、不 warn', async () => {
    const govs = [{ id: 'g1', ruleName: 'g', ruleCode: 'GC', category: 'C', domain: 'D', ruleType: 'T', severity: 'HIGH', targetKind: 'TABLE', status: 'ACTIVE' }];
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).startsWith(GOV_URL)) return okEnvelope(govs);
      throw new Error(`legacy must NOT be called, got ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    const rules = await fetchDqRulesWithFallback();

    expect(rules).toHaveLength(1);
    expect(rules[0].ruleCode).toBe('GC');
    expect(warnSpy).not.toHaveBeenCalled();
    expect((window as { __ecosFallbacks: unknown[] }).__ecosFallbacks).toHaveLength(0);
  });
});

describe('recordDqFallback — 遥测记录契约', () => {
  it('记录 path/status/traceId/note 且 console.warn 一次', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    recordDqFallback('/api/v1/dq/rules', 'test-fallback', { status: 404, traceId: 'tc-x' });
    const f = (window as unknown as { __ecosFallbacks: Array<Record<string, unknown>> }).__ecosFallbacks;
    expect(f).toHaveLength(1);
    expect(f[0]).toMatchObject({ path: '/api/v1/dq/rules', status: 404, traceId: 'tc-x' });
    expect(warnSpy).toHaveBeenCalledTimes(1);
  });
});
