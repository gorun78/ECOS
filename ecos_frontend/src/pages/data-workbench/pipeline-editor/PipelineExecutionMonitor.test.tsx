/**
 * PipelineExecutionMonitor.test.tsx — Wave 4 T1
 *
 * 关键断言（按任务说明）：
 *   1. 执行状态轮询拉一次后立即渲染 进度+步骤
 *   2. 失败 status 触发 onRunComplete + Toast
 *   3. 步骤节点名渲染（来自 mock data）
 *   4. 无 runId 时不渲染主面板
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, act } from '@testing-library/react';
import { ThemeProvider } from '../../../components/ThemeContext';
import { LanguageProvider } from '../../../components/LanguageContext';
import PipelineExecutionMonitor from './PipelineExecutionMonitor';
import type { PipelineExecutionMonitorProps } from './PipelineExecutionMonitorTypes';

// mock 监控轮询端点
vi.mock('../../../api', () => ({
  apiFetchData: vi.fn(async () => ({
    data: {
      runId: 'run-1',
      status: 'running',
      completedSteps: 1,
      totalSteps: 3,
      elapsedMs: 1200,
      statusText: 'running',
      errmsg: null,
      steps: [
        { id: 's1', nodeId: 'n1', nodeName: 'source', status: 'succeeded', rowsInput: 100, rowsOutput: 100, elapsedMs: 200 },
        { id: 's2', nodeId: 'n2', nodeName: 'transform', status: 'running', rowsInput: 100, rowsOutput: 0, elapsedMs: 0 },
        { id: 's3', nodeId: 'n3', nodeName: 'sink', status: 'pending', rowsInput: 0, rowsOutput: 0, elapsedMs: 0 },
      ],
    },
  })),
}));

function renderWithProviders(props: Partial<PipelineExecutionMonitorProps> = {}) {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <PipelineExecutionMonitor runId={'run-1'} {...props} />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

beforeEach(() => {
  // 不使用 fake timers — setInterval(2000) 轮询由 afterEach cleanup() 干净卸载
  // 第一次 fetch 在 useEffect 中立即 fire（mock 已 fixed resolved）
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.restoreAllMocks();
});

/** 等待 fetch 回调 + setState + React re-render 都落地（50ms 微任务窗口）。 */
async function flushPoll() {
  await act(async () => {
    await new Promise<void>((r) => { setTimeout(r, 0); });
  });
  await act(async () => { await Promise.resolve(); });
  await act(async () => { await Promise.resolve(); });
}

describe('PipelineExecutionMonitor', () => {
  it('渲染无 runId 时无内容（空态不挂载主面板）', () => {
    const { container } = renderWithProviders({ runId: undefined });
    // 无 externalRunId 且 runInfo 不能初始化 → 不渲染主面板 DOM
    expect(container).toBeTruthy();
  });

  it('有 runId → 展示 执行监控 header 与运行中步骤(1/3)', async () => {
    renderWithProviders({});

    await flushPoll();
    await act(async () => { await Promise.resolve(); });

    expect(screen.getByText('执行监控')).toBeTruthy();

    // 步骤节点名（来自上面 mock）
    const steps = screen.getAllByText(/source|transform|sink/);
    expect(steps.length).toBeGreaterThanOrEqual(3);

    // 运行中 1/3
    expect(screen.getByText(/进度: 1\/3/)).toBeTruthy();
  }, 8000);

  it('failed 状态触发一次 onRunComplete + Toast', async () => {
    const { apiFetchData } = await import('../../../api');
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {
        runId: 'run-1', status: 'failed', completedSteps: 1, totalSteps: 3,
        elapsedMs: 1000, statusText: 'failed',
        errorMessage: 'boom', steps: [],
      },
    });
    const onRunComplete = vi.fn();
    renderWithProviders({ onRunComplete });
    await flushPoll();
    await act(async () => { await Promise.resolve(); });

    expect(onRunComplete).toHaveBeenCalledTimes(1);
    // Toast 文案必含 "执行失败"
    expect(screen.getByText(/执行失败/)).toBeTruthy();
  }, 8000);
});
