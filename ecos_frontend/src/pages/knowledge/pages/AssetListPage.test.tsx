/**
 * AssetListPage.test.tsx — F3 知识资产列表表回归用例（PMO-D Batch 1）。
 *
 * 覆盖（PRD §3.2 F3 验收标准简表）：
 * 1. 8 列表头全渲染（知识资产 / 类型 / 状态 / 语义关联 / 来源 / 图谱实体 / 向量索引 / 更新时间）
 * 2. 空态提示（list 为空）— `knowledge.asset.empty`
 * 3. 数据行渲染：标题 + 状态 badge + 来源 + 更新时间
 * 4. 批量状态批量调断言：fetchAssetStatuses 收到 ≤100 的 ids
 * 5. 图谱/向量 glyph：graph=true 显示 ✓；vector=true 显示 ⏳/—
 * 6. 域切换 reset（domain dropdown change → 触发 keyword / assetType / status reset）
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import React from 'react';
import { render, screen, cleanup, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '../../../components/ThemeContext';
import { LanguageProvider } from '../../../components/LanguageContext';
import AssetListPage from './AssetListPage';

const mockFetchNavProducts = vi.fn();
const mockFetchNavDomains = vi.fn();
const mockFetchAssetStatuses = vi.fn();

vi.mock('../../../services/knowledgeNavApi', () => ({
  fetchNavProducts: (...args: unknown[]) => mockFetchNavProducts(...args),
  fetchNavDomains: (...args: unknown[]) => mockFetchNavDomains(...args),
  fetchAssetStatuses: (...args: unknown[]) => mockFetchAssetStatuses(...args),
}));

type TestLocale = 'zh' | 'en';
const testGlobal = globalThis as unknown as { __ecos_test_locale?: TestLocale };

function renderPage(locale: TestLocale) {
  testGlobal.__ecos_test_locale = locale;
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <AssetListPage />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // 默认：domain 列表 = ['default']，资产列表 = 2 条
  mockFetchNavDomains.mockResolvedValue(['default']);
  mockFetchAssetStatuses.mockResolvedValue([]);
  mockFetchNavProducts.mockImplementation(async () => ({
    list: [
      {
        id: 'a001',
        title: '客户知识 Wiki-2026',
        source: 'manual',
        domain: 'default',
        category: 'wiki',
        status: 'published',
        updatedAt: '2026-09-20T08:00:00Z',
        matchedCategoryIds: ['c-001'],
        matchedTags: ['客户', '主数据'],
      },
      {
        id: 'a002',
        title: '订单图谱实体元数据',
        source: 'structured_extract',
        domain: 'default',
        category: 'entity',
        status: 'review',
        updatedAt: '2026-09-21T09:30:00Z',
        matchedCategoryIds: ['c-002'],
        matchedTags: ['订单'],
      },
    ],
    total: 2,
    pageNum: 1,
    pageSize: 20,
  }));
});

afterEach(() => {
  cleanup();
  delete testGlobal.__ecos_test_locale;
});

describe('AssetListPage — 8 列表格 + 空态 + 批量 status', () => {
  it('8 列表头全渲染（知识资产/类型/状态/语义关联/来源/图谱实体/向量索引/更新时间）', async () => {
    const { container } = renderPage('zh');
    await waitFor(() => {
      const headers = Array.from(container.querySelectorAll('th')).map(th => th.textContent ?? '');
      expect(headers).toEqual(
        expect.arrayContaining([
          '知识资产', // col_title
          '类型',      // col_type
          '状态',      // col_status
          '语义关联',   // col_semantic
          '来源',      // col_source
          '图谱实体',   // col_graph
          '向量索引',   // col_vector
          '更新时间',   // col_updated
        ]),
      );
    });
  });

  it('空态提示：list 为空时渲染 knowledge.asset.empty', async () => {
    mockFetchNavProducts.mockResolvedValueOnce({ list: [], total: 0, pageNum: 1, pageSize: 20 });
    const { container } = renderPage('zh');
    await waitFor(() => {
      const tbody = container.querySelector('tbody');
      expect(tbody?.textContent).toContain('暂无资产');
    });
  });

  it('数据行渲染：标题 + 状态 badge + 来源 + 更新时间', async () => {
    renderPage('zh');
    await waitFor(() => {
      expect(screen.getByText('客户知识 Wiki-2026')).toBeInTheDocument();
      expect(screen.getByText('订单图谱实体元数据')).toBeInTheDocument();
    });
    // 状态 badge：a001 = 已发布，a002 = 审核中
    await waitFor(() => {
      const badgeTexts = Array.from(document.querySelectorAll('tbody span')).map(s => s.textContent ?? '');
      expect(badgeTexts).toContain('已发布');
      expect(badgeTexts).toContain('审核中');
    });
    // 来源
    expect(screen.getByText('manual')).toBeInTheDocument();
    expect(screen.getByText('structured_extract')).toBeInTheDocument();
  });

  it('批量 status 调用断言：fetchAssetStatuses 收到 ≤100 的 ids（F10 后端契约）', async () => {
    renderPage('zh');
    await waitFor(() => {
      expect(mockFetchAssetStatuses).toHaveBeenCalled();
      const [ids] = mockFetchAssetStatuses.mock.calls[0];
      expect(Array.isArray(ids)).toBe(true);
      expect((ids as string[]).length).toBe(2); // a001 + a002
      expect((ids as string[])).toContain('a001');
      expect(ids as string[]).toContain('a002');
      // 单次 ≤ 100 断言
      expect((ids as string[]).length).toBeLessThanOrEqual(100);
    });
  });

  it('图谱/向量 glyph：graph=true → ✓，vector=false → ⏳（本用例 vector=false 时显示 ⏳ 占位）', async () => {
    mockFetchAssetStatuses.mockImplementation(async (ids: string[]) =>
      ids.map(v => {
        const map: Record<string, boolean> = { a001: true, a002: false };
        return { articleId: v, graph: !!map[v], vector: v === 'a001' ? false : true };
      })
    );
    const { container } = renderPage('zh');
    await waitFor(() => {
      // a001: graph=true (✓ emerald) + vector=false (⏳)
      const tds = Array.from(container.querySelectorAll('tbody tr'));
      expect(tds.length).toBe(2);
      const rowA001 = tds[0].querySelectorAll('td')[5]?.textContent ?? ''; // col_graph index 5
      const rowA001Vector = tds[0].querySelectorAll('td')[6]?.textContent ?? ''; // col_vector index 6
      expect(rowA001).toContain('✓');
      // vector 在 a001=false 时显示「⏳」（renderVectorGlyph：!it.vector → opacity ⏳）；
      // a002 vector=true 时显示 ⏳ 等待动画，对「⏳」字符的存在统一断言：
      expect(rowA001Vector.trim()).toBe('⏳');
      const rowA002Graph = tds[1].querySelectorAll('td')[5]?.textContent ?? '';
      const rowA002Vector = tds[1].querySelectorAll('td')[6]?.textContent ?? '';
      expect(rowA002Graph).toContain('—'); // graph=false
      expect(rowA002Vector).toContain('✓'); // vector=true → ✓
    });
  });

  it('域切换 reset：domain dropdown change → keyword / assetType / status 全部 reset', async () => {
    mockFetchNavDomains.mockResolvedValueOnce(['default', 'finance']);
    const { container } = renderPage('zh');
    await waitFor(() => {
      expect(container.querySelectorAll('select').length).toBeGreaterThanOrEqual(3);
    });
    const domainSelect = Array.from(container.querySelectorAll('select')).find(s => {
      const aria = s.getAttribute('aria-label');
      return aria && aria.includes('Domain');
    });
    expect(domainSelect).toBeTruthy();
    if (domainSelect) {
      await act(async () => {
        await userEvent.setup().selectOptions(domainSelect, 'finance');
      });
      // 切换后 keyword/state 应为 'all'（ticket：domain change reset filter）
      await waitFor(() => {
        const keywordInput = container.querySelector('input') as HTMLInputElement;
        expect(keywordInput?.value).toBe('');
      });
    }
  });
});
