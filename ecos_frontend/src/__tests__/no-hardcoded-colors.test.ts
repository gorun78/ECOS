// @vitest-environment node
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
 *
 * 2026-10-12 复盘收口扩展（复盘报告 S3/S12「门禁白名单盲区」）：
 *  - `threeReformSurfacesHaveNoLiteralPalette` — 三项前端改造面（L2 blueprint 目录 +
 *    认知资产/候选评审组件）零容忍，堵上"新组件不在结构性白名单内"的盲区；
 *  - `srcLiteralPaletteDoesNotExceedBaseline` — 全库棘轮：逐文件残量不得超过
 *    palette-baseline.json 快照（新文件/新增行即红；修复后应同步调低基线）。
 *    单源豁免：components/ThemeContext.tsx 是四主题 token 的合法定义处，不参与扫描。
 */
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

/** 三项前端改造面（复盘 S12：BlueprintStatusTrack 违例即因旧白名单盲区漏检）。 */
const REFORM_DIRS = ['pages/workbench/[scenarioId]/blueprint'];
const REFORM_FILES = [
  'pages/knowledge/pages/CognitiveAssetPage.tsx',
  'pages/knowledge/components/CognitiveAssetCard.tsx',
  'pages/knowledge/components/CognitiveAssetResolveDrawer.tsx',
  'pages/knowledge/components/CandidateReviewPanel.tsx',
];

/** 全库棘轮豁免：主题 token 单源定义处本身即色板字面量。 */
const BASELINE_EXEMPT = ['components/ThemeContext.tsx'];

const PALETTE_RE =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|shadow|divide|outline)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b/;
const HEX_RE = /["'`]#[0-9a-fA-F]{3,8}\b/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function countViolations(file: string): number {
  return readFileSync(file, 'utf8')
    .split('\n')
    .filter((l) => !isComment(l) && (PALETTE_RE.test(l) || HEX_RE.test(l))).length;
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

function collectReformTargets(): string[] {
  const targets: string[] = REFORM_FILES.map((f) => join(SRC_ROOT, f));
  for (const d of REFORM_DIRS) {
    const dir = join(SRC_ROOT, d);
    if (existsSync(dir)) {
      for (const e of readdirSync(dir)) {
        if (e.endsWith('.tsx') && !e.includes('.test.') && !e.includes('.spec.')) targets.push(join(dir, e));
      }
    }
  }
  return targets.filter((p) => existsSync(p));
}

function walkSourceFiles(dir: string, acc: string[] = []): string[] {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'node_modules' || e.name === '__tests__' || e.name.startsWith('.')) continue;
    const p = join(dir, e.name);
    if (e.isDirectory()) walkSourceFiles(p, acc);
    else if (/\.(tsx|ts)$/.test(e.name) && !/\.(test|spec)\./.test(e.name)) acc.push(p);
  }
  return acc;
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

  it('threeReformSurfacesHaveNoLiteralPalette — 改造面零容忍（复盘 S12 白名单盲区封堵）', () => {
    const violations: string[] = [];
    for (const f of collectReformTargets()) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      const lines = readFileSync(f, 'utf8').split('\n');
      lines.forEach((line, i) => {
        if (isComment(line)) return;
        if (PALETTE_RE.test(line) || HEX_RE.test(line)) {
          violations.push(`${rel}:${i + 1} — ${line.trim().slice(0, 120)}`);
        }
      });
    }
    expect(
      violations,
      violations.length
        ? `三项改造面出现字面色/字面十六进制（必须走 useTheme token）：\n${violations.join('\n')}`
        : undefined,
    ).toEqual([]);
    for (const f of REFORM_FILES) {
      expect(existsSync(join(SRC_ROOT, f)), `改造面零容忍清单指向不存在的文件：${f}`).toBe(true);
    }
  });

  it('srcLiteralPaletteDoesNotExceedBaseline — 全库棘轮（只减不增）', () => {
    const baselinePath = join(SRC_ROOT, '__tests__', 'palette-baseline.json');
    const baseline = JSON.parse(readFileSync(baselinePath, 'utf8')) as Record<string, number>;
    const regressions: string[] = [];
    const reduced: string[] = [];

    for (const f of walkSourceFiles(SRC_ROOT)) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      if (BASELINE_EXEMPT.includes(rel)) continue;
      const count = countViolations(f);
      const allowed = baseline[rel] ?? 0;
      if (count > allowed) {
        regressions.push(`${rel}: 实扫 ${count} 行 > 基线 ${allowed} 行${allowed === 0 ? '（新引入）' : '（较基线回潮）'}`);
      } else if (count < allowed) {
        reduced.push(`${rel}: ${allowed} → ${count}（请同步调低 palette-baseline.json）`);
      }
    }

    expect(
      regressions,
      regressions.length
        ? `字面色较基线回潮/新增 ${regressions.length} 个文件（R-55② 只减不增）：\n${regressions.join('\n')}`
        : undefined,
    ).toEqual([]);
    if (reduced.length) console.info(`[palette-baseline] 以下文件残量已下降，可收紧基线：\n${reduced.join('\n')}`);
  });
});
