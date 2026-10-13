// @vitest-environment node
/**
 * sixStates.matrix.guard.spec.ts — 三项前端改造面 六态矩阵结构守卫
 * （《前端设计方案-知识库认知资产-AI认知工作台去留-场景工作台重构-2026-10-11.md》§3.9：
 * 「每个 L1/L2/L3 面都要过六态（空/加载/错误/边界/权限/离线）」；口径同详设-08 W212 静态源守卫，
 * 与 sevenStates.matrix.spec.tsx（design-05 B.5 运行态渲染矩阵）互补，不重复其覆盖面）。
 *
 * <p>本仓九个改造面（2026-10-12 复盘 S3 扩面：原 4 面 + 候选评审/进度轨/逻辑投影/发布门槛/高级调试）
 * 把状态机做成组件内判别联合（phase/status/kind），renderToStaticMarkup
 * 无法驱动 effect 切态，行为面由 live E2E（cognition-surfaces-live.spec.ts 空态/降级横幅/真数据）
 * 覆盖；本守卫冻结**结构契约**：每个面必须存在全部承诺的状态分支、每分支必须经 i18n 文案键
 * 与主题 token 呈现，且禁止出现假数据兜底字面量。删除任一状态分支 = 本门禁红。</p>
 *
 * 六态映射（判别联合逐面对号）：
 *   空 empty / 加载 loading / 错误 error / 边界 not_found(404) / 权限 denied(401/403) /
 *   离线·降级 offline|engine(网络失败与 503 如实横幅，禁本地伪造)。
 */
import { readFileSync } from 'node:fs';
import { join, sep } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = join(process.cwd(), 'src');
const read = (rel: string): string => readFileSync(join(SRC, rel), 'utf8').split(sep).join('/');

/** 去注释后的源码（守卫只看实质代码，Javadoc 里的口径说明不计违规） */
function codeOf(src: string): string {
  return src
    .split('\n')
    .filter((l) => {
      const t = l.trim();
      return !t.startsWith('//') && !t.startsWith('*') && !t.startsWith('/*');
    })
    .join('\n');
}

interface SurfaceContract {
  file: string;
  /** 状态判别联合必须包含的字面量（union 行） */
  unionMembers: string[];
  /** 每个状态必须有渲染分支（phase === 'x' 或 kind === 'x' 或 status === 'x'） */
  branches: string[];
  /** 渲染分支必须引用的 i18n 键（至少命中其一，证明无硬编码中文态文案） */
  i18nKeys: string[];
}

const SURFACES: SurfaceContract[] = [
  {
    // L2 蓝图中心面：7 态判别联合（六态 + engine 降级独立态，§6.3 状态机权威）
    file: 'pages/workbench/[scenarioId]/blueprint/BlueprintPanel.tsx',
    unionMembers: ["'loading'", "'ready'", "'empty'", "'not_found'", "'denied'", "'engine'", "'error'"],
    branches: ['phase === \'loading\'', 'phase === \'empty\'', 'phase === \'not_found\'', 'phase === \'denied\''],
    i18nKeys: ['workspace.bp.loading', 'workspace.bp.empty', 'workspace.bp.retry'],
  },
  {
    // L2 能力盘（§3.6）：5 态（503 → engine 横幅 + traceId，禁快照伪造）
    file: 'pages/workbench/[scenarioId]/blueprint/CapabilityPanel.tsx',
    unionMembers: ["'loading'", "'ready'", "'empty'", "'engine'", "'error'"],
    branches: ['phase === \'loading\'', 'phase === \'empty\'', 'phase === \'engine\'', 'phase === \'error\''],
    i18nKeys: ['workspace.cap.engine_unavailable'],
  },
  {
    // L2 向导步骤2 上下文构建（§3.4）：kind 判别联合 + 503/危险双色分支 + 权限键
    file: 'pages/workbench/[scenarioId]/blueprint/ContextBuildPanel.tsx',
    unionMembers: ["'idle'", "'submitting'", "'result'", "'error'"],
    branches: ['phase.kind === \'error\'', "phase.status === 503"],
    i18nKeys: ['workspace.ctx.err.denied', 'workspace.ctx.submitting'],
  },
  {
    // 议题1 知识台认知资产只读面：catalog/list 双通道各态 + engineDown 与真错误可分 + traceId 呈现
    file: 'pages/knowledge/pages/CognitiveAssetPage.tsx',
    unionMembers: ["'loading'", "'ok'", "'error'"],
    branches: ['catalog.status === \'error\'', 'catalog.status === \'ok\'', 'listStatus === \'loading\'', 'listStatus === \'error\''],
    i18nKeys: [
      'knowledge.cognitiveAsset.scope_note',
      'knowledge.cognitiveAsset.engine_unavailable',
      'knowledge.cognitiveAsset.catalog_failed',
      'knowledge.cognitiveAsset.list_failed',
      'knowledge.cognitiveAsset.empty',
    ],
  },
  // ── 2026-10-12 复盘收口扩面（复盘报告 S3：原守卫仅冻结 4 面，承诺为"每个改造面"）──
  {
    // 议题1 §3.5 候选评审面板：resolve/action 双判别联合 + 引擎降级与冲突态可分
    file: 'pages/knowledge/components/CandidateReviewPanel.tsx',
    unionMembers: ["'idle'", "'loading'", "'ok'", "'error'", "'busy'", "'proposal'", "'transition'", "'conflict'", "'failed'"],
    branches: [
      'resolve.kind === \'loading\'',
      'resolve.kind === \'error\'',
      'action.kind === \'conflict\'',
      'action.kind === \'failed\'',
    ],
    i18nKeys: [
      'knowledge.candidate.loading',
      'knowledge.candidate.err_engine',
      'knowledge.candidate.err_conflict',
      'knowledge.candidate.badge_uncalibrated',
      'knowledge.candidate.states_not_ready',
    ],
  },
  {
    // L2 状态机进度轨（§3.3）：纯展示面无自有 fetch（态由父层注入），契约 = 8 tone 全覆盖
    // + 当前/可达/异常三分支 + 空态与 traceId 经 i18n；不可达灰化必须由后端 allowedTransitions 驱动
    file: 'pages/workbench/[scenarioId]/blueprint/BlueprintStatusTrack.tsx',
    unionMembers: ["'initial'", "'progress'", "'ready'", "'approved'", "'live'", "'terminal'", "'exception'", 'unknown:'],
    branches: ['allowed.has(step)', 'isCurrent', 'exception && status'],
    i18nKeys: ['workspace.bp.current_state', 'workspace.bp.empty_field', 'workspace.bp.traceId'],
  },
  {
    // L2 逻辑模型只读投影（§6.2）：空值显式「—」不合成 + 缺口/测试等六子段空分支 + 审批摘要 traceId
    file: 'pages/workbench/[scenarioId]/blueprint/BlueprintDetail.tsx',
    unionMembers: [],
    branches: ['Object.keys(scope).length === 0', 'gaps.length === 0', 'tests.length === 0', 'deps.length === 0'],
    i18nKeys: ['workspace.bp.empty_field', 'workspace.bp.goal_statement', 'workspace.bp.context_digest', 'workspace.bp.traceId'],
  },
  {
    // L2 发布前四门槛（§6.6）：loading/error/passed/failed 分支齐 + 门槛空态经 i18n + 禁伪造 passed
    file: 'pages/workbench/[scenarioId]/blueprint/BlueprintValidatePanel.tsx',
    unionMembers: [],
    branches: ['loading ? t(', 'error &&', 'result.passed', '!result.passed && result.reasons.length > 0'],
    i18nKeys: ['workspace.bp.validate', 'workspace.bp.validating', 'workspace.bp.error', 'workspace.bp.gate.empty'],
  },
  {
    // L2 高级调试折叠区（§2.3 三分下沉承接面）：折叠双分支 + CRP 容器 i18n
    file: 'pages/workbench/[scenarioId]/advanced/AdvancedDebugPanel.tsx',
    unionMembers: [],
    branches: ['expanded ?'],
    i18nKeys: ['workspace.adv.title', 'workspace.adv.cards', 'workspace.adv.legacy_link'],
  },
];

const FAKE_FALLBACK = /\b(?:mockData|fakeData|fallbackRows|placeholderRows|DEFAULT_ASSETS)\b|生成演示数据/;
const RAW_ZH_STATE = /["'`][^"'`\n]*(加载中|暂无|不可用|无权限|未找到)[^"'`\n]*["'`]/;

describe('改造面六态矩阵结构守卫（详设 §3.9 / W212 口径）', () => {
  for (const s of SURFACES) {
    describe(s.file, () => {
      const src = read(s.file);
      const code = codeOf(src);

      it('状态判别联合完整（六态可映射）', () => {
        for (const m of s.unionMembers) {
          expect(code, `联合类型缺少状态 '${m}'`).toContain(m);
        }
      });

      it('每个承诺状态有渲染分支', () => {
        for (const b of s.branches) {
          expect(code, `缺少渲染分支：${b}`).toContain(b);
        }
      });

      it('状态文案经 i18n 键（§六），无硬编码中文态文案', () => {
        for (const k of s.i18nKeys) {
          expect(code, `状态分支未引用 i18n 键 ${k}`).toContain(k);
        }
        expect(FAKE_FALLBACK.test(code), '出现假数据兜底字面量（P0 无假真理）').toBe(false);
        expect(RAW_ZH_STATE.test(code), '出现硬编码中文状态文案').toBe(false);
      });
    });
  }

  it('i18n 键 zh↔en 平权（四改造面全部键在双语存在）', () => {
    const keys = SURFACES.flatMap((s) => s.i18nKeys);
    const zh = read('locales/workspace/zh-CN.json') + read('locales/knowledge/zh-CN.json');
    const en = read('locales/workspace/en.json') + read('locales/knowledge/en.json');
    for (const k of new Set(keys)) {
      expect(zh, `zh 缺键 ${k}`).toContain(k);
      expect(en, `en 缺键 ${k}`).toContain(k);
    }
  });
});
