import { chromium } from 'playwright';

(async () => {
  const b = await chromium.launch({ headless: true });
  const p = await b.newPage();
  await p.goto('http://localhost:3000/#/login');
  await p.waitForLoadState('networkidle');
  await p.fill('input[type="text"]', 'admin');
  await p.fill('input[type="password"]', 'admin123');
  await p.click('button[type="submit"]');
  await p.waitForTimeout(2000);
  await p.goto('http://localhost:3000/#/security-center');
  await p.waitForLoadState('networkidle');
  await p.waitForTimeout(2000);

  // Click audit tab — target tab button with border-b-2
  const tabBtn = p.locator('button[class*="border-b-2"]:has-text("安全审计")').first();
  if (await tabBtn.count() > 0) {
    await tabBtn.click();
  } else {
    const all = p.locator('button:has-text("安全审计")');
    const cnt = await all.count();
    await all.nth(cnt - 1).click();
  }
  await p.waitForTimeout(3000);

  // Screenshot
  await p.screenshot({ path: '/tmp/audit-tab.png', fullPage: true });
  console.log('Screenshot saved');

  // Get body text
  const txt = await p.textContent('body');
  console.log('Body text (first 800):', txt.substring(0, 800));

  await b.close();
})().catch(e => { console.error(e.message); process.exit(1); });
