// @vitest-environment node
/**
 * W201/C183 门禁 — i18n 静默回落消除
 * 冻结门禁名：unit: i18n-coverage.test.ts › noSilentFallbackToRawKey
 * @license Apache-2.0
 */
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  NAV_ENTRIES,
  PALETTE_ITEMS,
  TYPE_META_MAP,
  SIDEBAR_ITEMS,
} from '../navigation/navConfig';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/
const LOCALES = path.join(SRC, 'locales');

/** 合并全部 namespace 语料（与 LanguageContext 的合并注册同构）。 */
function loadCorpus(file: 'zh-CN.json' | 'en.json'): Record<string, string> {
  const merged: Record<string, string> = {};
  for (const ns of readdirSync(LOCALES)) {
    const p = path.join(LOCALES, ns, file);
    try {
      Object.assign(merged, JSON.parse(readFileSync(p, 'utf8')) as Record<string, string>);
    } catch {
      // namespace without this locale file — skip
    }
  }
  return merged;
}

const zh = loadCorpus('zh-CN.json');
const en = loadCorpus('en.json');

function assertKeyPair(key: string, from: string) {
  expect(zh[key], `zh-CN missing "${key}" (required by ${from})`).toBeTypeOf('string');
  expect(en[key], `en missing "${key}" (required by ${from})`).toBeTypeOf('string');
  expect(zh[key].length, `zh-CN "${key}" is empty`).toBeGreaterThan(0);
  expect(en[key].length, `en "${key}" is empty`).toBeGreaterThan(0);
}

describe('noSilentFallbackToRawKey (W201 gate)', () => {
  it('App.tsx source no longer contains the raw-key `?? app.tab.${id}` fallback pattern', () => {
    const appSrc = readFileSync(path.join(SRC, 'App.tsx'), 'utf8');
    expect(appSrc).not.toMatch(/TAB_LABEL_KEY\[.*\]\s*\?\?/);
    expect(appSrc).not.toMatch(/`app\.tab\.\$\{/);
    // and tab labels must resolve through the navConfig single source
    expect(appSrc).toMatch(/from\s+"\.\/navigation\/navConfig"/);
  });

  it('every navConfig labelKey exists as a zh-CN/en pair in the merged corpus', () => {
    for (const e of NAV_ENTRIES) {
      assertKeyPair(e.labelKey, `NAV_ENTRIES(${e.id}).labelKey`);
    }
  });

  it('every sidebar descKey exists as a zh-CN/en pair', () => {
    for (const item of SIDEBAR_ITEMS) {
      assertKeyPair(item.descKey, `sidebar item ${item.id}.descKey`);
      assertKeyPair(item.labelKey, `sidebar item ${item.id}.labelKey`);
    }
    // group headers
    for (const key of [
      'sidebar.group.overview',
      'sidebar.group.resources',
      'sidebar.group.system',
      'sidebar.group.product',
    ]) {
      assertKeyPair(key, 'sidebar group header');
    }
  });

  it('palette + tab-placeholder keys exist as a zh-CN/en pair', () => {
    assertKeyPair('tab.unknown', 'W201 fail-closed placeholder');
    const categories = new Set<string>(['palette.category.other']);
    for (const item of PALETTE_ITEMS) {
      assertKeyPair(item.titleKey, `palette item ${item.id}.titleKey`);
      assertKeyPair(item.descKey, `palette item ${item.id}.descKey`);
      categories.add(item.categoryKey);
    }
    for (const meta of Object.values(TYPE_META_MAP)) {
      categories.add(meta.categoryKey);
    }
    for (const cat of categories) {
      assertKeyPair(cat, 'palette/hit category');
    }
  });
});
