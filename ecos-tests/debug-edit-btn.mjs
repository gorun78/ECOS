// Debug: find the edit button properly
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

  // Find the edit button — it's likely the second button (first is status toggle, second is edit, third is delete)
  const firstRow = page.locator('table tbody tr').first();
  const allBtns = firstRow.locator('button');
  const btnCount = await allBtns.count();
  console.log(`First row has ${btnCount} buttons`);

  // Check each button's HTML
  for (let i = 0; i < btnCount; i++) {
    const html = await allBtns.nth(i).innerHTML();
    const cls = await allBtns.nth(i).getAttribute('class');
    console.log(`  Button ${i}: class="${cls}", html="${html.substring(0, 80)}"`);
  }

  // Click the second button (index 1) — likely the edit button
  if (btnCount >= 2) {
    await allBtns.nth(1).click();
    await page.waitForTimeout(2000);
  }

  // Check what modal appeared
  const fixedPanels = await page.locator('.fixed.inset-0').count();
  console.log('\nFixed panels after edit click:', fixedPanels);

  if (fixedPanels > 0) {
    const modalText = await page.locator('.fixed.inset-0').last().textContent();
    console.log('Modal text (first 300):', modalText?.substring(0, 300));

    // Look for tabs in the modal
    const modalTabs = await page.locator('.fixed.inset-0').last().locator('button, [role="tab"]').allTextContents();
    console.log('Modal tabs:', modalTabs);
  }

} catch (e) {
  console.error('Error:', e.message);
} finally {
  await browser.close();
}
