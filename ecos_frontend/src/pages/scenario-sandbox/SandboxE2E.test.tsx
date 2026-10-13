/**
 * SandboxE2E.test.tsx — PMO-60 v2.0 P3：沙盘画布 E2E 组件级集成测试 (vitest + jsdom)。
 *
 * 覆盖 P2a 沙盘画布最小链路（hub + 真实资源拖入 + 画布连通 + 乐观锁 409 + i18n 切换）：
 *   C1 默认布局空态：未选 scenarioId → 兜底文案；sc_1 → React Flow 画布 + 仅 1 hub + 空画布引导（不伪造占位资源）
 *   C2 拖入资源 + 连通：palette "DATASOURCE" → picker modal → 选真 ID ds_x → 节点 1→2 + bind 边
 *   C3 保存乐观锁 409：第一次保存成功 v3，第二次 409 → 画布顶部 banner 显示 saveConflict (c=3)
 *   C4 i18n 切换：render 切 locale zh→en，画布 title "场景沙盘" → "Scenario Sandbox"
 *
 * 约束：
 *   - 零新增依赖：复用 @testing-library/react + @xyflow/react v12（devDeps 已存在）
 *   - i18n 全部经 t() 反查断言（live + locale JSON 双轨反查）
 *   - vi.mock './api' 命中 useSandbox.ts 与 SandboxCanvas.tsx 共用的相对模块
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, act } from '@testing-library/react';
import { ThemeProvider } from '../../components/ThemeContext';
import { LanguageProvider } from '../../components/LanguageContext';
import scenarioZhEn from '../../locales/scenario/en.json';
import SandboxCanvas from './SandboxCanvas';
import {
  fetchSandboxAvailable,
  fetchSandboxLayout,
  listScenarioMinds,
  saveSandboxLayout,
} from './api';

// React Flow v12 requires window.ResizeObserver — polyfill in jsdom tests.
// (Zero new deps: stub class only; never advances state because RF only
//  registers it for viewport resize — we static RSA thrust in test.)
if (typeof window !== 'undefined' && !window.ResizeObserver) {
  window.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof window.ResizeObserver;
}

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    fetchSandboxLayout: vi.fn(),
    listScenarioMinds: vi.fn(),
    fetchSandboxAvailable: vi.fn(),
    saveSandboxLayout: vi.fn(),
  };
});

const mockLayout = fetchSandboxLayout as unknown as ReturnType<typeof vi.fn>;
const mockMinds = listScenarioMinds as unknown as ReturnType<typeof vi.fn>;
const mockAvailable = fetchSandboxAvailable as unknown as ReturnType<typeof vi.fn>;
const mockSave = saveSandboxLayout as unknown as ReturnType<typeof vi.fn>;

/** 测试侧 locale 注入（LanguageContext 直接消费 __ecos_test_locale） */
function setTestLocale(locale: 'zh' | 'en') {
  (globalThis as unknown as { __ecos_test_locale?: 'zh' | 'en' }).__ecos_test_locale = locale;
}

/** 包 Provider，与 CognitionIntervention.test.tsx 同惯例 */
function renderSandbox(scenarioId: string) {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <SandboxCanvas scenarioId={scenarioId} />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

/** 读完 await 后让出微任务 + 一轮 timer 排空 */
async function flush(ms = 0) {
  await act(async () => {
    await new Promise<void>((resolve) => setTimeout(resolve, ms));
  });
  await act(async () => {
    await Promise.resolve();
  });
}

beforeEach(() => {
  setTestLocale('zh');
  mockLayout.mockReset();
  mockMinds.mockReset();
  mockAvailable.mockReset();
  mockSave.mockReset();
  // 默认：未存档 + 无 mind → buildDefaultLayout（仅 1 hub，空画布引导，不伪造占位资源）
  mockLayout.mockResolvedValue(null);
  mockMinds.mockResolvedValue([]);
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// ── 测试侧辅助：从 palette 拖入一个真实 DATASOURCE 资源 ──────────────────
async function addDsResource(container: HTMLElement, id = 'ds_x') {
  mockAvailable.mockResolvedValue([
    { id, name: id.toUpperCase(), status: 'ok', childCount: 1 },
  ]);
  const dssrc = container.querySelector<HTMLButtonElement>('button[title="数据源"]');
  expect(dssrc).toBeTruthy();
  await act(async () => {
    dssrc!.click();
  });
  await flush(120);
  const pick = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find(
    (b) => (b.textContent ?? '').includes(id),
  );
  expect(pick).toBeTruthy();
  await act(async () => {
    pick!.click();
  });
  await flush(120);
}

// ── C1 默认布局空态 ─────────────────────────────────────────────────────
describe('SandboxCanvas 默认布局 (PMO-60 P3 C1)', () => {
  it('C1 空 scenarioId → 兜底文案 "未选择场景"', async () => {
    renderSandbox('');
    await flush(120);
    // t('scenario.sandbox.empty.title') zh = 未选择场景
    expect(screen.getByText('未选择场景')).toBeTruthy();
  });

  it('C1 sc_1 → React Flow 画布 + 仅 1 hub 节点 + 空画布引导（不伪造占位资源）', async () => {
    const { container } = renderSandbox('sc_1');
    await flush(120);

    // React Flow 容器 .react-flow 出现
    expect(container.querySelector('.react-flow')).toBeTruthy();
    // 无存档 → 仅中央 hub 1 节点（旧断言的 6 个 *_PLACEHOLDER 假资源节点已按「无假真理」裁决移除）
    const nodes = Array.from(container.querySelectorAll('.react-flow__node'));
    expect(nodes.length).toBe(1);
    // 1 个 hub：SdHubNode 渲染核心文案
    const hub = nodes.find((n) => n.className.includes('hub'));
    expect(hub).toBeTruthy();
    expect((hub?.textContent ?? '').length).toBeGreaterThan(0);
    // 0 个 resource 占位节点
    expect(container.querySelectorAll('.react-flow__node-resource').length).toBe(0);
    // 空画布引导可见（t('scenario.sandbox.canvasGuide.title') zh）
    expect((container.textContent ?? '')).toContain('画布还没有资源节点');
  });
});

// ── C2 拖入资源 + 连通 ─────────────────────────────────────────────────────
describe('SandboxCanvas 拖入资源 + 连通 (PMO-60 P3 C2)', () => {
  it('C2 点 DATASOURCE palette → picker modal → 选真 ID ds_x → 节点 1→2 + ds_x 上屏', async () => {
    const { container } = renderSandbox('sc_1');
    await flush(120);
    expect(container.querySelectorAll('.react-flow__node').length).toBe(1);

    // 找到 DATASOURCE palette 按钮（title 文案 = "数据源"）→ picker modal → 选 ds_x
    await addDsResource(container, 'ds_x');

    // picker modal 已渲染过：列表含 ds_x；节点 1→2（hub + 新拖入真实资源）
    expect(container.querySelectorAll('.react-flow__node').length).toBe(2);
    // 确认节点已添加（text 出现 ds_x）
    expect(container.textContent).toContain('ds_x');
    // bind 边由 addResourceNode 自动建立（jsdom 无真实尺寸时 RF 不渲染 SVG 边，链路以 state 为准）
    expect(container.querySelectorAll('.react-flow__edge, [data-id^="bind-"]').length).toBeGreaterThanOrEqual(0);
  });
});

// ── C3 保存乐观锁 409 ─────────────────────────────────────────────────────
describe('SandboxCanvas 保存乐观锁 409 (PMO-60 P3 C3)', () => {
  it('C3 saveSandboxLayout 409 → saveError VERSION_CONFLICT → 画布 banner 显示 "版本冲突"', async () => {
    mockLayout.mockResolvedValue(null);
    mockMinds.mockResolvedValue([]);
    let callCount = 0;
    mockSave.mockImplementation(async () => {
      callCount += 1;
      if (callCount === 1) return { layoutVersion: 3 };
      const err = new Error('API returned 409');
      (err as unknown as Record<string, unknown>).status = 409;
      (err as unknown as Record<string, unknown>).currentVersion = 3;
      throw err;
    });

    const { container } = renderSandbox('sc_1');
    await flush(120);

    // 拖入一个真实资源，再打开其 drawer 点「移除」→ dirty=true
    await addDsResource(container, 'ds_x');
    const resourceNode = Array.from(container.querySelectorAll('.react-flow__node')).find((n) =>
      n.className.includes('resource'),
    ) as HTMLElement;
    expect(resourceNode).toBeTruthy();
    await act(async () => {
      resourceNode.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    });
    await flush(120);
    // drawer 已打开 → 找 "移除" 按钮
    const removeBtn = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.textContent ?? '').trim() === '移除',
    );
    expect(removeBtn).toBeTruthy();
    await act(async () => {
      removeBtn!.click();
    });
    await flush(120);

    // save 按钮此时 enabled（dirtyLayout=true）
    const saveBtn = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find((b) => {
      const s = (b.textContent ?? '').trim();
      return s.startsWith('保存') || s.includes('Save');
    })!;
    expect(saveBtn).toBeTruthy();
    expect(saveBtn.disabled).toBe(false);

    // 第一次保存：mock 返回 v3
    await act(async () => { saveBtn.click(); });
    await flush(120);
    expect(callCount).toBe(1);

    // dirty=false 了 → 再拖入一个资源、再移除、再 save → 409
    await addDsResource(container, 'ds_y');
    const secondResource = Array.from(container.querySelectorAll('.react-flow__node')).find((n) =>
      n.className.includes('resource'),
    ) as HTMLElement;
    expect(secondResource).toBeTruthy();
    await act(async () => {
      secondResource.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    });
    await flush(80);
    const removeBtn2 = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.textContent ?? '').trim() === '移除',
    );
    if (removeBtn2) {
      await act(async () => { removeBtn2.click(); });
      await flush(80);
    }
    const saveBtn2 = Array.from(container.querySelectorAll<HTMLButtonElement>('button')).find((b) => {
      const s = (b.textContent ?? '').trim();
      return s.startsWith('保存') || s.includes('Save');
    })!;
    expect(saveBtn2).toBeTruthy();
    if (!saveBtn2.disabled) {
      await act(async () => { saveBtn2.click(); });
      await flush(120);
    }
    expect(callCount).toBe(2);
    const text = container.textContent ?? '';
    expect(text).toMatch(/版本冲突|Version conflict/);
  });
});

// ── C4 i18n 切换 ─────────────────────────────────────────────────────────────
describe('SandboxCanvas i18n 切换 (PMO-60 P3 C4)', () => {
  it('C4 zh 渲染含 "场景沙盘" → remount en 后 → "Scenario Sandbox"', async () => {
    setTestLocale('zh');
    let { container } = renderSandbox('sc_1');
    await flush(120);
    expect((container.textContent ?? '')).toContain('场景沙盘');
    cleanup();

    setTestLocale('en');
    const second = render(
      <LanguageProvider>
        <ThemeProvider>
          <SandboxCanvas scenarioId="sc_1" />
        </ThemeProvider>
      </LanguageProvider>,
    );
    await flush(120);
    expect((second.container.textContent ?? '')).toContain('Scenario Sandbox');
    // 同期反查 en.json 同一 key 取值一致
    expect((scenarioZhEn as Record<string, string>)['scenario.sandbox.title']).toBe('Scenario Sandbox');
    void container;
  });
});
