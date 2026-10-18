/**
 * mobile-reuse.test.ts — 冻结门禁名 `noNewBreakpointPrimitives`
 * （详设-08 §九 W200/C182，前端开发规范 §八）
 *
 * 口径（§八）：响应式判定只允许复用三个既有原语
 *   hooks/useMediaQuery.ts（规范实现）、hooks/useMobileSidebar.ts、
 *   components/common/MobileDataTable.tsx；
 *   JS 断点字符串只允许 "(max-width: 767px)"；
 *   window.innerWidth 仅允许用于浮层位置钳制（§八.2，白名单登记）。
 * 本门禁扫描 src 全部 .ts/.tsx 代码行（排除注释行 / 测试 / .d.ts）：
 *   1) matchMedia( 调用   2) window.innerWidth 引用   3) 非 767 的断点查询字符串。
 * 存量违规登记如下（随 W200 属主渐进收口，参照 R-55②/H6-T5 口径：清单只减不增，
 * 新增违规即红，禁止在此豁免）。
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/**
 * 基线白名单（文件级；rel 相对 src/，斜杠分隔）
 * - matchMedia 规范实现：hooks/useMediaQuery.ts（§八 钦定唯一实现载体）
 * - 存量违规：pages/platform/AlertsPage.tsx:746/:750 自建 matchMedia 监听，
 *   应改用 useMediaQuery —— 属主 W200（档随 R-55② 渐进收口，当前登记）。
 * - window.innerWidth 浮层位置钳制（§八.2 允许用途）：
 *   pages/data-workbench/CatalogContextMenu.tsx、pages/ontology/CanvasContextMenu.tsx
 */
const MATCH_MEDIA_BASELINE = new Set([
  'hooks/useMediaQuery.ts',
  'pages/platform/AlertsPage.tsx',
]);
const INNER_WIDTH_BASELINE = new Set([
  'pages/data-workbench/CatalogContextMenu.tsx',
  'pages/ontology/CanvasContextMenu.tsx',
]);

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

function isTestFile(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

describe('mobile-reuse — 禁新增响应式原语 (W200/C182, §八)', () => {
  it('noNewBreakpointPrimitives', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const violations: string[] = [];

    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (isTestFile(rel)) continue;
      const lines = readFileSync(f, 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (isComment(line)) return;
        const at = `${rel}:${i + 1}`;
        if (line.includes('matchMedia(') && !MATCH_MEDIA_BASELINE.has(rel)) {
          violations.push(`${at} — matchMedia 直用（§八：只允许 useMediaQuery 载体），新增即红`);
        }
        if (line.includes('window.innerWidth') && !INNER_WIDTH_BASELINE.has(rel)) {
          violations.push(`${at} — window.innerWidth 断点/布局用途（§八.2 仅允许浮层位置钳制白名单）`);
        }
        const mq = line.match(/\((max-width|min-width):\s*(\d+)px\)/g);
        if (mq) {
          for (const q of mq) {
            if (!/\(max-width:\s*767px\)/.test(q)) {
              violations.push(`${at} — 非规范断点 ${q}（§八：JS 断点仅 "(max-width: 767px)"）`);
            }
          }
        }
      });
    }

    expect(
      violations,
      violations.length ? `新增响应式原语/断点违规 ${violations.length} 处：\n${violations.join('\n')}` : undefined,
    ).toEqual([]);
  });
});
