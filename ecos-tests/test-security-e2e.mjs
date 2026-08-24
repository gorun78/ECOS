// E2E: Modify security config in UserEditModal → save → verify via API
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

  // Click edit button (index 1) on first user row (wangwu / u_wangwu)
  await page.locator('table tbody tr').first().locator('button').nth(1).click();
  await page.waitForTimeout(1000);

  // Click "安全配置" tab
  await page.locator('.fixed.inset-0').last().locator('button:has-text("安全配置")').click();
  await page.waitForTimeout(2000);

  const modal = page.locator('.fixed.inset-0').last();

  // Read current values
  const clearanceSelect = modal.locator('select').first();
  const workstationInput = modal.locator('input[type="text"]').first();
  const auditSelect = modal.locator('select').nth(1);
  const sandboxCheckbox = modal.locator('input[type="checkbox"]').first();

  const beforeClearance = await clearanceSelect.inputValue();
  const beforeWorkstation = await workstationInput.inputValue();
  const beforeAudit = await auditSelect.inputValue();
  const beforeSandbox = await sandboxCheckbox.isChecked();

  console.log('=== Before ===');
  console.log('Clearance:', beforeClearance);
  console.log('Workstation:', beforeWorkstation);
  console.log('Audit:', beforeAudit);
  console.log('Sandbox:', beforeSandbox);

  // Change values
  await clearanceSelect.selectOption('4');  // L4 绝密
  await workstationInput.fill('WS-PLAYWRIGHT-TEST');
  await auditSelect.selectOption('full');
  await sandboxCheckbox.check();

  // Click save
  await modal.locator('button:has-text("保存安全配置")').click();
  await page.waitForTimeout(2000);

  // Read after save
  const afterClearance = await clearanceSelect.inputValue();
  const afterWorkstation = await workstationInput.inputValue();
  const afterAudit = await auditSelect.inputValue();
  const afterSandbox = await sandboxCheckbox.isChecked();

  console.log('\n=== After ===');
  console.log('Clearance:', afterClearance);
  console.log('Workstation:', afterWorkstation);
  console.log('Audit:', afterAudit);
  console.log('Sandbox:', afterSandbox);

  // Verify via API
  const token = await page.evaluate(() => localStorage.getItem('token'));
  const apiResp = await page.evaluate(async (token) => {
    const r = await fetch('/api/v1/security-profiles/user/u_wangwu', {
      headers: { Authorization: `Bearer ${token}` },
    });
    return r.json();
  }, token);

  console.log('\n=== API Verification ===');
  console.log('API code:', apiResp.code);
  console.log('API data:', JSON.stringify(apiResp.data));

  const allMatch = afterClearance === '4' && afterWorkstation === 'WS-PLAYWRIGHT-TEST' &&
    afterAudit === 'full' && afterSandbox === true &&
    apiResp.data?.clearanceLevel === 4 && apiResp.data?.linkedWorkstation === 'WS-PLAYWRIGHT-TEST' &&
    apiResp.data?.auditMode === 'full' && apiResp.data?.sandboxMandatory === true;

  console.log('\n' + (allMatch ? '✅ PASS — UI → API → DB synced' : '❌ FAIL — Mismatch'));

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
