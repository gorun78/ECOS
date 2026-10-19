/**
 * gateTraversalTimeout.test.ts — 遍历型静态门禁必须声明文件级 test 超时（D-B5-27）。
 *
 * <p>为什么要一条元门禁：以 `readdirSync` 遍历全 src 的门禁单次要读上千个文件，本机
 * 全量跑（75 个测试文件并发）会把它饿到 vitest 默认 5s 之外，随机假红且不可复现——
 * 2026-10-18 #143/B5-8 实证：同一棵树首跑 `6 failed | 482 passed`（多出 `unused-deps`
 * 与 `sseChannelSingleSource` 两项 `Test timed out in 5000ms`），隔离单跑 8/8 绿，
 * 复跑回落 `4 failed | 484 passed`。判定不可复现的门禁等于没有门禁。</p>
 *
 * <p>只认「文件级 `vi.setConfig({ testTimeout: … })`」这一种形状：per-test 第三参只在
 * 写它的那条用例上生效，同文件其余用例照旧吃 5s——`unused-deps.test.ts` 正是已经有
 * 一处 per-test 超时、却在另一条遍历用例上超时的实证。阈值下限 15_000ms，防止用
 * `testTimeout: 1` 这类占位声明糊过本门。</p>
 *
 * <p>判据两侧自检：缺陷原形必须能变红（下方 DEFECT_SAMPLES），收口形状不得误报；
 * 本文件自己遍历目录，故同样受本门约束（头部已声明 setConfig）。</p>
 *
 * <p>D-B5-39（2026-10-19 #143/B5-16 全量跑实测读出）：原扫描面只吃 `.test.ts`/`.spec.ts`，
 * `.tsx` 门禁文件对本门永不可见，`sevenStates.matrix.spec.tsx` 的遍历腿超时假红因此长期
 * 无人拦截。现扫描面扩为 `\.(test|spec)\.tsx?$`，并加一条「.tsx 确实在面内」的自检防回归。</p>
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, expect, it, vi } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, relative, sep } from 'node:path';

vi.setConfig({ testTimeout: 30_000 });

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));

/** 声明形状：文件级 setConfig 且带 testTimeout 数值 */
const DECLARATION_RE = /vi\.setConfig\(\s*\{[^}]*\btestTimeout\s*:\s*(\d[\d_]*)/;

/** 本门阈值下限（ms）——低于它视为占位糊闸 */
const MIN_TIMEOUT_MS = 15_000;

/**
 * 受本门约束的测试文件形状（D-B5-39 补漏）：原判据只吃 `.test.ts`/`.spec.ts`，
 * `.tsx` 门禁（含遍历腿的 JSX 面门）对本门完全不可见——#143/B5-16 全量跑实测
 * 读出的 `sevenStates.matrix.spec.tsx` 超时假红正是这个盲区造成的。
 */
const GATE_FILE_RE = /\.(test|spec)\.tsx?$/;

function walk(dir: string, out: string[]): void {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (GATE_FILE_RE.test(entry) && !entry.endsWith('.d.ts')) out.push(full);
  }
}

/** 遍历型门禁 = 在测试文件里调用 readdirSync（不认注释，只看调用面） */
function isTraversalGate(src: string): boolean {
  return /\breaddirSync\s*\(/.test(src);
}

/** 返回违例原因（无违例返回 null），供真实扫描与缺陷样本共用同一判据 */
function violation(src: string): string | null {
  if (!isTraversalGate(src)) return null;
  const m = DECLARATION_RE.exec(src);
  if (!m) return '未声明文件级 vi.setConfig({ testTimeout })';
  const ms = Number(m[1].replace(/_/g, ''));
  if (!Number.isFinite(ms) || ms < MIN_TIMEOUT_MS) return `声明的 testTimeout=${ms}ms 低于下限 ${MIN_TIMEOUT_MS}ms`;
  return null;
}

describe('遍历型门禁超时声明 (D-B5-27)', () => {
  it('判据能变红：未声明超时的遍历门禁命中', () => {
    const DEFECT_NO_DECL = "import { readdirSync } from 'node:fs';\nfor (const e of readdirSync(dir)) {}\nit('g', () => {});";
    const DEFECT_TOO_SMALL = "import { readdirSync } from 'node:fs';\nfor (const e of readdirSync(dir)) {}\nvi.setConfig({ testTimeout: 1000 });";
    expect(violation(DEFECT_NO_DECL)).toContain('未声明');
    expect(violation(DEFECT_TOO_SMALL)).toContain('低于下限');
  });

  it('不误报：收口形状（含本门自身）满足声明', () => {
    const OK = "import { readdirSync } from 'node:fs';\nvi.setConfig({ testTimeout: 30_000 });";
    expect(violation(OK)).toBeNull();
    const self = readFileSync(fileURLToPath(new URL(import.meta.url)), 'utf8');
    expect(violation(self), '本门自身未满足判据').toBeNull();
  });

  it('盲区补漏 (D-B5-39)：.tsx 门禁确实在扫描面内', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const tsx = files.filter((f) => f.endsWith('.tsx'));
    expect(tsx.length, '扫描面内没有任何 .tsx 门禁文件 ⇒ 本门重新闭上了 D-B5-39 盲区').toBeGreaterThan(0);
    const matrix = tsx.find((f) => f.endsWith('sevenStates.matrix.spec.tsx'));
    expect(matrix, '实证缺陷文件未进入扫描面').toBeDefined();
    expect(violation(readFileSync(matrix as string, 'utf8')), 'sevenStates 遍历门仍未声明文件级超时').toBeNull();
  });

  it('全 src 遍历型门禁均已声明 >= 15s 文件级超时', () => {
    const files: string[] = [];
    walk(SRC_ROOT, files);
    const offenders: string[] = [];
    for (const f of files) {
      const rel = relative(SRC_ROOT, f).split(sep).join('/');
      const why = violation(readFileSync(f, 'utf8'));
      if (why) offenders.push(`${rel} — ${why}`);
    }
    expect(
      offenders,
      offenders.length
        ? `遍历型门禁缺文件级 test 超时（高负载全量跑会假红，判定不可复现）。修法：在文件顶部加 vi.setConfig({ testTimeout: 30_000 })：\n${offenders.join('\n')}`
        : undefined,
    ).toEqual([]);
  }, 30_000);
});
