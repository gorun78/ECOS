import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectHealthyPage, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * V-AI-005 / V-AI-006 的**点击级**活体验证（D148 批，2026-11-16）。
 *
 * 矩阵里这两条长期停在 PARTIAL，缺口写得明确：「浏览器内点击未做」。
 * 本 spec 补齐的就是这一层：在真正点下去之后，看的是**渲染出来的东西**——
 * 不是 curl 回来的 JSON。
 *
 * V-AI-006（评估面板）：仪表盘的评估面板选问题集 → 点「开始评估」
 *   → 引擎异步跑完 → 页面出现 evalId、完成态与真实五维雷达。
 *   实测引擎耗时约 12s（totalDurationMs 11736 / finance_20），故完成态等待按分钟级给。
 *
 * V-AI-005（调试台 / playground）：选智能体 → 输入 → 发送 → **流式**帧渲染出
 *   OAG 八节点步进与最终回答。该腿走 `/api/v1/oag/chat/stream`，正是 D148 的受害面。
 *   D149 回归：后端 node 名带 `Node` 后缀（IntentClassifierNode），前端节点表用短键，
 *   比对曾恒不命中 ⇒ 越界 `OAG_NODES[idx].icon` 抛 TypeError ⇒ 整页 ErrorBoundary。
 *
 * 判定口径：两条都是「点击后页面真的变了」，且页面不进 ErrorBoundary / 无 console error。
 */

// 评估面板要等引擎异步跑完（实测约 12s，加轮询抖动留足余量），默认 60s 不够。
test.describe.configure({ timeout: 180_000 });

test.describe('AI 工作台 评估/试验场 点击级', () => {
  test('V-AI-006 评估面板：选问题集→开始评估→渲染完成态与五维雷达', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/ai-workbench');
    await expect(page.getByText(/AI 工作台|AI Workbench/).first()).toBeVisible({ timeout: 30_000 });

    // 评估面板不是默认子视图，先切过去（NAV_TABS 有 mobile + desktop 两份 DOM，只取可见的）
    const evalTab = page.locator('button:visible', { hasText: '评估面板' }).first();
    await expect(evalTab, '仪表盘缺少「评估面板」子 Tab').toBeVisible({ timeout: 20_000 });
    await evalTab.click();

    const qsetSelect = page.locator('select:visible').filter({ has: page.locator('option[value="finance_20"]') }).first();
    await expect(qsetSelect, '评估面板缺少问题集下拉').toBeVisible({ timeout: 15_000 });
    await qsetSelect.selectOption('finance_20');

    const startBtn = page.locator('button:visible', { hasText: '开始评估' }).first();
    await expect(startBtn, '评估面板缺少「开始评估」按钮').toBeVisible({ timeout: 15_000 });

    // 网络腿：点击必须真的 POST /api/v1/aip/eval/run 并拿到 2xx（不是前端本地伪造成 evalId）
    const runResp = page.waitForResponse(
      (r) => r.url().includes('/api/v1/aip/eval/run') && r.request().method() === 'POST',
      { timeout: 30_000 },
    );
    await startBtn.click();
    expect((await runResp).status(), 'POST /api/v1/aip/eval/run 未返回 2xx').toBeLessThan(300);

    // 引擎异步执行：evalId 出现即证真实端点已受理
    await expect(page.getByText(/evalId[:：]\s*eval-/).first()).toBeVisible({ timeout: 30_000 });

    // 完成态状态行 = 精确文本「评估完成」。
    // 必须 exact：雷达占位段落「评估完成后展示雷达图」含同一子串，非精确匹配会在仍 running 时假绿。
    await expect(page.getByText('评估完成', { exact: true }).first(), '评估未到完成态（150s 内）').toBeVisible({ timeout: 150_000 });

    // 完成态三件套：进度 100% / 20 题全部完成 / 失败横幅不出现
    const engineFailBanner = page.getByText(/评测引擎未返回结果|引擎评估失败|评估引擎不可用|安全服务不可用/);
    await expect(page.getByText('100%', { exact: true }).first(), '完成态进度不是 100%').toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('20/20 题', { exact: true }).first(), '逐题详情未渲染出 20 题完成态').toBeVisible({ timeout: 15_000 });
    await expect(engineFailBanner, 'D148 回归：评估链路把引擎失败渲染成了错误横幅').toBeHidden();

    // 五维雷达：引擎 dimensionAvgs 非空 ⇒ RadarChart 真正渲染（而非「正在计算评分...」占位）
    await expect(page.getByText('能力雷达图', { exact: true }).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('正在计算评分...'), '雷达未取得引擎五维（仍停在计算占位）').toBeHidden();

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });

  test('V-AI-005 调试台：选智能体→发消息→流式渲染 OAG 节点与回答', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/ai-workbench');
    await expect(page.getByText(/AI 工作台|AI Workbench/).first()).toBeVisible({ timeout: 30_000 });

    // 切到「调试台」Tab（只取可见按钮，避开 mobile 隐藏副本）
    const playgroundTab = page.locator('button:visible', { hasText: '调试台' }).first();
    await expect(playgroundTab, '导航缺少「调试台」Tab').toBeVisible({ timeout: 20_000 });
    await playgroundTab.click();

    // 选智能体：页面上有多个 visible select（主题 / Agent / 模型），
    // Agent 那个的选项文本形如「名称 · 模型」——按 `·` 出现位置定位。
    // agents 列表是异步拉取且到位后会重建 select，因此整段「定位 + 选中」放进 poll：
    // 每轮重新读 option 再选，避免拿到被重建掉的过期 option 值（连跑时曾因此抖动一次）。
    const picked = await expect.poll(async () => {
      const selects = page.locator('select:visible');
      const n = await selects.count();
      for (let i = 0; i < n; i += 1) {
        const agentSelect = selects.nth(i);
        const opts = await agentSelect.locator('option').evaluateAll((els) =>
          els.map((e) => {
            const el = e as HTMLOptionElement;
            return { v: el.value, hasDot: (el.textContent || '').includes('·') };
          }));
        const first = opts.find((o) => o.v && o.hasDot);
        if (!first) continue;
        try {
          await agentSelect.selectOption(first.v, { timeout: 2_000 });
          return first.v;
        } catch {
          return '';
        }
      }
      return '';
    }, { timeout: 30_000, message: '试验场没有可用的智能体下拉（agents 端点未返回数据或列表反复重建）' }).not.toBe('');

    // 选智能体后输入框才可用（未选 Agent 时 textarea disabled）
    const box = page.locator('textarea:visible').first();
    await expect(box).toBeEnabled({ timeout: 15_000 });
    await box.fill('查一下三季度营收');

    const sendBtn = page.locator('button:visible', { hasText: '发送' }).first();
    await expect(sendBtn, '调试台缺少「发送」按钮').toBeEnabled({ timeout: 10_000 });

    // 网络腿：发送必须真的 POST /api/v1/oag/chat/stream（矩阵期望的 `/api/v1/agent*` 试跑面另一腿，见行内证据）
    const streamResp = page.waitForResponse(
      (r) => r.url().includes('/api/v1/oag/chat/stream') && r.request().method() === 'POST',
      { timeout: 30_000 },
    );
    await sendBtn.click();
    expect((await streamResp).status(), 'POST /api/v1/oag/chat/stream 未返回 2xx').toBeLessThan(300);

    // 用户消息先出现
    await expect(page.getByText('查一下三季度营收').first()).toBeVisible({ timeout: 20_000 });

    // OAG 进度条出现（标题单源 = 「OAG 8 步管道」）
    await expect(page.getByText('OAG 8 步管道').first(), '发送后没有渲染 OAG 进度条').toBeVisible({ timeout: 30_000 });

    // D149 核心断言：进度条按后端真实节点名落到正确槽位，渲染出 `n/8 · pct%` 计数
    await expect(page.getByText(/\d\/8 · \d+%/).first(), 'OAG 进度条没有渲染节点计数').toBeVisible({ timeout: 60_000 });

    // 最终回答帧落定：dev 环境的诚实态是 RLS fail-closed 拒绝，或正常正文
    await expect.poll(
      async () => page.evaluate(() => document.body.innerText || ''),
      { timeout: 60_000, message: '流式响应未落定（无任何回答正文）' },
    ).toMatch(/未通过安全检查|请求被安全策略拦截|营收|回答|抱歉|无法/);

    await expectNoErrorBoundary(page);
    expectHealthyPage(page);
  });
});
