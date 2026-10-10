// @vitest-environment jsdom
/**
 * W214/C196 门禁 — 路由级错误边界：单页崩溃不拖垮兄弟路由
 * 冻结门禁名：unit: ErrorBoundary.route.test.tsx › siblingRoutesSurviveThrow
 * 使用生产 App.tsx 的真实 <Outlet/> 层接线（ErrorBoundary key=当前路由根）。
 * @license Apache-2.0
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ReactElement } from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import App from '../App';
import { LanguageProvider } from '../components/LanguageContext';
import { ThemeProvider } from '../components/ThemeContext';

// 屏蔽 App 的后台轮询请求，让断言聚焦于边界行为本身。
vi.mock('../api', () => ({
  apiHealth: () => Promise.resolve('UP'),
  notifyNetworkUp: vi.fn(),
}));
vi.mock('../services/taskCenter', () => ({
  apiTaskStats: () =>
    Promise.resolve({ total: 0, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 }),
}));

// 显式 ReactElement 返回类型：仅 throw 的函数被推断为 () => void，React 19 类型下不可作 JSX 组件（TS2786）
function Boom(): ReactElement {
  throw new Error('boom-render-crash');
}

function Fine() {
  return <div>FINE_SIBLING_OK</div>;
}

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
  if (typeof window !== 'undefined' && !window.matchMedia) {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: ((query: string) => ({
        matches: false,
        media: query,
        onchange: null as null | ((e: MediaQueryListEvent) => void),
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      })) as unknown as typeof window.matchMedia,
    });
  }
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('route-level ErrorBoundary (W214)', () => {
  it('siblingRoutesSurviveThrow', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <MemoryRouter initialEntries={['/boom']}>
        <LanguageProvider>
          <ThemeProvider>
            <Routes>
              <Route element={<App />}>
                <Route path="boom" element={<Boom />} />
                <Route path="monitor" element={<Fine />} />
                <Route path="world_model" element={<Fine />} />
              </Route>
            </Routes>
          </ThemeProvider>
        </LanguageProvider>
      </MemoryRouter>,
    );

    // 1) 崩溃路由降级为友好错误页（默认 locale = zh）
    expect(await screen.findByText('页面渲染出错')).toBeInTheDocument();
    expect(screen.queryByText('FINE_SIBLING_OK')).not.toBeInTheDocument();
    expect(consoleErrorSpy).toHaveBeenCalled();

    // 2) 应用外壳（Sidebar 导航按钮）不受影响 —— 崩溃被限制在 Outlet 内
    expect(document.getElementById('side-nav-world_model'), 'sidebar shell must survive').not.toBeNull();

    // 3) 导航到兄弟路由：key=路由根 使边界整体重挂载，兄弟路由正常渲染
    const navBtn = document.getElementById('side-nav-monitor');
    expect(navBtn, 'sidebar nav button for sibling route must exist').not.toBeNull();
    fireEvent.click(navBtn as HTMLElement);

    expect(await screen.findByText('FINE_SIBLING_OK')).toBeInTheDocument();
    expect(screen.queryByText('页面渲染出错')).not.toBeInTheDocument();

    consoleErrorSpy.mockRestore();
  });
});
