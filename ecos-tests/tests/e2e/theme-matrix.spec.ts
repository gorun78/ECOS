import { expect, test, type Page } from '@playwright/test';
import { expectNoErrorBoundary, injectAuth, loginAsAdmin, watchPageHealth } from '../helpers/ecos';

/**
 * 详设-08 §2.4 / W210（C192）主题基线矩阵 — 冻结用例名 `fourThemesSixTabsNoRawColor`。
 *
 * 【口径】四主题（slate-light / deep-space / cyber-terminal / royal-purple，
 * components/ThemeContext.tsx，localStorage 键 `ecos_theme`，切换实证为
 * html[data-theme] 根属性）× 六个已落位稳定界面。
 * P01~P13 界面落位随 **R-49**（登记不排期），矩阵先取既有结构页做基线：
 *   gateway-diagnostics / alerts-center / system-config / dict / iam / tenants
 * （main.tsx hash 路由实测在位；W190 导航单源属主并行改造中——本用例直接 hash
 *   goto，不经导航点击，避免与并行批次耦合。）
 *
 * 【断言】
 *   1. 主题切换生效：documentElement[data-theme] === 目标主题；
 *   2. 结构容器（nav/aside/header）零裸字面色（§七/铁律 #6；Tailwind 色板字面类
 *      或内联十六进制色），W196 清零（任务 #126，R-55②）的界面侧兜底闸。
 *
 * 【凭据】只走 env（ECOS_USER/ECOS_PASS 经 helpers/ecos.creds()），禁明文写入用例。
 * 【执行状态】写了不要求跑绿（任务口径）；P-3 全链跑归终验（任务 #122）。
 */

const THEMES = ['slate-light', 'deep-space', 'cyber-terminal', 'royal-purple'] as const;

const SIX_TABS = [
  { hash: '#/gateway-diagnostics', title: { zh: '网关诊断', en: 'Gateway Diagnostics' } },
  { hash: '#/alerts-center', title: { zh: '告警', en: 'Alerts' } },
  { hash: '#/system-config', title: { zh: '系统配置', en: 'System Config' } },
  { hash: '#/dict', title: { zh: '字典', en: 'Dict' } },
  { hash: '#/iam', title: { zh: '权限', en: 'IAM' } },
  { hash: '#/tenants', title: { zh: '租户', en: 'Tenant' } },
];

function zhOrEn(a: { zh: string; en: string }): RegExp {
  return new RegExp(`${a.zh}|${a.en}`, 'i');
}

/** 切换主题：写 localStorage 后 reload（ThemeContext 于 useState 初值读取）。 */
async function setTheme(page: Page, theme: string): Promise<void> {
  await page.evaluate((t) => localStorage.setItem('ecos_theme', t), theme);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.getAttribute('data-theme') ?? ''), {
      message: `data-theme 未切换为 ${theme}`,
    })
    .toBe(theme);
}

/** 结构性裸色抽样：色板字面类（含 hover:/focus: 变体前缀）或内联十六进制色。 */
async function collectRawColorOffenders(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const paletteRe =
      /(?:bg|text|border|ring|from|to|via|fill|stroke|shadow|divide|outline)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}/;
    const hexRe = /#[0-9a-fA-F]{3,8}/;
    const offenders: string[] = [];
    for (const el of Array.from(document.querySelectorAll('nav, aside, header'))) {
      const cls = el.getAttribute('class') || '';
      for (const token of cls.split(/\s+/)) {
        if (paletteRe.test(token)) offenders.push(`${el.tagName}.${token}`);
      }
      const style = el.getAttribute('style') || '';
      if (hexRe.test(style)) offenders.push(`${el.tagName}[style*="#"]`);
    }
    return offenders;
  });
}

test.describe('W210/C192 主题矩阵（R-55②/R-49 口径）', () => {
  test('fourThemesSixTabsNoRawColor', async ({ page, context, request }) => {
    const token = await loginAsAdmin(request);
    await injectAuth(context, token);
    watchPageHealth(page);

    for (const theme of THEMES) {
      await page.goto(`/${SIX_TABS[0].hash}`, { waitUntil: 'domcontentloaded' });
      await setTheme(page, theme);
      for (const tab of SIX_TABS) {
        await page.goto(`/${tab.hash}`, { waitUntil: 'domcontentloaded' });
        await expect(
          page.getByRole('heading', { name: zhOrEn(tab.title) }).or(page.getByText(zhOrEn(tab.title))).first(),
          `标题缺失：${tab.hash} @ ${theme}`,
        ).toBeVisible({ timeout: 15_000 });
        await expect
          .poll(() => page.evaluate(() => document.documentElement.getAttribute('data-theme') ?? ''))
          .toBe(theme);
        const offenders = await collectRawColorOffenders(page);
        expect(offenders, `结构容器裸字面色 @ ${theme} / ${tab.hash}（§七）`).toEqual([]);
      }
    }
    await expectNoErrorBoundary(page);
  });
});
