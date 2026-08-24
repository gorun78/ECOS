// Debug: check what's in the modal after clicking edit
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

  // Check if we're on the users tab
  const url = page.url();
  console.log('URL:', url);

  // Check what tabs exist
  const tabButtons = await page.locator('button, [role="tab"]').allTextContents();
  console.log('All buttons/tabs (first 20):', tabButtons.slice(0, 20));

  // Check what rows exist
  const rows = await page.locator('table tbody tr').count();
  console.log('Table rows:', rows);

  // Check what buttons are in the first row
  if (rows > 0) {
    const rowBtns = await page.locator('table tbody tr').first().locator('button').count();
    console.log('First row buttons:', rowBtns);
    const rowBtnTexts = await page.locator('table tbody tr').first().locator('button').allTextContents();
    console.log('First row button texts:', rowBtnTexts);
  }

  // Check what the first row looks like
  const firstRowText = await page.locator('table tbody tr').first().textContent();
  console.log('First row text:', firstRowText?.substring(0, 200));

  // Take screenshot
  await page.screenshot({ path: '/tmp/iam-page-debug.png' });

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
