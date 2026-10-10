// @vitest-environment node
/**
 * no-fabricated-truth.test.ts — 冻结门禁名 `unit: no-fabricated-truth.test.ts › noRandomOrMockTruthInRenderPaths`
 * （详设-08 W204/C186 假真值清除收尾；前端 AGENTS.md 铁律 §7「无假真理 P0」静态面）
 *
 * 口径（参照 gitService-single-channel.test.ts 的源码扫描写法）：
 *   扫描 src/pages、src/components 的 .ts/.tsx（排除测试文件/.d.ts/注释行与行尾注释），断言：
 *   ① 无 `buildMock*` 标识符（罐头数据工厂不得出现在渲染路径）；
 *   ② `Math.random` 不得出现在真值语义上下文。启发式实现采取从严口径：
 *      受治文件内任何（非注释）Math.random 行即违规，必须逐个登记归类：
 *      - 非真值语义（画布布局坐标 / uuid-id 生成 / 骨架屏动画抖动）→ NON_TRUTH_WHITELIST 附一行理由；
 *      - 真值语义（verdict/status/score/chunks/latency/count/percent/tokens/hash 等字段赋值旁）→
 *        TRUTH_FABRICATION_BASELINE 存量登记（属主+行号，随 W204 尾面清偿，只减不增，新增即红）。
 *   另附回归哨兵：本批已真值化的四个属主面不得重新引入 Math.random。
 *
 * 白名单登记说明（文件级 + 理由；整体豁免禁止）：
 *   pages/aiworkbench/LogicView.tsx        — 画布节点落点坐标 (x/y) 随机 = 布局，非真值（任务点名登记）
 *   pages/WorkflowDesigner.tsx             — 画布节点坐标 x/y 随机落点 = 布局，非真值
 *   components/GraphCanvas.tsx             — 图谱节点布局散布偏移 (x/y ±40) = 布局，非真值
 *   pages/user-management/UserList.tsx     — 加载骨架条宽度抖动 = 动画占位，非真值
 *   components/common/SkeletonTable.tsx    — 加载骨架条宽度抖动 = 动画占位，非真值
 *   pages/WorldModelViewer.tsx             — GOAL-xxxxx 编码后缀 = uuid/id 生成，非真值
 *   pages/knowledge/tabs/KnowledgeEvalTab.tsx — 评测样例行 id 后缀 = uuid/id 生成，非真值
 *   pages/knowledge/services/extract.ts    — doc/imp 记录 id 后缀 = uuid/id 生成，非真值
 * 存量真值伪造登记（TRUTH_FABRICATION_BASELINE，只减不增）：
 *   pages/knowledge/tabs/SyncTab.tsx:114   — 同步完成合成 chunksCount（W204 同族假真值，属主任务 #124 尾面）
 *   pages/AgentStudio.tsx:232              — 合成 tokensUsed 增长（遗留页用量计量假真值，属主任务 #124/#120）
 *   components/pipeline/StepSchedule.tsx:37 — 演示流行数增量合成（pipeline 向导遗留 demo，属主 C 批返工任务 #120）
 * 罐头工厂存量登记：
 *   components/pipeline/StepVerify.tsx     — buildMockFileDiffs 假 diff 生成器（C 批返工面 W196/210，属主任务 #120）
 * @license Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url)); // src/

/** 扫描面：仅渲染路径两目录（详设-08 口径），services/api 等网络层不在本门禁面内。 */
const SCAN_DIRS = ['pages', 'components'];

/** 本批 W204/C186 已真值化属主面 — 回归哨兵，禁再引入 Math.random。 */
const REPAIRED_FILES = [
  'pages/aiworkbench/ChatbotStudioView.tsx',
  'pages/aiworkbench/DashboardView.tsx',
  'pages/aiworkbench/AgentStudioView.tsx',
  'pages/aiworkbench/agent-studio/agentStudioHelpers.ts',
  'components/copilot/useAipAutomation.ts',
];

/** 非真值语义白名单（文件级，逐文件一行理由，登记说明见文件头）。 */
const NON_TRUTH_WHITELIST = new Set([
  'pages/aiworkbench/LogicView.tsx',
  'pages/WorkflowDesigner.tsx',
  'components/GraphCanvas.tsx',
  'pages/user-management/UserList.tsx',
  'components/common/SkeletonTable.tsx',
  'pages/WorldModelViewer.tsx',
  'pages/knowledge/tabs/KnowledgeEvalTab.tsx',
  'pages/knowledge/services/extract.ts',
]);

/** 真值语义伪造存量登记（只减不增；文件命中行号见文件头）。 */
const TRUTH_FABRICATION_BASELINE = new Set([
  'pages/knowledge/tabs/SyncTab.tsx',
  'pages/AgentStudio.tsx',
  'components/pipeline/StepSchedule.tsx',
]);

/** buildMock* 罐头工厂存量登记（只减不增）。 */
const MOCK_FACTORY_BASELINE = new Set([
  'components/pipeline/StepVerify.tsx',
]);

const BUILD_MOCK_RE = /\bbuildMock[A-Za-z0-9_$]*\b/;
const RANDOM_RE = /Math\.random\s*\(\s*\)/;
/** 真值语义词（仅用于违规报告里标注伪造语义；扫描本身对全部 Math.random 行从严）。 */
const TRUTH_SEMANTIC_RE = /verdict|status|score|chunk|latency|count|percent|accuracy|token|hash/i;

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

/** 去行尾注释（`:（` 保护的 `://` URL 不误伤——先找 '//' 且前一个字符不是 ':'）。 */
function stripTrailingComment(line: string): string {
  let idx = -1;
  for (let i = 0; i + 1 < line.length; i++) {
    if (line[i] === '/' && line[i + 1] === '/' && line[i - 1] !== ':') { idx = i; break; }
  }
  return idx === -1 ? line : line.slice(0, idx);
}

function relOf(f: string): string {
  return relative(SRC_ROOT, f).split(sep).join('/');
}

function collectScanUnits(): Array<{ rel: string; lines: string[] }> {
  const units: Array<{ rel: string; lines: string[] }> = [];
  for (const d of SCAN_DIRS) {
    const abs = join(SRC_ROOT, d);
    if (!existsSync(abs)) continue;
    const files: string[] = [];
    walk(abs, files);
    for (const f of files) {
      const rel = relOf(f);
      if (isTestFile(rel)) continue;
      units.push({ rel, lines: readFileSync(f, 'utf8').split('\n') });
    }
  }
  return units;
}

describe('no-fabricated-truth — 渲染路径禁合成真值（W204/C186 冻结门禁）', () => {
  it('noRandomOrMockTruthInRenderPaths', () => {
    const units = collectScanUnits();
    expect(units.length, `扫描面为空（src/${SCAN_DIRS.join('、src/')} 缺失？不允许删面保绿）`).toBeGreaterThan(0);

    const mockViolations: string[] = [];
    const randomViolations: string[] = [];
    const repairedRegressions: string[] = [];

    for (const { rel, lines } of units) {
      lines.forEach((raw, i) => {
        if (isComment(raw)) return;
        const line = stripTrailingComment(raw);
        if (!line) return;

        // ① buildMock* 标识符
        if (BUILD_MOCK_RE.test(line) && !MOCK_FACTORY_BASELINE.has(rel)) {
          mockViolations.push(`${rel}:${i + 1} — buildMock* 罐头数据工厂（W204/C186：渲染路径禁合成回复/评分）`);
        }

        // ② Math.random（非注释行；白名单/存量登记见文件头逐条理由）
        if (RANDOM_RE.test(line)) {
          if (NON_TRUTH_WHITELIST.has(rel)) return; // 布局坐标 / uuid-id / 动画抖动（已逐文件登记）
          if (TRUTH_FABRICATION_BASELINE.has(rel)) return; // 存量登记（只减不增），新增行仍会随该文件其他命中现形
          const semantic = TRUTH_SEMANTIC_RE.test(line)
            ? '（命中真值语义字段：verdict/status/score/chunks/latency/count/percent/tokens/hash 等）'
            : '';
          randomViolations.push(`${rel}:${i + 1}${semantic} — ${line.trim().slice(0, 120)}`);
        }
      });
    }

    // 回归哨兵：本批已真值化面不得重新引入 Math.random（也不得被任何白名单收编）
    for (const rel of REPAIRED_FILES) {
      const abs = join(SRC_ROOT, rel);
      expect(existsSync(abs), `${rel} 缺失（哨兵面必须仍在位）`).toBe(true);
      if (!existsSync(abs)) continue;
      expect(NON_TRUTH_WHITELIST.has(rel) || TRUTH_FABRICATION_BASELINE.has(rel),
        `${rel} 属本批已真值化面，禁止进入白名单/存量登记`).toBe(false);
      const hit = readFileSync(abs, 'utf8').split('\n')
        .findIndex((raw, i) => !isComment(raw) && RANDOM_RE.test(stripTrailingComment(raw)));
      if (hit !== -1) repairedRegressions.push(`${rel}:${hit + 1}`);
    }

    expect(
      mockViolations,
      mockViolations.length ? `buildMock* 工厂新增 ${mockViolations.length} 处（存量基线=StepVerify，只减不增）：\n${mockViolations.join('\n')}` : undefined,
    ).toEqual([]);
    expect(
      randomViolations,
      randomViolations.length
        ? `Math.random 未登记命中 ${randomViolations.length} 处 —— 真值语义伪造一律禁止；布局/uuid/动画用法请逐文件加白名单并附一行理由（禁整体豁免）：\n${randomViolations.join('\n')}`
        : undefined,
    ).toEqual([]);
    expect(
      repairedRegressions,
      repairedRegressions.length ? `W204/C186 已真值化面重新引入 Math.random：\n${repairedRegressions.join('\n')}` : undefined,
    ).toEqual([]);
  });
});
