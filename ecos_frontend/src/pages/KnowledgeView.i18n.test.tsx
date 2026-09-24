/**
 * KnowledgeView.i18n.test.tsx — 知识工作台左侧菜单 i18n 回归用例。
 *
 * 修复背景：`KnowledgeView` 曾以 `locale === 'zh-CN'` 手工判定语言（`Locale` 实际只有 `'zh' | 'en'`），
 * 该判断恒为 false，导致中文态菜单仍显示英文；现全部改走 `t('knowledge.group.*')` / `t('knowledge.nav.*')`。
 *
 * 本用例通过全局 `__ecos_test_locale` 强制语言，断言分组名与子项名随语言切换，
 * 即「菜单文案来自 i18n 词条」的可执行证据（本环境无浏览器工具，不做 E2E）。
 */
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import React from 'react';
import { render, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '../components/ThemeContext';
import { LanguageProvider } from '../components/LanguageContext';
import KnowledgeView from './KnowledgeView';

// 菜单文案断言与 Tab 内容无关：桩掉默认激活 Tab 与 6 Page，避免用例触发后端请求
vi.mock('./knowledge/tabs/OverviewDashboard', () => ({
  default: (): null => null,
}));
vi.mock('./knowledge/pages/OverviewPage', () => ({
  default: (): null => null,
}));
vi.mock('./knowledge/pages/AssetListPage', () => ({
  default: (): null => null,
}));
vi.mock('./knowledge/pages/ExtractionPage', () => ({
  default: (): null => null,
}));
vi.mock('./knowledge/pages/GraphPage', () => ({
  default: (): null => null,
}));
vi.mock('./knowledge/pages/WikiPage', () => ({
  default: (): null => null,
}));
vi.mock('./knowledge/pages/GovernPage', () => ({
  default: (): null => null,
}));

type TestLocale = 'zh' | 'en';

const testGlobal = globalThis as unknown as { __ecos_test_locale?: TestLocale };

/** 以指定语言渲染 KnowledgeView（Provider 层级与运行态一致，含 MemoryRouter 满足 useSearchParams） */
function renderWithLocale(locale: TestLocale, initialPath?: string) {
  testGlobal.__ecos_test_locale = locale;
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <MemoryRouter initialEntries={initialPath ? [initialPath] : ['/knowledge_view']}>
          <KnowledgeView />
        </MemoryRouter>
      </ThemeProvider>
    </LanguageProvider>,
  );
}

beforeEach(() => {
  // 清空所有 mock 状态
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
  delete testGlobal.__ecos_test_locale;
});

describe('KnowledgeView 6 平铺 Page 侧栏 i18n（F1 — PMO-D Batch 1）', () => {
  it('中文态渲染 6 个 Page 侧栏项（知识总览/知识资产/知识抽取/知识图谱/企业知识/知识治理）', () => {
    const { container } = renderWithLocale('zh');
    const text = container.textContent ?? '';
    expect(text).toContain('知识总览'); // knowledge.nav.page_overview
    expect(text).toContain('知识资产'); // knowledge.nav.page_assets
    expect(text).toContain('知识抽取'); // knowledge.nav.page_extract
    expect(text).toContain('知识图谱'); // knowledge.nav.page_graph
    expect(text).toContain('企业知识'); // knowledge.nav.page_wiki
    expect(text).toContain('知识治理'); // knowledge.nav.page_govern
    expect(text).toContain('任务中心'); // knowledge.nav.global_task_center
    expect(text).toContain('引擎监控'); // knowledge.nav.global_engine_monitor
  });

  it('英文态渲染英文 6 Page 侧栏项', () => {
    const { container } = renderWithLocale('en');
    const text = container.textContent ?? '';
    expect(text).toContain('Knowledge Overview');
    expect(text).toContain('Knowledge Assets');
    expect(text).toContain('Knowledge Extraction');
    expect(text).toContain('Knowledge Graph');
    expect(text).toContain('Enterprise Knowledge');
    expect(text).toContain('Knowledge Governance');
    expect(text).toContain('Task Center'); // knowledge.nav.global_task_center
    expect(text).toContain('Engine Monitor'); // knowledge.nav.global_engine_monitor
  });

  it('默认路由 ?page=overview 命中 6 Page 而非 14 Tab deep-link', () => {
    const { container } = renderWithLocale('zh', '/knowledge_view?page=overview');
    const text = container.textContent ?? '';
    // 面包屑出现 6 page 标题（不是 14 Tab 名称）
    expect(text).toContain('知识总览');
    expect(text).not.toContain('数据同步'); // knowledge.nav.datasync — 撤下，不在侧栏
    expect(text).not.toContain('RAG 实验台'); // rag — 撤下，不在侧栏
  });

  it('?tab=rag 撤下 deep-link 显示 warn banner + 旧 Tab 仍可达', () => {
    const { container } = renderWithLocale('zh', '/knowledge_view?tab=rag');
    const text = container.textContent ?? '';
    // 撤下 warn banner 出现
    expect(text).toContain('已撤下导航');
    // Banner 出现「回到 6 页主导航」按钮文案
    expect(text).toContain('回到 6 页主导航');
  });
});

describe('KnowledgeView 旧 14 Tab 侧栏（保留作 deep-link 后端，但 F1 主侧栏已切换）', () => {
  it('侧栏不再渲染旧 5 组 14 Tab 的分组列表', () => {
    const { container } = renderWithLocale('zh');
    const text = container.textContent ?? '';
    // 6 Page 侧栏：含 Knowledge Workbench + Global 入口
    expect(text).toContain('Knowledge Workbench');
    expect(text).toContain('Global');
    // 旧 5 组分组名（管理 / 查询 / 治理 / 引擎配置）均不在侧栏
    // （这些 key 仅在路由 ?tab=xxx 时由 14 Tab 调用 t(`knowledge.group.*`) 消费，但 14 Tab 已挂撤下）
    expect(text).not.toContain('知识查询'); // knowledge.group.retrieve 仅 6 Page 路由下不出现
    expect(text).not.toContain('知识管理'); // knowledge.group.manage 同上
  });
});
