/**
 * MonitoringCenterTabDeepLink.test.tsx — D67：监控中心 Tab 深链接
 *
 * 缺陷：`MonitoringCenter` 用 `useState(TABS[0].id)` 初始化 tab，不读 `?tab=`，
 * 导致 `#/monitor?tab=twins` 落到「基础指标」——深链接/分享/刷新全部失效。
 * 同仓 `SecurityCenterLayout` 已有正确形态（URL 为单一状态源），本测试把该口径钉到监控中心。
 *
 * 断言：
 *   C1 `?tab=twins` → 数字孪生面板渲染（非基础指标）
 *   C2 未知 tab（`?tab=ghost`）→ 回落第一个 Tab（禁白屏）
 *   C3 缺省无参 → 第一个 Tab
 *   C4 点击 Tab → URL 同步（tab 参数写回，replace 语义不新增历史）
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { ThemeProvider } from '../../components/ThemeContext';
import { LanguageProvider } from '../../components/LanguageContext';
import MonitoringCenter from '../MonitoringCenter';

vi.mock('../monitoring/tabs/BasicMonitoringTab', () => ({
  default: () => <div data-testid="panel-metrics">basic-metrics-panel</div>,
}));
vi.mock('../monitoring/tabs/DigitalTwinTab', () => ({
  default: () => <div data-testid="panel-twins">digital-twin-panel</div>,
}));
vi.mock('../EngineMonitor', () => ({
  default: () => <div data-testid="panel-engines">engine-monitor-panel</div>,
}));
vi.mock('../../components/common/Toast', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="loc">{loc.pathname + loc.search}</div>;
}

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ThemeProvider>
        <LanguageProvider>
          <MonitoringCenter />
          <LocationProbe />
        </LanguageProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe('D67 MonitoringCenter Tab 深链接', () => {
  beforeEach(() => {
    if (!window.ResizeObserver) {
      window.ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
      } as unknown as typeof window.ResizeObserver;
    }
  });
  afterEach(() => cleanup());

  it('C1 `?tab=twins` 直达数字孪生面板', () => {
    renderAt('/monitor?tab=twins');
    expect(screen.getByTestId('panel-twins')).toBeInTheDocument();
    expect(screen.queryByTestId('panel-metrics')).toBeNull();
  });

  it('C2 未知 tab 回落第一个面板（不白屏）', () => {
    renderAt('/monitor?tab=ghost');
    expect(screen.getByTestId('panel-metrics')).toBeInTheDocument();
    expect(screen.queryByTestId('panel-twins')).toBeNull();
  });

  it('C3 无参数时渲染第一个面板', () => {
    renderAt('/monitor');
    expect(screen.getByTestId('panel-metrics')).toBeInTheDocument();
  });

  it('C4 点击 Tab 后 URL 同步为 tab 参数', () => {
    renderAt('/monitor');
    const twinsTab = screen.getByRole('button', { name: /数字孪生|Digital Twin/ });
    fireEvent.click(twinsTab);
    expect(screen.getByTestId('panel-twins')).toBeInTheDocument();
    expect(screen.getByTestId('loc').textContent).toContain('tab=twins');
  });
});
