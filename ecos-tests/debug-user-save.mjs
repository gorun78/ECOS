// Debug: capture the exact error message when saving user edit
import { chromium } from 'playwright';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();

const consoleErrors = [];
page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

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

  // Click edit button on first user row
  await page.locator('table tbody tr').first().locator('button').nth(1).click();
  await page.waitForTimeout(1000);

  const modal = page.locator('.fixed.inset-0').last();

  // Select org (second option)
  await modal.locator('select').first().selectOption({ index: 1 });
  await page.waitForTimeout(500);

  // Click save
  await modal.locator('button:has-text("保存")').last().click();
  await page.waitForTimeout(3000);

  // Get all text in the modal
  const modalText = await modal.textContent().catch(() => '(modal closed)');
  console.log('Modal text after save:', modalText.substring(0, 500));

  // Check for any visible error/toast on the page
  const allText = await page.textContent('body');
  // Find error-related text
  const errorMatches = allText.match(/[^。]*?(?:失败|错误|不存在|密码|error|fail)[^。]*?/gi);
  console.log('\nError-related text:', errorMatches?.slice(0, 5));

  // Check console errors
  console.log('\nConsole errors:', consoleErrors.slice(0, 5));

  // Check if modal is still open (save failed) or closed (success)
  const modalStillOpen = await page.locator('.fixed.inset-0').last().locator('button:has-text("保存")').count();
  console.log('\nModal still open:', modalStillOpen > 0);

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
