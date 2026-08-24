// Quick test: verify role permission panel shows permissions after fix
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

  // Click roles tab
  const roleTab = page.locator('button:has-text("角色"), [role="tab"]:has-text("角色"), button:has-text("Roles")');
  if (await roleTab.count() > 0) {
    await roleTab.first().click();
    await page.waitForTimeout(2000);
  }

  // Click first role row
  const roleRow = page.locator('table tbody tr').first();
  if (await roleRow.count() > 0) {
    await roleRow.click();
    await page.waitForTimeout(3000);
  }

  // Check permission panel content
  const fixedPanels = await page.locator('.fixed.inset-0').count();
  const panelText = await page.locator('.fixed.inset-0').last().textContent().catch(() => '(no panel)');

  // Check if any permission names appear (like "perm-", "system:", etc.)
  const hasPermissionItems = panelText.includes('perm-') || panelText.includes('system:') || panelText.includes('权限');

  console.log('Fixed panels visible:', fixedPanels);
  console.log('Panel text (first 300):', panelText.substring(0, 300));
  console.log('Has permission items:', hasPermissionItems);
  console.log('Result:', hasPermissionItems ? '✅ PASS — permissions visible' : '❌ FAIL — permissions empty');

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
