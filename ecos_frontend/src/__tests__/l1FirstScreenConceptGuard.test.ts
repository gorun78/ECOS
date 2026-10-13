// @vitest-environment node
/**
 * l1FirstScreenConceptGuard.test.ts — L1 首屏「业务语义纯度」静态 grep 门禁
 * （《前端设计方案-…-2026-10-11.md》§3.9 明文承诺：L1 首屏不得出现
 * Mind / Goal Tree / Causal / 后端原始字段名；详设-07 F07-06 三层 IA 第一层口径）。
 *
 * 2026-10-12 复盘 S3 判定：该门禁**从未建立**（属承诺未兑现，非"口径不变"），本文件补建。
 * 口径：对 `pages/workbench/index.tsx`（L1 唯一入口文件）逐行扫描（剥离注释行后）：
 *  1. 禁认知/智能域技术概念词（Mind、Goal Tree、Causal 及驼峰变体）；
 *  2. 禁 L2/L3 后端 VO 原始字段名外泄（contextDigest/allowedTransitions/evidenceRefs 等——
 *     这些字段只允许出现在 L2 blueprint 面）；
 *  3. 禁字面色板类/字面十六进制色（与 no-hardcoded-colors 同正则，L1 属新改造面=零容忍）；
 *  4. 渲染文案必须经 t('workspace.l1.*')，禁硬编码中英文状态词。
 * 任何一条命中 = 门禁红。范围只锁 L1 文件本身，误伤面为零。
 */
import { describe, it, expect } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const L1_FILE = join(SRC_ROOT, 'pages/workbench/index.tsx');

const TECH_CONCEPT_RE =
  /\b(?:Mind|MindEngine|Goal\s?Tree|GoalTree|Causal[A-Za-z]*|counterfactual[A-Za-z]*)\b/;
const RAW_VO_FIELD_RE =
  /\b(?:contextDigest|artifactDigest|blueprintVersion|allowedTransitions|evidenceRefs|goalStatement|successCriteria|cognitiveAsset|blueprintId|traceId|lifecycleState)\b/;
const PALETTE_RE =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|shadow|divide|outline)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b/;
const HEX_RE = /["'`]#[0-9a-fA-F]{3,8}\b/;
const RAW_STATE_TEXT_RE = /["'`][^"'`\n]*(加载中|暂无|不可用|无权限|未找到|离线)[^"'`\n]*["'`]/;

function isComment(line: string): boolean {
  const t = line.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///');
}

function codeLines(): Array<{ no: number; text: string }> {
  return readFileSync(L1_FILE, 'utf8')
    .split('\n')
    .map((text, i) => ({ no: i + 1, text }))
    .filter((l) => !isComment(l.text));
}

describe('L1 首屏业务语义纯度门禁（设计方案 §3.9 / F07-06，复盘 S3 补建）', () => {
  it('门禁目标文件存在', () => {
    expect(existsSync(L1_FILE), `L1 入口文件缺失：${L1_FILE}`).toBe(true);
  });

  it('首屏零技术概念词（Mind / Goal Tree / Causal / counterfactual）', () => {
    const hits = codeLines()
      .filter((l) => TECH_CONCEPT_RE.test(l.text))
      .map((l) => `index.tsx:${l.no} — ${l.text.trim().slice(0, 120)}`);
    expect(hits, `L1 首屏出现认知域技术概念（违反 §3.9 业务语义红线）：\n${hits.join('\n')}`).toEqual([]);
  });

  it('首屏零后端原始字段名外泄', () => {
    const hits = codeLines()
      .filter((l) => RAW_VO_FIELD_RE.test(l.text))
      .map((l) => `index.tsx:${l.no} — ${l.text.trim().slice(0, 120)}`);
    expect(hits, `L1 首屏外泄 L2/L3 后端 VO 原始字段名：\n${hits.join('\n')}`).toEqual([]);
  });

  it('首屏零字面色（useTheme token 单源，同 no-hardcoded-colors 正则）', () => {
    const hits = codeLines()
      .filter((l) => PALETTE_RE.test(l.text) || HEX_RE.test(l.text))
      .map((l) => `index.tsx:${l.no} — ${l.text.trim().slice(0, 120)}`);
    expect(hits, `L1 首屏出现字面色板/十六进制色：\n${hits.join('\n')}`).toEqual([]);
  });

  it('首屏状态文案全部经 i18n（无硬编码中文态词）', () => {
    const hits = codeLines()
      .filter((l) => RAW_STATE_TEXT_RE.test(l.text))
      .map((l) => `index.tsx:${l.no} — ${l.text.trim().slice(0, 120)}`);
    expect(hits, `L1 首屏出现硬编码中文状态文案（应走 t('workspace.l1.*')）：\n${hits.join('\n')}`).toEqual([]);
  });
});
