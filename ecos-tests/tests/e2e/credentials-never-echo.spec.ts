import { expect, test } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详设-08 §六-4 / W205（C187，**M0 待复核**）— 冻结用例名
 * `secretFieldsRenderConfiguredBadge`（§九"凭据不回显"行）。
 *
 * 【口径】凭据永不回显：密钥/口令类字段界面只呈现"已配置/未配置"状态徽标（或
 * 等价的留空占位），**不得**把服务端存量密文/明文回填进输入框。W205 属主逐点
 * 复核（禁据推断改码）；本用例为界面侧兜底闸。
 *
 * 【实测锚点（2026-10-10 勘察）】`/#/data-workbench` 连接管理编辑面
 * （pages/data-workbench/tabs/connections/EditConnectionModal.tsx）：
 *   - 口令字段 type=password、useState('') 初值空、placeholder="******"，
 *     保存时空串保留服务端原值 ⇒ 现状满足"不回显"，但尚无 §六-4 目标态
 *     "已配置/未配置"徽标 —— 徽标断言与占位断言二者取其一即视为通过，
 *     W205 落徽标后仍成立。
 *   - 复核清单其余点（/system-config 密钥区、AI 供应商 Key 面）逐点属主 B 批。
 *
 * 【凭据】ECOS_USER/ECOS_PASS 只走 env（.env → global-setup 校验），禁明文入用例。
 */

test.describe('W205 凭据永不回显（§六-4）', () => {
  test('secretFieldsRenderConfiguredBadge', async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    await page.goto('/#/data-workbench', { waitUntil: 'domcontentloaded' });

    // 打开任一既有连接的编辑面（按钮命名族：编辑/Edit/✎；无连接时本用例跳过面复核）。
    const editBtn = page
      .getByRole('button', { name: /编辑|Edit|✎|pencil/i })
      .or(page.getByTitle(/编辑|Edit/i))
      .first();
    await expect(editBtn, '未找到连接编辑入口（页面结构变更 ⇒ 复核清单同步 W205 属主）').toBeVisible({
      timeout: 20_000,
    });
    await editBtn.click();

    // 定位口令字段：优先 type=password；再按标签兜底。
    const secretInput = page.locator('input[type="password"]').first();
    await expect(secretInput, '编辑面无口令字段（W205 逐点复核清单需登记新面）').toBeVisible({ timeout: 10_000 });

    // 核心红线：字段值必须为空（服务端密文/明文永不回填）。
    await expect(secretInput, '凭据回显红线：口令字段值必须为空（§六-4）').toHaveValue('');

    // 目标态二选一：留空占位（****** / 留空不修改）或"已配置/未配置"徽标。
    const placeholder = (await secretInput.getAttribute('placeholder')) ?? '';
    const badgeVisible = await page
      .getByText(/已配置|未配置|Configured|Not configured/i)
      .first()
      .isVisible()
      .catch(() => false);
    const hintOk =
      /^\*+$/.test(placeholder) ||
      /留空|empty|unchanged|不修改/i.test(placeholder) ||
      badgeVisible;
    expect(
      hintOk,
      `口令字段既无"已配置/未配置"徽标也无留空占位（placeholder="${placeholder}"）——W205 目标态未达`,
    ).toBeTruthy();

    // 邻近标签应明示"留空不修改"语义（zh/en 双匹配），防诱导性覆盖输入。
    await expect(
      page.getByText(/留空不修改|leave empty/i).or(page.locator('label', { hasText: /密码|Password/i })).first(),
      '口令字段标签缺失',
    ).toBeVisible();

    await expectNoErrorBoundary(page);
  });

  test('全页快照无疑似凭据明文串', async ({ page, context, request }) => {
    // 面兜底：DOM 全文不得出现 ECOS_PASS 值本身（若 env 未设则跳过断言主体）。
    const pass = process.env.ECOS_PASS;
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);
    await page.goto('/#/system-config', { waitUntil: 'domcontentloaded' });
    await expectNoErrorBoundary(page);
    const body = await page.evaluate(() => document.body.innerText || '');
    if (pass && pass.length >= 6) {
      expect(body, '页面文本出现登录口令明文（§六-4 永不回显）').not.toContain(pass);
    }
    // 常见伪装回显：value 属性中的密文 base64/hex 长串也不允许出现在口令输入框。
    const leaked = await page.evaluate(() =>
      Array.from(document.querySelectorAll('input'))
        .filter((i) => i.type === 'password' && i.value.length > 0)
        .map((i) => i.name || i.id || 'anonymous'),
    );
    expect(leaked, '存在带值口令输入框（疑似回显）').toEqual([]);
  });
});
