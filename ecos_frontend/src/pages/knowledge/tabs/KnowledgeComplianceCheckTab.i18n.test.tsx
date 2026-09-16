/**
 * KnowledgeComplianceCheckTab.i18n.test.tsx — 合规检查「业务对象」下拉选项 i18n 回归用例。
 *
 * 修复背景：`BUSINESS_OBJECTS` 曾内联 `labelZh` / `labelEn`，组件内以 `zh` 手工二选一，
 * 属硬编码中文（违反前端铁律 §4.3）；现文案统一走 `t('knowledge.compliance.objectType.<value>')`。
 *
 * 本用例通过全局 `__ecos_test_locale` 强制语言，断言 7 个业务对象 option 文案随语言切换，
 * 即「选项文案来自 i18n 词条」的可执行证据。
 */
import { describe, it, expect, afterEach, vi } from 'vitest';
import React from 'react';
import { render, cleanup } from '@testing-library/react';
import { ThemeProvider } from '../../../components/ThemeContext';
import { LanguageProvider } from '../../../components/LanguageContext';
import KnowledgeComplianceCheckTab from './KnowledgeComplianceCheckTab';

// 选项文案断言不触发请求：桩掉唯一 API 依赖，避免真实网络调用
vi.mock('../../../api', () => ({
  apiFetch: vi.fn(),
}));

type TestLocale = 'zh' | 'en';

const testGlobal = globalThis as unknown as { __ecos_test_locale?: TestLocale };

/** 以指定语言渲染该 Tab（Provider 层级与运行态一致） */
function renderWithLocale(locale: TestLocale) {
  testGlobal.__ecos_test_locale = locale;
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <KnowledgeComplianceCheckTab />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

/** 取首个 select（业务对象下拉，第 2/3 个 select 属事实值 true/false/unknown）的全部 option 文案 */
function businessObjectOptionTexts(container: HTMLElement): string[] {
  const select = container.querySelector('select');
  if (!select) return [];
  return Array.from(select.querySelectorAll('option')).map(o => o.textContent ?? '');
}

afterEach(() => {
  cleanup();
  delete testGlobal.__ecos_test_locale;
});

describe('KnowledgeComplianceCheckTab 业务对象下拉 i18n', () => {
  it('中文态渲染中文业务对象名', () => {
    const { container } = renderWithLocale('zh');
    const texts = businessObjectOptionTexts(container);
    expect(texts).toHaveLength(8); // 1 占位项 + 7 业务对象
    expect(texts).toContain('医疗器械');
    expect(texts).toContain('体外诊断试剂');
    expect(texts).not.toContain('Medical Device');
    expect(texts).not.toContain('In Vitro Diagnostic');
  });

  it('英文态渲染英文业务对象名', () => {
    const { container } = renderWithLocale('en');
    const texts = businessObjectOptionTexts(container);
    expect(texts).toHaveLength(8);
    expect(texts).toContain('Medical Device');
    expect(texts).toContain('In Vitro Diagnostic');
    expect(texts).not.toContain('医疗器械');
    expect(texts).not.toContain('体外诊断试剂');
  });
});
