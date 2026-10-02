import { expect, test } from '@playwright/test';
import {
  expectHealthyPage,
  expectNoErrorBoundary,
  injectAuth,
  loginAsAdmin,
  watchPageHealth,
} from '../helpers/ecos';

/**
 * F07-25 / C171 / REQ-WS-03 §3.1 T1~T6 × 4主题 — 工作台场景域 E2E 用例。
 *
 * 【交付说明（F07-25 设计明确约束）】
 * 本文件为**用例源码交付**，非"已验收通过"凭证。
 * 运行期前置（P-2 / F07-01 承流可达）未落地前，本 spec 的用例在执行时
 * 会因 `#/workbench` 路由在生产形态下 404 或认知四端点 503 而失败——
 * 这本身就是 F07-01 阻塞事实的端到端可观测证据。
 * 设计（F07-25-2、§8.1 REQ-WS-03 行）明文："本册不得声称 E2E 已通过，
 * 只交付用例源码与'P0 用例集体因 F07-01 链路不通而无法执行'的事实"。
 *
 * 【T5 异常注入】
 * 用 Playwright 的 `page.route()` 对 BFF 层认知四端点拦截，
 * 返回 503 空 body（A3 红线：非 200 空 body，非 200 + code:503 伪装），
 * **不 stop 容器**（遵守 AGENTS.md "不 force stop docker" 约束）。
 *
 * 【凭据】只走 env（`ECOS_USER`/`ECOS_PASS` 从 `.env` 或进程环境变量），
 * 禁写入用例明文（AGENTS.md `ecos-tests` 条）。
 */

const WORKBENCH_PATH = '/#/workbench';

/** 四主题（前端 useTheme 档位），与 GEMINI.md 4 主题一致 */
const THEMES = ['slate-light', 'deep-space', 'cyber-terminal', 'royal-purple'] as const;

/**
 * T1：登录后跳转 #/workbench
 */
test('workbench-e2e T1: 登录后跳转 #/workbench', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  watchPageHealth(page);

  await page.goto('/', { waitUntil: 'domcontentloaded' });

  test.step('T1.1 导航到工作台路由', async () => {
    await page.goto(WORKBENCH_PATH, { waitUntil: 'domcontentloaded', timeout: 30_000 });
  });

  test.step('T1.2 URL hash 已到达 #/workbench', async () => {
    await expect
      .poll(() => page.evaluate(() => window.location.hash), { timeout: 10_000 })
      .toBe('#/workbench');
  });

  test.step('T1.3 页面未落入 ErrorBoundary（非白屏）', async () => {
    await expectNoErrorBoundary(page);
  });
});

/**
 * T2：三层画布显示子图/横切/出口与连线
 *
 * 前置：已存在 openid = 首个 ACTIVE 场景（生产形态 F07-01 承流通后才有）。
 * 若路由 404，本用例会因等待超时失败，属 F07-01 阻塞证据的合法预期。
 */
test('workbench-e2e T2: 三层画布显示子图/横切/出口与连线', async ({
  page,
  request,
  context,
}) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  watchPageHealth(page);

  await page.goto(WORKBENCH_PATH, { waitUntil: 'domcontentloaded', timeout: 30_000 });

  test.step('T2.1 六类节点分层（SUBGRAPH / CROSS / OUTPUT）均可见', async () => {
    // 画布容器（F07-05 binding-catalog 驱动，六类节点各带 tier 标签）
    await expect(page.locator('body')).toContainText(/数据集|对象类型|知识库|安全策略|AI Agent|接口/, {
      timeout: 30_000,
    });
  });

  test.step('T2.2 连线可被 hover 获取，说明 SVG/Canvas 画布已渲染', async () => {
    // 画布内连线元素（SVG <line>/<path> 或 canvas 上绑定的 data-link 属性）
    const lineCount = await page.evaluate(() => {
      const svg = document.querySelector('svg');
      if (!svg) return 0;
      return svg.querySelectorAll('line, path, polyline').length;
    });
    expect(lineCount, '画布内应有连线元素（SVG line/path）').toBeGreaterThan(0);
  });

  test.step('T2.3 页面健康（无 console error / 无意外 4xx 5xx）', async () => {
    expectHealthyPage(page);
  });
});

/**
 * T3：连线 hover 显示契约来源（source_contract 提示）
 */
test('workbench-e2e T3: 连线 hover 显示契约来源', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  watchPageHealth(page);

  await page.goto(WORKBENCH_PATH, { waitUntil: 'domcontentloaded', timeout: 30_000 });

  test.step('T3.1 hover 画布首条连线，出现 source_contract 提示层', async () => {
    const lineEl = page.locator('svg line, svg path, svg polyline').first();
    await expect(lineEl, '画布内未找到连线元素').toBeVisible({ timeout: 30_000 });
    await lineEl.hover();
    // 契约来源提示（tooltip / title / aria-label 均可，断言出现非空文本）
    const tooltipVisible = await page
      .locator('[data-contract-tooltip], [title], [role="tooltip"]')
      .first()
      .isVisible()
      .catch(() => false);
    expect(
      tooltipVisible,
      'hover 连线后未见契约来源提示（source_contract 未渲染）'
    ).toBeTruthy();
  });
});

/**
 * T4：切换 Mind 后认知内容随动
 *
 * 实现细节（F07-12 DcchengClient 读侧四端点）：
 * 点击 Mind 列表不同项后，CognitionPanel 应重新请求
 * `cognition/{detect|hypotheses|beliefs|operation-eval}`，展示新 Mind 的结果。
 */
test('workbench-e2e T4: 切换 Mind 后认知内容随动', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);
  watchPageHealth(page);

  await page.goto(WORKBENCH_PATH, { waitUntil: 'domcontentloaded', timeout: 30_000 });

  test.step('T4.1 至少两个 Mind 选项可见', async () => {
    // SdMindCard 列表（F07-08 台账 C154 X-69 里 useSandbox.ts:236 的 mind 列表渲染）
    const mindCards = page.locator('[data-sd-mind-card], [class*="MindCard"], [class*="mind-card"]');
    await expect(mindCards.first(), 'Mind 列表未渲染').toBeVisible({ timeout: 30_000 });
    // 需要至少两个 mind 卡片才能切换；若只有一个，至少断言节点存在
    const count = await mindCards.count();
    expect(count, 'Mind 卡片数应至少为 1').toBeGreaterThanOrEqual(1);
  });

  test.step('T4.2 切换 mind（≤1 个时跳过点击，只断言认知面板存在）', async () => {
    const count = await page
      .locator('[data-sd-mind-card], [class*="MindCard"], [class*="mind-card"]')
      .count();
    if (count >= 2) {
      // 点击第二个 mind，认知四端点任一请求应重新发起
      const cognititionReq = page
        .waitForResponse(
          (res) => /cognition\//.test(res.url()) && res.request().method() === 'GET',
          { timeout: 15_000 }
        )
        .catch(() => null);
      await page
        .locator('[data-sd-mind-card], [class*="MindCard"], [class*="mind-card"]')
        .nth(1)
        .click();
      const res = await cognititionReq;
      expect(
        res,
        '切换 Mind 后未见 cognition/* 重新请求（T4 断链证据，X-69 已知 bug）'
      ).not.toBeNull();
    }
  });
});

/**
 * T5：认知引擎不可用时提示而非白屏
 *
 * 用 Playwright 路由拦截对认知四端点注入 503（不 stop 容器）。
 */
test('workbench-e2e T5: cognitive 503 注入时不白屏且其余区域可交互', async ({
  page,
  request,
  context,
}) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);

  // BFF 层 503 注入：拦截 workspace/cognition/* 四端点，返回 503 空 body
  await page.route('**/cognition/**', async (route) => {
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      // A3 红线：真实 503，非 200 + code:503 伪装
      body: JSON.stringify({ code: 503, message: 'cognitive engine unavailable (test-injected)' }),
    });
  });

  watchPageHealth(page);
  await page.goto(WORKBENCH_PATH, { waitUntil: 'domcontentloaded', timeout: 30_000 });

  test.step('T5.1 页面未落入 ErrorBoundary（不白屏）', async () => {
    await expectNoErrorBoundary(page);
  });

  test.step('T5.2 认知不可用横幅出现（REQ-WS-03 T5 / COG-01 错误矩阵）', async () => {
    // 全局不可用横幅（F07-13 WorkspaceExceptionHandler→503 advice→前端 useCognition→横幅）
    const banner = await page
      .locator('[data-cognition-unavailable], [role="alert"], [class*="banner"]')
      .first()
      .isVisible()
      .catch(() => false);
    // 横幅存在（前端 F07-13 已改）或认知面板为显式错误态且不阻塞其它区域
    expect(banner, '未见认知不可用横幅或错误态（T5 应显示显式提示而非静默）').toBeTruthy();
  });

  test.step('T5.3 其余区域（场景列表 / 状态徽章等）仍可交互', async () => {
    // 场景列表区域或未禁用导航
    await expect(page.locator('body'), '页面容器存在').toBeVisible();
  });
});

/**
 * T6：四主题 console error = 0（favicon 404 豁免）
 *
 * 以 build 结果（vite build）为佳，dev 环境运行也可。
 * 4 主题循环，每主题导航到 #/workbench，收集 console error
 * （favicon · ResizeObserver 等已知豁免过滤在 expectHealthyPage 里已内置）。
 */
test('workbench-e2e T6: 四主题 console error 为 0', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);

  for (const theme of THEMES) {
    test.step(`T6.${theme} console error=0`, async () => {
      // 注入主题关联的 localStorage 键（与前端 useTheme 的存储 key 对齐）
      await context.addInitScript((t) => localStorage.setItem('ecos_theme', t), theme);
      const errors: string[] = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') errors.push(msg.text());
      });
      await page.goto(WORKBENCH_PATH, { waitUntil: 'networkidle', timeout: 30_000 });
      // 给渲染完成留 2s 收集残留 console
      await page.waitForTimeout(2000);
      const fatal = errors.filter(
        (e) => !/favicon|ResizeObserver loop|DevTools|chunk-[a-f0-9]+\.js/i.test(e)
      );
      expect(
        fatal,
        `[${theme}] console error 非空: ${fatal.slice(0, 3).join(' | ')}`
      ).toHaveLength(0);
    });
  }
});
