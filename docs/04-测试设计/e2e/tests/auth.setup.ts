import { test as setup, expect } from '@playwright/test';
import path from 'path';

const AUTH_FILE = path.join(__dirname, '..', '.auth', 'user.json');

setup('authenticate', async ({ page }) => {
  await page.goto('/');
  
  // Wait for login form
  await page.waitForSelector('input[type="text"], input[placeholder*="用户"], input[name="username"]', { timeout: 10000 });
  
  // Fill credentials
  const usernameInput = page.locator('input[type="text"]').first();
  const passwordInput = page.locator('input[type="password"]').first();
  await usernameInput.fill('admin');
  await passwordInput.fill('admin123');
  
  // Click login button
  await page.click('button[type="submit"], button:has-text("登录"), button:has-text("Login")');
  
  // Wait for navigation to Mission Control
  await page.waitForURL('**/mission_control**', { timeout: 15000 });
  
  // Save authentication state
  await page.context().storageState({ path: AUTH_FILE });
});
