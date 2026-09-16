/**
 * KnowledgeView.i18n.test.tsx — 知识工作台左侧菜单 i18n 回归用例。
 *
 * 修复背景：`KnowledgeView` 曾以 `locale === 'zh-CN'` 手工判定语言（`Locale` 实际只有 `'zh' | 'en'`），
 * 该判断恒为 false，导致中文态菜单仍显示英文；现全部改走 `t('knowledge.group.*')` / `t('knowledge.nav.*')`。
 *
 * 本用例通过全局 `__ecos_test_locale` 强制语言，断言分组名与子项名随语言切换，
 * 即「菜单文案来自 i18n 词条」的可执行证据（本环境无浏览器工具，不做 E2E）。
 */
import { describe, it, expect, afterEach, vi } from 'vitest';
import React from 'react';
import { render, cleanup } from '@testing-library/react';
import { ThemeProvider } from '../components/ThemeContext';
import { LanguageProvider } from '../components/LanguageContext';
import KnowledgeView from './KnowledgeView';

// 菜单文案断言与 Tab 内容无关：桩掉默认激活 Tab，避免用例触发后端请求
vi.mock('./knowledge/tabs/OverviewDashboard', () => ({
  default: (): null => null,
}));

type TestLocale = 'zh' | 'en';

const testGlobal = globalThis as unknown as { __ecos_test_locale?: TestLocale };

/** 以指定语言渲染 KnowledgeView（Provider 层级与运行态一致） */
function renderWithLocale(locale: TestLocale) {
  testGlobal.__ecos_test_locale = locale;
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <KnowledgeView />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

afterEach(() => {
  cleanup();
  delete testGlobal.__ecos_test_locale;
});

describe('KnowledgeView 左侧菜单 i18n', () => {
  it('中文态渲染中文分组名与子项名', () => {
    const { container } = renderWithLocale('zh');
    const text = container.textContent ?? '';
    expect(text).toContain('知识摄入'); // knowledge.group.ingest
    expect(text).toContain('知识建模'); // knowledge.group.model
    expect(text).toContain('图谱构建'); // knowledge.nav.graph_build
    expect(text).toContain('引擎配置'); // knowledge.nav.engine_config
    expect(text).not.toContain('Ingest');
    expect(text).not.toContain('Graph Build');
  });

  it('英文态渲染英文分组名与子项名', () => {
    const { container } = renderWithLocale('en');
    const text = container.textContent ?? '';
    expect(text).toContain('Ingest');
    expect(text).toContain('Graph Build');
    expect(text).toContain('Engine Config');
    expect(text).not.toContain('知识摄入');
    expect(text).not.toContain('图谱构建');
  });
});
