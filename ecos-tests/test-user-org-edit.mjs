// E2E: Edit user → change org → save → verify
import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

try {
  // Login
  await page.goto('http://localhost:3000/#/login');
  await page.waitForLoadState('networkidle');
  await page.fill('input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);

  // Go to IAM
  await page.goto('http://localhost:3000/#/iam');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Click edit button (index 1) on first user row
  await page.locator('table tbody tr').first().locator('button').nth(1).click();
  await page.waitForTimeout(1000);

  const modal = page.locator('.fixed.inset-0').last();
  const modalText = await modal.textContent();
  console.log('Modal opened:', modalText.includes('编辑用户'));

  // Check if org select exists on the basic tab
  const orgSelect = modal.locator('select').first();
  const orgSelectCount = await orgSelect.count();
  console.log('Org select exists:', orgSelectCount > 0);

  if (orgSelectCount > 0) {
    // Select the second option (first is "-- 选择组织 --")
    const options = await orgSelect.locator('option').count();
    console.log('Org options count:', options);
    if (options > 1) {
      const secondValue = await orgSelect.locator('option').nth(1).getAttribute('value');
      console.log('Selecting org:', secondValue);
      await orgSelect.selectOption({ index: 1 });
    }
  }

  // Click save
  await modal.locator('button:has-text("保存")').last().click();
  await page.waitForTimeout(2000);

  // Check for success toast or error
  const bodyText = await page.textContent('body');
  const hasSuccess = bodyText.includes('用户更新成功') || bodyText.includes('User updated');
  const hasError = bodyText.includes('用户不存在') || bodyText.includes('密码') || bodyText.includes('失败');
  console.log('Has success toast:', hasSuccess);
  console.log('Has error:', hasError);

  // Verify via API
  const token = await page.evaluate(() => localStorage.getItem('token'));
  const apiResp = await page.evaluate(async (token) => {
    const r = await fetch('/api/v1/system/users/u_wangwu', {
      headers: { Authorization: `Bearer ${token}` },
    });
    return r.json();
  }, token);

  console.log('\nAPI GET user:', apiResp.code === 0 ? '✅' : '❌');
  console.log('  orgId:', apiResp.data?.orgId || '(none)');

  console.log('\n' + (hasSuccess && !hasError ? '✅ PASS' : '❌ FAIL'));

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
