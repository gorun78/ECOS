// @vitest-environment node
/**
 * httpClient.headers.spec.ts — B30：调用方 `options.headers` 不得冲掉默认 Authorization。
 *
 * <p>缺陷实证（浏览器）：`POST /api/v1/cognitive/frameworks/select` 在页面上恒 403，
 * 而同一 payload 直连网关为 200。根因是 `apiFetchData` 里 `fetch(url, { headers, ...options })`：
 * 调用方一传 `headers` 就整体覆盖默认头，Authorization 被丢掉 ⇒ 匿名请求被 fail-closed 拦下。</p>
 *
 * <p>判据：三条传输腿（apiFetch / apiFetchData / doFetch）在调用方自带 headers 时，
 * 仍必须带 `authorization: Bearer <token>`，且同一 header 不得以大小写两种形态重复下发
 * （重复 Content-Type 会被拼成 `application/json, application/json`，后端 500 实证）。</p>
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, apiFetchData, doFetch } from '../httpClient';

type Captured = { url: string; init: RequestInit };

function stubFetch(): Captured[] {
  const calls: Captured[] = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit): Promise<Response> => {
    calls.push({ url, init });
    return {
      ok: true,
      status: 200,
      headers: { get: (_k: string): string | null => null },
      json: () => Promise.resolve({ code: 0, data: { ok: true } }),
      text: () => Promise.resolve('{"code":0,"data":{"ok":true}}'),
    } as unknown as Response;
  }));
  return calls;
}

function headerEntries(init: RequestInit): Array<[string, string]> {
  return Object.entries((init.headers ?? {}) as Record<string, string>);
}

function header(init: RequestInit, name: string): string | undefined {
  const e = headerEntries(init).find(([k]) => k.toLowerCase() === name.toLowerCase());
  return e?.[1];
}

describe('httpClient 默认头合并（B30）', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => (k === 'token' ? 'tok-123' : null),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('apiFetchData：调用方传 headers 时仍带 Authorization', async () => {
    const calls = stubFetch();
    await apiFetchData('/api/v1/cognitive/frameworks/select', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    expect(calls).toHaveLength(1);
    expect(header(calls[0].init, 'authorization')).toBe('Bearer tok-123');
  });

  it('apiFetchData：Content-Type 不得大小写重复下发', async () => {
    const calls = stubFetch();
    await apiFetchData('/x', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    const cts = headerEntries(calls[0].init).filter(([k]) => k.toLowerCase() === 'content-type');
    expect(cts).toHaveLength(1);
    expect(cts[0][1]).toBe('application/json');
  });

  it('apiFetch：调用方自定义头与 Authorization 并存', async () => {
    const calls = stubFetch();
    await apiFetch('/api/v1/x', { headers: { 'X-Custom': '1' } });
    expect(header(calls[0].init, 'authorization')).toBe('Bearer tok-123');
    expect(header(calls[0].init, 'x-custom')).toBe('1');
  });

  it('doFetch：调用方自定义头与 Authorization 并存', async () => {
    const calls = stubFetch();
    await doFetch('/api/v1/y', { method: 'POST', headers: { 'X-Custom': '2' } });
    expect(header(calls[0].init, 'authorization')).toBe('Bearer tok-123');
    expect(header(calls[0].init, 'x-custom')).toBe('2');
  });
});
