/**
 * KnowledgeCategoryFilter.test.tsx — 七分类筛选面板（#147U / F-KB-18）。
 *
 * 覆盖：① 读端路径拼装（必填分类 ⇒ 空分类不发请求、limit 夹紧、分类编码）；
 * ② 初始 idle 不发请求；③ 选中分类按闭集码请求；
 * ④ 空结果走空态并说明存量未分类成因；⑤ 4xx 走错误态并显示业务码 + 重试（禁渲染成空态）。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import React from 'react';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '../../../../components/ThemeContext';
import { LanguageProvider } from '../../../../components/LanguageContext';
import KnowledgeCategoryFilter from '../KnowledgeCategoryFilter';
import { categoryFilterPath } from '../../services/categoryFilter';
import { KbApiError } from '../../services/kbFetch';

const mockFetchByCategory = vi.fn();

vi.mock('../../services/categoryFilter', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/categoryFilter')>();
  return {
    ...actual,
    fetchArticlesByCategory: (...args: unknown[]) => mockFetchByCategory(...args),
  };
});

type TestLocale = 'zh' | 'en';
const testGlobal = globalThis as unknown as { __ecos_test_locale?: TestLocale };

function renderPanel() {
  testGlobal.__ecos_test_locale = 'zh';
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <KnowledgeCategoryFilter />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

describe('categoryFilterPath', () => {
  it('分类必填：空/null/空白返回 null（前端不得发无分类请求）', () => {
    expect(categoryFilterPath(null)).toBeNull();
    expect(categoryFilterPath('')).toBeNull();
    expect(categoryFilterPath('   ')).toBeNull();
  });

  it('路径含显式 by-category 子路径并编码分类', () => {
    const path = categoryFilterPath('Rule');
    expect(path).toContain('/knowledge/articles/by-category');
    expect(path).toContain('category=Rule');
    expect(path).toContain('limit=20');
  });

  it('limit 夹紧到 [1,100]', () => {
    expect(categoryFilterPath('Fact', 500)).toContain('limit=100');
    expect(categoryFilterPath('Fact', 0)).toContain('limit=20');
  });
});

describe('KnowledgeCategoryFilter', () => {
  beforeEach(() => {
    mockFetchByCategory.mockReset();
  });

  afterEach(() => {
    cleanup();
    mockFetchByCategory.mockReset();
  });

  it('初始 idle：不发请求，提示先选分类', () => {
    renderPanel();
    expect(screen.getByTestId('knowledge-category-idle')).toBeTruthy();
    expect(mockFetchByCategory).not.toHaveBeenCalled();
  });

  it('选「规则」按闭集码 Rule 请求一次', async () => {
    mockFetchByCategory.mockResolvedValue([]);
    renderPanel();

    await userEvent.click(screen.getByRole('button', { name: '规则' }));

    await waitFor(() => expect(mockFetchByCategory).toHaveBeenCalledTimes(1));
    expect(mockFetchByCategory).toHaveBeenCalledWith('Rule');
  });

  it('空结果 → 空态 + 存量未分类说明（不显示错误）', async () => {
    mockFetchByCategory.mockResolvedValue([]);
    renderPanel();

    await userEvent.click(screen.getByRole('button', { name: '规则' }));

    const empty = await screen.findByTestId('knowledge-category-empty');
    expect(empty.textContent).toContain('该分类暂无知识资产');
    expect(empty.textContent).toContain('存量资产尚未回填分类');
    expect(screen.queryByTestId('knowledge-category-error')).toBeNull();
  });

  it('4xx → 错误态显示业务码并可重试（禁渲染成空态）', async () => {
    mockFetchByCategory.mockRejectedValueOnce(new KbApiError(400, 'ECOS-KB-040', 'bad category'));
    renderPanel();

    await userEvent.click(screen.getByRole('button', { name: '证据' }));

    const err = await screen.findByTestId('knowledge-category-error');
    expect(err.textContent).toContain('ECOS-KB-040');
    expect(screen.queryByTestId('knowledge-category-empty')).toBeNull();

    mockFetchByCategory.mockResolvedValueOnce([]);
    await userEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByTestId('knowledge-category-empty');
    expect(mockFetchByCategory).toHaveBeenCalledTimes(2);
  });

  it('有数据 → 渲染列表行标题', async () => {
    mockFetchByCategory.mockResolvedValue([{ id: 'a-1', title: '审批规则说明', status: 'active' }]);
    renderPanel();

    await userEvent.click(screen.getByRole('button', { name: '规则' }));

    const list = await screen.findByTestId('knowledge-category-list');
    expect(list.textContent).toContain('审批规则说明');
  });
});
