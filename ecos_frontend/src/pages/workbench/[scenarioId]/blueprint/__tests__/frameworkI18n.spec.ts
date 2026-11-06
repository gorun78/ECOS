import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(__dirname, '../../../../../..');

const KEYS = [
  'workspace.framework.loading',
  'workspace.framework.denied',
  'workspace.framework.engine',
  'workspace.framework.retry',
  'workspace.framework.error',
  'workspace.framework.empty',
  'workspace.framework.title',
  'workspace.framework.refresh',
  'workspace.framework.problem_type',
  'workspace.framework.domain',
  'workspace.framework.risk_level',
  'workspace.framework.present_inputs',
  'workspace.framework.present_inputs_ph',
  'workspace.framework.capabilities',
  'workspace.framework.capabilities_ph',
  'workspace.framework.select',
  'workspace.framework.selecting',
  'workspace.framework.registered',
  'workspace.framework.selection_status',
  'workspace.framework.ordinal_note',
  'workspace.framework.order_score',
  'workspace.framework.not_calibrated',
  'workspace.framework.missing_inputs',
  'workspace.framework.plan_valid',
  'workspace.completeness.loading',
  'workspace.completeness.denied',
  'workspace.completeness.engine',
  'workspace.completeness.error',
  'workspace.completeness.not_applicable',
  'workspace.completeness.title',
  'workspace.completeness.refresh',
  'workspace.completeness.coverage',
  'workspace.completeness.single_source',
  'workspace.completeness.missing_edges',
  'workspace.completeness.islands',
  'workspace.completeness.probe_errors',
];

describe('FrameworkPanel i18n keys', () => {
  it.each(['zh-CN', 'en'])('%s locale contains every framework key', (locale) => {
    const file = path.join(ROOT, 'src/locales/workspace', `${locale}.json`);
    const json = JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, string>;
    for (const key of KEYS) {
      expect(json[key], `${locale} missing ${key}`).toBeTruthy();
    }
  });

  it('panel source has no inline Chinese literals in t()-independent strings', () => {
    const src = fs.readFileSync(path.join(__dirname, '..', 'FrameworkPanel.tsx'), 'utf8');
    const strings = src.match(/['"`][^'"`]*[\u4e00-\u9fa5][^'"`]*['"`]/g) ?? [];
    // 只允许注释/JSDoc 中的中文；受治字符串一律走 i18n。
    const codeOnly = src
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*$/gm, '');
    const codeStrings = codeOnly.match(/['"`][^'"`]*[\u4e00-\u9fa5][^'"`]*['"`]/g) ?? [];
    expect(strings.length).toBeGreaterThan(0);
    expect(codeStrings).toEqual([]);
  });
});
