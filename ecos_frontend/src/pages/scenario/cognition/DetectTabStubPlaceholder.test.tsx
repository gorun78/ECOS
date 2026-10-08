// @vitest-environment node
/**
 * DetectTabStubPlaceholder.test.tsx — design-05 §F05-16 要点 2 / B.5 七态「未实现」占位前端验收。
 *
 * 本仓 jsdom 在 node 20.18.1 上有 ERR_REQUIRE_ESM 已知问题，故走 `react-dom/server` 静态渲染，
 * 只判 DOM 出口（占位 testid + i18n 文案 + 禁渲染固定值），不依赖测试库。
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { ThemeProvider } from '../../../components/ThemeContext';
import { LanguageProvider } from '../../../components/LanguageContext';
import DetectTab from './DetectTab';
import { CognitiveApiError } from '../../../services/cognitiveClient';
import type { MindLoadState } from './CognitiveRightPanel';

// ThemeProvider / LanguageProvider 的惰性 state 读 localStorage：node 环境补最小垫片
const storageShim = {
  getItem: (): string | null => null,
  setItem: (): void => undefined,
  removeItem: (): void => undefined,
  clear: (): void => undefined,
  key: (): string | null => null,
  length: 0,
};

beforeAll(() => {
  const g = globalThis as unknown as Record<string, unknown>;
  g.localStorage = storageShim;
  g.__ecos_test_locale = 'zh';
});

afterAll(() => {
  const g = globalThis as unknown as Record<string, unknown>;
  delete g.localStorage;
  delete g.__ecos_test_locale;
});

function render(minds: MindLoadState): string {
  return renderToStaticMarkup(
    <LanguageProvider>
      <ThemeProvider>
        <DetectTab scenarioId="sc-1" minds={minds} onRetryMinds={() => undefined} />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

const error = (info: { code: 'STUB' | 'ENGINE_DISABLED'; stub: boolean; message: string }) =>
  new CognitiveApiError({ status: info.stub ? 501 : 503, traceId: 't-1', ...info });

describe('DetectTab 未实现（501 + stub=true）占位态', () => {
  it('stub 错误渲染 cog-stub-placeholder 占位与 i18n 文案，不渲染假数据', () => {
    const html = render({
      loading: false,
      rows: [],
      error: error({ code: 'STUB', stub: true, message: 'capability not implemented' }),
    });
    expect(html).toContain('data-testid="cog-stub-placeholder"');
    expect(html).toContain('未实现（占位）');
    // F05-16 要点 2：禁渲染固定 0.75/0.15/0.3（X-43 world-model 伪数据）
    for (const fake of ['0.75', '0.15', '0.3']) {
      expect(html).not.toContain(fake);
    }
    expect(html).not.toContain('role="alert"');
  });

  it('未启用（503）仍走故障态，与占位态互斥', () => {
    const html = render({
      loading: false,
      rows: [],
      error: error({ code: 'ENGINE_DISABLED', stub: false, message: 'cognitive engine not enabled' }),
    });
    expect(html).not.toContain('data-testid="cog-stub-placeholder"');
    expect(html).toContain('role="alert"');
  });
});
