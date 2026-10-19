// @vitest-environment node
/**
 * sevenStates.matrix.spec.tsx — design-05 B.5 七态矩阵 + B.0 入口归一 + B.9-5 假交互清理
 * 前端门禁（vitest，node 环境；用例名对齐本册冻结的 COG-xx 验收名）。
 * 注：因需 renderToStaticMarkup 直接渲染组件（JSX），本门禁 spec 使用 .tsx 扩展名。
 *
 * 覆盖面：
 *  1. COG-IA 认知四件套唯一入口：CognitiveRightPanel 必须且只能自 L2 场景详情
 *     「高级/调试」子面（AdvancedDebugPanel）挂载（2026-10-11 §3.8 R2 迁一删一）；
 *     场景管理视图不得再引用假 Git UI（GitVersionTab/localStorage 缓存/假 push 动画）。
 *  2. COG-E1 右栏探测列表渲染（Mind 名称/能力 chips）。
 *  3. COG-503 引擎停止/ schema 未就绪 → 置灰 banner 分支存在 + 故障态与空态视觉可分。
 *  4. COG-stub 未实现（501 + stub=true）→ 共享占位态（非故障 banner）。
 *  5. B.6 limit 钳制契约（默认 10 / 上限 50 / 必传）。
 *  6. D.5 错误码 → 文案键映射（traceId 可复制 UI 存在）。
 */
import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
// D-B5-27 同型补漏（#143/B5-16 全量跑实测读出）：本门禁含 pages 目录遍历腿，全量并发下
// 5s 默认超时被 CPU 争用吃成假红（单跑 160ms 通过）⇒ 遍历型门禁一律带 test 级超时。
vi.setConfig({ testTimeout: 30_000 });
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { ThemeProvider } from '../../../../components/ThemeContext';
import { LanguageProvider } from '../../../../components/LanguageContext';
import DetectTab from '../DetectTab';
import { CognitiveErrorState, CognitiveEmptyState, CognitiveSkeleton, cognitiveErrorTitleKey } from '../CognitionStateViews';
import type { MindLoadState } from '../CognitiveRightPanel';
import {
  CognitiveApiError,
  clampCognitionLimit,
  COGNITION_DEFAULT_LIMIT,
  COGNITION_MAX_LIMIT,
} from '../../../../services/cognitiveClient';

const SRC = path.resolve(__dirname, '..', '..', '..', '..');
const read = (rel: string) => fs.readFileSync(path.join(SRC, rel), 'utf8');
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, ' ');

// ThemeProvider / LanguageProvider 惰性读 localStorage：node 环境补最小垫片（同 DetectTabStubPlaceholder 约定）
const storageShim = {
  getItem: (): string | null => null,
  setItem: (): void => undefined,
  removeItem: (): void => undefined,
  clear: (): void => undefined,
  key: (): string | null => null,
  length: 0,
};
beforeAll(() => {
  const g = globalThis as unknown as Record<string, unknown>;
  g.localStorage = storageShim;
  g.__ecos_test_locale = 'zh';
});
afterAll(() => {
  const g = globalThis as unknown as Record<string, unknown>;
  delete g.localStorage;
  delete g.__ecos_test_locale;
});

const render = (node: React.ReactElement): string =>
  renderToStaticMarkup(
    <LanguageProvider>
      <ThemeProvider>{node}</ThemeProvider>
    </LanguageProvider>,
  );

describe('COG-IA 认知四件套唯一入口（B.0 入口归一）', () => {
  // 2026-10-11 §3.8 R2 双结构收敛：认知右栏自场景管理视图整体迁至 L2 场景详情
  // 「高级/调试」子面（AdvancedDebugPanel），本用例锚点随迁一删一改钉新落点。
  const PANEL_MOUNT = path.join('pages', 'workbench', '[scenarioId]', 'advanced', 'AdvancedDebugPanel.tsx');
  const view = stripComments(read('pages/ScenarioManagementView.tsx'));

  /** 全仓生产源码中 JSX 挂载 `<CognitiveRightPanel ...>` 的文件清单（注释与测试文件不计）。 */
  function listPanelMounts(dir: string, out: string[] = []): string[] {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (e.name === '__tests__' || e.name === 'node_modules') continue;
        listPanelMounts(p, out);
      } else if (/\.tsx$/.test(e.name) && !/\.(test|spec)\.tsx$/.test(e.name)) {
        if (/<CognitiveRightPanel\s/.test(stripComments(fs.readFileSync(p, 'utf8')))) out.push(p);
      }
    }
    return out;
  }

  it('L2 高级/调试子面挂载 CognitiveRightPanel（四卡契约 E1~E4 唯一渲染入口）', () => {
    const advanced = stripComments(read(PANEL_MOUNT));
    expect(advanced).toContain('CognitiveRightPanel');
    expect(advanced).toMatch(/<CognitiveRightPanel\s+scenarioId=\{scenarioId\}\s*\/>/);
  });
  it('挂载点全应用唯一（双结构不得并存；多一处即回归）', () => {
    const mounts = listPanelMounts(path.join(SRC, 'pages')).map((f) => path.relative(SRC, f).replace(/\\/g, '/'));
    expect(mounts, `CognitiveRightPanel 挂载点应唯一`).toEqual([PANEL_MOUNT.replace(/\\/g, '/')]);
  });
  it('场景管理视图不再挂载右栏（迁一删一，禁止回流出第二入口）', () => {
    expect(view).not.toMatch(/<CognitiveRightPanel\s/);
    expect(view).not.toMatch(/from\s+['"]\.\/scenario\/cognition\/CognitiveRightPanel['"]/);
  });
  it('认知四卡数据源单源：右栏组件只经 services/cognitiveClient 调 E1~E4（禁 workspace 历史路径 GET）', () => {
    const panelDir = path.join(SRC, 'pages', 'scenario', 'cognition');
    for (const f of fs.readdirSync(panelDir).filter((n) => /\.(ts|tsx)$/.test(n))) {
      const code = stripComments(fs.readFileSync(path.join(panelDir, f), 'utf8'));
      expect(code, `${f} 不得调用 workspace 历史认知路径`).not.toMatch(/\/workspace\/scenarios\/[^"'`]*cognitive/);
    }
    expect(read('services/cognitiveClient.ts')).toContain('/api/v1/business/scenarios');
  });
});

describe('B.9-5 假交互/伪持久化删除（E 项防回归）', () => {
  const view = stripComments(read('pages/ScenarioManagementView.tsx'));
  it('场景管理视图无 localStorage 缓存 ecos_cached_git_commits/branches（伪持久化删除）', () => {
    expect(view).not.toContain('ecos_cached_git_commits');
    expect(view).not.toContain('ecos_cached_git_branches');
    expect(view).not.toMatch(/localStorage\.(getItem|setItem)/);
  });
  it('假 git push 动画与伪造 commit 生成器已删除', () => {
    expect(view).not.toContain('git push origin');
    expect(view).not.toContain('handleGitPushRemote');
    expect(view).not.toContain('handleGitCommitManual');
    expect(view).not.toMatch(/Math\.random\(\)\.toString\(16\)/);
  });
  it('Git UI 走 services/gitService 单一通道（经 GitPanel，只传 repositoryId），假 GitVersionTab 已移除', () => {
    expect(view).toContain('GitPanel');
    expect(view).toMatch(/<GitPanel\s+repoId=/);
    expect(view).not.toContain('GitVersionTab');
    expect(fs.existsSync(path.join(SRC, 'pages', 'project-workbench', 'tabs', 'GitVersionTab.tsx'))).toBe(false);
    const gitPanel = stripComments(read('components/data-workbench/GitPanel.tsx'));
    expect(gitPanel).toContain('services/gitService');
  });
});

describe('F05-16/B 因果分析假数据删除（B 项防回归）', () => {
  // 2026-10-11 议题 2：假数据原载体 pages/aiworkbench/cognition/causalAnalysis.tsx
  // 随 AI 台认知 Tab 整体删除（三分下沉），比"清掉常量"更强 —— 载体不得复活；
  // 硬编码假链/假数值的禁令同步转移到在册认知组件扫描面上。
  it('假数据载体文件与其所在目录已物理删除且不得复活', () => {
    expect(
      fs.existsSync(path.join(SRC, 'pages', 'aiworkbench', 'cognition', 'causalAnalysis.tsx')),
      'causalAnalysis.tsx（硬编码 mock 因果链）必须已删除',
    ).toBe(false);
    expect(
      fs.existsSync(path.join(SRC, 'pages', 'aiworkbench', 'cognition')),
      'AI 台 cognition 目录（第四认知入口）必须已删除',
    ).toBe(false);
  });

  it('在册认知组件不再持有硬编码 mock 因果链/固定结论数值', () => {
    const scope = [
      ...fs
        .readdirSync(path.join(SRC, 'pages', 'scenario', 'cognition'))
        .filter((n) => /\.(ts|tsx)$/.test(n) && !n.includes('.test.'))
        .map((n) => path.join('pages', 'scenario', 'cognition', n)),
      'pages/scenario/CognitionPanel.tsx',
    ];
    for (const rel of scope) {
      const code = stripComments(read(rel));
      for (const forbidden of [/const NODES/, /EDGE_PAIRS/, /TRACE_NODES/]) {
        expect(code, `${rel} 残留 mock 常量 ${forbidden}`).not.toMatch(forbidden);
      }
      for (const fake of ['0.82', '87%', '12.3%', '0.54']) {
        expect(code, `${rel} 残留 mock 固定值: ${fake}`).not.toContain(fake);
      }
      // 原"复用 CausalGraphView 且 showWhatIf={false}"断言的现势形态：认知域根本不挂
      // 该组件（CausalGraphView 的 What-If 是本地线性伪推演，默认开启），故认知面
      // 一律禁止 import 它，比逐处关开关更严。
      expect(code, `${rel} 认知域不得挂 CausalGraphView（含本地 What-If 伪推演）`).not.toMatch(
        /import\s+\S*\s+from\s+['"][^'"]*CausalGraphView['"]/,
      );
    }
  });
});

describe('B.5 七态矩阵渲染（C 项）', () => {
  const minds = (over: Partial<MindLoadState>): MindLoadState => ({
    loading: false,
    rows: [],
    error: null,
    ...over,
  });

  it('COG-E1 探测列表渲染 Mind 名称与能力 chips', () => {
    const html = render(
      <DetectTab
        scenarioId="sc-1"
        minds={minds({
          rows: [{ mindId: 'm-1', name: '经营预测 Mind', capabilityMask: ['DETECT', 'EVALUATE'], isActive: true }],
        })}
        onRetryMinds={() => undefined}
      />,
    );
    expect(html).toContain('经营预测 Mind');
    expect(html).toContain('DETECT');
    expect(html).toContain('EVALUATE');
  });

  it('空（200 data:[]）与故障（5xx/ECOS-COG-*）视觉可分：图标/role/testid 均不同', () => {
    const empty = render(
      <DetectTab scenarioId="sc-1" minds={minds({ rows: [] })} onRetryMinds={() => undefined} />,
    );
    const failure = render(
      <DetectTab
        scenarioId="sc-1"
        minds={minds({
          error: new CognitiveApiError({ code: 'HTTP_ERROR', status: 500, message: 'cognitive internal error', traceId: 'tr-500' }),
        })}
        onRetryMinds={() => undefined}
      />,
    );
    // 空态：role=status + ShieldX 图标 + 灰色文案
    expect(empty).toContain('role="status"');
    expect(empty).not.toContain('role="alert"');
    // 故障态：role=alert + 红色 danger + traceId + 复制按钮（traceId 可复制）
    expect(failure).toContain('role="alert"');
    expect(failure).toContain('data-testid="cog-error-state"');
    expect(failure).toContain('tr-500');
    expect(failure).toContain('data-testid="cog-copy-trace"');
    expect(failure).not.toBe(empty);
  });

  it('COG-503 引擎停止（ECOS-COG-500）→ 右栏置灰 banner 分支存在且区分 schema 未就绪（ECOS-COG-501）', () => {
    const panel = read('pages/scenario/cognition/CognitiveRightPanel.tsx');
    expect(panel).toContain('cog-engine-down-banner');
    expect(panel).toContain('ENGINE_DISABLED');
    expect(panel).toContain('SCHEMA_NOT_READY');
    expect(panel).toContain('opacity-40');
    const html = render(
      <DetectTab
        scenarioId="sc-1"
        minds={minds({
          error: new CognitiveApiError({ code: 'SCHEMA_NOT_READY', status: 503, message: 'cognitive schema not ready' }),
        })}
        onRetryMinds={() => undefined}
      />,
    );
    expect(html).toContain('schema 未就绪');
    expect(html).not.toContain('未启用');
  });

  it('加载态为骨架屏（animate-pulse 占位条），非裸 spinner 文本', () => {
    const html = render(<CognitiveSkeleton rows={3} />);
    expect(html).toContain('animate-pulse');
    expect(html).toContain('data-testid="cog-skeleton"');
    expect(html).toContain('role="status"');
  });

  it('COG-stub：501 + stub=true 经共享组件仍走占位态（禁伪装成故障）', () => {
    const stubErr = new CognitiveApiError({ code: 'STUB', status: 501, message: 'capability not implemented', stub: true });
    const html = render(
      <CognitiveErrorState error={stubErr} titleKey={cognitiveErrorTitleKey(stubErr)} />,
    );
    expect(html).toContain('data-testid="cog-stub-placeholder"');
    expect(html).not.toContain('role="alert"');
    expect(html).toContain('未实现（占位）');
  });

  it('空态组件与故障态组件互斥（不允许同一 fallback）', () => {
    const emptyHtml = render(<CognitiveEmptyState labelKey="scenario.cognition.empty" />);
    const errHtml = render(
      <CognitiveErrorState
        error={new CognitiveApiError({ code: 'NOT_FOUND', status: 404, message: 'scenario not found' })}
        titleKey={cognitiveErrorTitleKey(new CognitiveApiError({ code: 'NOT_FOUND', status: 404, message: 'scenario not found' }))}
      />,
    );
    expect(emptyHtml).toContain('data-testid="cog-empty"');
    expect(errHtml).toContain('场景不存在');
    expect(errHtml).toContain('data-testid="cog-error-state"');
    expect(emptyHtml).not.toContain('cog-error-state');
  });
});

describe('B.6 / D.5 契约', () => {
  it('limit 必传且钳制：缺省 10、上限 50、下限 1（禁无 limit 全量拉取）', () => {
    expect(clampCognitionLimit(undefined)).toBe(COGNITION_DEFAULT_LIMIT);
    expect(clampCognitionLimit(999)).toBe(COGNITION_MAX_LIMIT);
    expect(clampCognitionLimit(0)).toBe(1);
    expect(clampCognitionLimit(-5)).toBe(1);
    expect(clampCognitionLimit(25.7)).toBe(25);
  });
  it('错误码 → 文案键映射单源（traceId/未启用/schema/场景不存在/mind 不属场景互不混用）', () => {
    expect(cognitiveErrorTitleKey(new CognitiveApiError({ code: 'NOT_FOUND', status: 404, message: 'scenario not found' }))).toBe('scenario.cognition.sceneNotFound');
    expect(cognitiveErrorTitleKey(new CognitiveApiError({ code: 'ENGINE_DISABLED', status: 503, message: 'cognitive engine not enabled' }))).toBe('scenario.cognition.notEnabled');
    expect(cognitiveErrorTitleKey(new CognitiveApiError({ code: 'SCHEMA_NOT_READY', status: 503, message: 'cognitive schema not ready' }))).toBe('scenario.cognition.schemaNotReady');
    expect(cognitiveErrorTitleKey(new CognitiveApiError({ code: 'BAD_REQUEST', status: 400, message: 'mind not found on this scenario' }))).toBe('scenario.cognition.mindNotOnScenario');
    expect(cognitiveErrorTitleKey(new CognitiveApiError({ code: 'STUB', status: 501, message: 'capability not implemented', stub: true }))).toBe('scenario.cognition.stub');
    expect(cognitiveErrorTitleKey(new CognitiveApiError({ code: 'NETWORK', status: 0, message: 'boom' }))).toBe('scenario.cognition.error');
  });
});
