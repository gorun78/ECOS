import { expect, APIRequestContext, BrowserContext, Page } from '@playwright/test';

export interface ApiResponse {
  code?: number;
  success?: boolean;
  message?: string;
  token?: string;
  accessToken?: string;
  // 响应体形态由后端 ApiResponse 门面决定，各用例显式断言自己关心的字段
  data?: any;
}

export interface ApiResult {
  status: number;
  json: ApiResponse | null;
}

export function creds(): { username: string; password: string } {
  const username = process.env.ECOS_USER;
  const password = process.env.ECOS_PASS;
  if (!username || !password) throw new Error('ECOS_USER / ECOS_PASS 未设置（凭据只走 env，见 .env.example）');
  return { username, password };
}

/** 迁移自三个 .mjs 的登录步骤：token 兼容 data.token / data.accessToken 两种形态 */
export async function loginAsAdmin(api: APIRequestContext): Promise<string> {
  const res = await api.post('/api/v1/auth/login', { data: creds() });
  expect(res.ok(), `POST /api/v1/auth/login 期望 2xx，实得 ${res.status()}`).toBeTruthy();
  const json = (await res.json().catch(() => ({}))) as ApiResponse;
  const token = json?.data?.token || json?.data?.accessToken || '';
  expect(token, '登录响应缺少 token / accessToken').toBeTruthy();
  return token;
}

export function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export async function call(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'DELETE',
  path: string,
  options: { token?: string; data?: unknown } = {}
): Promise<ApiResult> {
  const res = await api.fetch(path, {
    method,
    headers: { ...(options.token ? bearer(options.token) : {}) },
    data: options.data,
  });
  const json = (await res.json().catch(() => null)) as ApiResponse | null;
  return { status: res.status(), json };
}

/** 浏览器端免登录：与历史冒烟一致，经 localStorage 注入 token（键名与前端 auth store 对齐） */
export async function injectAuth(ctx: BrowserContext, token: string): Promise<void> {
  const { username } = creds();
  await ctx.addInitScript(
    ([tk, usr]) => {
      localStorage.setItem('token', tk);
      localStorage.setItem('username', usr);
      localStorage.setItem('roles', JSON.stringify(['ADMIN']));
      localStorage.setItem('ecos_locale', 'zh');
    },
    [token, username]
  );
}

/**
 * 铁律 V4「浏览器 E2E 必查三项」的执行载体：渲染无 ErrorBoundary / console 无 error / network 无意外 4xx·5xx。
 * 每个 e2e 用例末尾必须调用一次 expectHealthyPage()。
 *
 * expectDegraded（2026-10-11 三项前端改造 live 轮）：文档已声明的**按设计降级**端点模式
 * （如认知引擎未承流时 cognition 四端点恒 503，见前端设计方案 §3.12 降级面清单）。
 * 命中模式的 4xx/5xx 从「意外」集合剔除——必须逐 URL 显式声明，禁止整页豁免；
 * trace 中仍可查该响应，页面自身必须渲染如实降级横幅（由用例另行断言）。
 */
export interface PageHealthOptions {
  expectDegraded?: readonly RegExp[];
}

export function watchPageHealth(page: Page, opts: PageHealthOptions = {}): void {
  const errors: string[] = [];
  const badResponses: { url: string; status: number }[] = [];
  page.on('console', (msg) => {
    // 浏览器对 4xx/5xx 自动打印的 "Failed to load resource" 无 URL 信息，
    // 与 badResponses 监听器重复计数 ⇒ 网络面统一走 badResponses（带 URL，可按声明剔除）。
    if (msg.type() === 'error' && !/^Failed to load resource/i.test(msg.text())) {
      errors.push(msg.text());
    }
  });
  page.on('pageerror', (e) => errors.push(`pageerror: ${String(e)}`));
  page.on('response', (res) => {
    if (res.url().includes('/api/') && res.status() >= 400) {
      badResponses.push({ url: res.url(), status: res.status() });
    }
  });
  health.set(page, { errors, badResponses, expectDegraded: opts.expectDegraded ?? [] });
}

const health = new WeakMap<Page, {
  errors: string[];
  badResponses: { url: string; status: number }[];
  expectDegraded: readonly RegExp[];
}>();

export function expectHealthyPage(page: Page): void {
  const seen = health.get(page);
  if (!seen) throw new Error('用例未调用 watchPageHealth(page)');
  const fatal = seen.errors.filter((e) => !/favicon|ResizeObserver loop|DevTools/i.test(e));
  expect(fatal, `console error: ${fatal.slice(0, 3).join(' | ')}`).toHaveLength(0);
  const unexpected = seen.badResponses.filter(
    (r) => !seen.expectDegraded.some((p) => p.test(r.url)),
  );
  expect(unexpected, '出现意外 4xx/5xx').toHaveLength(0);
}

/** 页面文本里出现错误边界 = 渲染失败 */
export async function expectNoErrorBoundary(page: Page): Promise<void> {
  const text = await page.evaluate(() => document.body.innerText || '');
  expect(
    /Something went wrong|Unexpected Application Error|应用发生错误|useTheme must be used within/i.test(text),
    '页面落入 ErrorBoundary'
  ).toBeFalsy();
  expect(text.length, '页面空白（可能卡在 RequireAuth loading）').toBeGreaterThan(0);
}
