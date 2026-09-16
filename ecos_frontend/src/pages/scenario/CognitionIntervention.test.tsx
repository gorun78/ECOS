/**
 * CognitionIntervention.test.tsx — PMO-59 P4b（前端人机干预面板）组件级验收测试。
 *
 * 覆盖：
 *  1. CounterfactualTab：表单校验 + POST /api/v1/cognitive/counterfactual 请求体 + 四指标/敏感性/假设留痕渲染
 *  2. ReplayTab：版本链加载（beliefs）+ GET replay 渲染 replayedVersion/currentVersion/believedDistribution
 *  3. OverrideTab：覆盖 ConfirmDialog 二次确认 + POST beliefs/{variable}/override 请求体 + 写后刷新
 *  4. MentalReviewTab：GET mental-reviews 渲染 warnAlerts（faultContext/reviewTag）+ 假设时间线 + summary
 *  5. CognitionPanel：子视图切换渲染各干预面板（人机干预入口通达）
 *
 * 说明：PMO-59 P4b 验收原口径为浏览器 E2E；本执行环境未暴露浏览器工具，按指令允许的降级路径
 * 以「tsc 绿 + 组件级验证」补足（详见 Phase4 验收记录 §P4b）。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, act, fireEvent } from '@testing-library/react';
import { ThemeProvider } from '../../components/ThemeContext';
import { LanguageProvider } from '../../components/LanguageContext';
import { ToastProvider } from '../../components/common/Toast';
import CounterfactualTab from './CounterfactualTab';
import ReplayTab from './ReplayTab';
import OverrideTab from './OverrideTab';
import MentalReviewTab from './MentalReviewTab';
import CognitionPanel from './CognitionPanel';

vi.mock('../../api', () => ({
  apiFetchData: vi.fn(),
}));

import { apiFetchData } from '../../api';

const mockApi = apiFetchData as unknown as ReturnType<typeof vi.fn>;

/** 统一 Provider 包裹（主题 + i18n + toast，与运行态一致） */
function renderWithProviders(node: React.ReactNode) {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <ToastProvider>{node}</ToastProvider>
      </ThemeProvider>
    </LanguageProvider>,
  );
}

/** 等待异步渲染/防抖（beliefs 加载有 400ms 防抖） */
async function flush(ms = 0) {
  await act(async () => {
    await new Promise<void>((resolve) => {
      setTimeout(resolve, ms);
    });
  });
  await act(async () => {
    await Promise.resolve();
  });
}

beforeEach(() => {
  mockApi.mockReset();
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// ── 1. 仿真推演（T1） ────────────────────────────────────────
describe('CounterfactualTab (PMO-59 P4b T1)', () => {
  it('未填必填项时发起推演 → 不发请求（前端校验拦截）', async () => {
    const { container } = renderWithProviders(<CounterfactualTab />);
    const runBtn = container.querySelectorAll('button');
    await act(async () => {
      fireEvent.click(runBtn[runBtn.length - 1]);
    });
    expect(mockApi).not.toHaveBeenCalled();
  });

  it('填写域/变量/干预项 → POST 请求体正确 + 渲染四指标/敏感性 Top3/假设留痕', async () => {
    mockApi.mockResolvedValue({
      requestEcho: { domain: 'pricing', variableName: 'competitor_price_cut_prob', sampleCount: 200, seed: 42 },
      baselineMean: 1.35,
      intervenedMean: 1.85,
      riskMetrics: { expectedBenefit: 0.5, maxDrawdown: 0.25, lossProbability: 0.13, volatilityRange: [1.5, 3.5] },
      sensitivityTop3: [{ variable: 'competitor_price_cut_prob', sensitivity: 0.139 }],
      assumptionRefs: ['cog_hyp_valid_001'],
      excludedAssumptions: [{ hypothesisId: 'cog_hyp_invalid_001', status: 'INVALIDATED', invalidReason: 'refuting 证据命中' }],
      sampleCount: 200,
      seed: 42,
      variableName: 'competitor_price_cut_prob',
      scenarioSummary: 'pricing ΔE=+0.5',
    });
    const { container } = renderWithProviders(<CounterfactualTab />);
    const inputs = container.querySelectorAll('input');
    await act(async () => {
      fireEvent.change(inputs[0], { target: { value: 'pricing' } });
      fireEvent.change(inputs[1], { target: { value: 'competitor_price_cut_prob' } });
      fireEvent.change(inputs[2], { target: { value: '200' } });
      fireEvent.change(inputs[3], { target: { value: '42' } });
      fireEvent.change(inputs[4], { target: { value: 'competitor_price_cut_prob' } });
      fireEvent.change(inputs[5], { target: { value: '0.5' } });
    });
    const buttons = container.querySelectorAll('button');
    const runBtn = buttons[buttons.length - 1];
    await act(async () => {
      fireEvent.click(runBtn);
    });
    await flush();

    expect(mockApi).toHaveBeenCalledTimes(1);
    const [url, options] = mockApi.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/cognitive/counterfactual');
    expect(options.method).toBe('POST');
    const body = JSON.parse(String(options.body)) as Record<string, unknown>;
    expect(body.domain).toBe('pricing');
    expect(body.variableName).toBe('competitor_price_cut_prob');
    expect(body.sampleCount).toBe(200);
    expect(body.seed).toBe(42);
    expect(body.interventions).toEqual([
      { variableName: 'competitor_price_cut_prob', op: 'SET', value: 0.5 },
    ]);

    const text = container.textContent ?? '';
    expect(text).toContain('0.5000'); // expectedBenefit
    expect(text).toContain('0.2500'); // maxDrawdown
    expect(text).toContain('0.1300'); // lossProbability
    expect(text).toContain('[1.5000, 3.5000]'); // volatilityRange
    expect(text).toContain('0.1390'); // sensitivity Top3
    expect(text).toContain('cog_hyp_valid_001'); // assumptionRefs
    expect(text).toContain('cog_hyp_invalid_001'); // excludedAssumptions
    expect(text).toContain('INVALIDATED');
  });
});

// ── 2. 时间回放（T2） ────────────────────────────────────────
describe('ReplayTab (PMO-59 P4b T2)', () => {
  it('输入业务域 → 加载版本链；选版本回放 → GET replay 渲染版本对比与分布快照', async () => {
    mockApi.mockImplementation(async (url: string) => {
      if (String(url).startsWith('/api/v1/cognitive/beliefs?')) {
        return [
          { id: 'b1', variableName: 'price_cut_prob', domain: 'pricing', version: 1, distribution: [{ outcome: 'none', prob: 0.7 }] },
          { id: 'b2', variableName: 'price_cut_prob', domain: 'pricing', version: 3, distribution: [{ outcome: 'high', prob: 0.6 }] },
        ];
      }
      return {
        baselineMean: 1.2,
        intervenedMean: 1.7,
        riskMetrics: { expectedBenefit: 0.5, maxDrawdown: 0, lossProbability: 0, volatilityRange: [1, 2] },
        sensitivityTop3: [],
        assumptionRefs: [],
        excludedAssumptions: [],
        sampleCount: 1000,
        seed: 42,
        replayMeta: {
          replayedVersion: 1,
          believedDistribution: [
            { outcome: 'none', prob: 0.7 },
            { outcome: 'mild', prob: 0.25 },
            { outcome: 'aggressive', prob: 0.05 },
          ],
          versionUpdatedAt: '2026-09-13T10:00:00',
          assumptionsValidAtReplayTime: 2,
          currentVersion: 3,
        },
      };
    });
    const { container } = renderWithProviders(<ReplayTab />);
    const inputs = container.querySelectorAll('input');
    await act(async () => {
      fireEvent.change(inputs[0], { target: { value: 'pricing' } });
    });
    await flush(600);

    const selects = container.querySelectorAll('select');
    // 版本链已加载：变量下拉出现 price_cut_prob
    expect(Array.from(selects[0].options).map((o) => o.value)).toContain('price_cut_prob');
    await act(async () => {
      fireEvent.change(selects[0], { target: { value: 'price_cut_prob' } });
    });
    await flush();
    // 默认带出最新版本 v3
    expect(selects[1].value).toBe('3');
    await act(async () => {
      fireEvent.change(selects[1], { target: { value: '1' } });
    });
    const buttons = container.querySelectorAll('button');
    await act(async () => {
      fireEvent.click(buttons[buttons.length - 1]);
    });
    await flush();

    const replayCall = mockApi.mock.calls.find((c) => String(c[0]).includes('/replay')) as [string, RequestInit];
    expect(replayCall).toBeDefined();
    expect(String(replayCall[0])).toContain('/api/v1/cognitive/beliefs/price_cut_prob/1/replay?');
    expect(String(replayCall[0])).toContain('domain=pricing');

    const text = container.textContent ?? '';
    expect(text).toContain('v1'); // replayedVersion
    expect(text).toContain('v3'); // currentVersion
    expect(text).toContain('none'); // believedDistribution
    expect(text).toContain('aggressive');
  });
});

// ── 3. 人工覆写（T3） ────────────────────────────────────────
describe('OverrideTab (PMO-59 P4b T3)', () => {
  it('选变量 → 覆写提交走 ConfirmDialog 二次确认 → POST override + 写后重拉列表标记人工覆写', async () => {
    mockApi.mockImplementation(async (url: string, options?: RequestInit) => {
      if (options?.method === 'POST') {
        return {
          id: 'b9',
          variableName: 'price_cut_prob',
          domain: 'pricing',
          version: 5,
          manualOverride: true,
          overrideReason: '专家复盘判定',
          distribution: [{ outcome: 'high', prob: 1 }],
        };
      }
      return [
        { id: 'b2', variableName: 'price_cut_prob', domain: 'pricing', version: 3, distribution: [{ outcome: 'high', prob: 0.6 }, { outcome: 'low', prob: 0.4 }] },
        { id: 'b9', variableName: 'price_cut_prob', domain: 'pricing', version: 5, manualOverride: true, overrideReason: '专家复盘判定', distribution: [{ outcome: 'high', prob: 1 }] },
      ];
    });
    const { container } = renderWithProviders(<OverrideTab />);
    const inputs = container.querySelectorAll('input');
    await act(async () => {
      fireEvent.change(inputs[0], { target: { value: 'pricing' } });
    });
    await flush(600);

    const select = container.querySelector('select') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.value)).toContain('price_cut_prob');
    await act(async () => {
      fireEvent.change(select, { target: { value: 'price_cut_prob' } });
    });
    await flush();

    // 概率和不为 1（v3 分布 0.6+0.4=1 → 先改成 0.6 触发校验）
    const distInputs = container.querySelectorAll('input[type="number"]');
    await act(async () => {
      fireEvent.change(distInputs[0], { target: { value: '0.6' } });
    });
    const buttons = container.querySelectorAll('button');
    await act(async () => {
      fireEvent.click(buttons[buttons.length - 1]);
    });
    await flush();
    expect(container.textContent ?? '').toContain('Σprob=0.6000');
    expect(screen.queryByText('确认人工覆写')).toBeNull(); // 校验未通过 → 不弹确认

    // 修复概率和 + 填理由 → 弹确认
    await act(async () => {
      fireEvent.change(distInputs[0], { target: { value: '1' } });
    });
    const textInputs = container.querySelectorAll('input:not([type="number"])');
    // [0]=domain, [1]=outcome, [2]=reason, [3]=evidenceId
    await act(async () => {
      fireEvent.change(textInputs[2], { target: { value: '专家复盘判定' } });
    });
    await act(async () => {
      fireEvent.click(buttons[buttons.length - 1]);
    });
    await flush();
    expect(screen.getByText('确认人工覆写')).toBeTruthy();

    // 确认 → 发覆写请求
    const confirmBtn = screen.getByText('确认覆写');
    await act(async () => {
      fireEvent.click(confirmBtn);
    });
    await flush();

    const postCall = mockApi.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'POST') as [string, RequestInit];
    expect(String(postCall[0])).toBe('/api/v1/cognitive/beliefs/price_cut_prob/override');
    const body = JSON.parse(String(postCall[1].body)) as Record<string, unknown>;
    expect(body.domain).toBe('pricing');
    expect(body.overrideReason).toBe('专家复盘判定');
    expect(body.discreteDistribution).toEqual([{ outcome: 'high', prob: 1 }]);

    // 写后刷新：覆写成功后重拉列表并渲染人工覆写标记
    const listCalls = mockApi.mock.calls.filter((c) => String(c[0]).startsWith('/api/v1/cognitive/beliefs?'));
    expect(listCalls.length).toBeGreaterThanOrEqual(2);
    expect(container.textContent ?? '').toContain('人工覆写');
  });
});

// ── 4. 假设失效告警 + 复盘（T4） ──────────────────────────────
describe('MentalReviewTab (PMO-59 P4b T4)', () => {
  it('查询复盘 → 渲染 warnAlerts（faultContext/reviewTag）+ 假设时间线 + 作废 run + summary', async () => {
    mockApi.mockResolvedValue({
      tag: 'P2b-mental-layer-review',
      since: '2026-09-16T00:00:00',
      reconstructionMode: 'three-table-version-chain+V130-impact',
      beliefTimelines: [{ variableName: 'price_cut_prob', domain: 'pricing', versions: [{ version: 4, distribution: [{ outcome: 'high', prob: 1 }], manualOverride: true, updatedAt: '2026-09-14T10:00:00' }] }],
      hypotheses: [{ id: 'h1', hypothesisCode: 'HYP-001', domain: 'pricing', status: 'INVALIDATED', isValid: false, invalidAt: '2026-09-16T22:52:03', invalidReason: 'refuting 命中' }],
      evidence: [],
      runImpacts: [{ eventId: 'e1', runId: 'run-1', hypothesisId: 'h1', autoDetected: true, supersededAt: '2026-09-16T22:52:05' }],
      warnAlerts: [
        {
          id: 'warn_1',
          logId: 'log_1',
          warnType: 'COGNITIVE_HYPOTHESIS_INVALIDATED',
          warnLevel: 'WARN',
          warnObjName: 'cognitive-mental-layer',
          warnMessage: '认知假设失效: HYP-001',
          faultContext: { reviewTag: 'P2b-mental-layer-review', autoDetected: false },
          reviewTag: 'P2b-mental-layer-review',
          warnTime: '2026-09-16T22:52:03',
        },
      ],
      summary: { hypothesesInvalidated: 1, warnAlerts: 1 },
    });
    const { container } = renderWithProviders(<MentalReviewTab />);
    const buttons = container.querySelectorAll('button');
    await act(async () => {
      fireEvent.click(buttons[0]);
    });
    await flush();

    const [url] = mockApi.mock.calls[0] as [string];
    expect(String(url)).toContain('/api/v1/cognitive/mental-reviews?tag=P2b-mental-layer-review');

    const text = container.textContent ?? '';
    expect(text).toContain('COGNITIVE_HYPOTHESIS_INVALIDATED');
    expect(text).toContain('P2b-mental-layer-review');
    expect(text).toContain('cognitive-mental-layer');
    expect(text).toContain('HYP-001');
    expect(text).toContain('INVALIDATED');
    expect(text).toContain('run-1');
    expect(text).toContain('自动检测');
    expect(text).toContain('warnAlerts');
  });
});

// ── 5. CognitionPanel 子视图入口（T1~T4 通达性） ───────────────
describe('CognitionPanel 子视图切换 (PMO-59 P4b)', () => {
  it('默认渲染诊断子视图；切到仿真推演 → 渲染推演表单（人机干预入口）', async () => {
    mockApi.mockResolvedValue([]);
    const { container } = renderWithProviders(<CognitionPanel scenario={undefined} />);
    await flush();

    // 子视图切换按钮全部就位
    const text0 = container.textContent ?? '';
    expect(text0).toContain('认知诊断');
    expect(text0).toContain('仿真推演');
    expect(text0).toContain('时间回放');
    expect(text0).toContain('人工覆写');
    expect(text0).toContain('告警与复盘');

    const tabBtn = Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.includes('仿真推演'));
    expect(tabBtn).toBeTruthy();
    await act(async () => {
      fireEvent.click(tabBtn as HTMLButtonElement);
    });
    await flush();
    expect(container.textContent ?? '').toContain('干预项');
    expect(container.textContent ?? '').toContain('敏感性 Top3');
  });
});
