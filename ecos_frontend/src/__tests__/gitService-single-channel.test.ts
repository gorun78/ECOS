/**
 * gitService-single-channel.test.ts — 冻结门禁名 `noDirectGitEndpointCallOutsideGitService`
 * （详设-08 §九 W199/C181，前端开发规范 §十一：Git 面单通道 services/gitService.ts，
 *  ADR-12；铁律 #6）
 *
 * 口径：`/api/v1/ecos/git/*` 端点只允许 `services/gitService.ts` 直接调用，
 * 且只传 repositoryId（不传本地路径/凭据——§十一 契约，另见交付报告勘察项）。
 * 本门禁扫描 src 代码行（排除注释/测试/.d.ts）中出现 "/api/v1/ecos/git" 字面量的文件：
 *   - services/gitService.ts 为唯一正主（通道本体，不计违规）；
 *   - 其余出现即违规，存量登记如下。
 * 注：`/api/v1/engine/ontology/git/*` 属引擎面端点，非本通道管辖（services/ontology/
 * ontologyEngineApi.ts 有注释说明），不判违规。
 *
 * 基线白名单（存量登记，属主 W199/B 批任务 #119 收敛为经 gitService 调用；
 * R-55②/H6-T5 口径清单只减不增，新增即红）：
 *   components/data-workbench/GitPanel.tsx:206/:209 — apiFetchData 直调 ecos/git 动作端点
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const CANONICAL = 'services/gitService.ts';
const BASELINE = new Set([
  'components/data-workbench/GitPanel.tsx',
]);

const GIT_ENDPOINT_RE = /\/api\/v1\/ecos\/git\b/;

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
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///') || t.startsWith('/**');
}

describe('gitService-single-channel — ecos/git 端点单通道 (W199/C181, §十一/ADR-12)', () => {
  it('noDirectGitEndpointCallOutsideGitService', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const violations: string[] = [];

    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (rel === CANONICAL || isTestFile(rel)) continue;
      const lines = readFileSync(f, 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (isComment(line)) return;
        if (GIT_ENDPOINT_RE.test(line)) {
          if (!BASELINE.has(rel)) {
            violations.push(`${rel}:${i + 1} — ecos/git 端点直调（§十一：请改经 services/gitService 且只传 repositoryId）`);
          }
        }
      });
    }

    expect(
      violations,
      violations.length
        ? `Git 单通道违规 ${violations.length} 处（基线仅 GitPanel 登记随 W199 收口，新增即红）：\n${violations.join('\n')}`
        : undefined,
    ).toEqual([]);

    // 通道本体存在性哨兵：gitService.ts 必须仍在位，防止"删通道保绿"。
    const hasCanonical = files.some((f) => relative(SRC_ROOT, f).split(sep).join('/') === CANONICAL);
    expect(hasCanonical, 'services/gitService.ts 单通道本体缺失（不允许移除通道来通过门禁）').toBe(true);
  });
});
