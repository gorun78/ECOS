/**
 * file-size.test.ts — 冻结门禁名 `noSourceFileExceedsEightHundredLines`
 * （详设-08 §九 W197/C179，前端开发规范 §十：单文件 ≤800 行）
 *
 * 口径：扫描 ecos_frontend/src 全部 .ts/.tsx（含测试文件；仅排除 .d.ts 声明文件）
 * 行数为 0 或 >800 视为违规。
 *
 * 基线白名单：**空**。
 * 拆分前唯一超限文件 `services/ontologyApi.ts`（1,605 行）已按 W197/R-56② 手工按域
 * 拆至 `services/ontology/` 九个模块（最长 430 行），入口保留为纯 re-export 兼容层。
 * 其余超限文件（api.ts 等 5 个）已在先批次（W66 等）完成拆分。
 * ⇒ 本门禁当前必须为绿；任何其他 agent 新增/遗留的 >800 行文件即红，
 *   处理方式是拆分或由 PM 登记，**不是**在本文件豁免。
 *
 * 旧门禁 `file-size-lint.test.ts`（W66，仅扫 src/pages api 层、非冻结名）已由本文件
 * 取代删除；`npm run lint:size` 已改指向本文件。
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX_LINES = 800;

/** src/ 根目录（本文件位于 src/__tests__/）。 */
const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const st = statSync(full);
    if (st.isDirectory()) {
      walk(full, out);
    } else if ((entry.endsWith('.ts') || entry.endsWith('.tsx')) && !entry.endsWith('.d.ts')) {
      out.push(full);
    }
  }
}

function countLines(absPath: string): number {
  const content = readFileSync(absPath, 'utf8');
  if (content.length === 0) return 0;
  const normalized = content.endsWith('\n') ? content.slice(0, -1) : content;
  return normalized.split('\n').length;
}

describe('file-size — src 全量 .ts/.tsx ≤ 800 行 (W197/C179, R-56)', () => {
  it('noSourceFileExceedsEightHundredLines', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    expect(
      files.length,
      `未扫描到任何源文件 — 检查 SRC_ROOT=${SRC_ROOT}`,
    ).toBeGreaterThan(0);

    const violators = files
      .map((f) => ({ rel: relative(SRC_ROOT, f).split(sep).join('/'), lines: countLines(f) }))
      .filter((v) => v.lines > MAX_LINES)
      .sort((a, b) => b.lines - a.lines);

    const report = violators
      .map((v) => `  ${v.rel} — ${v.lines} 行 (上限 ${MAX_LINES})`)
      .join('\n');

    expect(
      violators,
      violators.length
        ? `以下源文件超过 ${MAX_LINES} 行上限，请按 §十 拆分（基线为空，新增违规即红）：\n${report}`
        : undefined,
    ).toEqual([]);
  });
});
