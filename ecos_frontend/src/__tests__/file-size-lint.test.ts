/**
 * file-size-lint - W66 gate
 *
 * Frontend convention (section 10): one api-module file <= 800 lines.
 * This spec recursively walks `src/pages` and flags any "api layer" file
 * (a file whose basename is `api.ts`, or any .ts module living inside a
 * directory named `api/`) whose line count exceeds 800.
 *
 * vitest runs under Node (jsdom does not shadow node:fs), so we simply
 * fs-walk the tree. Pure contract test - no jsdom / BFF / backend.
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX_LINES = 800;

/** 仓库根下的 src/pages 绝对目录（本文件位于 src/__tests__/）。 */
const PAGES_DIR = join(
  fileURLToPath(new URL('..', import.meta.url)),
  'pages',
);

/** 递归收集目录下全部 .ts / .tsx 文件 */
function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) {
      // 不进入隐藏目录 / node_modules
      if (entry.startsWith('.') || entry === 'node_modules') continue;
      walk(full, out);
    } else if (/\.(ts|tsx)$/.test(entry) && !entry.endsWith('.d.ts')) {
      out.push(full);
    }
  }
}

/** 是否为「api 层」文件：basename 为 api.ts，或位于名为 api 的目录内 */
function isApiLayerFile(absPath: string): boolean {
  const name = absPath.split(sep).pop() ?? '';
  if (name === 'api.ts') return true;
  const parts = absPath.split(sep);
  return parts.includes('api');
}

function countLines(absPath: string): number {
  // 0 结尾换行会使 naive split 多出一空行，这里按"非空内容行 + 尾部"归一为行数。
  const content = readFileSync(absPath, 'utf8');
  if (content.length === 0) return 0;
  // 去除末尾可能的单个换行后按换行计数 + 1
  const trimmed = content.endsWith('\n') ? content.slice(0, -1) : content;
  return trimmed.split('\n').length;
}

describe('file-size-lint — api 层单文件 ≤ 800 行 (W66)', () => {
  it('src/pages 下所有 api 层文件均不超过 800 行', () => {
    const files: string[] = [];
    walk(PAGES_DIR, files);
    const apiFiles = files.filter(isApiLayerFile);

    expect(
      apiFiles.length,
      `未找到任何 api 层文件 — 检查 PAGES_DIR=${PAGES_DIR}`,
    ).toBeGreaterThan(0);

    const origin = PAGES_DIR.split(sep).slice(0, -1).join(sep);
    const violators = apiFiles
      .map((f) => ({ rel: relative(origin, f), lines: countLines(f) }))
      .filter((v) => v.lines > MAX_LINES)
      .sort((a, b) => b.lines - a.lines);

    const report = violators
      .map((v) => `  ${v.rel} — ${v.lines} 行 (上限 ${MAX_LINES})`)
      .join('\n');

    expect(
      violators,
      violators.length
        ? `以下 api 层文件超过 ${MAX_LINES} 行上限，请拆分（参考 §十 收口）：\n${report}`
        : undefined,
    ).toEqual([]);
  });
});
