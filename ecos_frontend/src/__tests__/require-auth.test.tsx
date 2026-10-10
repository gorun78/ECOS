// @vitest-environment jsdom
/**
 * W203/C185 门禁 — RequireAuth fail-closed
 * 冻结门禁名：unit: require-auth.test.tsx › unauthorizedRouteRendersNoAccessWithoutDataFetch
 * @license Apache-2.0
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import RequireAuth from '../components/RequireAuth';
import { ThemeProvider } from '../components/ThemeContext';

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  const store: Record<string, string> = {};
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { Object.keys(store).forEach((k) => delete store[k]); },
    length: 0,
    key: (): string | null => null,
  });
  fetchSpy = vi.fn();
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <ThemeProvider>
        <Routes>
          <Route
            path="/protected"
            element={
              <RequireAuth>
                <div>PROTECTED_SECRET_DATA</div>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<div>LOGIN_PAGE</div>} />
          <Route path="/no-access" element={<div>NO_ACCESS_PAGE</div>} />
        </Routes>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

function isAuthMeCall(call: unknown): boolean {
  const url = typeof call === 'object' && call !== null && '0' in call
    ? String((call as { 0: unknown })[0])
    : '';
  return url.includes('/api/v1/auth/me');
}

describe('RequireAuth fail-closed (W203)', () => {
  it('unauthorizedRouteRendersNoAccessWithoutDataFetch', async () => {
    // ── 分支 1：无有效 token → 跳登录；不发起任何 fetch（探测与数据请求都没有）
    localStorage.setItem('token', ''); // 空值不算有效 token（getAuthToken 要求非空）
    const r1 = renderGuarded();
    expect(await screen.findByText('LOGIN_PAGE')).toBeInTheDocument();
    expect(screen.queryByText('PROTECTED_SECRET_DATA')).not.toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledTimes(0);
    r1.unmount();

    // ── 分支 2：有 token 但 /auth/me 传输失败 → fail-closed 渲染 NoAccess，
    //    children（及其数据请求）绝不挂载；fetch 只允许出现 auth 探测本身。
    fetchSpy.mockImplementation(() => Promise.reject(new TypeError('network down')));
    localStorage.setItem('token', 'stale-token-value');
    const r2 = renderGuarded();
    await waitFor(() => {
      expect(screen.getByText('NO_ACCESS_PAGE')).toBeInTheDocument();
    });
    expect(screen.queryByText('PROTECTED_SECRET_DATA')).not.toBeInTheDocument();
    const nonAuthCalls = fetchSpy.mock.calls.filter((c) => !isAuthMeCall(c));
    expect(nonAuthCalls, 'guard must never issue data requests before authorization settles').toEqual([]);
    r2.unmount();

    // ── 分支 3：403 → NoAccess；200 之外的真实错误不得合成放行（回归护栏）
    fetchSpy.mockImplementation(() =>
      Promise.resolve({ status: 403, json: () => Promise.resolve({}) } as unknown as Response),
    );
    localStorage.setItem('token', 'another-token');
    const r3 = renderGuarded();
    await waitFor(() => {
      expect(screen.getByText('NO_ACCESS_PAGE')).toBeInTheDocument();
    });
    expect(screen.queryByText('PROTECTED_SECRET_DATA')).not.toBeInTheDocument();
    r3.unmount();
  });
});
