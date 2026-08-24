// Test: edit role shows assigned permissions
import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

const consoleMsgs = [];
page.on('console', msg => consoleMsgs.push(`${msg.type()}: ${msg.text()}`));

try {
  // Login
  await page.goto('http://localhost:3000/#/login');
  await page.waitForLoadState('networkidle');
  await page.fill('input[type="text"]', 'admin');
  await page.fill('input[type="password"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);

  // Go to IAM → roles tab
  await page.goto('http://localhost:3000/#/iam');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Click roles tab
  const roleTab = page.locator('button:has-text("角色"), [role="tab"]:has-text("角色"), button:has-text("Roles")');
  if (await roleTab.count() > 0) {
    await roleTab.first().click();
    await page.waitForTimeout(2000);
  }

  // Click first role row to open permission panel
  const roleRow = page.locator('table tbody tr').first();
  if (await roleRow.count() > 0) {
    const roleName = await roleRow.locator('td').first().textContent();
    console.log('Clicking role:', roleName?.trim());
    await roleRow.click();
    await page.waitForTimeout(3000);
  }

  // Check the permission panel
  const panel = page.locator('.fixed.inset-0').last();
  const panelText = await panel.textContent().catch(() => '(no panel)');

  // Check for "已分配" / "assigned" section
  const hasAssigned = panelText.includes('已分配') || panelText.includes('Assigned') || panelText.includes('assigned');
  const hasAvailable = panelText.includes('可用') || panelText.includes('Available') || panelText.includes('available');

  // Count items in assigned vs available
  console.log('Panel has "已分配":', hasAssigned);
  console.log('Panel has "可用":', hasAvailable);
  console.log('Panel text (first 500):', panelText.substring(0, 500));

  // Take screenshot for reference
  await page.screenshot({ path: '/tmp/role-perm-panel.png', fullPage: false });
  console.log('\nScreenshot saved: /tmp/role-perm-panel.png');

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
