// E2E: Verify org name in user list + role binding in edit modal
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

  // Bug1: Check if org name shows in user list
  const firstRow = page.locator('table tbody tr').first();
  const rowText = await firstRow.textContent();
  const hasOrgName = rowText.includes('研发部') || rowText.includes('高速信科');
  console.log('=== Bug1: Org name in list ===');
  console.log('First row has org name:', hasOrgName);
  console.log('First row text:', rowText?.substring(0, 150));

  // Bug2: Check role binding in edit modal
  // Click edit button on first user row
  await firstRow.locator('button').nth(1).click();
  await page.waitForTimeout(1000);

  // Click "角色绑定" tab
  const rolesTab = page.locator('.fixed.inset-0').last().locator('button:has-text("角色绑定")');
  await rolesTab.click();
  await page.waitForTimeout(2000);

  const modal = page.locator('.fixed.inset-0').last();
  const modalText = await modal.textContent();

  console.log('\n=== Bug2: Role binding tab ===');
  console.log('Has role search input:', await modal.locator('input[placeholder*="搜索"], input[placeholder*="search"], input[type="text"]').count());

  // Check if any roles are shown
  const roleCheckboxes = await modal.locator('input[type="checkbox"]').count();
  console.log('Role checkboxes:', roleCheckboxes);

  // Check if any role names are visible
  const roleNames = ['系统管理员', '运维操作员', '普通用户', '审计员', '安全管理员'];
  const foundRoles = roleNames.filter(name => modalText.includes(name));
  console.log('Visible role names:', foundRoles);

  // Check if any roles are selected (for U001 which has R001)
  const selectedCount = await modal.locator('input[type="checkbox"]:checked').count();
  console.log('Selected roles:', selectedCount);

  const allPass = hasOrgName && roleCheckboxes > 0 && foundRoles.length > 0;
  console.log('\n' + (allPass ? '✅ PASS' : '❌ FAIL'));

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
