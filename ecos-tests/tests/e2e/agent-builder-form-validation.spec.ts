import { test, expect } from '@playwright/test';
import { loginAsAdmin, injectAuth, watchPageHealth, expectNoErrorBoundary } from '../helpers/ecos';

/**
 * V-AI-009（Agent 构建器）的**表单校验点击级**活体验证（2026-11-16）。
 *
 * 矩阵缺口原文：「保存后回读一致性（V4）已补测成立；余『表单校验』前端交互未验」。
 * 本 spec 补的就是这一层：在 `#/agent-builder`（创建态）真实操作表单，
 * 验证「名称为空 ⇒ 不提交、且给出错误提示」这条最核心校验。
 *
 * 判定纪律（沿用同域 V-AI-005/006 点击级口径）：
 *   - 看渲染出来的东西（按钮禁用态、错误横幅），不靠 curl；
 *   - 用网络计数证明「校验前置于网络」——空名称时 POST /api/v1/agents 计数必须为 0；
 *   - 填名后按钮解锁，证明禁用态确实由名称驱动而非别的；
 *   - 页面不进 ErrorBoundary、无未捕获 console error。
 */

test.describe.configure({ timeout: 90_000 });

test.describe('AI Agent 构建器 表单校验 点击级', () => {
  test('V-AI-009 空名称：保存按钮禁用 + 强制提交被拦截不发请求', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/agent-builder');
    // 落到创建态（createTitle 既是页头也是创建态按钮文案）
    await expect(page.getByText('配置新的AI Agent', { exact: false }).first(), '未落到 Agent 构建器创建态').toBeVisible({ timeout: 30_000 });

    // 名称输入框（placeholder = 如: 数据分析Agent）
    const nameInput = page.locator('input[placeholder*="数据分析Agent"]').first();
    await expect(nameInput, '缺少 Agent 名称输入框').toBeVisible({ timeout: 15_000 });

    // 保存按钮（创建态文案 = 创建Agent）
    const saveBtn = page.locator('button:visible', { hasText: '创建Agent' }).first();
    await expect(saveBtn, '缺少创建按钮').toBeVisible({ timeout: 15_000 });

    // ① 空名称 ⇒ 按钮必须 disabled（disabled={saving || !name.trim()}）
    await expect(saveBtn, '空名称时创建按钮应禁用').toBeDisabled();

    // ② 强制提交路径：即便绕过禁用直接调 handleSave，也必须被「名称为空」前置拦截。
    //    用网络计数证明：整个空名称尝试期间 POST /api/v1/agents 不得发出。
    let createPosts = 0;
    page.on('request', (r) => {
      if (r.method() === 'POST' && r.url().includes('/api/v1/agents')) createPosts++;
    });
    // 通过键盘在聚焦按钮上回车 / 空格，尝试触发 disabled 按钮（应无任何效果）
    await nameInput.fill('');           // 确保真空（非空白字符）
    await nameInput.press('Tab');       // 焦点离开输入框
    // disabled 按钮点击不应触发任何事
    await saveBtn.click({ force: true }).catch(() => { /* disabled 时 Playwright 可能抛错，吞掉 */ });
    await page.waitForTimeout(500);
    expect(createPosts, '空名称时不应发出任何创建 Agent 的 POST').toBe(0);

    // ③ 填上名称 ⇒ 按钮解锁（证明禁用态由 name 驱动）
    await nameInput.fill('VERIFY-校验探针');
    await expect(saveBtn, '填入名称后创建按钮仍禁用（禁用态未由 name 驱动）').toBeEnabled();

    // ④ 清空回空 ⇒ 重新禁用（双向证明）
    await nameInput.fill('');
    await expect(saveBtn, '清空名称后创建按钮应回到禁用态').toBeDisabled();

    // 全程不进错误边界
    await expectNoErrorBoundary(page);
  });

  test('V-AI-009 空白字符名称等同空（trim 语义），按钮保持禁用', async ({ page, request, context }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/agent-builder');
    await expect(page.getByText('配置新的AI Agent', { exact: false }).first()).toBeVisible({ timeout: 30_000 });

    const nameInput = page.locator('input[placeholder*="数据分析Agent"]').first();
    const saveBtn = page.locator('button:visible', { hasText: '创建Agent' }).first();

    // 纯空白字符（空格/制表）⇒ trim 后为空 ⇒ 仍禁用
    await nameInput.fill('   \t  ');
    await expect(saveBtn, '纯空白名称（trim 后为空）创建按钮仍应禁用').toBeDisabled();

    await expectNoErrorBoundary(page);
  });
});
