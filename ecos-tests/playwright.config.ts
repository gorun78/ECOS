import { defineConfig, devices } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

// 无第三方 dotenv：仅解析 ecos-tests/.env（KEY=VALUE，# 注释），已存在的环境变量优先
function loadDotEnv(): void {
  const file = path.resolve(__dirname, '.env');
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim().replace(/^["']|["']$/g, '');
    if (process.env[key] === undefined) process.env[key] = value;
  }
}
loadDotEnv();

export const ECOS_API = process.env.ECOS_API ?? 'http://localhost:8080';
export const ECOS_BASE = process.env.ECOS_BASE ?? 'http://localhost:3000';

export default defineConfig({
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['json', { outputFile: 'results.json' }], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: { trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  globalSetup: './tests/global-setup.ts',
  projects: [
    {
      name: 'api',
      testDir: './tests/api',
      use: { baseURL: ECOS_API },
    },
    {
      name: 'app',
      testDir: './tests/e2e',
      use: { ...devices['Desktop Chrome'], baseURL: ECOS_BASE, viewport: { width: 1600, height: 1000 } },
    },
  ],
});
