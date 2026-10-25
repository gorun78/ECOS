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
  // App.tsx pollVersion 启动即调用（Sidebar 认知引擎版本面板）：mock 缺导出会把
  // 边界行为测试拖进 defineComponent 崩道——siblingRoutesSurviveThrow 红（W214/C196 既有红收口）。
  apiCognitiveHealth: () => Promise.reject(new Error('mock-not-required')),
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
                <Route path="workbench" element={<Fine />} />
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

    // 2) 应用外壳（Sidebar 导航按钮）不受影响 —— 崩溃被限制在 Outlet 内。
    // 注：2026-10-14 菜单重构后「总览/工作区/资源概览」三组平铺渲染，控制面板组
    // 靠底默认折叠（side-nav-monitor 等控制项在折叠态不在 DOM）；原断言 world_model
    // 已摘出菜单（2026-10-14 菜单外保留），路由按钮侧改用平铺工作区项 workbench。
    expect(document.getElementById('side-nav-workbench'), 'sidebar shell must survive').not.toBeNull();

    // 3) 导航到兄弟路由：key=路由根 使边界整体重挂载，兄弟路由正常渲染
    const navBtn = document.getElementById('side-nav-workbench');
    expect(navBtn, 'sidebar nav button for sibling route must exist').not.toBeNull();
    fireEvent.click(navBtn as HTMLElement);

    expect(await screen.findByText('FINE_SIBLING_OK')).toBeInTheDocument();
    expect(screen.queryByText('页面渲染出错')).not.toBeInTheDocument();

    consoleErrorSpy.mockRestore();
  });
});
