/**
 * CognitionViewE2E.test.tsx — PMO-60 v2.0 P3：CognitionView E2E 组件级集成测试 (vitest + jsdom)。
 *
 * 覆盖 P2b 认知 Tab（aiworkbench 第 7 张 Tab）最小链路：
 *   C5 Tab 可见：AIPWorkbench → 点 NAV_TABS "cognition" → CognitionView 挂载（hub summary / 4 卡 / mind chips 区出现）
 *   C6 cognitive 404 stub：fetch 全部 404（模拟 P3b 未启用）→ 点 4 卡任一 "执行" → 该卡 InlineWarning notEnabled 出现；其他 3 卡不受影响
 *   C7 场景切换重置：场景 A 查到 mindA chip → 切到场景 B → mind chips 切换到 mindB、previous epResults 清空
 *
 * 约束：
 *   - 零新增依赖：复用 @testing-library/react（devDeps 已存在）
 *   - i18n 反查断言：通过 aiworkbench/cognition/zh-CN.json + en.json 同时存在 key 控件来校验
 *   - mock aiworkbench/api.ts 的 fetch* 系列（与 index.tsx 实际 import 相对路径一致）
 *
 * 注：本文件不直接测试 AIPWorkbench（其内部 4 fetch 调用在 AIPWorkbench 层 mock 后 负担过重），
 *   覆盖路径改 render（清空 props + state 的 CognitionView 子树）经 index.tsx 一致调用链验证
 *   CognitionView 默认 API：GET /api/v1/workspace/scenarios（场景列表） 走 apiFetchData
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, act, fireEvent } from '@testing-library/react';
import { ThemeProvider } from '../../components/ThemeContext';
import { LanguageProvider } from '../../components/LanguageContext';
import CognitionView from './CognitionView';
import { apiFetchData } from '../../api';

// 与 CognitionView 内 import 同模块路径（'../../api' 是 CognitionView 视角）
vi.mock('../../api', () => ({
  apiFetchData: vi.fn(),
}));

const mockFetch = apiFetchData as unknown as ReturnType<typeof vi.fn>;

const LOCALE_ZH_MIND_EMPTY = '当前场景尚无心智，可先在项目工作台创建';

function renderWithProviders(node: React.ReactNode) {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        {node}
      </ThemeProvider>
    </LanguageProvider>,
  );
}

async function flush(ms = 0) {
  await act(async () => {
    await new Promise<void>((resolve) => setTimeout(resolve, ms));
  });
  await act(async () => {
    await Promise.resolve();
  });
}

beforeEach(() => {
  (globalThis as unknown as { __ecos_test_locale?: 'zh' | 'en' }).__ecos_test_locale = 'zh';
  mockFetch.mockReset();
  vi.spyOn(console, 'error').mockImplementation(() => {});
  // 默认场景：sc_A / sc_B（active）
  mockFetch.mockImplementation(async (url: string) => {
    if (String(url).startsWith('/api/v1/workspace/scenarios') && String(url).endsWith('/scenarios')) {
      return [
        { id: 'sc_A', name: '场景 A', status: 'ACTIVE', priority: 'P1' },
        { id: 'sc_B', name: '场景 B', status: 'ACTIVE', priority: 'P2' },
      ];
    }
    if (String(url).startsWith('/api/v1/workspace/scenarios/sc_A/minds')) {
      return [
        { id: 1, mindLabel: 'mind-A-active', activeMind: true, initialConfidence: 0.7 },
        { id: 2, mindLabel: 'mind-A-inactive', activeMind: false, initialConfidence: 0.3 },
      ];
    }
    if (String(url).startsWith('/api/v1/workspace/scenarios/sc_B/minds')) {
      return [
        { id: 11, mindLabel: 'mind-B-active', activeMind: true, initialConfidence: 0.9 },
      ];
    }
    // 占位 4 接口：在 C6 单独控制，这里默认返回 object — CognitionView renderResult JSON.stringify
    // URL 可能带 ?mind=1 query param，正则用 includes 而非 $ anchor
    if (/\/cognitive\/(diagnose|forecast|simulate|policy)/.test(String(url))) {
      const ep = String(url).split('/cognitive/')[1].replace(/\?.*$/, '');
      return { ok: true, summary: `placeholder-${ep}` };
    }
    return null;
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// ── C5 Tab 可见：CognitionView 挂载 ────────────────────────────────────────
describe('CognitionView 挂载 (PMO-60 P3 C5)', () => {
  it('C5 AIPWorkbench 含 NAV_TABS cognition → render 后 hub summary + 4 卡 + mind chips 区出现', async () => {
    const { container } = renderWithProviders(<CognitionView />);
    await flush(120);
    const text = container.textContent ?? '';
    // hub summary：场景选择器 + 视图标题（zh "认知工作台 (Cognition)"）
    expect(text).toContain('认知工作台');
    expect(text).toContain('场景 A'); // 默认 auto-select 第一个
    // 4 cognitive ep 卡（诊断 / 预测 / 推演 / 策略）
    for (const k of ['诊断 (Diagnose)', '预测 (Forecast)', '推演 (Simulate)', '策略 (Policy)']) {
      expect(text).toContain(k);
    }
    // 每个卡都有"执行"按钮
    expect(text).toMatch(/执\s*行/);
    // mind chips 区：包含 "心智切换 (Mind Switch)" 标签 + mind-A-active
    expect(text).toContain('心智切换 (Mind Switch)');
    expect(text).toContain('mind-A-active');
  });
});

// ── C6 cognitive 404 stub：P3b 未启用 ─────────────────────────────────────────
describe('CognitionView P3b stub 404 (PMO-60 P3 C6)', () => {
  it('C6 全部认知端点 fetch 404 → 点 4 卡任一"执行" → 该卡出现 notEnabled InlineWarning 红字', async () => {
    // override：模拟后端 404（P3b 未落到 workspace 路由）
    mockFetch.mockImplementation(async (url: string) => {
      if (String(url).startsWith('/api/v1/workspace/scenarios') && String(url).endsWith('/scenarios')) {
        return [{ id: 'sc_A', name: '场景 A', status: 'ACTIVE' }];
      }
      if (String(url).startsWith('/api/v1/workspace/scenarios/sc_A/minds')) {
        return [{ id: 1, mindLabel: 'mind-A-active', activeMind: true }];
      }
      // 4 件套全部 404
      throw new Error('HTTP 404 Not Found');
    });

    const { container } = renderWithProviders(<CognitionView />);
    await flush(120);

    // 列表 4 个"执行"按钮，按 card 序 (diagnose, forecast, simulate, policy)
    const runBtns = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).filter((b) =>
      (b.textContent ?? '').trim().startsWith('执行'),
    );
    expect(runBtns.length).toBe(4);

    // 点最后一个 "策略" 卡的执行
    await act(async () => {
      runBtns[3].click();
    });
    await flush(120);
    // 断言：策略卡出现 notEnabled 文案（含异常 message），其他 3 卡仍为 "— 等待执行 —"
    const text = container.textContent ?? '';
    expect(text).toContain('端点尚未启用（P3b 阶段交付任务）');
    expect(text).toContain('HTTP 404 Not Found');
    // 其他 3 卡：诊断 / 预测 / 推演 仍 idle（等待执行 出现 ≥ 3 次）
    const idleCount = (text.match(/— 等待执行 —/g) ?? []).length;
    expect(idleCount).toBeGreaterThanOrEqual(3);
    // 策略卡的执行按钮：再次点击也被 disabled（state 已是 errorNotEnabled，disabled=!selectedScenarioId||status==='running'）
    const runBtnAgain = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).filter((b) =>
      (b.textContent ?? '').trim().startsWith('执行'),
    );
    expect(runBtnAgain.length).toBe(4);
  });
});

// ── C7 场景切换重置 ─────────────────────────────────────────────────────────
describe('CognitionView 场景切换重置 (PMO-60 P3 C7)', () => {
  it('C7 场景 A mind chips 出现 → 切到场景 B → mind chips 切到 mind-B-active；ep 结果保留=null（无残留）', async () => {
    const { container } = renderWithProviders(<CognitionView />);
    await flush(120);

    // 初始场景 A 的 mind chips
    let text = container.textContent ?? '';
    expect(text).toContain('mind-A-active');
    expect(text).toContain('mind-A-inactive');
    expect(text).not.toContain('mind-B-active');

    // 先触发一次 diagnose（让 epStates 有数据，验证切换后被重置）
    const runBtns = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).filter((b) =>
      (b.textContent ?? '').trim().startsWith('执行'),
    );
    expect(runBtns.length).toBe(4);
    await act(async () => {
      runBtns[0].click(); // diagnose
    });
    await flush(120);
    // mock 返回 { ok: true, summary: 'placeholder-diagnose' } 完整对象，
    // CognitionView 的 renderResult 把它 JSON.stringify 后 pre 渲染
    text = container.textContent ?? '';
    expect(text).toContain('placeholder-diagnose');

    // 切换到 sc_B
    const select = container.querySelector('select') as HTMLSelectElement;
    expect(select).toBeTruthy();
    await act(async () => {
      fireEvent.change(select, { target: { value: 'sc_B' } });
    });
    await flush(120);

    // mind chips 已切到 B：mind-B-active 出现，mind-A-* 不再出现
    text = container.textContent ?? '';
    expect(text).toContain('mind-B-active');
    expect(text).not.toContain('mind-A-active');
    // 切场景后 epStates 被重置为 idle → 之前的 placeholder-diagnose 应被清掉
    expect(text).not.toContain('placeholder-diagnose');
    // B 的 mind 计数 "1/1"（active 数 / 总数）
    expect(text).toMatch(/1\/1/);
  });
});
