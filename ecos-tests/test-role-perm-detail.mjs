// Detailed test: check if assigned permissions are shown when editing a role
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

  // Go to IAM → roles tab
  await page.goto('http://localhost:3000/#/iam');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Click roles tab
  const roleTab = page.locator('button:has-text("角色"), [role="tab"]:has-text("角色")');
  if (await roleTab.count() > 0) {
    await roleTab.first().click();
    await page.waitForTimeout(2000);
  }

  // Click 系统管理员 row (R001, has 20 permissions)
  const rows = page.locator('table tbody tr');
  const count = await rows.count();
  for (let i = 0; i < count; i++) {
    const txt = await rows.nth(i).textContent();
    if (txt && txt.includes('系统管理员')) {
      await rows.nth(i).click();
      break;
    }
  }
  await page.waitForTimeout(3000);

  // Get the panel HTML structure
  const panel = page.locator('.fixed.inset-0').last();
  const fullText = await panel.textContent();

  // Check for 已分配 and 可用 sections
  const hasAssigned = fullText.includes('已分配');
  const hasAvailable = fullText.includes('可用');

  // Extract counts from parentheses
  const assignedMatch = fullText.match(/已分配.*?(\d+)/);
  const availableMatch = fullText.match(/可用.*?(\d+)/);

  console.log('=== Role Permission Panel Analysis ===');
  console.log('Has "已分配" section:', hasAssigned);
  console.log('Has "可用" section:', hasAvailable);
  console.log('已分配 count:', assignedMatch ? assignedMatch[1] : 'not found');
  console.log('可用 count:', availableMatch ? availableMatch[1] : 'not found');
  console.log('Full text length:', fullText.length);
  console.log('\nFull panel text:');
  console.log(fullText);

  // Also check the HTML structure to understand layout
  const panelHtml = await panel.innerHTML();
  // Look for section headers
  const h3Elements = await panel.locator('h3, h4, [class*="header"], [class*="title"]').allTextContents();
  console.log('\nSection headers:', h3Elements);

  // Count items in each column
  const columns = await panel.locator('[class*="flex-1"], [class*="column"], [class*="col"]').count();
  console.log('Columns found:', columns);

  // Check for empty state messages
  const emptyMsgs = await panel.locator('text=/暂无|空|empty|no data/i').allTextContents();
  console.log('Empty messages:', emptyMsgs);

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
