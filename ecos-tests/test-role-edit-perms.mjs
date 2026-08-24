// Test: edit role modal shows assigned permissions — use a role that HAS permissions
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

  // Find a role that has permissions — click row first to trigger perm load, then click edit
  // Let's click the row for "系统管理员" which has 20 permissions
  const rows = page.locator('table tbody tr');
  const rowCount = await rows.count();
  let clickedRow = false;
  for (let i = 0; i < rowCount; i++) {
    const txt = await rows.nth(i).textContent();
    if (txt && txt.includes('系统管理员')) {
      // Click the edit button (first button in the last td)
      const editBtn = rows.nth(i).locator('button').first();
      await editBtn.click();
      clickedRow = true;
      console.log('Clicked edit on: 系统管理员');
      break;
    }
  }
  
  if (!clickedRow) {
    // Fallback: try "审计员" (R007, has 5 permissions)
    for (let i = 0; i < rowCount; i++) {
      const txt = await rows.nth(i).textContent();
      if (txt && txt.includes('审计员')) {
        const editBtn = rows.nth(i).locator('button').first();
        await editBtn.click();
        clickedRow = true;
        console.log('Clicked edit on: 审计员');
        break;
      }
    }
  }

  await page.waitForTimeout(4000); // Wait for permissions to load

  // Check the edit modal
  const modal = page.locator('.fixed.inset-0').last();
  const modalText = await modal.textContent();

  console.log('=== Edit Role Modal ===');
  console.log('Has "编辑角色":', modalText.includes('编辑角色'));
  console.log('Has "已分配权限":', modalText.includes('已分配权限'));
  
  const assignedMatch = modalText.match(/已分配权限\s*\((\d+)\)/);
  console.log('已分配权限 count:', assignedMatch ? assignedMatch[1] : 'not found');
  
  console.log('Has "管理权限":', modalText.includes('管理权限'));
  
  const permItems = modalText.match(/[a-z\-]+:[A-Z\*]+/g);
  console.log('Permission items found:', permItems ? permItems.length : 0);
  if (permItems) {
    console.log('First 5 items:', permItems.slice(0, 5));
  }

  // Result
  const count = assignedMatch ? parseInt(assignedMatch[1]) : 0;
  console.log('\n' + (count > 0 ? '✅ PASS' : '❌ FAIL') + ` — 已分配权限: ${count}`);

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
