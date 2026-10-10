/**
 * no-hardcoded-colors.test.ts — 冻结门禁名 `structuralComponentsHaveNoLiteralPalette`
 * （详设-08 §九 W196/C178，前端开发规范 §七；裁决 R-55②：结构性组件+新组件优先令牌化，
 * 历史页面按域渐进）
 *
 * 口径：结构性容器（应用外壳 + 通用结构化组件）代码行禁止出现
 *  - 字面 Tailwind 色板类（(bg|text|border|ring|from|to|via|fill|stroke|shadow|divide|outline)-<色名>-<阶>）
 *  - 十六进制字面色（"#rgb"~"#rrggbbaa"）
 * 范围（任务冻结）：App.tsx、components/Sidebar.tsx、components/CommandPalette.tsx、
 * components/common/ErrorBoundary.tsx + components/common/structured/*.tsx。
 *
 * 当前状态：**允许为红**（W196 字面色清零属主为并行 agent，任务 #126；本门禁负责暴露
 * 残量并在失败信息中报告逐文件计数）。不做基线豁免——§七.7 的"小号强调色"豁免仅适用于
 * 通用 Button/Badge 内部，本范围未含它们；structured/ 目录内当前实测为 0，若未来引入
 * 强调色需先在 §七 口径下改令牌。
 */
// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

const STRUCTURAL_FILES = [
  'App.tsx',
  'components/Sidebar.tsx',
  'components/CommandPalette.tsx',
  'components/common/ErrorBoundary.tsx',
];
const STRUCTURED_DIR = 'components/common/structured';

const PALETTE_RE =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|shadow|divide|outline)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b/;
const HEX_RE = /["'`]#[0-9a-fA-F]{3,8}\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function collectTargets(): string[] {
  const targets = STRUCTURAL_FILES.map((f) => join(SRC_ROOT, f));
  const dir = join(SRC_ROOT, STRUCTURED_DIR);
  if (existsSync(dir)) {
    for (const e of readdirSync(dir)) {
      if (e.endsWith('.tsx') && !e.includes('.test.')) targets.push(join(dir, e));
    }
  }
  return targets.filter((p) => existsSync(p));
}

describe('no-hardcoded-colors — 结构性容器零字面色 (W196/C178, §七/R-55②)', () => {
  it('structuralComponentsHaveNoLiteralPalette', () => {
    const violations: string[] = [];
    let residual = 0;

    for (const f of collectTargets()) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      const lines = readFileSync(f, 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (isComment(line)) return;
        if (PALETTE_RE.test(line) || HEX_RE.test(line)) {
          residual += 1;
          violations.push(`${rel}:${i + 1} — ${line.trim().slice(0, 120)}`);
        }
      });
    }

    expect(
      violations,
      violations.length
        ? `结构性字面色残量 ${residual} 行（W196 属主渐进清零，R-55②）：\n${violations.join('\n')}`
        : undefined,
    ).toEqual([]);
  });
});
