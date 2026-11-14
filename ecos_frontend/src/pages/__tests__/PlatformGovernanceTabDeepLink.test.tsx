/**
 * PlatformGovernanceTabDeepLink.test.tsx — D67 同族：平台治理 Tab 深链接
 *
 * 缺陷：`PlatformGovernance` 用 `useState<TabId>("deployment")` 初始化，
 * 不读 `?tab=` ⇒ 矩阵登记的入口 `?tab=config` / `?tab=diagnostics` 打不开（恒落「部署情况」）。
 * 本测试钉住三 Tab 均可深链直达 + 未知值回落 + 点击同步 URL。
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { ThemeProvider } from '../../components/ThemeContext';
import { LanguageProvider } from '../../components/LanguageContext';
import PlatformGovernance from '../PlatformGovernance';

vi.mock('../governance/DeploymentTab', () => ({
  default: () => <div data-testid="tab-deployment">deployment-panel</div>,
}));
vi.mock('../governance/EngineConfigTab', () => ({
  default: () => <div data-testid="tab-config">config-panel</div>,
}));
vi.mock('../governance/DiagnosticsTab', () => ({
  default: () => <div data-testid="tab-diagnostics">diagnostics-panel</div>,
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
          <PlatformGovernance />
          <LocationProbe />
        </LanguageProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe('D67 PlatformGovernance Tab 深链接', () => {
  afterEach(() => cleanup());

  it('`?tab=config` 直达引擎配置面板', () => {
    renderAt('/platform-governance?tab=config');
    expect(screen.getByTestId('tab-config')).toBeInTheDocument();
    expect(screen.queryByTestId('tab-deployment')).toBeNull();
  });

  it('`?tab=diagnostics` 直达平台自检面板', () => {
    renderAt('/platform-governance?tab=diagnostics');
    expect(screen.getByTestId('tab-diagnostics')).toBeInTheDocument();
  });

  it('未知 tab 回落部署面板', () => {
    renderAt('/platform-governance?tab=ghost');
    expect(screen.getByTestId('tab-deployment')).toBeInTheDocument();
  });

  it('无参数默认部署面板', () => {
    renderAt('/platform-governance');
    expect(screen.getByTestId('tab-deployment')).toBeInTheDocument();
  });

  it('点击「引擎配置」后 URL 同步 tab=config', () => {
    renderAt('/platform-governance');
    fireEvent.click(screen.getByRole('tab', { name: /引擎配置/ }));
    expect(screen.getByTestId('tab-config')).toBeInTheDocument();
    expect(screen.getByTestId('loc').textContent).toContain('tab=config');
  });
});
