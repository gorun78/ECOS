/**
 * @vitest-environment node
 *
 * 知识工作台第七页「规则」门（R11 / §6.2.6⑥ 裁决；#147I）。
 *
 * 钉三件事：① ACTIVE_PAGES 含 rules 且为七 Tab；② 导航/页面 i18n 键在中英双语均存在；
 * ③ 图标映射与页面组件映射齐备（缺任一即入口不可达或渲染空页）。
 */
import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import {
  ACTIVE_PAGES,
  ACTIVE_PAGE_I18N_KEYS,
  ACTIVE_PAGE_ICONS,
} from '../pages/knowledge/typesAndConstants';

const ROOT = process.cwd();

function loadLocale(locale: string): Record<string, string> {
  const p = path.join(ROOT, 'src', 'locales', 'knowledge', `${locale}.json`);
  return JSON.parse(fs.readFileSync(p, 'utf-8')) as Record<string, string>;
}

describe('knowledge workbench rules tab', () => {
  it('adds rules as the seventh F-KB-16 page while keeping cognitive assets', () => {
    expect(ACTIVE_PAGES).toContain('rules');
    // F-KB-16 六 Tab（总览/资产/抽取/图谱/导航/治理）→ 七 Tab（+规则）；
    // 认知资产中心为既有独立页，与规则同构承载、不新增部署入口 ⇒ 合计 8 个可达页
    expect(ACTIVE_PAGES).toHaveLength(8);
    expect(ACTIVE_PAGES).toEqual(
      expect.arrayContaining(['overview', 'assets', 'extract', 'graph', 'wiki', 'govern', 'rules']),
    );
    expect(ACTIVE_PAGES).toContain('cognitive_asset');
  });

  it('rules tab has icon mapping and i18n keys in both locales', () => {
    expect(ACTIVE_PAGE_ICONS.rules).toBeTruthy();
    const key = ACTIVE_PAGE_I18N_KEYS.rules;
    expect(key).toBe('knowledge.nav.page_rules');
    const zh = loadLocale('zh-CN');
    const en = loadLocale('en');
    expect(zh[key]).toBeTruthy();
    expect(en[key]).toBeTruthy();
    for (const k of [
      'knowledge.rules.title',
      'knowledge.rules.not_available_title',
      'knowledge.rules.empty_title',
      'knowledge.rules.col_layer',
    ]) {
      expect(zh[k], `zh missing ${k}`).toBeTruthy();
      expect(en[k], `en missing ${k}`).toBeTruthy();
    }
  });
});
